// deno-lint-ignore-file no-explicit-any
// analyze-body v2: one-time body calibration for style personalization.
// Proxies to Fal.ai vision LLM (google/gemini-pro-1.5) for consistency with
// score-outfit. Returns a strict JSON body profile: proportions + coloring.
//
// Request:  { front_url: string, side_url?: string }
// Response: { profile: {...}, engine: "fal:google/gemini-pro-1.5" } | { error, ... }
//
// This function NEVER estimates weight, BMI, age, or height. Style only.

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
  try { return JSON.parse(text); } catch { /* fall through */ }
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

const ALLOWED_SHAPE = new Set([
  "triangle", "inverted_triangle", "hourglass", "rectangle", "apple",
]);
const ALLOWED_UNDERTONE = new Set(["warm", "cool", "neutral"]);
const ALLOWED_SEASON = new Set(["spring", "summer", "autumn", "winter"]);

function normalizeHex(s: unknown): string | null {
  if (typeof s !== "string") return null;
  let v = s.trim();
  if (!v.startsWith("#")) v = "#" + v;
  if (/^#[0-9a-fA-F]{6}$/.test(v)) return v.toUpperCase();
  if (/^#[0-9a-fA-F]{3}$/.test(v)) {
    const r = v[1], g = v[2], b = v[3];
    return ("#" + r + r + g + g + b + b).toUpperCase();
  }
  return null;
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

  const front_url = (body?.front_url ?? "").toString().trim();
  const side_url = (body?.side_url ?? "").toString().trim();

  if (!front_url) return json(400, { error: "missing_front_url" });

  const systemPrompt = [
    "You are a professional style analyst producing calibration data for a personal styling app.",
    "You analyze proportions and coloring ONLY. Never estimate weight, BMI, age, or height.",
    "Output MUST be strict JSON matching the requested schema. No prose outside the JSON. No markdown fences.",
  ].join(" ");

  const sideNote = side_url
    ? `A side-profile photo is also available at ${side_url} — reference it if useful for torso/leg proportions. `
    : "";

  const userPrompt =
    `Analyze the person in the image for STYLE PERSONALIZATION only. ` +
    sideNote +
    `Return strict JSON with this exact shape:\n` +
    `{\n` +
    `  "body_shape": "triangle"|"inverted_triangle"|"hourglass"|"rectangle"|"apple",\n` +
    `  "shoulder_hip_ratio": number,       // e.g. 1.12\n` +
    `  "torso_leg_ratio": number,          // e.g. 0.94\n` +
    `  "skin_undertone": "warm"|"cool"|"neutral",\n` +
    `  "coloring_season": "spring"|"summer"|"autumn"|"winter",\n` +
    `  "palette_hex": [6 hex codes tailored to this person],\n` +
    `  "notes": "1-2 sentences editorial voice describing shape + coloring"\n` +
    `}\n` +
    `STRICT: never estimate weight, BMI, age, or height. Only proportions and coloring. ` +
    `Return ONLY the JSON object, no prose, no markdown fences.`;

  const falResp = await fetch("https://fal.run/fal-ai/any-llm/vision", {
    method: "POST",
    headers: {
      Authorization: `Key ${FAL_KEY}`,
      "content-type": "application/json",
    },
    body: JSON.stringify({
      model: "google/gemini-pro-1.5",
      prompt: userPrompt,
      image_url: front_url,
      system_prompt: systemPrompt,
    }),
  });

  const rawText = await falResp.text();
  if (!falResp.ok) {
    return json(502, { error: "fal_error", status: falResp.status, detail: rawText.slice(0, 800) });
  }

  let falJson: any = null;
  try { falJson = JSON.parse(rawText); } catch {
    return json(502, { error: "fal_bad_json", raw: rawText.slice(0, 500) });
  }

  const modelText: string =
    falJson?.output ??
    falJson?.text ??
    falJson?.response ??
    (typeof falJson === "string" ? falJson : "");

  const parsed = extractJson(modelText);
  if (!parsed || typeof parsed !== "object") {
    return json(502, { error: "parse_failed", raw: String(modelText).slice(0, 800) });
  }

  // Normalize + validate
  const shape = String(parsed.body_shape ?? "").toLowerCase().trim();
  const undertone = String(parsed.skin_undertone ?? "").toLowerCase().trim();
  const season = String(parsed.coloring_season ?? "").toLowerCase().trim();

  const shRatio = Number(parsed.shoulder_hip_ratio);
  const tlRatio = Number(parsed.torso_leg_ratio);

  const palette: string[] = Array.isArray(parsed.palette_hex)
    ? parsed.palette_hex.map(normalizeHex).filter((x: string | null): x is string => !!x)
    : [];

  const profile = {
    body_shape: ALLOWED_SHAPE.has(shape) ? shape : null,
    shoulder_hip_ratio: isFinite(shRatio) ? shRatio : null,
    torso_leg_ratio: isFinite(tlRatio) ? tlRatio : null,
    skin_undertone: ALLOWED_UNDERTONE.has(undertone) ? undertone : null,
    coloring_season: ALLOWED_SEASON.has(season) ? season : null,
    palette_hex: palette.slice(0, 6),
    notes: String(parsed.notes ?? "").slice(0, 400),
  };

  return json(200, { profile, engine: "fal:google/gemini-pro-1.5" });
});
