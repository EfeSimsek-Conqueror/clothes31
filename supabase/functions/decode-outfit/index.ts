// deno-lint-ignore-file no-explicit-any
// decode-outfit v2: decompose a reference photo into pieces + palette + signature.
// v2 adds piece.name + piece.detail. silhouette/note are still emitted for
// older shipped clients that read them.

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

/** First `n` whitespace-separated words of `s`, trimmed. */
function firstWords(s: string, n: number): string {
  return String(s ?? "").trim().split(/\s+/).filter(Boolean).slice(0, n).join(" ");
}

/** Sentence case: only the first letter is forced upper, the rest is left alone. */
function sentenceCase(s: string): string {
  const t = String(s ?? "").trim();
  if (!t) return "";
  return t.charAt(0).toUpperCase() + t.slice(1);
}

/**
 * Normalise to 6-digit uppercase #RRGGBB.
 * Expands 3-digit shorthand (#ABC -> #AABBCC). Returns null for anything else.
 * The UI prints this string under the swatch, so malformed values are user-visible.
 */
function normalizeHex(raw: any): string | null {
  let h = String(raw ?? "").trim().replace(/^#+/, "");
  if (/^[0-9A-Fa-f]{3}$/.test(h)) {
    h = h.split("").map((c) => c + c).join("");
  }
  if (!/^[0-9A-Fa-f]{6}$/.test(h)) return null;
  return `#${h.toUpperCase()}`;
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
  if (!FAL_KEY) return json(500, { error: "server_misconfigured" });

  let body: any = {};
  try { body = await req.json(); } catch { /* noop */ }
  const image_url = (body?.image_url ?? "").toString().trim();
  if (!image_url) return json(400, { error: "missing_image_url" });

  const systemPrompt = [
    "You are Hem — an editorial fashion critic and stylist for a monthly print magazine.",
    "Voice: terse, sharp, image-first. Never chatty. No emoji. No cliches.",
    "You are decoding a reference look into its wearable recipe.",
    "Output MUST be strict JSON matching the requested schema. No prose outside the JSON. No markdown fences.",
  ].join(" ");

  const userPrompt =
    `Decode this reference outfit into its recipe. ` +
    `Return strict JSON with this exact shape: ` +
    `{"pieces": [{"name": string, "detail": string, "fabric": string, "type": string, "colors": [string], "silhouette": string, "note": string}], ` +
    `"palette_hex": [string], ` +
    `"style_signature": string}. ` +
    `Rules: ` +
    `- pieces: 3 to 7 items, one per visible garment or major accessory. ` +
    `- name: 2-4 words, sentence case (only the first letter capitalised), a natural garment name a stylist would say out loud ("Boxy chore jacket", "Straight mid-grey trousers", "Ribbed crew tee", "Leather derbies"). NEVER a bare type word like "jacket". NEVER put the fabric in the name. ` +
    `- detail: 1-3 words naming the single crucial construction detail ("dropped shoulder", "no break", "high neck", "round toe"). ` +
    `- fabric: 1 or 2 words ("washed cotton", "wool blend", "raw denim", "silk"). ` +
    `- type: one lowercase token, used only for closet categorisation ("shirt","trousers","jacket","boots","belt","bag","hat"). ` +
    `- colors: 1-2 human color names ("washed indigo", "bone"). ` +
    `- silhouette: 2-4 words ("boxy cropped","wide straight","cocoon"). Required — older clients still read it. ` +
    `- note: a stylist-scribble under 60 chars naming the crucial detail. Required — older clients still read it. ` +
    `- palette_hex: exactly 5 hex colors when the image supports it, minimum 3 ("#B4A38A"), sampled from the look, most-dominant first. ` +
    `- style_signature: MAX 60 characters, editorial voice, no cliches, ends with a period. E.g. "Quiet tailoring with a workwear spine.". ` +
    `Return ONLY JSON.`;

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
    return json(502, { error: "fal_error", status: falResp.status, detail: rawText.slice(0, 500) });
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
    return json(502, { error: "parse_failed", raw: modelText.slice(0, 800) });
  }

  const pieces = Array.isArray(parsed.pieces)
    ? parsed.pieces.slice(0, 8).map((p: any) => {
        const type = String(p?.type ?? "").trim().slice(0, 32);
        const silhouette = String(p?.silhouette ?? "").trim().slice(0, 60);
        const note = String(p?.note ?? "").trim().slice(0, 80);

        // v2 field, with a server-side fallback so the client never synthesises.
        let name = String(p?.name ?? "").trim().slice(0, 48);
        if (!name) {
          name = sentenceCase(
            [firstWords(silhouette, 2), type].filter(Boolean).join(" ").trim(),
          ).slice(0, 48);
        }

        let detail = String(p?.detail ?? "").trim().slice(0, 40);
        if (!detail) {
          detail = firstWords(note, 3).slice(0, 40);
        }

        return {
          name,
          detail,
          fabric: String(p?.fabric ?? "").trim().slice(0, 32),
          type,
          colors: Array.isArray(p?.colors)
            ? p.colors.slice(0, 4).map((c: any) => String(c).slice(0, 24))
            : [],
          silhouette,
          note,
        };
      }).filter((p: any) => p.name.length > 0 || p.type.length > 0)
    : [];

  const palette_hex = Array.isArray(parsed.palette_hex)
    ? parsed.palette_hex
        .slice(0, 8)
        .map((h: any) => normalizeHex(h))
        .filter((h: string | null): h is string => h !== null)
        .slice(0, 5)
    : [];

  const style_signature = String(parsed.style_signature ?? "").trim().slice(0, 120);

  return json(200, {
    pieces,
    palette_hex,
    style_signature,
    version: "v2",
  });
});
