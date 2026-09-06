// deno-lint-ignore-file no-explicit-any
// compare-outfits v4 — two looks, judged on the clothes.
//
// v3 scored physique / pose / light / style / vibe. Three of those five judge
// the PHOTOGRAPH and one judges the BODY, so the number could not answer the
// only question a wearer actually asks: which outfit is better, and why. It also
// sat against this app's own rule, stated in `score-outfit`, that the clothes are
// on trial and the person never is.
//
// v4 judges both looks on the same nine axes the score sheet uses — silhouette,
// fit, colour, fabric, dress code, detail, footwear, upkeep, point of view — and
// weights them by the brief, so the winner is the outfit that better serves the
// occasion the wearer named rather than the shot that photographed better.
// Every axis carries its own evidence for each side, which is what turns "B wins"
// into "B wins because the shoulder sits right and the hem does not".
//
// Kept from v3, because it was right:
//   * BOTH orders are judged in parallel and averaged. Position bias is the
//     dominant failure mode of a vision judge; the same pair flips winner when
//     you swap the panels.
//   * The winner is DERIVED from the totals, never taken from the model, so the
//     headline can never contradict the breakdown.
//   * The client picks WHICH criteria to judge on; only those are scored.
//   * Position bias is the dominant failure mode of vision judges — the same
//     pair flips winner when you swap the order. So we run BOTH orders in
//     parallel (same wall-clock as one call) and average them. A pass that
//     fails or parses badly is simply dropped; one good pass still answers.
//   * The winner is DERIVED from the averaged totals, never taken on faith
//     from the model, so the headline can't contradict the breakdown. Inside
//     a 2-point band, or when the two passes disagree, it's a tie.
//   * Client-side scene hints ("mirror · indoor" / "outdoor") are passed in so
//     the model knows the shots were taken under different conditions.
//
// v1 fields (score_a / score_b on the 0-10 scale, comment, reason_a, reason_b)
// are still emitted so the Android client keeps working unchanged.

import { buildWeights } from "../_shared/rubric/weights.ts";
import {
  AXIS_LABELS,
  FORMALITY_CAPTIONS,
  SEED_FORMALITY,
  SEED_PRESENCE,
  TARGET_FORMALITY,
} from "../_shared/rubric/rubric.v4.ts";
import type { AxisKey, Intake, Occasion } from "../_shared/rubric/types.ts";

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers":
    "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

// Readable keys on purpose: a shipped client renders `key` capitalised, so
// these have to survive as English words without a lookup table.
const ALL_CRITERIA = [
  "silhouette", "fit", "colour", "fabric",
  "detail", "footwear", "upkeep", "point of view",
] as const;
type Criterion = typeof ALL_CRITERIA[number];

/// The axis each judged criterion weighs as. Dress code is absent because the
/// model never scores it — the server computes it from the formality gap.
const CRITERION_AXIS: Record<Criterion, AxisKey> = {
  "silhouette": "SIL",
  "fit": "FIT",
  "colour": "COL",
  "fabric": "TEX",
  "detail": "DTL",
  "footwear": "SHO",
  "upkeep": "PRS",
  "point of view": "POV",
};

const DRESS_CODE_KEY = "dress code";

const CRITERION_BRIEF: Record<Criterion, string> = {
  "silhouette":
    "silhouette — the outline the clothes make, where the break points sit, volume balance top against bottom, where the hems land",
  "fit":
    "fit — how the garments actually sit on this body: shoulder seam against the deltoid, pulling, sleeve length, trouser break, gaping, drape",
  "colour":
    "colour — hue relationships, whether a colour is echoed anywhere, neutral-to-accent ratio, contrast against the wearer's own colouring",
  "fabric":
    "fabric — sheen against matte, drape, weave, wrinkle character, whether two synthetics are stacked, seasonal weight",
  "detail":
    "detail — belt, tuck, cuff, layering, hardware, jewellery, bag scale, sock choice; absence counts as a decision",
  "footwear":
    "footwear — register, proportion to the hem and the leg, condition",
  "upkeep":
    "upkeep — pressing, storage creases, misbuttoning, an unfinished hem, a scuffed toe; cheap and immaculate outranks expensive and sloppy",
  "point of view":
    "point of view — whether a person with taste made these choices or a mannequin did",
};

/// Tie band: totals this close are noise, not a difference.
const TIE_BAND = 2;
/// A gap this wide stands even when the two passes named different winners —
/// the model's `winner` field is far noisier than the scores it hands back.
const DECISIVE = 6;

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...CORS, "content-type": "application/json" },
  });
}

function extractJson(text: string): any | null {
  if (!text) return null;
  try { return JSON.parse(text); } catch { /* noop */ }
  const fenced = text.match(/```(?:json)?\s*([\s\S]*?)```/i);
  if (fenced) {
    try { return JSON.parse(fenced[1]); } catch { /* noop */ }
  }
  const first = text.indexOf("{");
  const last = text.lastIndexOf("}");
  if (first >= 0 && last > first) {
    try { return JSON.parse(text.slice(first, last + 1)); } catch { /* noop */ }
  }
  return null;
}

const clamp100 = (n: any) => Math.max(0, Math.min(100, Math.round(Number(n) || 0)));

/// One judged pass, always expressed in terms of the FIRST and SECOND image
/// that were sent — the caller maps those back onto A and B.
interface Pass {
  scores: Record<string, { first: number; second: number; noteFirst: string; noteSecond: string }>;
  /// Where each look sits on the 1-5 formality ladder, judged without being told
  /// what the wearer asked for. The server does the comparing.
  formalityFirst: number;
  formalitySecond: number;
  winner: "first" | "second" | "tie";
  verdict: string;
  next_shot: string;
  reason_first: string;
  reason_second: string;
}

function buildPrompts(
  criteria: Criterion[],
  occasion: string,
  hintFirst: string,
  hintSecond: string,
  intent: string,
) {
  const systemPrompt = [
    "You are Hem — an editorial fashion critic for a monthly print magazine.",
    "Voice: terse, sharp, image-first. Never chatty. No emoji. No hedging.",
    "You are shown ONE contact sheet holding TWO different photographs, left and right of a dividing line. Judge the OUTFIT in each panel and score both on the given criteria.",
    "The two panels are two separate pictures — never describe them as one scene, and never give them the same note unless they are visibly identical.",
    "You are judging the CLOTHES, not the photograph and not the person. Framing, lighting, camera quality and the wearer's body are not on trial and must never move a score. If one shot is simply darker or further away, say what you can still see rather than marking the outfit down for it.",
    "SCORING RUBRIC (0-100, integers, use the whole range):",
    "95+ = magazine-worthy, nothing to fix. 80-94 = strong, one small flaw. 65-79 = solid but ordinary.",
    "50-64 = compromised — the criterion is visibly working against the shot. 30-49 = weak. Below 30 = broken.",
    "Most real phone snapshots land between 50 and 85. Do not cluster every score in the seventies.",
    "Score the two images INDEPENDENTLY against the rubric — do not score one relative to the other.",
    "Never comment on weight, body shape, face or attractiveness. The garments are on trial and the wearer never is.",
    "Every note must cite a garment: the shoulder seam, the hem, the break at the shoe, the collar, the shoe's toe shape. \"Looks better\" is not a note.",
    "TIE RULES (very important):",
    "- If the two images are the same photo, the same person in the same outfit, or visually indistinguishable, every score must match and winner must be 'tie'.",
    "- The same outfit shot from a slightly different angle is still a tie.",
    "- Only separate them when one shot is genuinely stronger. Never invent a difference.",
    "Output MUST be strict JSON. No prose outside the JSON. No markdown fences.",
  ].join(" ");

  const briefs = criteria.map((c) => CRITERION_BRIEF[c]).join("; ");
  const shape = criteria
    .map((c) => `"${c}": {"first": number, "second": number, "note_first": string, "note_second": string}`)
    .join(", ");

  const hints: string[] = [];
  if (hintFirst) hints.push(`the first image looks like: ${hintFirst}`);
  if (hintSecond) hints.push(`the second image looks like: ${hintSecond}`);
  const hintLine = hints.length
    ? `Shooting conditions detected on device — ${hints.join("; ")}. Take that into account instead of punishing a shot twice for the same condition. `
    : "";

  const intentLine = intent
    ? `The reader says these shots are for: "${intent}". Judge them against that goal, and say so plainly when a shot works everywhere except there. `
    : "";

  const userPrompt =
    `This image is a contact sheet: two separate photographs side by side, split by a vertical line. ` +
    `The LEFT panel is the first shot. The RIGHT panel is the second shot. Look at each panel on its own before scoring. ` +
    `The wearer is choosing between these two for: ${occasion}. Name that in the verdict. ` +
    `Do NOT score the criteria on how formal each look is — report that separately below and let the engine do the comparing. ` +
    hintLine + intentLine +
    `Score both on exactly these criteria: ${briefs}. ` +
    `Return strict JSON with this exact shape: ` +
    `{"scores": {${shape}}, ` +
    `where "note_first" judges ONLY the first image on that criterion and "note_second" judges ONLY the second image, each max 70 chars. ` +
    `Each note must state the concrete thing you see that earned that image its number — the stance, the crop, where the light falls. ` +
    `A note must never mention the other image, never repeat the number, and must match its own score: a low score needs a note that says what is wrong. ` +
    `"formality_first": 1-5 and "formality_second": 1-5 — where each OUTFIT ITSELF sits, ` +
    `independently of what it is for: 1 loungewear or errands, 2 everyday casual, 3 smart casual, ` +
    `4 dressed, tailored or cocktail, 5 formal — tuxedo, dinner jacket, floor-length gown. ` +
    `Report what you see; do not adjust either number toward the occasion named above. ` +
    `"winner": "first" | "second" | "tie", ` +
    `"verdict": string (max 200 chars, two sentences: name the stronger shot and what drags the other one down), ` +
    `"next_shot": string (max 160 chars, one concrete reshoot instruction for the weaker shot — what to change and roughly how many points it is worth), ` +
    `"reason_first": string (max 80 chars, one sharp line on the first image), ` +
    `"reason_second": string (max 80 chars, one sharp line on the second image)}. ` +
    `Return ONLY JSON, no prose, no code fences.`;

  return { systemPrompt, userPrompt };
}

async function judge(
  falKey: string,
  sheetUrl: string,
  firstUrl: string,
  secondUrl: string,
  criteria: Criterion[],
  occasion: string,
  hintFirst: string,
  hintSecond: string,
  intent: string,
): Promise<Pass | null> {
  const { systemPrompt, userPrompt } = buildPrompts(criteria, occasion, hintFirst, hintSecond, intent);
  let resp: Response;
  try {
    resp = await fetch("https://fal.run/fal-ai/any-llm/vision", {
      method: "POST",
      headers: { Authorization: `Key ${falKey}`, "content-type": "application/json" },
      body: JSON.stringify({
        model: "google/gemini-pro-1.5",
        prompt: userPrompt,
        // One frame only. `images: [...]` is silently ignored by this
        // endpoint, which is why shot B never reached the judge.
        image_url: sheetUrl || firstUrl,
        system_prompt: systemPrompt,
      }),
    });
  } catch {
    return null;
  }
  if (!resp.ok) return null;

  let falJson: any = null;
  try { falJson = JSON.parse(await resp.text()); } catch { return null; }

  const modelText: string =
    falJson?.output ?? falJson?.text ?? falJson?.response ??
    (typeof falJson === "string" ? falJson : "");

  const parsed = extractJson(modelText);
  if (!parsed || typeof parsed !== "object") return null;

  const rawScores = parsed.scores ?? parsed.criteria ?? {};
  const scores: Record<string, { first: number; second: number; noteFirst: string; noteSecond: string }> = {};
  for (const key of criteria) {
    const row = Array.isArray(rawScores)
      ? rawScores.find((r: any) => String(r?.key ?? r?.name ?? "").toLowerCase() === key)
      : rawScores?.[key];
    if (row == null) return null;  // incomplete pass — let the other one answer
    if (typeof row === "number") {
      scores[key] = { first: clamp100(row), second: clamp100(row), noteFirst: "", noteSecond: "" };
    } else {
      scores[key] = {
        first: clamp100(row?.first ?? row?.a ?? row?.score_a),
        second: clamp100(row?.second ?? row?.b ?? row?.score_b),
        noteFirst: String(row?.note_first ?? row?.note_a ?? "").slice(0, 100),
        noteSecond: String(row?.note_second ?? row?.note_b ?? "").slice(0, 100),
      };
    }
  }

  const ladder = (v: any, fallback = 3) => {
    const n = Math.round(Number(v));
    return isFinite(n) ? Math.max(1, Math.min(5, n)) : fallback;
  };

  const w = String(parsed.winner ?? "").toLowerCase().trim();
  return {
    scores,
    formalityFirst: ladder(parsed.formality_first),
    formalitySecond: ladder(parsed.formality_second),
    winner: w === "first" || w === "a" ? "first" : (w === "second" || w === "b" ? "second" : "tie"),
    verdict: String(parsed.verdict ?? parsed.comment ?? "").slice(0, 240),
    next_shot: String(parsed.next_shot ?? "").slice(0, 200),
    reason_first: String(parsed.reason_first ?? parsed.reason_a ?? "").slice(0, 120),
    reason_second: String(parsed.reason_second ?? parsed.reason_b ?? "").slice(0, 120),
  };
}

/// Rewrites a swapped pass (B sent first) back into A/B terms.
/// The swapped pass saw B first, so "the first shot" in its prose means B.
/// Rewriting the sides inside the note is what keeps the copy honest.
function swapSides(text: string): string {
  return text
    .replace(/\bshot A\b/gi, "\u0000")
    .replace(/\bshot B\b/gi, "shot A")
    .replace(/\u0000/g, "shot B")
    .replace(/\bthe first (shot|image|frame)\b/gi, "\u0001$1")
    .replace(/\bthe second (shot|image|frame)\b/gi, "the first $1")
    .replace(/\u0001(shot|image|frame)/gi, "the second $1");
}

function unswap(p: Pass): Pass {
  const scores: Record<string, { first: number; second: number; noteFirst: string; noteSecond: string }> = {};
  for (const [k, v] of Object.entries(p.scores)) {
    scores[k] = { first: v.second, second: v.first, noteFirst: v.noteSecond, noteSecond: v.noteFirst };
  }
  return {
    scores,
    formalityFirst: p.formalitySecond,
    formalitySecond: p.formalityFirst,
    winner: p.winner === "first" ? "second" : (p.winner === "second" ? "first" : "tie"),
    verdict: swapSides(p.verdict),
    next_shot: swapSides(p.next_shot),
    reason_first: p.reason_second,
    reason_second: p.reason_first,
  };
}

/**
 * The brief the two looks are being judged against.
 *
 * A comparison without one is not answerable: the tailored look wins a wedding
 * and loses a Saturday, and saying "B is better" without naming the room is the
 * vagueness this rewrite exists to remove. A client that sends nothing still
 * gets a coherent answer on the everyday rubric.
 */
function parseIntake(raw: any, occasionWord: string): Intake {
  const OCC: Occasion[] = ["work", "date", "wedding", "casual", "everyday"];
  const pick = (v: unknown): Occasion => {
    const t = String(v ?? "").toLowerCase().trim();
    return (OCC as string[]).includes(t) ? t as Occasion : "everyday";
  };
  const occasion = pick(raw?.occasion ?? occasionWord);
  const dial = (v: unknown, fallback: number) => {
    const n = Math.round(Number(v));
    return isFinite(n) ? Math.max(1, Math.min(5, n)) : fallback;
  };
  return {
    occasion,
    formality: dial(raw?.formality, SEED_FORMALITY[occasion]),
    presence: dial(raw?.presence, SEED_PRESENCE[occasion]),
    role: null, venue: null, room: null, on_feet: null,
    intent: null, weather: null,
    time_of_day: raw?.time_of_day === "evening" ? "evening" : "daytime",
    has_back: false,
  };
}

/**
 * Dress code, computed rather than judged — the same arithmetic the score sheet
 * uses. The model says where a look sits; the engine measures the distance to
 * where the wearer said they were going.
 */
function dressCodeScore(read: number, intake: Intake): number {
  const target = TARGET_FORMALITY[intake.occasion][intake.formality - 1] ?? intake.formality;
  const gap = Math.abs(read - target);
  return Math.max(0, Math.min(100, Math.round((9.0 - 1.9 * gap) * 10)));
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });

  const auth = req.headers.get("Authorization") ?? "";
  if (!auth.toLowerCase().startsWith("bearer ")) return json(401, { error: "unauthorized" });

  const FAL_KEY = Deno.env.get("FAL_KEY");
  if (!FAL_KEY) return json(500, { error: "server_misconfigured" });

  let body: any = {};
  try { body = await req.json(); } catch { /* noop */ }
  const a = (body?.image_a_url ?? "").toString().trim();
  const b = (body?.image_b_url ?? "").toString().trim();
  // Both injected strings are collapsed and capped — they land inside the
  // prompt, so a newline-stuffed value would be a prompt-injection hole.
  const occasion =
    (body?.occasion ?? "everyday").toString().replace(/\s+/g, " ").trim().slice(0, 48) || "everyday";
  if (!a || !b) return json(400, { error: "missing_images" });

  // Which criteria to judge on. Unknown or empty -> all five.
  const requested: string[] = Array.isArray(body?.focus) ? body.focus : [];
  const picked = requested
    .map((f) => String(f).toLowerCase().trim())
    .filter((f): f is Criterion => (ALL_CRITERIA as readonly string[]).includes(f));
  const criteria: Criterion[] = picked.length
    ? ALL_CRITERIA.filter((c) => picked.includes(c))   // keep canonical order
    : [...ALL_CRITERIA];

  const intake = parseIntake(body?.intake, occasion);

  const hintA = (body?.scene_a ?? "").toString().trim().slice(0, 40);
  const hintB = (body?.scene_b ?? "").toString().trim().slice(0, 40);
  // Free-text purpose ("first day at a new office"), sharper than the occasion chip.
  const intent = (body?.intent ?? "").toString().replace(/\s+/g, " ").trim().slice(0, 160);

  // Both orders, in parallel — one call's latency, no position bias.
  // The client renders the pair twice, in both orders, so neither position
  // can decide the winner. Without a sheet there is nothing to compare.
  const sheetAB = (body?.sheet_ab_url ?? "").toString().trim();
  const sheetBA = (body?.sheet_ba_url ?? "").toString().trim();
  if (!sheetAB) return json(400, { error: "missing_sheet" });

  const [straight, swapped] = await Promise.all([
    judge(FAL_KEY, sheetAB, a, b, criteria, occasion, hintA, hintB, intent),
    sheetBA
      ? judge(FAL_KEY, sheetBA, b, a, criteria, occasion, hintB, hintA, intent)
      : Promise.resolve(null),
  ]);

  const passes: Pass[] = [];
  if (straight) passes.push(straight);
  if (swapped) passes.push(unswap(swapped));
  if (!passes.length) return json(502, { error: "judge_failed" });

  const scored: Array<{ key: string; a: number; b: number; note_a: string; note_b: string }> =
    criteria.map((key) => {
    const av = Math.round(passes.reduce((s, p) => s + p.scores[key].first, 0) / passes.length);
    const bv = Math.round(passes.reduce((s, p) => s + p.scores[key].second, 0) / passes.length);
    // Each note describes one frame only, so the pass that scored that frame
    // closest to the average is the one whose words fit the number shown.
    const pick = (side: "first" | "second", target: number) =>
      passes
        .filter((p) => p.scores[key][side === "first" ? "noteFirst" : "noteSecond"])
        .sort((x, y) =>
          Math.abs(x.scores[key][side] - target) - Math.abs(y.scores[key][side] - target)
        )[0]?.scores[key][side === "first" ? "noteFirst" : "noteSecond"] ?? "";
    return { key, a: av, b: bv, note_a: pick("first", av), note_b: pick("second", bv) };
  });

  // Dress code joins the table as a computed row, from the formality each look
  // was read at averaged across the passes.
  const readA = Math.round(passes.reduce((s, p) => s + p.formalityFirst, 0) / passes.length);
  const readB = Math.round(passes.reduce((s, p) => s + p.formalitySecond, 0) / passes.length);
  const targetRung = TARGET_FORMALITY[intake.occasion][intake.formality - 1] ?? intake.formality;
  const step = (r: number) =>
    r === targetRung ? "on the mark" : r < targetRung ? `${targetRung - r} under` : `${r - targetRung} over`;
  scored.push({
    key: DRESS_CODE_KEY,
    a: dressCodeScore(readA, intake),
    b: dressCodeScore(readB, intake),
    note_a: `Reads ${step(readA)} for ${FORMALITY_CAPTIONS[intake.occasion][intake.formality - 1].toLowerCase()}.`,
    note_b: `Reads ${step(readB)} for ${FORMALITY_CAPTIONS[intake.occasion][intake.formality - 1].toLowerCase()}.`,
  });

  // Weighted by the brief, not averaged flat. A flat mean says footwear settles
  // a wedding as firmly as the dress code does, which is how a comparison ends
  // up unable to explain itself.
  const axisOf = (key: string): AxisKey =>
    key === DRESS_CODE_KEY ? "CTX" : CRITERION_AXIS[key as Criterion];
  const { w } = buildWeights(intake);
  const present = scored.map((c) => axisOf(c.key));
  const shareTotal = present.reduce((s, a) => s + w[a], 0) || 1;

  const rows = scored.map((c) => {
    const axis = axisOf(c.key);
    const share = w[axis] / shareTotal;
    return {
      ...c,
      label: AXIS_LABELS[axis],
      weight: Math.round(share * 1000) / 1000,
      // What this row actually moved the result by, which is the only honest
      // answer to "why did that one win".
      swing: Math.round((c.a - c.b) * share * 10) / 10,
    };
  });

  const weighted = (side: "a" | "b") =>
    Math.round(rows.reduce((s, r) => s + r[side] * r.weight, 0));
  const totalA = weighted("a");
  const totalB = weighted("b");
  const margin = totalA - totalB;

  // The two passes disagreeing is the honest signal that this is a coin flip.
  const disagreed =
    passes.length === 2 &&
    passes[0].winner !== passes[1].winner &&
    passes[0].winner !== "tie" &&
    passes[1].winner !== "tie";

  const gap = Math.abs(margin);
  let winner: "A" | "B" | "tie";
  if (gap <= TIE_BAND) winner = "tie";
  else if (disagreed && gap < DECISIVE) winner = "tie";
  else winner = margin > 0 ? "A" : "B";

  const confidence = winner === "tie"
    ? "low"
    : (disagreed ? "low" : (gap >= 8 ? "high" : "medium"));

  // Prefer the copy from a pass that agrees with the derived winner.
  const voice =
    passes.find((p) =>
      (winner === "A" && p.winner === "first") ||
      (winner === "B" && p.winner === "second") ||
      (winner === "tie" && p.winner === "tie")
    ) ?? passes[0];

  // A pass that crowned a winner can't narrate a draw — the headline and the
  // copy would contradict each other. Say the honest thing instead.
  const namesAWinner = /\b(first|second|shot a|shot b)\b/i.test(voice.verdict);
  const verdict = winner === "tie" && (voice.winner !== "tie" || namesAWinner)
    ? "Nothing separates these two — judged in both orders, they trade the lead. Call it a draw."
    : voice.verdict;

  // The sentence that answers "what decided this". Written from the arithmetic,
  // never by the model, so it can never disagree with the table above it.
  const decisive = [...rows]
    .filter((r) => Math.abs(r.swing) >= 0.5)
    .sort((x, y) => Math.abs(y.swing) - Math.abs(x.swing))
    .slice(0, 2);
  const side = (n: number) => (n > 0 ? "A" : "B");
  const why = winner === "tie"
    ? decisive.length
      ? `They trade the lead: ${decisive.map((r) => `${r.label.toLowerCase()} goes to ${side(r.swing)}`).join(", ")}. Nothing separates them overall.`
      : "Every axis lands within a point. There is nothing to choose between them."
    : decisive.length
    ? `${winner} wins on ${decisive.map((r) => r.label.toLowerCase()).join(" and ")}. ` +
      `${decisive[0].label} carries ${Math.round(decisive[0].weight * 100)}% of a ${intake.occasion} brief, ` +
      `and that is where the gap is.`
    : `${winner} takes it, but narrowly and on no single axis.`;

  return json(200, {
    winner,
    // v3
    total_a: totalA,
    total_b: totalB,
    criteria: rows,
    verdict,
    next_shot: voice.next_shot,
    focus: criteria,
    occasion,
    intent,
    // v4
    axes: rows,
    why,
    dress_code: {
      target: targetRung,
      caption: FORMALITY_CAPTIONS[intake.occasion][intake.formality - 1],
      read_a: readA,
      read_b: readB,
    },
    rubric: {
      occasion: intake.occasion,
      formality: intake.formality,
      presence: intake.presence,
    },
    margin: Math.abs(margin),
    confidence,
    passes: passes.length,
    // v1 compatibility
    score_a: totalA / 10,
    score_b: totalB / 10,
    comment: verdict.slice(0, 160),
    reason_a: voice.reason_first,
    reason_b: voice.reason_second,
    version: "v4",
  });
});
