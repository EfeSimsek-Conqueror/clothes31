import "jsr:@supabase/functions-js/edge-runtime.d.ts";

// Occasion Coach: takes a freeform occasion (e.g. "outdoor summer wedding, boho")
// and returns 3 curated outfit combinations. Prefers matching pieces to the
// user's closet; falls back to a generation prompt for Studio.

const GEMINI_KEY = Deno.env.get("GEMINI_API_KEY") ?? Deno.env.get("GOOGLE_API_KEY") ?? "";
const FAL_KEY = Deno.env.get("FAL_KEY") ?? "";
const MODEL = "gemini-1.5-pro";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

async function llm(prompt: string): Promise<string> {
  if (GEMINI_KEY) {
    const url = `https://generativelanguage.googleapis.com/v1beta/models/${MODEL}:generateContent?key=${GEMINI_KEY}`;
    const r = await fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        contents: [{ role: "user", parts: [{ text: prompt }] }],
        generationConfig: { temperature: 0.85, responseMimeType: "application/json" },
      }),
    });
    const j = await r.json();
    if (j.error) throw new Error(String(j.error?.message ?? j.error));
    const text = j?.candidates?.[0]?.content?.parts?.[0]?.text ?? "";
    return String(text);
  }
  if (!FAL_KEY) throw new Error("No GEMINI_API_KEY or FAL_KEY configured.");
  const r = await fetch("https://fal.run/fal-ai/any-llm", {
    method: "POST",
    headers: { Authorization: `Key ${FAL_KEY}`, "Content-Type": "application/json" },
    body: JSON.stringify({ model: "google/gemini-pro-1.5", prompt }),
  });
  const j = await r.json();
  if (j.error) throw new Error(String(j.error));
  return String(j.output ?? "");
}

// deno-lint-ignore no-explicit-any
function extractJSON(s: string): any {
  const a = s.indexOf("{"), b = s.lastIndexOf("}");
  if (a >= 0 && b > a) { try { return JSON.parse(s.slice(a, b + 1)); } catch { /* fall through */ } }
  return null;
}

// deno-lint-ignore no-explicit-any
function normalize(s: any): string {
  return String(s ?? "").toLowerCase().replace(/[^a-z0-9]+/g, " ").trim();
}

// deno-lint-ignore no-explicit-any
function matchClosetId(piece: any, closet: any[]): string | null {
  if (!Array.isArray(closet) || closet.length === 0) return null;
  const pn = normalize(piece?.name);
  const pc = normalize(piece?.category);
  const pcol = normalize(piece?.color);
  let best: { id: string; score: number } | null = null;
  for (const c of closet) {
    const cn = normalize(c?.name);
    const csub = normalize(c?.subcategory);
    const ccat = normalize(c?.category);
    const ccol = normalize(c?.color ?? c?.color_hex);
    let score = 0;
    if (pn && cn && (cn.includes(pn.split(" ")[0]) || pn.includes(cn.split(" ")[0]))) score += 3;
    if (pc && (pc === ccat || pc === csub)) score += 2;
    if (pcol && ccol && (ccol.includes(pcol) || pcol.includes(ccol))) score += 2;
    if (score > (best?.score ?? 0) && c?.id) best = { id: String(c.id), score };
  }
  return best && best.score >= 3 ? best.id : null;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  const json = (o: unknown, status = 200) =>
    new Response(JSON.stringify(o), { status, headers: { ...cors, "Content-Type": "application/json" } });

  try {
    const body = await req.json();
    const promptText: string = String(body?.prompt ?? "").trim();
    if (!promptText) return json({ error: "missing_prompt" }, 400);
    const closet = Array.isArray(body?.closet_items) ? body.closet_items : [];
    const styleProfile = body?.style_profile ?? {};
    const gender = String(styleProfile?.gender ?? "unspecified");
    const styleTags: string[] = Array.isArray(styleProfile?.style_tags) ? styleProfile.style_tags : [];

    const closetSlim = closet.slice(0, 60).map((c: Record<string, unknown>) => ({
      id: c.id, name: c.name, category: c.category,
      subcategory: c.subcategory, color: c.color ?? null, color_hex: c.color_hex ?? null,
    }));

    const sys = `You are Hem, an editorial fashion stylist. The user needs 3 outfit combinations for this occasion:
"${promptText}"

The user's gender: ${gender}
Style tags: ${JSON.stringify(styleTags)}
Available closet pieces:
${JSON.stringify(closetSlim)}

Return exactly 3 combinations. Each combination is a full outfit (top/bottom/shoes/accessory as needed). For each piece, PREFER matching an existing closet item by name+category+color. If no good match exists, describe the ideal piece with a generation prompt.

Output STRICT JSON only:
{
  "combos": [
    {
      "title": "Short editorial title (<=6 words)",
      "rationale": "One editorial sentence explaining why this works (<=140 chars)",
      "palette_hex": ["#2E2A26", "#6E6558", "#D8CDB9"],
      "pieces": [
        {"name": "Cream linen suit jacket", "category": "outerwear", "color": "cream", "fabric": "washed linen", "detail": "dropped shoulder", "matched_closet_id": "abc-123", "generate_prompt": null},
        {"name": "...", "category": "...", "color": "...", "fabric": "...", "detail": "...", "matched_closet_id": null, "generate_prompt": "Studio product photograph of..."}
      ]
    }
  ]
}
Field rules:
- name: 2-4 words, sentence case, a garment name said out loud ("Charcoal wool hoodie", "Straight indigo jeans"). Never a bare type word like "jacket".
- category: one lowercase token — top, bottom, outerwear, dress, shoes, accessory.
- fabric: 1-2 words ("brushed cotton", "wool blend").
- detail: 1-3 words naming the one construction detail that matters ("dropped shoulder", "no break", "round toe").
- palette_hex: 3 to 5 hex colours for THIS combo, most-dominant first, drawn from the pieces you named.
No markdown, no prose outside JSON.`;

    const raw = await llm(sys);
    const parsed = extractJSON(raw) ?? {};
    const combosIn = Array.isArray(parsed?.combos) ? parsed.combos : [];

    // deno-lint-ignore no-explicit-any
    const combos = combosIn.slice(0, 3).map((c: any) => ({
      title: String(c?.title ?? "Look").slice(0, 80),
      rationale: String(c?.rationale ?? "").slice(0, 200),
      // Per-combo palette. Anything that is not a well-formed hex triplet is
      // dropped rather than shown as a broken swatch.
      palette_hex: (Array.isArray(c?.palette_hex) ? c.palette_hex : [])
        .slice(0, 6)
        // deno-lint-ignore no-explicit-any
        .map((h: any) => String(h ?? "").trim().toUpperCase())
        .filter((h: string) => /^#[0-9A-F]{6}$/.test(h))
        .slice(0, 5),
      // deno-lint-ignore no-explicit-any
      pieces: (Array.isArray(c?.pieces) ? c.pieces : []).map((p: any) => {
        const modelMatched = p?.matched_closet_id ? String(p.matched_closet_id) : null;
        const stillPresent = modelMatched && closet.some((x: Record<string, unknown>) => String(x.id) === modelMatched);
        const matched = stillPresent ? modelMatched : matchClosetId(p, closet);
        const genPrompt = matched
          ? null
          : (typeof p?.generate_prompt === "string" && p.generate_prompt.trim().length > 0
              ? String(p.generate_prompt)
              : `Studio product photograph of ${p?.name ?? "garment"}, ${p?.color ?? ""} ${p?.category ?? ""}, soft neutral background, editorial lighting.`.trim());
        return {
          name: String(p?.name ?? "Piece"),
          category: String(p?.category ?? ""),
          color: String(p?.color ?? ""),
          fabric: String(p?.fabric ?? "").trim().slice(0, 32),
          detail: String(p?.detail ?? "").trim().slice(0, 40),
          matched_closet_id: matched,
          generate_prompt: genPrompt,
        };
      }),
    }));

    return json({ combos });
  } catch (e) {
    return json({ error: "occasion_failed", detail: String(e).slice(0, 240) }, 500);
  }
});
