// deno-lint-ignore-file no-explicit-any
// decode-invitation v1: reads an invitation photo (signed URL) via Fal any-llm/vision
// with google/gemini-pro-1.5, returns structured decoded event fields.
//
// Request:  { image_url: string }
// Response: { decoded: {...}, engine, error? }

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

const ALLOWED_EVENT = new Set([
  "wedding", "cocktail", "dinner", "gallery", "work", "casual", "other",
]);
const ALLOWED_DRESS = new Set([
  "black_tie", "cocktail", "smart_casual", "casual", "business",
  "black_tie_optional", "creative_black_tie", "business_casual",
]);
const ALLOWED_TIME = new Set(["morning", "afternoon", "evening", "night"]);

function pickEnum(v: unknown, allow: Set<string>): string | null {
  if (typeof v !== "string") return null;
  const t = v.toLowerCase().trim().replace(/\s+/g, "_").replace(/-/g, "_");
  return allow.has(t) ? t : null;
}

function pickString(v: unknown, max = 200): string | null {
  if (typeof v !== "string") return null;
  const t = v.trim();
  if (!t) return null;
  return t.slice(0, max);
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

  const image_url = (body?.image_url ?? "").toString().trim();
  if (!image_url) return json(400, { error: "missing_image_url" });

  const systemPrompt = [
    "You are an editorial stylist reading an event invitation.",
    "You extract structured fields from the invitation photo (text + visual mood).",
    "Voice for the 'notes' field: one crisp editorial sentence, image-first, no advice, no emoji.",
    "Output MUST be strict JSON matching the requested schema. No prose outside the JSON. No markdown fences.",
  ].join(" ");

  const userPrompt =
    `Read this invitation and return strict JSON with this exact shape:\n` +
    `{\n` +
    `  "event_type": "wedding"|"cocktail"|"dinner"|"gallery"|"work"|"casual"|"other",\n` +
    `  "dress_code": "black_tie"|"cocktail"|"smart_casual"|"casual"|"business"|"black_tie_optional"|"creative_black_tie"|"business_casual"|null,\n` +
    `  "time_of_day": "morning"|"afternoon"|"evening"|"night"|null,\n` +
    `  "venue": string|null,\n` +
    `  "date_text": string|null,\n` +
    `  "notes": "one sentence editorial reading of the vibe"\n` +
    `}\n` +
    `Infer dress_code from cues (venue, time, formality words) if not explicit. ` +
    `If truly unreadable, still fill event_type ("other") and notes; leave others null. ` +
    `Return ONLY the JSON object, no prose, no code fences.`;

  const engine = "fal:google/gemini-pro-1.5";

  const falResp = await fetch("https://fal.run/fal-ai/any-llm/vision", {
    method: "POST",
    headers: {
      Authorization: `Key ${FAL_KEY}`,
      "content-type": "application/json",
    },
    body: JSON.stringify({
      model: "google/gemini-pro-1.5",
      prompt: userPrompt,
      image_url,
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

  const decoded = {
    event_type: pickEnum(parsed.event_type, ALLOWED_EVENT) ?? "other",
    dress_code: pickEnum(parsed.dress_code, ALLOWED_DRESS),
    time_of_day: pickEnum(parsed.time_of_day, ALLOWED_TIME),
    venue: pickString(parsed.venue, 200),
    date_text: pickString(parsed.date_text, 120),
    notes: pickString(parsed.notes, 400) ?? "",
  };

  return json(200, { decoded, engine });
});
