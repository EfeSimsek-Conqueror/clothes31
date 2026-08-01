// deno-lint-ignore-file no-explicit-any
// score-outfit v3: adds annotation markup (arrow/line/focus/swap),
// fit_map (16x32 tension grid + hotspots), fits_you (personalized fit score
// when body_profile provided), intent-aware critique, and back-view scoring.
// Proxies to Fal.ai vision LLM to score an outfit photo as Hem.

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers":
    "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

const HONESTY_LINES: Record<string, string> = {
  kind: "Be generous but specific. Never harsh.",
  honest:
    "Be direct and specific. Praise real strengths, name real weaknesses.",
  brutal:
    "Roast the outfit like an editor with a deadline. Never insult the person — only the clothes. Every roast must end with a fix.",
};

const MARKUP_TYPES = new Set(["arrow", "line", "focus", "swap"]);
const FIT_COLS = 16;
const FIT_ROWS = 32;

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...CORS, "content-type": "application/json" },
  });
}

function extractJson(text: string): any | null {
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch { /* fall through */ }
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

function clamp01(n: unknown, fallback = 0.5): number {
  const v = Number(n);
  if (!isFinite(v)) return fallback;
  return Math.max(0, Math.min(1, v));
}

function clampRange(n: unknown, lo: number, hi: number, fallback: number): number {
  const v = Number(n);
  if (!isFinite(v)) return fallback;
  return Math.max(lo, Math.min(hi, v));
}

function normalizePoint(p: any): [number, number] | null {
  if (!p) return null;
  if (Array.isArray(p) && p.length >= 2) return [clamp01(p[0]), clamp01(p[1])];
  if (typeof p === "object" && "x" in p && "y" in p) return [clamp01(p.x), clamp01(p.y)];
  return null;
}

function sanitizeMarkupAnnotations(raw: any): any[] {
  if (!Array.isArray(raw)) return [];
  const out: any[] = [];
  for (const a of raw.slice(0, 6)) {
    const type = String(a?.type ?? "").toLowerCase().trim();
    if (!MARKUP_TYPES.has(type)) continue;
    const note = String(a?.note ?? "").slice(0, 200);
    const confidence = clamp01(a?.confidence, 0.7);
    const coordsRaw = a?.coords ?? {};
    const coords: any = {};
    if (type === "arrow" || type === "line") {
      const from = normalizePoint(coordsRaw.from);
      const to = normalizePoint(coordsRaw.to);
      if (!from || !to) continue;
      coords.from = from;
      coords.to = to;
    } else if (type === "focus") {
      const at = normalizePoint(coordsRaw.at);
      if (!at) continue;
      coords.at = at;
      coords.radius = clamp01(coordsRaw.radius, 0.08);
    } else if (type === "swap") {
      const at = normalizePoint(coordsRaw.at);
      if (!at) continue;
      coords.at = at;
    }
    out.push({ type, coords, note, confidence });
  }
  return out;
}

function sanitizeFitMap(raw: any): any | null {
  if (!raw || typeof raw !== "object") return null;
  const gridIn = raw.grid;
  if (!Array.isArray(gridIn)) return null;

  // Coerce to FIT_ROWS x FIT_COLS. Accept smaller/larger and resample naively.
  const rowsIn = gridIn.length;
  const grid: number[][] = [];
  for (let r = 0; r < FIT_ROWS; r++) {
    const srcR = Math.min(rowsIn - 1, Math.floor((r / FIT_ROWS) * rowsIn));
    const srcRow = Array.isArray(gridIn[srcR]) ? gridIn[srcR] : [];
    const row: number[] = [];
    const colsIn = srcRow.length || 1;
    for (let c = 0; c < FIT_COLS; c++) {
      const srcC = Math.min(colsIn - 1, Math.floor((c / FIT_COLS) * colsIn));
      row.push(clampRange(srcRow[srcC], -1, 1, 0));
    }
    grid.push(row);
  }

  const hotspotsIn: any[] = Array.isArray(raw.hotspots) ? raw.hotspots : [];
  const hotspots = hotspotsIn.slice(0, 3).map((h: any) => {
    const at = normalizePoint(h?.at) ?? [0.5, 0.5];
    const label = String(h?.label ?? "").slice(0, 80);
    const severity = clampRange(h?.severity, -1, 1, 0);
    return { at, label, severity };
  }).filter((h: any) => h.label.length > 0);

  return {
    resolution: [FIT_COLS, FIT_ROWS],
    grid,
    hotspots,
  };
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: CORS });
  }

  const auth = req.headers.get("Authorization") ?? "";
  if (!auth.toLowerCase().startsWith("bearer ")) {
    return json(401, { error: "unauthorized" });
  }

  const FAL_KEY = Deno.env.get("FAL_KEY");
  if (!FAL_KEY) {
    return json(500, { error: "server_misconfigured" });
  }

  let body: any = {};
  try {
    body = await req.json();
  } catch { /* noop */ }

  const image_url = (body?.image_url ?? "").toString().trim();
  const back_url = (body?.back_url ?? "").toString().trim();
  const occasion = (body?.occasion ?? "everyday").toString().trim() || "everyday";
  const honestyRaw = (body?.honesty ?? "honest").toString().trim().toLowerCase();
  const honesty = HONESTY_LINES[honestyRaw] ? honestyRaw : "honest";
  const intent = (body?.intent ?? "").toString().trim().slice(0, 300);
  const bodyProfile = body?.body_profile && typeof body.body_profile === "object"
    ? body.body_profile
    : null;

  if (!image_url) {
    return json(400, { error: "missing_image_url" });
  }

  const systemPrompt = [
    "You are Hem — an editorial fashion critic for a monthly print magazine.",
    "Voice: terse, sharp, image-first. Never chatty. No emoji. No hedging.",
    "Your comments read like a one-sentence pull-quote in a style feature: concrete nouns, specific fabrics, cuts, colors, silhouette. Never advice-column.",
    HONESTY_LINES[honesty],
    "Scoring calibration (IMPORTANT — do NOT be a harsh grader):",
    "10 = magazine cover material; 9 = editorial-ready; 8 = confident and considered; 7 = solid everyday look with one weak note; 6 = works but forgettable; 5 = a fixable miss; 4 = wrong pieces together; 3 = mismatch of context; 2 = careless; 1 = essentially unstyled.",
    "Most real casual looks land 6–8. Reserve 3 and below for genuine disasters — never punish an honest daytime outfit into the 2s just because it isn't formal.",
    "Weigh CONTEXT: if occasion is 'everyday' or 'casual', a t-shirt and shorts is a 7 unless the fit or color choice fails. Do not conflate 'not fancy' with 'bad'.",
    "Annotation scores follow the same scale — individual pieces should mostly land 5–9 for real garments; only give 1–3 to genuinely broken items.",
    "The overall score should roughly equal the average of your annotation scores, weighted toward the largest visible garments.",
    "Every piece-annotation MUST include a 'note' — ONE concrete, actionable fix in 60 characters or fewer, phrased like a stylist's margin scribble.",
    "Output MUST be strict JSON matching the requested schema. No prose outside the JSON. No markdown fences.",
  ].join(" ");

  // Extended prompt sections
  const markupInstruction =
    `"markup_annotations": [{"type":"arrow"|"line"|"focus"|"swap", "coords": <shape below>, "note": string (8-12 words, editorial voice), "confidence": number 0-1}]. ` +
    `Add annotation markup for the eye path, proportion breaks, best detail worth defending, and highest-ROI swap. ` +
    `Coords are normalized [0,1] with origin top-left. ` +
    `arrow/line coords: {"from":[x,y], "to":[x,y]}. focus coords: {"at":[x,y], "radius": number 0-1}. swap coords: {"at":[x,y]}. ` +
    `Keep captions editorial, 8-12 words. Between 3 and 6 items.`;

  const fitMapInstruction =
    `"fit_map": {"resolution":[${FIT_COLS},${FIT_ROWS}], "grid": number[${FIT_ROWS}][${FIT_COLS}] each in [-1,1], "hotspots":[{"at":[x,y],"label":string,"severity":number [-1,1]}]}. ` +
    `Add fit_map — approximate garment tension across a ${FIT_COLS}x${FIT_ROWS} grid (${FIT_COLS} cols by ${FIT_ROWS} rows). ` +
    `-1 = pooling/loose, +1 = tight/pulling, 0 = neutral. ` +
    `Fill the grid row-by-row top to bottom over the whole frame. Add 1-3 hotspots with human-readable labels (e.g. "waistband pulls", "hem pools at ankle").`;

  const bodyProfileInstruction = bodyProfile
    ? `The user's baseline: ${String(bodyProfile.body_shape ?? "unknown")}, ${String(bodyProfile.coloring_season ?? "unknown")}, palette ${JSON.stringify(bodyProfile.palette_hex ?? [])}. ` +
      `Add a "fits_you" number 0-10 for how well this outfit works with THEIR proportions and coloring specifically.`
    : "";

  const intentInstruction = intent
    ? `The user's stated intent for this look: '${intent.replace(/'/g, "\\'")}'. Weight the critique against the intent. If they hit it, score higher. If contradiction, name the mismatch in hem_comment.`
    : "";

  const backInstruction = back_url
    ? `A second photo is the back view (see additional image). Rate both views together; note back-specific issues (belt sit, jacket vent, trouser drape) in hem_comment or annotations.`
    : "";

  const userPrompt =
    `Score this outfit for a ${occasion} occasion out of 10 (one decimal). ` +
    `Return strict JSON with this exact shape: ` +
    `{"score": number, ` +
    `"subscores": {"color": number, "fit": number, "style_match": number, "seasonal": number}, ` +
    `"hem_comment": string (max 140 chars, one sentence, editorial voice), ` +
    `"swaps": ["...", "..."] (max 2 swap suggestions), ` +
    `"annotations": [{"x_pct": number, "y_pct": number, "label": string, "score": number, "note": string}], ` +
    `${markupInstruction}, ` +
    `${fitMapInstruction}` +
    (bodyProfile ? `, "fits_you": number 0-10` : "") +
    `}. ` +
    `The annotations array MUST have between 3 and 6 items. Each annotation pins a critique to a garment area on the image: ` +
    `x_pct and y_pct are normalized image coordinates from 0 to 100 (top-left origin). ` +
    `label is a short lowercase token like "top", "trousers", "shoes". ` +
    `score is 0..10 for that specific piece/detail. ` +
    `note is ONE concrete fix, max 60 characters. ` +
    (bodyProfileInstruction ? bodyProfileInstruction + " " : "") +
    (intentInstruction ? intentInstruction + " " : "") +
    (backInstruction ? backInstruction + " " : "") +
    `Return ONLY JSON, no prose, no code fences.`;

  const falBody: any = {
    model: "google/gemini-pro-1.5",
    prompt: userPrompt,
    image_url,
    system_prompt: systemPrompt,
  };
  if (back_url) {
    // Fal any-llm/vision accepts an array of image_urls on multi-image models.
    falBody.image_urls = [image_url, back_url];
  }

  const falResp = await fetch("https://fal.run/fal-ai/any-llm/vision", {
    method: "POST",
    headers: {
      Authorization: `Key ${FAL_KEY}`,
      "content-type": "application/json",
    },
    body: JSON.stringify(falBody),
  });

  const rawText = await falResp.text();
  if (!falResp.ok) {
    return json(502, { error: "fal_error", status: falResp.status, detail: rawText.slice(0, 500) });
  }

  let falJson: any = null;
  try {
    falJson = JSON.parse(rawText);
  } catch {
    return json(502, { error: "fal_bad_json", raw: rawText.slice(0, 500) });
  }

  const modelText: string =
    falJson?.output ??
    falJson?.text ??
    falJson?.response ??
    (typeof falJson === "string" ? falJson : "");

  const parsed = extractJson(modelText);
  if (!parsed || typeof parsed !== "object") {
    return json(502, { error: "parse_failed", raw: modelText.slice(0, 800) });
  }

  const score = Number(parsed.score);
  const sub = parsed.subscores ?? {};
  const subscores = {
    color: Number(sub.color) || 0,
    fit: Number(sub.fit) || 0,
    style_match: Number(sub.style_match ?? sub.styleMatch) || 0,
    seasonal: Number(sub.seasonal) || 0,
  };
  const hem_comment = String(parsed.hem_comment ?? "").slice(0, 200);
  const swaps: string[] = Array.isArray(parsed.swaps)
    ? parsed.swaps.slice(0, 2).map((s: unknown) => String(s))
    : [];

  const annotations: Array<{ x_pct: number; y_pct: number; label: string; score: number; note: string }> =
    Array.isArray(parsed.annotations)
      ? parsed.annotations
          .slice(0, 6)
          .map((a: any) => ({
            x_pct: Math.max(0, Math.min(100, Number(a?.x_pct) || 50)),
            y_pct: Math.max(0, Math.min(100, Number(a?.y_pct) || 50)),
            label: String(a?.label ?? "").slice(0, 24),
            score: Math.max(0, Math.min(10, Number(a?.score) || 0)),
            note: String(a?.note ?? "").slice(0, 60),
          }))
          .filter((a: any) => a.label.length > 0)
      : [];

  const markupAnnotations = sanitizeMarkupAnnotations(parsed.markup_annotations ?? parsed.markupAnnotations);
  const fitMap = sanitizeFitMap(parsed.fit_map ?? parsed.fitMap);

  let fitsYou: number | null = null;
  if (bodyProfile) {
    const fy = Number(parsed.fits_you ?? parsed.fitsYou);
    if (isFinite(fy)) fitsYou = Math.max(0, Math.min(10, fy));
  }

  if (!isFinite(score)) {
    return json(502, { error: "parse_failed", raw: modelText.slice(0, 800) });
  }

  const out: Record<string, unknown> = {
    score,
    subscores,
    hem_comment,
    swaps,
    annotations,
    version: "v3",
    raw_model_response: modelText,
  };
  // New fields — omit rather than null so old clients don't see unexpected keys unnecessarily.
  if (markupAnnotations.length > 0) out.markup_annotations = markupAnnotations;
  if (fitMap) out.fit_map = fitMap;
  if (fitsYou !== null) out.fits_you = fitsYou;

  return json(200, out);
});
