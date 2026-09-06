// Scoring v4 — transport.
//
// Primary: Fal `any-llm/vision`, the transport every other function in this
// project runs on and the one v3 shipped with. The shape is held by
// `RESPONSE_SHAPE` in the prompt, which both transports read.
//
// Gemini direct is an OPTIONAL fallback, used only when GEMINI_API_KEY happens
// to be set. It adds a machine-enforced `responseSchema` on top of the same
// prompt, so it is a stricter second opinion rather than a requirement — the
// function is fully operational with FAL_KEY alone.

const GEMINI_KEY = Deno.env.get("GEMINI_API_KEY") ?? Deno.env.get("GOOGLE_API_KEY") ?? "";
const FAL_KEY = Deno.env.get("FAL_KEY") ?? "";
const GEMINI_MODEL = Deno.env.get("SCORE_GEMINI_MODEL") ?? "gemini-1.5-pro";
const FAL_MODEL = Deno.env.get("SCORE_FAL_MODEL") ?? "google/gemini-pro-1.5";

// deno-lint-ignore no-explicit-any
export function extractJson(text: string): any | null {
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
    try { return JSON.parse(text.slice(first, last + 1)); } catch { /* noop */ }
  }
  return null;
}

function toBase64(bytes: Uint8Array): string {
  let binary = "";
  const chunk = 0x8000;
  for (let i = 0; i < bytes.length; i += chunk) {
    binary += String.fromCharCode(...bytes.subarray(i, i + chunk));
  }
  return btoa(binary);
}

interface InlineImage {
  mimeType: string;
  data: string;
}

const imageCache = new Map<string, InlineImage>();

async function fetchInline(url: string): Promise<InlineImage> {
  const cached = imageCache.get(url);
  if (cached) return cached;
  const r = await fetch(url);
  if (!r.ok) throw new Error(`image_fetch_failed ${r.status}`);
  const buf = new Uint8Array(await r.arrayBuffer());
  const mimeType = r.headers.get("content-type")?.split(";")[0] || "image/jpeg";
  const out = { mimeType, data: toBase64(buf) };
  // Front and back are both used by two calls; fetching each once matters more
  // than the memory, and the entry dies with the request.
  imageCache.set(url, out);
  return out;
}

export interface CallOptions {
  system?: string;
  prompt: string;
  imageUrls: string[];
  schema?: Record<string, unknown>;
  temperature?: number;
}

// deno-lint-ignore no-explicit-any
async function callGemini(opts: CallOptions, temperature: number): Promise<any> {
  const images = await Promise.all(opts.imageUrls.map(fetchInline));
  const parts: unknown[] = [{ text: opts.prompt }];
  for (const img of images) {
    parts.push({ inline_data: { mime_type: img.mimeType, data: img.data } });
  }
  // deno-lint-ignore no-explicit-any
  const generationConfig: any = { temperature, responseMimeType: "application/json" };
  if (opts.schema) generationConfig.responseSchema = opts.schema;

  const url =
    `https://generativelanguage.googleapis.com/v1beta/models/${GEMINI_MODEL}:generateContent?key=${GEMINI_KEY}`;
  const r = await fetch(url, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({
      contents: [{ role: "user", parts }],
      ...(opts.system ? { systemInstruction: { parts: [{ text: opts.system }] } } : {}),
      generationConfig,
    }),
  });
  const j = await r.json();
  if (!r.ok || j?.error) {
    throw new Error(`gemini_error ${r.status}: ${String(j?.error?.message ?? "").slice(0, 200)}`);
  }
  const text = j?.candidates?.[0]?.content?.parts?.[0]?.text ?? "";
  const parsed = extractJson(String(text));
  if (!parsed) throw new Error("gemini_parse_failed");
  return parsed;
}

// deno-lint-ignore no-explicit-any
async function callFal(opts: CallOptions): Promise<any> {
  // deno-lint-ignore no-explicit-any
  const body: any = {
    model: FAL_MODEL,
    prompt: opts.prompt +
      "\n\nReturn ONLY a single JSON object. No prose, no code fences.",
    image_url: opts.imageUrls[0],
  };
  if (opts.system) body.system_prompt = opts.system;
  if (opts.imageUrls.length > 1) body.image_urls = opts.imageUrls;

  const r = await fetch("https://fal.run/fal-ai/any-llm/vision", {
    method: "POST",
    headers: { Authorization: `Key ${FAL_KEY}`, "content-type": "application/json" },
    body: JSON.stringify(body),
  });
  const raw = await r.text();
  if (!r.ok) throw new Error(`fal_error ${r.status}: ${raw.slice(0, 200)}`);
  let j: unknown = null;
  try { j = JSON.parse(raw); } catch { /* the model text may be bare */ }
  // deno-lint-ignore no-explicit-any
  const text = (j as any)?.output ?? (j as any)?.text ?? (j as any)?.response ?? raw;
  const parsed = extractJson(String(text));
  if (!parsed) throw new Error("fal_parse_failed");
  return parsed;
}

export interface CallResult {
  // deno-lint-ignore no-explicit-any
  json: any;
  transport: "gemini" | "fal";
  attempts: number;
}

/**
 * One model call with a bounded retry ladder: Gemini at the requested
 * temperature, Gemini again colder, then Fal. Throws only when every rung
 * fails.
 */
export async function call(opts: CallOptions): Promise<CallResult> {
  const t = opts.temperature ?? 0.4;
  const errors: string[] = [];
  // Fal first, twice: a single bad generation is far more common than an outage,
  // so one retry here is worth more than reaching for a second provider.
  if (FAL_KEY) {
    for (const i of [0, 1]) {
      try {
        return { json: await callFal(opts), transport: "fal", attempts: i + 1 };
      } catch (e) {
        errors.push(String(e));
      }
    }
  }
  if (GEMINI_KEY) {
    for (const temp of [t, 0.2]) {
      try {
        return { json: await callGemini(opts, temp), transport: "gemini", attempts: errors.length + 1 };
      } catch (e) {
        errors.push(String(e));
      }
    }
  }
  throw new Error(errors.join(" | ") || "no_model_configured");
}

export function hasModelKey(): boolean {
  return Boolean(GEMINI_KEY || FAL_KEY);
}
