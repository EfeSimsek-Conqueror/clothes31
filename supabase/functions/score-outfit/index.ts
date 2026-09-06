// deno-lint-ignore-file no-explicit-any
// score-outfit v4 — "the brief dial".
//
// What changed, and why: in v3 the occasion was a word spliced into a prompt and
// the four subscores were fixed, so a wedding and a Tuesday were graded on the
// same axes with the same weights and nothing the user picked could move the
// number except by nudging the model's mood. v4 splits the work in two. The
// model is a WITNESS: it scores eight named axes with evidence, reports where
// the outfit itself sits on a formality ladder, and lists literal observations.
// The server is the JUDGE: it owns every weight, every cap and every floor, and
// it computes the dress-code axis outright as the distance between what the
// wearer asked for and what the photograph reads.
//
// Wire compatibility is absolute. `score`, `subscores` (four numeric keys),
// `hem_comment`, `swaps` (flat strings) and `annotations` keep their exact v3
// types forever — shipped iOS and Android builds hard-fail without them. The
// v4 payload is additive and gated on `client_features: ["axes_v4"]`, never on
// the response `version`, because neither shipped client reads it.

import { primaryAxes } from "../_shared/rubric/weights.ts";
import { briefLine, rubricId, rubricLabel } from "../_shared/rubric/brief.ts";
import {
  FORMALITY_CAPTIONS,
  PRESENCE_CAPTIONS,
  RUBRIC_VERSION,
} from "../_shared/rubric/rubric.v4.ts";
import { AXIS_LABELS } from "../_shared/rubric/rubric.v4.ts";
import type { AxisKey, Intake } from "../_shared/rubric/types.ts";
import { parseRequest } from "./intake.ts";
import { call, hasModelKey } from "./model.ts";
import {
  DRAW_PROMPT,
  JUDGE_SYSTEM,
  judgePrompt,
  judgeSchema,
  RESPONSE_SHAPE,
  SIGNALS_LIST_INSTRUCTION,
} from "./prompt.ts";
import { runEngine, type JudgeInput } from "./engine.ts";
import { normalizeJudge, toSignalMap } from "./judge.ts";
import { legacyAnnotations, legacySwaps, normalizeSwaps, type SwapV2 } from "./legacy.ts";
import type { SignalMap } from "./rules.ts";
import {
  sanitizeFitMap,
  sanitizeMarkupAnnotations,
  sanitizePalette,
  sanitizePieces,
} from "./sanitize.ts";

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

const MAX_RESCORES = 3;

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...CORS, "content-type": "application/json" },
  });
}

function buildV4(
  intake: Intake,
  engine: ReturnType<typeof runEngine>,
  judge: JudgeInput,
  locale: string,
  swapsV2: SwapV2[],
  pieces: ReturnType<typeof sanitizePieces>,
  palette: string[],
  rescoresRemaining: number,
) {
  const codeMarkers = dressCodeMarkers(intake, judge.signals);
  return {
    headline: {
      value: engine.headline,
      weighted_mean: engine.weighted_mean,
      band: engine.band,
      reason: engine.reason,
    },
    rubric: {
      id: rubricId(intake),
      version: RUBRIC_VERSION,
      occasion: intake.occasion,
      role: intake.role,
      venue: intake.venue,
      room: intake.room,
      on_feet: intake.on_feet,
      formality: intake.formality,
      formality_caption: FORMALITY_CAPTIONS[intake.occasion][intake.formality - 1],
      presence: intake.presence,
      presence_caption: PRESENCE_CAPTIONS[intake.presence - 1],
      label: rubricLabel(intake),
      brief_line: briefLine(intake, locale),
      primary_axes: primaryAxes(intake),
    },
    brief_verdict: engine.brief_verdict,
    axes: engine.axes,
    dress_code: {
      read: engine.ctx.read,
      claimed: engine.ctx.claimed,
      // The absolute rung the dial position implies. The gap is measured
      // against this, not against the dial, so the client has to show it or the
      // "you said X, the photo says Y" rows will not reconcile.
      target: engine.ctx.target,
      gap: engine.ctx.gap,
      severity: engine.ctx.severity,
      direction: engine.ctx.direction,
      line: engine.ctx.line,
      evidence: judge.formality_evidence ?? "",
      markers: codeMarkers,
      /** Rendered only where the brief actually carries a code. */
      render: intake.occasion === "wedding" || (intake.occasion === "work" && intake.formality >= 4) || Math.abs(engine.ctx.gap) >= 1,
    },
    presence_check: {
      ...engine.presence,
      evidence: judge.presence_evidence ?? "",
      render: Math.abs(engine.presence.gap) >= 1,
      mechanism: mechanismLine(engine),
    },
    score_breakdown: {
      weights_source: rubricId(intake),
      null_axes: engine.null_axes,
      discarded_axes: engine.discarded_axes,
      weight_deltas: engine.weight_deltas,
      raw_weighted: engine.raw_weighted,
      rules_fired: engine.rules_fired,
      caps_applied: engine.caps_applied,
      downgraded_to_caveat: engine.downgraded_to_caveat,
      final: engine.headline,
    },
    why_this_number: engine.why_this_number,
    lever: engine.lever
      ? {
        ...engine.lever,
        action: swapsV2.find((s) => s.axis === engine.lever!.axis)?.to ??
          swapsV2[0]?.to ?? `Lift ${engine.lever.label.toLowerCase()}.`,
      }
      : null,
    caveats: engine.caveats,
    intake_echo: intakeEcho(intake, engine),
    pieces,
    swaps_v2: swapsV2,
    signals: judge.signals,
    palette_hex: palette,
    intent: intake.intent,
    intent_axis: engine.intent_axis,
    intent_note: judge.intent_note ?? null,
    rescores_remaining: rescoresRemaining,
    scoring_version: "v4",
  };
}

function mechanismLine(engine: ReturnType<typeof runEngine>): string {
  if (engine.presence.gap === 0) return "";
  const axis: AxisKey = engine.presence.gap > 0 ? "COL" : "POV";
  const row = engine.axes.find((a) => a.key === axis);
  if (!row) return "";
  return `${row.label} carries ${Math.round(row.weight * 100)}% of this score at that setting.`;
}

/** The dress-code checklist. Only the markers that the brief actually requires
 * are listed, and a marker the photograph could not show is `null`, never a
 * cross. */
function dressCodeMarkers(intake: Intake, signals: SignalMap): Array<{ name: string; required: boolean; present: boolean | null; note?: string }> {
  const out: Array<{ name: string; required: boolean; present: boolean | null; note?: string }> = [];
  const val = (k: string) => signals[k]?.value?.toLowerCase().trim() ?? "";
  const has = (k: string) => ["true", "yes", "1"].includes(val(k));
  const feetVisible = has("feet_visible");

  if (intake.occasion === "wedding" && intake.formality === 5) {
    const markers = val("black_tie_markers");
    const errors = val("bt_code_errors");
    out.push({ name: "A faced lapel, or a floor-length hem", required: true, present: markers === "full" ? true : markers === "partial" ? null : false });
    out.push({ name: "A bow tie, not a long tie", required: true, present: errors.includes("long_necktie") ? false : markers === "full" ? true : null });
    out.push({ name: "Black formal footwear", required: true, present: feetVisible ? !errors.includes("brown_shoe") && !errors.includes("derby") : null, note: feetVisible ? undefined : "Feet out of frame." });
  }
  if (intake.occasion === "wedding" && intake.formality === 4) {
    out.push({ name: "Cocktail length, not floor length", required: true, present: val("hem_length") === "floor" ? false : val("hem_length") ? true : null });
    out.push({ name: "No denim", required: true, present: !has("denim_present") });
  }
  if (intake.occasion === "wedding" && intake.role !== "couple") {
    const white = val("white_coverage");
    out.push({ name: "Not white", required: true, present: white === "dominant" ? false : white ? true : null });
  }
  if (intake.occasion === "wedding" && intake.venue === "registry_or_worship") {
    out.push({ name: "Shoulders covered", required: true, present: signals["shoulder_coverage"] ? has("shoulder_coverage") : null });
  }
  if (intake.occasion === "work" && (intake.room === "corporate" || intake.room === "client_facing" || intake.formality >= 4)) {
    out.push({ name: "No denim", required: true, present: !has("denim_present") });
    out.push({ name: "No athletic shoes", required: true, present: feetVisible ? !has("athletic_sneakers") : null, note: feetVisible ? undefined : "Feet out of frame." });
    out.push({ name: "Nothing distressed or slogan-printed", required: true, present: !has("distressing") && !has("graphic_or_slogan") });
  }
  return out;
}

/** "You picked X → here is what X did." The section that exists so a user can
 * never say the app ignored their answer. */
function intakeEcho(intake: Intake, engine: ReturnType<typeof runEngine>) {
  const pct = (a: AxisKey) => `${Math.round((engine.weights[a] ?? 0) * 100)}%`;
  const top = engine.axes[0];
  const out: Array<{ label: string; effect: string }> = [];
  out.push({
    label: intake.occasion.charAt(0).toUpperCase() + intake.occasion.slice(1),
    effect: `${top.label} became the heaviest axis at ${pct(top.key)}`,
  });
  out.push({
    label: `${FORMALITY_CAPTIONS[intake.occasion][intake.formality - 1]} ${"●".repeat(intake.formality)}${"○".repeat(5 - intake.formality)}`,
    effect: `Dress code weighted ${pct("CTX")}`,
  });
  out.push({
    label: `${PRESENCE_CAPTIONS[intake.presence - 1]} ${"●".repeat(intake.presence)}${"○".repeat(5 - intake.presence)}`,
    effect: engine.presence.gap === 0
      ? "Matched — point of view credited"
      : `Point of view weighted ${pct("POV")}`,
  });
  if (intake.role) out.push({ label: intake.role.replace(/_/g, " "), effect: intake.role === "couple" ? "The white rule does not apply to you" : "Armed the white rule" });
  if (intake.venue) out.push({ label: intake.venue.replace(/_/g, " "), effect: `Footwear weighted ${pct("SHO")}` });
  if (intake.room) out.push({ label: intake.room.replace(/_/g, " "), effect: `Dress code weighted ${pct("CTX")}` });
  if (intake.on_feet) out.push({ label: intake.on_feet.replace(/_/g, " "), effect: `Footwear weighted ${pct("SHO")}` });
  if (intake.weather) out.push({ label: intake.weather.band, effect: `Fabric weighted ${pct("TEX")}` });
  out.push({ label: intake.time_of_day, effect: intake.time_of_day === "evening" ? "Evening fabrics rewarded" : "Daylight fabrics expected" });
  if (intake.intent && engine.intent_axis) {
    out.push({ label: `"${intake.intent}"`, effect: `${AXIS_LABELS[engine.intent_axis]} weighted up` });
  }
  out.push({
    label: intake.has_back ? "Front and back" : "Front only",
    effect: intake.has_back ? "Full fit read" : "Fit ceilinged at 8.5",
  });
  return out;
}

// ── /rescore ───────────────────────────────────────────────────────────────

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";

async function userIdFromJwt(jwt: string): Promise<string | null> {
  if (!SUPABASE_URL || !SERVICE_KEY) return null;
  const r = await fetch(`${SUPABASE_URL}/auth/v1/user`, {
    headers: { Authorization: `Bearer ${jwt}`, apikey: SERVICE_KEY },
  });
  if (!r.ok) return null;
  const j = await r.json();
  return typeof j?.id === "string" ? j.id : null;
}

async function rest(path: string, init: RequestInit = {}) {
  return await fetch(`${SUPABASE_URL}/rest/v1/${path}`, {
    ...init,
    headers: {
      apikey: SERVICE_KEY,
      Authorization: `Bearer ${SERVICE_KEY}`,
      "content-type": "application/json",
      ...(init.headers ?? {}),
    },
  });
}

/**
 * Re-run the engine against the stored witness with a corrected brief. No model
 * call, so it is free and sub-second — which is the whole point: rather than
 * interrogating the user up front, assume, then let them fix the assumption in
 * one tap and watch the number move.
 *
 * The outfit is loaded scoped to the caller's own user_id. That scoping is the
 * only thing standing between this route and a full-table read, because this is
 * the first time the function holds the service role key.
 */
async function handleRescore(req: Request, jwt: string): Promise<Response> {
  const uid = await userIdFromJwt(jwt);
  if (!uid) return json(401, { error: "unauthorized" });

  let body: any = {};
  try { body = await req.json(); } catch { /* noop */ }
  const outfitId = String(body?.outfit_id ?? "").trim();
  if (!outfitId) return json(400, { error: "missing_outfit_id" });

  const r = await rest(
    `outfits?id=eq.${encodeURIComponent(outfitId)}&user_id=eq.${encodeURIComponent(uid)}&select=id,raw_axes,signals,rescore_count,score,score_breakdown,photo_path,back_photo_path`,
  );
  if (!r.ok) return json(502, { error: "load_failed", detail: (await r.text()).slice(0, 200) });
  const rows = await r.json();
  if (!Array.isArray(rows) || rows.length === 0) return json(404, { error: "not_found" });
  const row = rows[0];

  const used = Number(row.rescore_count ?? 0);
  if (used >= MAX_RESCORES) return json(429, { error: "rescore_limit", limit: MAX_RESCORES });

  const witness = row.raw_axes;
  if (!witness || typeof witness !== "object" || !witness.axes) {
    return json(409, { error: "no_witness", detail: "This look was scored before the engine kept its working." });
  }

  const parsed = parseRequest({
    ...body,
    image_url: "rescore",
    back_url: witness.has_back ? "rescore-back" : "",
  });
  const judge = normalizeJudge(witness, sanitizePalette(witness.palette_hex));
  judge.signals = { ...toSignalMap({ signals: row.signals }), ...judge.signals };
  const engine = runEngine(parsed.intake, judge);

  const history = Array.isArray(row.score_breakdown?.history) ? row.score_breakdown.history : [];
  history.push({ from: row.score, to: engine.headline, rubric_id: rubricId(parsed.intake) });

  const v4 = buildV4(parsed.intake, engine, judge, parsed.locale, [], [], judge.palette_hex ?? [], MAX_RESCORES - used - 1);

  const patch = {
    // The row's own headline moves, but the Journal graphs the FIRST score —
    // a repair tap is for correcting a wrong answer, not for shopping.
    score: engine.headline,
    subscores: engine.subscores,
    intake: parsed.intake,
    rubric_id: rubricId(parsed.intake),
    axes: engine.axes,
    score_breakdown: { ...v4.score_breakdown, history },
    dress_code: v4.dress_code,
    presence_check: v4.presence_check,
    lever: v4.lever,
    caveats: engine.caveats,
    rescore_count: used + 1,
    scoring_version: "v4",
  };
  const up = await rest(`outfits?id=eq.${encodeURIComponent(outfitId)}&user_id=eq.${encodeURIComponent(uid)}`, {
    method: "PATCH",
    body: JSON.stringify(patch),
  });
  if (!up.ok) return json(502, { error: "save_failed", detail: (await up.text()).slice(0, 200) });

  return json(200, {
    score: engine.headline,
    subscores: engine.subscores,
    previous_score: row.score,
    ...v4,
    score_breakdown: { ...v4.score_breakdown, history },
  });
}

// ── entry ──────────────────────────────────────────────────────────────────

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });

  const auth = req.headers.get("Authorization") ?? "";
  if (!auth.toLowerCase().startsWith("bearer ")) return json(401, { error: "unauthorized" });
  const jwt = auth.slice(7).trim();

  const url = new URL(req.url);
  if (url.pathname.endsWith("/rescore")) return await handleRescore(req, jwt);

  if (!hasModelKey()) return json(500, { error: "server_misconfigured" });

  let body: any = {};
  try { body = await req.json(); } catch { /* noop */ }

  const parsed = parseRequest(body);
  if (!parsed.imageUrl) return json(400, { error: "missing_image_url" });

  const images = parsed.backUrl ? [parsed.imageUrl, parsed.backUrl] : [parsed.imageUrl];

  // Two calls in parallel. The score renders off JUDGE alone, so a truncated
  // drawing response costs an x-ray, not a read.
  const judgeCall = call({
    system: JUDGE_SYSTEM,
    prompt: [
      judgePrompt(parsed.intake, parsed.intake.has_back),
      "",
      SIGNALS_LIST_INSTRUCTION,
      "",
      RESPONSE_SHAPE,
    ].join("\n"),
    imageUrls: images,
    schema: judgeSchema(),
    temperature: 0.4,
  });
  const drawCall = parsed.wantsXray
    ? call({ prompt: DRAW_PROMPT, imageUrls: [parsed.imageUrl], temperature: 0.5 })
    : Promise.reject(new Error("skipped"));

  const [judgeSettled, drawSettled] = await Promise.allSettled([judgeCall, drawCall]);

  if (judgeSettled.status === "rejected") {
    return json(502, { error: "model_failed", detail: String(judgeSettled.reason).slice(0, 400) });
  }
  const judgeRaw = judgeSettled.value.json;
  const draw = drawSettled.status === "fulfilled" ? drawSettled.value.json : null;

  const palette = sanitizePalette(draw?.palette_hex ?? judgeRaw?.palette_hex);
  const judge = normalizeJudge(judgeRaw, palette);
  if (Object.keys(judge.axes).length === 0) {
    // Include what the model actually said when asked — a bare "parse_failed"
    // gives no way to tell a truncated response from a wrong-shaped one.
    return json(502, {
      error: "parse_failed",
      detail: "no axes returned",
      transport: judgeSettled.value.transport,
      ...(url.searchParams.get("debug") === "1"
        ? { raw: JSON.stringify(judgeRaw).slice(0, 1500) }
        : {}),
    });
  }

  const engine = runEngine(parsed.intake, judge);
  const swapsV2 = normalizeSwaps(judgeRaw?.swaps);
  const pieces = sanitizePieces(draw?.pieces);
  const markup = sanitizeMarkupAnnotations(draw?.markup_annotations);
  const fitMap = sanitizeFitMap(draw?.fit_map);

  let fitsYou: number | null = null;
  if (parsed.bodyProfile) {
    // Personalised fit is not a second opinion on the same photograph — it is
    // the engine's own fit and silhouette read, weighted the way this body
    // profile weights them. Making it up from a separate model number is how
    // "Fits you" ended up on screen without ever touching the score.
    const fit = engine.axes.find((a) => a.key === "FIT")?.score;
    const sil = engine.axes.find((a) => a.key === "SIL")?.score;
    const vals = [fit, sil].filter((v): v is number => v != null);
    if (vals.length > 0) fitsYou = Math.round((vals.reduce((s, v) => s + v, 0) / vals.length) * 10) / 10;
  }

  const out: Record<string, unknown> = {
    // ── v3 envelope. Never remove, never change type. ──────────────────────
    score: engine.headline,
    subscores: engine.subscores,
    hem_comment: String(judgeRaw?.hem_comment ?? "").slice(0, 200),
    swaps: legacySwaps(swapsV2),
    annotations: legacyAnnotations(pieces, engine.lever?.label ?? null, swapsV2[0]),
    version: "v4",
  };
  if (markup.length > 0) out.markup_annotations = markup;
  if (fitMap) out.fit_map = fitMap;
  if (fitsYou !== null) out.fits_you = fitsYou;

  if (parsed.wantsV4Envelope) {
    Object.assign(out, buildV4(
      parsed.intake, engine, judge, parsed.locale, swapsV2, pieces, palette, MAX_RESCORES,
    ));
    out.verdict = String(judgeRaw?.verdict ?? "").slice(0, 600);
    out.intake_warnings = parsed.warnings;
    // Stored so /rescore can replay the whole pipeline with no model call.
    out.raw_axes = {
      axes: judgeRaw?.axes ?? {},
      formality_read: judge.formality_read,
      formality_evidence: judge.formality_evidence,
      presence_read: judge.presence_read,
      presence_evidence: judge.presence_evidence,
      interp: judge.interp,
      intent_axis: judge.intent_axis,
      intent_met: judge.intent_met,
      intent_note: judge.intent_note,
      caveats: judge.caveats,
      palette_hex: palette,
      has_back: parsed.intake.has_back,
    };
    out.transport = judgeSettled.value.transport;
  }

  if (url.searchParams.get("debug") === "1") {
    out.debug = { judge: judgeRaw, draw, intake: parsed.intake };
  }

  return json(200, out);
});
