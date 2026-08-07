// stylist-chat v2: moderate-tone stylist chat, powered by Fal any-llm.
// Reuses the existing FAL_KEY secret — no extra vendor / no extra key.
//
// Request:  { messages: [{role:'user'|'assistant', content:string}], image_base64?: string }
// Response: { text: string } on success, { error: string, detail?: string } on failure.

// deno-lint-ignore-file no-explicit-any

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers":
    "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

// Gemini 2.5 Pro via Fal any-llm — flagship multimodal, handles both chat
// text and attached outfit photos in one call.
const FAL_MODEL = "google/gemini-2.5-pro";
// Nano Banana (Gemini 2.5 Flash Image) — reserved for future image-generation
// features (season swap, style renders). Chat uses gemini-2.5-pro above.
const FAL_IMAGE_MODEL = "fal-ai/gemini-25-flash-image";

const SYSTEM_PROMPT = `You are Hem — a personal stylist inside the Fitrater app.

Voice:
- Moderate. Honest but never brutal. Never insult the person, only the clothes.
- Concrete. Reference proportions, colour, fabric, occasion. No vague praise.
- Short. 2–4 sentences unless the user asks to expand.
- End with one actionable next step when relevant ("swap the sneakers for brown loafers", "try tucking the shirt", etc.).

Never:
- Never rate a person's body or face.
- Never lecture about body positivity or disclaimers — just style advice.
- Never use emoji spam. At most one tasteful emoji per reply.

If an image is attached: read the outfit — silhouette, colour palette, formality, fit tension — and give one clear verdict plus one fix.
If no image: ask one crisp clarifying question OR give advice based on the described situation.`;

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...CORS, "content-type": "application/json" },
  });
}

interface Msg { role: string; content: string }

// Flatten chat history into a single prompt Fal any-llm can consume.
// any-llm/vision is a completion-style endpoint, not chat — so we serialise
// the transcript as "User: … / Hem: …" turns and let the model continue.
function buildPrompt(messages: Msg[]): string {
  const lines: string[] = [];
  for (const m of messages) {
    if (m.role !== "user" && m.role !== "assistant") continue;
    const speaker = m.role === "user" ? "User" : "Hem";
    lines.push(`${speaker}: ${m.content}`);
  }
  lines.push("Hem:");
  return lines.join("\n");
}

async function callFal(messages: Msg[], imageBase64: string | undefined): Promise<string> {
  const key = Deno.env.get("FAL_KEY");
  if (!key) throw new Error("FAL_KEY missing");

  const prompt = buildPrompt(messages);
  const body: Record<string, unknown> = {
    model: FAL_MODEL,
    prompt,
    system_prompt: SYSTEM_PROMPT,
  };
  if (imageBase64) {
    // Fal any-llm/vision accepts data URLs directly.
    body.image_url = `data:image/jpeg;base64,${imageBase64}`;
  }

  const resp = await fetch("https://fal.run/fal-ai/any-llm/vision", {
    method: "POST",
    headers: {
      Authorization: `Key ${key}`,
      "content-type": "application/json",
    },
    body: JSON.stringify(body),
  });

  const raw = await resp.text();
  if (!resp.ok) {
    throw new Error(`fal ${resp.status}: ${raw.slice(0, 200)}`);
  }

  let parsed: any = null;
  try { parsed = JSON.parse(raw); } catch { throw new Error(`bad json: ${raw.slice(0, 200)}`); }

  const text: string =
    parsed?.output ??
    parsed?.text ??
    parsed?.response ??
    (typeof parsed === "string" ? parsed : "");

  const cleaned = String(text).trim();
  if (!cleaned) throw new Error("empty reply");
  // Strip a leading "Hem:" if the model echoed our scaffold.
  return cleaned.replace(/^(hem|assistant)\s*:\s*/i, "");
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });
  if (req.method !== "POST") return json(405, { error: "method_not_allowed" });

  try {
    const body = await req.json();
    const messages: Msg[] = Array.isArray(body?.messages) ? body.messages : [];
    const imageBase64: string | undefined = typeof body?.image_base64 === "string" ? body.image_base64 : undefined;

    if (messages.length === 0 && !imageBase64) return json(400, { error: "no_messages" });

    const text = await callFal(messages, imageBase64);
    return json(200, { text });
  } catch (e) {
    return json(500, { error: "chat_failed", detail: String(e).slice(0, 300) });
  }
});
