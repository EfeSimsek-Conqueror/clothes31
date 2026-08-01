// deno-lint-ignore-file no-explicit-any
// regenerate-cover-headline v1: quick helper to re-roll the editorial headline
// + pull-quote for an existing magazine cover (or a raw outfit image).
//
// Request: { cover_id?: string } OR { outfit_id?: string, source_image_url: string }
// Response: { headline, pull_quote, error? }
//
// If `cover_id` is given we also patch the existing cover row's headline/pull_quote.

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

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

const BANNED = [
  "slay", "slaying", "slayed", "fire", "iconic", "icon", "queen", "king",
  "vibes", "vibe", "serve", "serving", "served", "ate", "eating", "period",
  "periodt", "moment", "goals", "obsessed",
];

function extractJson(text: string): any | null {
  if (!text) return null;
  try { return JSON.parse(text); } catch { /* fall through */ }
  const fenced = text.match(/```(?:json)?\s*([\s\S]*?)```/i);
  if (fenced) { try { return JSON.parse(fenced[1]); } catch { /* noop */ } }
  const first = text.indexOf("{");
  const last = text.lastIndexOf("}");
  if (first >= 0 && last > first) {
    try { return JSON.parse(text.slice(first, last + 1)); } catch { /* noop */ }
  }
  return null;
}

function sanitizeHeadline(raw: string): string {
  const cleaned = raw
    .replace(/["""''`]/g, "")
    .replace(/[^A-Za-z0-9\s\-&·]/g, " ")
    .replace(/\s+/g, " ")
    .trim()
    .toUpperCase();
  const words = cleaned.split(" ").filter(Boolean);
  const filtered = words.filter((w) => !BANNED.includes(w.toLowerCase()));
  const use = (filtered.length >= 2 ? filtered : words).slice(0, 3);
  return use.join(" ") || "MODERN FORM";
}

function sanitizePullQuote(raw: string): string {
  let s = raw.replace(/["""''`]/g, "").replace(/\s+/g, " ").trim();
  const parts = s.split(" ").filter(Boolean);
  if (parts.length > 12) s = parts.slice(0, 12).join(" ");
  return s || "A quiet study in proportion, palette, and restraint.";
}

async function generate(
  FAL_KEY: string,
  sourceImageUrl: string,
): Promise<{ headline: string; pull_quote: string }> {
  const systemPrompt = [
    "You are the editor-in-chief of an editorial fashion magazine.",
    "Write short, tasteful, referential cover lines — not social captions.",
    "Never use clichés like SLAY, FIRE, ICONIC, QUEEN, VIBES, SERVE, ATE, PERIOD, MOMENT, GOALS, OBSESSED.",
    "Output strict JSON only. No markdown fences.",
  ].join(" ");

  const userPrompt =
    `Look at the outfit in the image. Write ONE headline (2-3 UPPERCASE words, editorial, ` +
    `fit-specific) and ONE pull-quote (8-12 words, third-person editorial, present tense, ` +
    `no exclamations, no hashtags, no emoji). Return strict JSON: ` +
    `{"headline":"...","pull_quote":"..."}. No other text.`;

  const resp = await fetch("https://fal.run/fal-ai/any-llm/vision", {
    method: "POST",
    headers: {
      Authorization: `Key ${FAL_KEY}`,
      "content-type": "application/json",
    },
    body: JSON.stringify({
      model: "google/gemini-pro-1.5",
      prompt: userPrompt,
      image_url: sourceImageUrl,
      system_prompt: systemPrompt,
    }),
  });
  const rawText = await resp.text();
  if (!resp.ok) throw new Error(`fal_error ${resp.status}: ${rawText.slice(0, 300)}`);
  let js: any = null;
  try { js = JSON.parse(rawText); } catch { /* noop */ }
  const modelText: string =
    js?.output ?? js?.text ?? js?.response ?? (typeof js === "string" ? js : rawText);
  const parsed = extractJson(modelText) ?? {};
  return {
    headline: sanitizeHeadline(String(parsed.headline ?? "")),
    pull_quote: sanitizePullQuote(String(parsed.pull_quote ?? "")),
  };
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });

  const auth = req.headers.get("Authorization") ?? "";
  if (!auth.toLowerCase().startsWith("bearer ")) {
    return json(401, { error: "unauthorized" });
  }

  const FAL_KEY = Deno.env.get("FAL_KEY");
  const SUPABASE_URL = Deno.env.get("SUPABASE_URL");
  const SERVICE_ROLE = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!FAL_KEY || !SUPABASE_URL || !SERVICE_ROLE) {
    return json(500, { error: "server_misconfigured" });
  }

  let body: any = {};
  try { body = await req.json(); } catch { /* noop */ }

  const cover_id = body?.cover_id ? String(body.cover_id) : null;
  let source_image_url = body?.source_image_url ? String(body.source_image_url).trim() : "";

  const admin = createClient(SUPABASE_URL, SERVICE_ROLE);

  // If we only got a cover_id, look up its image_path so we can resolve a source url.
  let existingRow: any = null;
  if (cover_id && !source_image_url) {
    const { data } = await admin
      .from("magazine_covers")
      .select("id,image_path,outfit_id,user_id")
      .eq("id", cover_id)
      .maybeSingle();
    existingRow = data;
    if (data?.image_path) {
      const { data: pub } = admin.storage.from("magazine_covers").getPublicUrl(data.image_path);
      source_image_url = pub?.publicUrl ?? "";
    }
    // If cover has an outfit reference, we could pull the outfit photo instead —
    // but the cover image itself is usually a better prompt for regenerating a
    // headline that matches what the user is looking at.
  }

  if (!source_image_url) {
    return json(400, { error: "missing_source_image_url" });
  }

  let out: { headline: string; pull_quote: string };
  try {
    out = await generate(FAL_KEY, source_image_url);
  } catch (e) {
    return json(502, { error: "headline_failed", detail: String(e).slice(0, 300) });
  }

  if (cover_id) {
    await admin
      .from("magazine_covers")
      .update({ headline: out.headline, pull_quote: out.pull_quote })
      .eq("id", cover_id);
  }

  return json(200, { headline: out.headline, pull_quote: out.pull_quote });
});
