// deno-lint-ignore-file no-explicit-any
// suggest-outfits v1: given a dress code, event type, closet inventory
// (up to top 30 items), and optional body profile + style tags, ask
// Fal any-llm (google/gemini-pro-1.5, text-only) to build 3 combos.
//
// Request:
//   { dress_code, event_type, notes?, body_profile?, closet_items: [{id,name,category,subcategory,image_url}], style_tags? }
// Response: { combos: [{ score, piece_ids, rationale, gap|null }], engine, error? }

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers":
    "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

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
    const slice = text.slice(first, last + 1);
    try { return JSON.parse(slice); } catch { /* noop */ }
  }
  return null;
}

function clampScore(n: unknown): number {
  const v = Number(n);
  if (!isFinite(v)) return 0;
  return Math.max(0, Math.min(100, Math.round(v)));
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });

  const auth = req.headers.get("Authorization") ?? "";
  if (!auth.toLowerCase().startsWith("bearer ")) {
    return json(401, { error: "unauthorized" });
  }

  const FAL_KEY = Deno.env.get("FAL_KEY");
  if (!FAL_KEY) return json(500, { error: "server_misconfigured" });

  let body: any = {};
  try { body = await req.json(); } catch { /* noop */ }

  const dress_code = (body?.dress_code ?? "").toString().trim() || "smart_casual";
  const event_type = (body?.event_type ?? "").toString().trim() || "other";
  const notes = (body?.notes ?? "").toString().trim().slice(0, 400);
  const styleTags: string[] = Array.isArray(body?.style_tags)
    ? body.style_tags.slice(0, 12).map((t: unknown) => String(t).slice(0, 40))
    : [];
  const bodyProfile = body?.body_profile && typeof body.body_profile === "object"
    ? body.body_profile
    : null;

  const closetIn: any[] = Array.isArray(body?.closet_items) ? body.closet_items : [];
  const closet = closetIn.slice(0, 30).map((it) => ({
    id: String(it?.id ?? "").slice(0, 64),
    name: String(it?.name ?? "").slice(0, 80),
    category: String(it?.category ?? "").slice(0, 40),
    subcategory: it?.subcategory ? String(it.subcategory).slice(0, 40) : null,
  })).filter((it) => it.id && (it.name || it.category));

  if (closet.length === 0) {
    return json(400, { error: "missing_closet_items" });
  }

  const engine = "fal:google/gemini-pro-1.5";

  const bodyLine = bodyProfile
    ? `User baseline: shape=${String(bodyProfile.body_shape ?? "unknown")}, ` +
      `season=${String(bodyProfile.coloring_season ?? "unknown")}, ` +
      `undertone=${String(bodyProfile.skin_undertone ?? "unknown")}, ` +
      `palette=${JSON.stringify(bodyProfile.palette_hex ?? [])}. `
    : "";
  const tagsLine = styleTags.length > 0
    ? `Style tags user prefers: ${styleTags.join(", ")}. `
    : "";
  const notesLine = notes ? `Event vibe note: "${notes.replace(/"/g, '\\"')}". ` : "";

  const systemPrompt = [
    "You are a personal stylist assembling outfits from a real closet inventory.",
    "You must ONLY use piece ids that appear in the provided inventory. Never invent ids.",
    "Rationales read like a stylist's one-line margin note: concrete, image-first, no advice-column tone.",
    "Output MUST be strict JSON matching the requested schema. No prose outside the JSON. No markdown fences.",
  ].join(" ");

  const closetJson = JSON.stringify(closet);

  const userPrompt =
    `Given this closet, pick 3 outfit combinations that suit dress_code="${dress_code}" for event_type="${event_type}". ` +
    `Each combo MUST have: top, bottom, shoes. Optionally add outerwear and/or an accessory. ` +
    `Score each combination 0-100 for match quality (100 = flawless for this dress code and event). ` +
    `${bodyLine}${tagsLine}${notesLine}` +
    `Return strict JSON with this exact shape:\n` +
    `{ "combos": [ { "score": 0-100 integer, "piece_ids": [id, id, ...], "rationale": "one sentence", "gap": string|null } ] }\n` +
    `piece_ids MUST be a subset of the ids in the CLOSET below. ` +
    `If the closet is missing a critical piece for this dress code (e.g. no formal shoes for black_tie), set "gap" to a one-line description of what to add; otherwise gap=null. ` +
    `Return exactly 3 combos. Return ONLY the JSON object, no prose, no code fences.\n\n` +
    `CLOSET: ${closetJson}`;

  // Text-only Fal any-llm. Endpoint is fal-ai/any-llm (no /vision suffix).
  const falResp = await fetch("https://fal.run/fal-ai/any-llm", {
    method: "POST",
    headers: {
      Authorization: `Key ${FAL_KEY}`,
      "content-type": "application/json",
    },
    body: JSON.stringify({
      model: "google/gemini-pro-1.5",
      prompt: userPrompt,
      system_prompt: systemPrompt,
    }),
  });

  const rawText = await falResp.text();
  if (!falResp.ok) {
    return json(502, { error: "fal_error", status: falResp.status, detail: rawText.slice(0, 800), engine });
  }

  let falJson: any = null;
  try { falJson = JSON.parse(rawText); } catch {
    return json(502, { error: "fal_bad_json", raw: rawText.slice(0, 500), engine });
  }

  const modelText: string =
    falJson?.output ??
    falJson?.text ??
    falJson?.response ??
    (typeof falJson === "string" ? falJson : "");

  const parsed = extractJson(modelText);
  if (!parsed || typeof parsed !== "object") {
    return json(502, { error: "parse_failed", raw: String(modelText).slice(0, 800), engine });
  }

  const validIds = new Set(closet.map((c) => c.id));
  const combosIn: any[] = Array.isArray(parsed.combos) ? parsed.combos : [];
  const combos = combosIn.slice(0, 3).map((c: any) => {
    const pidsRaw: any[] = Array.isArray(c?.piece_ids) ? c.piece_ids : [];
    const piece_ids = pidsRaw
      .map((p) => String(p))
      .filter((p) => validIds.has(p))
      .slice(0, 6);
    const rationale = String(c?.rationale ?? "").slice(0, 240);
    const gapRaw = c?.gap;
    const gap = (gapRaw === null || gapRaw === undefined || gapRaw === "")
      ? null
      : String(gapRaw).slice(0, 200);
    return {
      score: clampScore(c?.score),
      piece_ids,
      rationale,
      gap,
    };
  });

  return json(200, { combos, engine });
});
