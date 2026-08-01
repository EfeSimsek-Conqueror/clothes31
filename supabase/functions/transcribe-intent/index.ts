// deno-lint-ignore-file no-explicit-any
// transcribe-intent v1: speech-to-text for user's stated look intent.
// Proxies to Fal.ai wizper (fal-ai/wizper) for STT — wizper is Fal's
// production Whisper endpoint. Returns { transcript, sanitized } where
// sanitized trims filler words.

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

// Trim common filler words + collapse whitespace. Not a paraphrase — we keep
// the user's intent verbatim as much as possible.
function sanitizeTranscript(raw: string): string {
  if (!raw) return "";
  let s = raw.trim();
  // Remove common leading fillers.
  const fillers = [
    /\b(um+|uh+|er+|ah+|hmm+|like|you know|i mean|sort of|kind of|basically|literally|actually)\b[,.!?]?/gi,
  ];
  for (const re of fillers) s = s.replace(re, "");
  // Collapse repeated whitespace and stray punctuation.
  s = s.replace(/\s+([,.!?])/g, "$1");
  s = s.replace(/\s{2,}/g, " ");
  s = s.replace(/^[,.\s]+/, "").trim();
  // Capitalize first letter, ensure trailing period.
  if (s.length > 0) {
    s = s[0].toUpperCase() + s.slice(1);
    if (!/[.!?]$/.test(s)) s += ".";
  }
  return s.slice(0, 400);
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

  const audio_url = (body?.audio_url ?? "").toString().trim();
  if (!audio_url) return json(400, { error: "missing_audio_url" });

  // fal-ai/wizper is Fal's Whisper-v3 STT endpoint. Input schema:
  //   { audio_url: string, task?: "transcribe", language?: string, chunk_level?: "segment" }
  // Response: { text: string, chunks?: [...] }
  const falResp = await fetch("https://fal.run/fal-ai/wizper", {
    method: "POST",
    headers: {
      Authorization: `Key ${FAL_KEY}`,
      "content-type": "application/json",
    },
    body: JSON.stringify({
      audio_url,
      task: "transcribe",
      chunk_level: "segment",
      version: "3",
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

  const transcript: string =
    (typeof falJson?.text === "string" && falJson.text) ||
    (typeof falJson?.transcript === "string" && falJson.transcript) ||
    "";

  if (!transcript) {
    return json(502, { error: "empty_transcript", raw: rawText.slice(0, 500) });
  }

  const sanitized = sanitizeTranscript(transcript);

  return json(200, {
    transcript: transcript.slice(0, 800),
    sanitized,
    engine: "fal:fal-ai/wizper",
  });
});
