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

// Every image the coach produces is shot on the same set, so three combos and
// the pieces inside them read as one editorial rather than a pile of stock
// photos. Appended to each generate_prompt rather than left to the model, which
// otherwise drifts backdrop and lighting from piece to piece.
const HOUSE_STYLE =
  "Editorial product photograph. Seamless warm grey-taupe backdrop with a soft " +
  "light gradient. The garment rests on a raw concrete plinth, lit by soft " +
  "directional light from the upper left casting one long soft shadow. " +
  "NO person, NO mannequin, NO hanger, NO props, NO text, NO other garments. " +
  "Sharp fabric and stitching detail, muted neutral colour grade.";

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  const json = (o: unknown, status = 200) =>
    new Response(JSON.stringify(o), { status, headers: { ...cors, "Content-Type": "application/json" } });

  try {
    const body = await req.json();
    const promptText: string = String(body?.prompt ?? "").trim();
    if (!promptText) return json({ error: "missing_prompt" }, 400);
    // Only pieces the wearer explicitly chose on the composer screen. The
    // coach used to receive the whole closet and guess which items a combo
    // "probably" meant, which produced combos built around things the wearer
    // had not asked for. Nothing is inferred now: these must appear, and
    // everything else in the combo is designed from scratch.
    const mustUse = Array.isArray(body?.must_use) ? body.must_use : [];
    const styleProfile = body?.style_profile ?? {};
    // One spelling, and never a guess: an unanswered profile gets "unisex"
    // rather than a default gender.
    const g = String(styleProfile?.gender ?? "").trim().toLowerCase();
    const cut = g === "female" || g === "f" || g === "woman"
      ? "women's"
      : g === "male" || g === "m" || g === "man"
      ? "men's"
      : "unisex";
    const styleTags: string[] = Array.isArray(styleProfile?.style_tags) ? styleProfile.style_tags : [];

    const mustUseSlim = mustUse.slice(0, 8).map((c: Record<string, unknown>) => ({
      id: c.id, name: c.name, category: c.category,
      subcategory: c.subcategory, color: c.color ?? null, color_hex: c.color_hex ?? null,
    }));
    const mustUseIds = new Set(mustUseSlim.map((c) => String(c.id)));

    const sys = `You are Hem, an editorial fashion stylist. The user needs 3 outfit combinations for this occasion:
"${promptText}"

Every garment you name is ${cut} — cut, proportion and detailing follow from that.
Style tags: ${JSON.stringify(styleTags)}
Pieces the wearer has chosen and wants to wear${mustUseSlim.length ? "" : " (none — design everything)"}:
${JSON.stringify(mustUseSlim)}

Return exactly 3 combinations. Each combination is a full outfit (top/bottom/shoes/accessory as needed).

Every chosen piece above MUST appear in EVERY combination, with its exact "id" copied into that piece's "matched_closet_id" and "generate_prompt" set to null. Keep its name as given. The combinations differ in what you build AROUND those pieces.

Design every other piece from scratch: set "matched_closet_id" to null and write a "generate_prompt" for it. A generate_prompt describes ONLY the garment itself — its cut, cloth, colour and construction — and must open with "${cut}" so the render is cut for this wearer. Do not describe the backdrop, the lighting or the camera; those are fixed and added afterwards. Never invent an id.

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
        // An id only survives if the wearer actually chose that piece; a model
        // that invents one gets a generated piece instead of a wrong garment.
        const modelMatched = p?.matched_closet_id ? String(p.matched_closet_id) : null;
        const matched = modelMatched && mustUseIds.has(modelMatched) ? modelMatched : null;
        const subject = (typeof p?.generate_prompt === "string" && p.generate_prompt.trim().length > 0)
          ? String(p.generate_prompt).trim()
          : `${p?.color ?? ""} ${p?.name ?? "garment"}`.trim();
        const genPrompt = matched ? null : `${subject}\n\n${HOUSE_STYLE}`;
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

    // The prompt asks for every chosen piece in every combo, but a dropped one
    // would silently turn into a piece the wearer pays to regenerate. Put back
    // whatever the model left out rather than trusting it to have complied.
    // deno-lint-ignore no-explicit-any
    for (const combo of combos as any[]) {
      for (const chosen of mustUseSlim) {
        const id = String(chosen.id);
        // deno-lint-ignore no-explicit-any
        if (combo.pieces.some((p: any) => p.matched_closet_id === id)) continue;
        combo.pieces.unshift({
          name: String(chosen.name ?? "Your piece"),
          category: String(chosen.category ?? ""),
          color: String(chosen.color ?? ""),
          fabric: "",
          detail: "",
          matched_closet_id: id,
          generate_prompt: null,
        });
      }
    }

    return json({ combos });
  } catch (e) {
    return json({ error: "occasion_failed", detail: String(e).slice(0, 240) }, 500);
  }
});
