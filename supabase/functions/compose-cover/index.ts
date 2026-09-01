// deno-lint-ignore-file no-explicit-any
// compose-cover v34: Magazine Cover composer for Fitrater.
//
// v34 changes from v33:
//   COMPLETE REDESIGN of Branch A (reference-mode person-swap).
//
//   Problem: v32/v33 used image-edit models (nano-banana, gemini-2.5-flash,
//   flux-kontext) as PRIMARY. These are text-guided edit models — they
//   interpret "swap person" as "paste user's photo on top of the reference"
//   producing collage-like output with the original model still visible
//   around the edges. Bad.
//
//   Solution: use a DEDICATED face-swap model as PRIMARY. Face-swap models
//   detect the face region, mask it, and replace ONLY those pixels while
//   leaving the rest of the reference (masthead, cover lines, background,
//   body, outfit) 100% pixel-identical to the source. That's exactly what
//   the user asked for: "same cover, model replaced with me."
//
//   New chain (Branch A):
//     Attempt 1: fal-ai/face-swap (base=reference, swap=subject) — the
//                actual dedicated face-swap model. Fast (~60s), cheap,
//                preserves reference EXACTLY.
//     Attempt 2: fal-ai/flux-pro/kontext/max/multi — full regeneration
//                if face-swap fails (e.g., cover has profile shot,
//                obscured face, illustration, etc).
//     Attempt 3: fal-ai/nano-banana/edit ref-first — legacy fallback.
//     Attempt 4: fal-ai/nano-banana/edit subject-first — legacy fallback.
//
//   Each attempt validated with:
//     - byte-hash check (must not equal reference).
//     - multi-image vision compare (identity must match subject, not ref).
//   If all four fail, return {error:"swap_failed"} — do NOT ship a bad
//   collage. Client already handles this.
//
//   fal-ai/face-swap schema (verified via live smoke):
//     Request:  {base_image_url, swap_image_url}
//     Response: {image: {url, content_type, width, height}}
//     base_image_url = the HOST (reference cover — everything preserved).
//     swap_image_url = the DONOR (subject's face — transplanted in).
//
//   Cost per compose (worst case, all attempts run):
//     face-swap $0.02 + kontext-multi $0.08 + nano-banana x2 $0.06 +
//     upscale $0.02 + vision verifies $0.01 = ~$0.19 max
//     Best case (face-swap succeeds first try): ~$0.05
//
// v33 changes from v32:
//   THREE fixes for the "reference image returned unchanged" and
//   "output is blurry" bugs (Cases A and B reported by user).
//
//   Bug 1 — Reference returned unchanged (Case B):
//     Root cause: verifyPersonChanged (v27) made two SEPARATE vision
//     calls describing each image with vague tokens (hair_color,
//     skin_tone, gender_presenting, ...) and compared token-by-token.
//     Two DIFFERENT people commonly share hair_color + skin_tone + age
//     bracket + gender, so the check returned "different" for the
//     SAME-person case. Additionally, runReferenceSwapChain's final
//     fallback SHIPPED the last attempted image as
//     `_fallback_unverified` — meaning if all 4 models no-op'd, we
//     still uploaded the reference untouched and returned success.
//
//   Fix: rewrite verification as a SINGLE multi-image vision call
//     (fal-ai/any-llm/vision supports image_urls: [a, b] — see
//     score-outfit for precedent). Directly ask gemini whether the
//     two people are the same. Default to "same" (fail closed) on any
//     parse ambiguity. Also, tighten runReferenceSwapChain: if every
//     attempt fails verification, do NOT ship anything — return
//     {url:"", failure:"all_attempts_failed"}. The Deno handler then
//     returns {error:"swap_failed"} instead of uploading the reference.
//
//   Bug 2 — Blurry output (Case A):
//     Root cause: nano-banana faithfully copies the subject's quality.
//     A blurry mirror selfie → blurry face on the cover.
//
//   Fix: ALWAYS pre-run the subject through fal-ai/clarity-upscaler
//     (scale=2, creativity low so face is faithful). Costs ~$0.02 per
//     compose. Falls back to raw subject_image_url if upscale fails.
//
//   Bug 3 — Instrumentation:
//     Added per-attempt logging: model, request payload preview,
//     output byte hash, byte-identical flag, vision verdict (raw and
//     parsed), decision reason, timing_ms.
//
//   Client contract change: MagazineCoverSheet.swift now handles
//     `error: "swap_failed"` in addition to `content_flagged`.
//
// -----------------------------------------------------------------
// v27: Magazine Cover composer for Fitrater.
//
// v27 changes from v26:
//   MAJOR REWRITE of Branch A (reference-mode person-swap).
//
//   Root cause of the "reference returned unchanged" bug:
//   - nano-banana/edit multi-image is inconsistent when told "keep almost
//     everything identical, change only person" — it takes the safest path
//     and returns the reference untouched.
//   - The old prompt was mostly PRESERVE constraints. The model needed a
//     CHANGE-first imperative prompt.
//   - No verification loop existed: if nano-banana no-op'd, we shipped the
//     no-op.
//
//   Fix: multi-strategy chain with per-attempt verification.
//     Attempt 1: nano-banana/edit, [reference, subject], CHANGE-first prompt.
//     Attempt 2: nano-banana/edit, [subject, reference], reversed-order prompt.
//     Attempt 3: fal-ai/gemini-25-flash-image/edit (different model, same
//                multi-image protocol).
//     Attempt 4: fal-ai/flux-pro/kontext/max/multi (different architecture).
//
//   After each attempt we verify:
//     a) SHA-256(output) != SHA-256(reference) — must not be byte-identical.
//     b) Vision compare via gemini-pro-1.5 — "is the person in the output
//        the same person as in the reference, or a different person?"
//        If SAME → strategy failed, try next.
//
//   Also: aggressive CHANGE-first, imperative prompt with negative
//   constraints. Prompt front-loads "REPLACE THE PERSON."
//
//   Verify_jwt remains true. I/O contract UNCHANGED.
//
//
// v24 changes from v23:
//   Reference branch (Branch A) now has a 2-tier chrome-preservation strategy
//   to combat nano-banana occasionally stripping the reference cover's text.
//
//   Tier 1: A stronger, more prescriptive REFERENCE_SWAP_PROMPT that hammers
//   home the requirement to keep masthead, cover lines, headline, pull-quote,
//   barcode, and all typography EXACTLY as they appear.
//
//   Tier 2: After nano-banana returns, do ONE fast vision pass on the output
//   via fal-ai/any-llm/vision (gemini-pro-1.5). Ask whether the image still
//   contains visible text / a masthead. If BOTH are false → we know the model
//   stripped the chrome. We then apply a minimal SVG chrome overlay
//   (masthead + fitrater.ai only — no cover lines, since we don't know what
//   the reference had). This is a safety net, not a full re-chrome.
//
//   I/O contract UNCHANGED.
//
// v23 changes from v19:
//   Adds optional `user_prompt` param — appended to the nano-banana prompt
//   in BOTH branches as `\n\nAdditional user direction: "..."`. Also adds
//   optional `custom_masthead` (Branch B only) to override the default
//   "FITRATER" SVG masthead. Not applicable to Branch A since that preserves
//   the reference's masthead. Fully backward-compatible; both new params
//   are optional strings.
//
// v19 changes from v16:
//   Reference-branch swap prompt rewritten to (1) preserve the FIRST person's
//   original pose (arm position, body angle, clothing) instead of copying the
//   reference cover's pose, and (2) explicitly require ALL cover text
//   (masthead, cover lines, captions) to remain fully visible and legible in
//   the output, with the swapped person shifted if needed to avoid covering
//   text. Fixes pose-mismatch and text-occlusion issues.
//
// v15 changes from v14:
//   TWO-BRANCH BEHAVIOR based on whether a reference cover is provided.
//
//   Branch A — REFERENCE PROVIDED (reference_cover_url OR reference_template_id):
//     Single nano-banana/edit call. Preserve EVERYTHING in the reference
//     (masthead text, cover lines, typography, layout, palette, background,
//     lighting) and ONLY swap in the subject's person. No cleanup pass, no
//     SVG chrome overlay, no headline generation. Return the raw nano-banana
//     output. Cost HALVED vs v10 (1 call instead of 2).
//
//   Branch B — NO REFERENCE (subject only):
//     Fallback to the classic pipeline. Nano-banana renders an editorial
//     photo from the subject, then we apply our FITRATER SVG chrome
//     (masthead + headline + cover lines + barcode). This is our
//     "editorial default" cover.
//
// I/O contract UNCHANGED: response is {cover_url, headline, pull_quote,
// vol_number, cover_id, error?}. In Branch A, headline and pull_quote are
// empty strings since no chrome is applied.

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { Image, TextLayout } from "https://deno.land/x/imagescript@1.3.0/mod.ts";

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
  "slay", "slaying", "slayed",
  "fire", "on fire",
  "iconic", "icon",
  "queen", "king",
  "vibes", "vibe",
  "serve", "serving", "served",
  "ate", "eating",
  "period", "periodt",
  "moment", "goals", "obsessed",
];

// -------------------------- Prohibited user text --------------------------
//
// NOTE: `BANNED` above is an EDITORIAL CLICHE filter. It keeps MODEL-written
// headlines from reading like a social caption. It is deliberately left alone
// and is NOT a safety filter.
//
// This second, separate filter screens text the USER typed — masthead,
// headline, pull-quote, cover lines — because that text is burned onto an
// image the user then shares. Profanity and slurs are rejected outright.

// Matched as whole words (after normalisation).
const PROFANITY_WORDS = [
  "fuck", "fucks", "fucked", "fucking", "fucker", "fuckers", "motherfucker",
  "shit", "shits", "shitty", "bullshit", "shithead",
  "bitch", "bitches", "cunt", "cunts", "asshole", "assholes", "arsehole",
  "bastard", "dickhead", "prick", "wanker", "twat", "whore", "slut", "sluts",
  "cock", "dick", "dicks", "pussy", "penis", "vagina", "clit", "nipples",
  "tits", "titties", "boobs", "cum", "jizz", "blowjob", "handjob", "rimjob",
  "gangbang", "bukkake", "creampie", "deepthroat", "dildo", "buttplug",
  "porn", "porno", "pornhub", "hentai", "milf", "nudes", "nsfw",
  "rape", "raped", "rapist", "molest", "molester", "incest", "bestiality",
  "zoophilia", "necrophilia", "pedo", "pedophile", "paedophile", "loli",
  "shota", "childporn",
  "dyke", "fag", "fags", "tranny", "trannies", "shemale",
  "coon", "darkie", "jigaboo", "spic", "gook", "kike", "yid", "beaner",
  "wetback", "paki", "abbo", "zipperhead",
  "nazi", "nazis", "kkk", "heil",
];

// Matched anywhere inside a word — unambiguous slurs where padding, plurals,
// or glued characters are the whole point of the evasion.
const SLUR_FRAGMENTS = [
  "nigger", "nigga", "faggot", "chink", "towelhead", "raghead", "retard",
  "sandnigger", "hitler",
];

// Multi-word phrases, matched against the normalised string.
const HATE_PHRASES = [
  "heil hitler", "sieg heil", "white power", "gas the", "kill all",
  "death to all",
];

/// Lowercase, strip accents, undo common leetspeak, and reduce every
/// non-letter to a space so "f.u.c.k", "f-u-c-k" and "F U C K" all collapse
/// to the same token stream.
function normalizeForMatch(raw: string): string {
  return raw
    .normalize("NFKD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/[0@]/g, "o")
    .replace(/[1!|]/g, "i")
    .replace(/3/g, "e")
    .replace(/4/g, "a")
    .replace(/[5$]/g, "s")
    .replace(/7/g, "t")
    .replace(/[^a-z]+/g, " ")
    .trim();
}

/// Glue runs of single letters back together ("f u c k" -> "fuck") so
/// letter-spaced evasion is caught.
function mergeSingleLetterRuns(tokens: string[]): string[] {
  const out: string[] = [];
  let run: string[] = [];
  const flush = () => { if (run.length > 1) out.push(run.join("")); run = []; };
  for (const t of tokens) {
    if (t.length === 1) { run.push(t); continue; }
    flush();
    out.push(t);
  }
  flush();
  return out;
}

/// Collapse repeated letters ("fuuuck" -> "fuck") for a second pass.
function collapseRepeats(word: string): string {
  return word.replace(/(.)\1+/g, "$1");
}

/// Returns the offending term, or null when the text is clean.
/// Empty / missing input is always clean.
function findProhibitedTerm(raw: string | null | undefined): string | null {
  if (!raw) return null;
  const norm = normalizeForMatch(raw);
  if (!norm) return null;

  for (const phrase of HATE_PHRASES) {
    if (norm.includes(phrase)) return phrase;
  }

  const tokens = mergeSingleLetterRuns(norm.split(" ").filter(Boolean));
  for (const token of tokens) {
    for (const frag of SLUR_FRAGMENTS) {
      if (token.includes(frag) || collapseRepeats(token).includes(frag)) return frag;
    }
    const squeezed = collapseRepeats(token);
    for (const word of PROFANITY_WORDS) {
      if (token === word || squeezed === word || squeezed === collapseRepeats(word)) {
        return word;
      }
    }
  }
  return null;
}

/// Screens every user-typed string that ends up rendered on the cover.
function findProhibitedInAny(values: Array<string | null | undefined>): string | null {
  for (const v of values) {
    const hit = findProhibitedTerm(v);
    if (hit) return hit;
  }
  return null;
}

const MONTHS = [
  "JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE",
  "JULY", "AUGUST", "SEPTEMBER", "OCTOBER", "NOVEMBER", "DECEMBER",
];

function weekOfYear(d: Date): number {
  const target = new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), d.getUTCDate()));
  const dayNum = target.getUTCDay() || 7;
  target.setUTCDate(target.getUTCDate() + 4 - dayNum);
  const yearStart = new Date(Date.UTC(target.getUTCFullYear(), 0, 1));
  return Math.ceil(((target.getTime() - yearStart.getTime()) / 86400000 + 1) / 7);
}

function extractJson(text: string): any | null {
  if (!text) return null;
  try { return JSON.parse(text); } catch { /* fall through */ }
  const fenced = text.match(/```(?:json)?\s*([\s\S]*?)```/i);
  if (fenced) { try { return JSON.parse(fenced[1]); } catch { /* noop */ } }
  const first = text.indexOf("{");
  const last = text.lastIndexOf("}");
  if (first >= 0 && last > first) {
    const slice = text.slice(first, last + 1);
    try { return JSON.parse(slice); } catch { /* noop */ }
  }
  return null;
}

// -------------------------- Sanitizers --------------------------

function sanitizeHeadline(raw: string): string {
  const cleaned = raw
    .replace(/["“”‘’'`]/g, "")
    .replace(/[^A-Za-z0-9\s\-&·]/g, " ")
    .replace(/\s+/g, " ")
    .trim()
    .toUpperCase();
  const words = cleaned.split(" ").filter(Boolean);
  const filtered = words.filter((w) => !BANNED.includes(w.toLowerCase()));
  const use = (filtered.length >= 2 ? filtered : words).slice(0, 6);
  return use.join(" ");
}

function sanitizePullQuote(raw: string): string {
  let s = raw.replace(/["“”‘’'`]/g, "").replace(/\s+/g, " ").trim();
  const parts = s.split(" ").filter(Boolean);
  if (parts.length > 16) s = parts.slice(0, 16).join(" ");
  return s;
}

function sanitizeMasthead(raw: string | undefined): string {
  const s = (raw ?? "FITRATER")
    .replace(/[^A-Za-z0-9 ]/g, "")
    .trim()
    .toUpperCase();
  return s.length > 0 && s.length <= 24 ? s : "FITRATER";
}

function sanitizeCoverLine(raw: string): string {
  const cleaned = raw
    .replace(/["“”‘’'`]/g, "")
    .replace(/[^A-Za-z0-9\s\-&·:]/g, " ")
    .replace(/\s+/g, " ")
    .trim()
    .toUpperCase();
  const words = cleaned.split(" ").filter(Boolean).slice(0, 6);
  return words.join(" ");
}

function sanitizePrice(raw: string | undefined): string {
  if (!raw) return "$8.00";
  const s = raw.replace(/[^0-9A-Za-z.,$€£¥ ]/g, "").trim();
  return s.length > 0 && s.length <= 12 ? s : "$8.00";
}

// -------------------------- Fal helpers --------------------------

class FalContentPolicyError extends Error {
  raw: string;
  constructor(raw: string) {
    super("fal_content_policy");
    this.raw = raw;
  }
}

function isContentPolicyError(_status: number, rawText: string): boolean {
  const lower = rawText.toLowerCase();
  if (lower.includes("content filter") || lower.includes("content [filter]") ||
      lower.includes("flagged by a content") || lower.includes("safety filter") ||
      lower.includes("nsfw") || lower.includes("content policy") ||
      lower.includes("content_policy") || lower.includes("cannot generate") ||
      lower.includes("could not generate")) {
    return true;
  }
  return false;
}

async function falCallVision(
  FAL_KEY: string,
  imageUrl: string,
  systemPrompt: string,
  userPrompt: string,
): Promise<string> {
  const resp = await fetch("https://fal.run/fal-ai/any-llm/vision", {
    method: "POST",
    headers: { Authorization: `Key ${FAL_KEY}`, "content-type": "application/json" },
    body: JSON.stringify({
      model: "google/gemini-pro-1.5",
      prompt: userPrompt,
      image_url: imageUrl,
      system_prompt: systemPrompt,
    }),
  });
  const rawText = await resp.text();
  if (!resp.ok) throw new Error(`fal_vision_error ${resp.status}: ${rawText.slice(0, 400)}`);
  let js: any = null;
  try { js = JSON.parse(rawText); } catch { /* noop */ }
  return js?.output ?? js?.text ?? js?.response ?? (typeof js === "string" ? js : rawText);
}

async function generateHeadlineAndQuote(
  FAL_KEY: string,
  sourceImageUrl: string,
  userName: string | undefined,
  moodHint: string | undefined,
): Promise<{ headline: string; pull_quote: string }> {
  const systemPrompt = [
    "You are the editor-in-chief of an editorial fashion magazine.",
    "You write short, tasteful, referential cover lines — not social-media captions.",
    "Never use clichés like SLAY, FIRE, ICONIC, QUEEN, KING, VIBES, SERVE, ATE, PERIOD, MOMENT, GOALS, OBSESSED.",
    "Output MUST be strict JSON. No prose outside the JSON. No markdown fences.",
  ].join(" ");
  const whom = userName?.trim()
    ? `The subject is named ${userName.trim()}.`
    : "The subject is anonymous — refer to them as 'she' or 'he' or 'they' as appropriate.";
  const moodLine = moodHint?.trim()
    ? `Bias the tone toward this mood: "${moodHint.trim()}". `
    : "";
  const userPrompt =
    `Look at the outfit in the image. ${moodLine}Write ONE headline (2-3 UPPERCASE words, editorial, tasteful, ` +
    `referencing something specific — silhouette, palette, era, or attitude) and ONE ` +
    `pull-quote (8-12 words, italic-feeling third-person editorial, present tense, no exclamations, ` +
    `no hashtags, no emoji). ${whom}\n` +
    `Return strict JSON: {"headline": "...", "pull_quote": "..."}. No other text.`;
  const text = await falCallVision(FAL_KEY, sourceImageUrl, systemPrompt, userPrompt);
  const parsed = extractJson(text) ?? {};
  const headline = sanitizeHeadline(String(parsed.headline ?? "MODERN FORM"));
  const pull_quote = sanitizePullQuote(
    String(parsed.pull_quote ?? "A quiet study in proportion, palette, and restraint."),
  );
  return { headline: headline || "MODERN FORM", pull_quote };
}

// -------------------------- Nano-banana --------------------------

// Branch A: preserve the reference cover, only swap the person.
// v27: CHANGE-first imperative prompt. Two variants — one for
// [reference, subject] order and one for [subject, reference] order (used
// by the reversed-order retry).
const REFERENCE_SWAP_PROMPT_REF_FIRST =
  "REPLACE THE PERSON.\n\n" +
  "The FIRST image is a magazine cover with a person on it. The SECOND image is a portrait of a DIFFERENT person.\n\n" +
  "Your task: remove the person currently on the FIRST image and put the person from the SECOND image there instead.\n\n" +
  "REQUIREMENTS:\n" +
  "- The new person MUST look identical to the person in the SECOND image: same face, same hair, same skin tone, same body build, same clothing, same pose.\n" +
  "- The new person MUST clearly be a DIFFERENT identity from the original person on the cover. Their face must change.\n" +
  "- KEEP the FIRST image's masthead, cover lines, headline, pull-quote, barcode, price, background, and layout EXACTLY unchanged. Do NOT remove, alter, blur, translate, or reposition any text.\n" +
  "- The output must still be a magazine cover — same shape, same text, same typography.\n\n" +
  "NEGATIVE CONSTRAINTS (do NOT do these):\n" +
  "- Do NOT return the FIRST image unchanged.\n" +
  "- Do NOT keep the original person's face.\n" +
  "- Do NOT blend the two people's faces together.\n" +
  "- Do NOT add extra people. There must be exactly one person.\n" +
  "- Do NOT change, remove, or blank out any text on the cover.\n" +
  "- Do NOT crop the face above the chin.\n\n" +
  "Only the person is different. Everything else stays.";

const REFERENCE_SWAP_PROMPT_SUBJECT_FIRST =
  "REPLACE THE PERSON.\n\n" +
  "The FIRST image is a portrait of a person (this is the target identity). The SECOND image is a magazine cover with a DIFFERENT person on it.\n\n" +
  "Your task: rebuild the SECOND image (the magazine cover) — remove the person currently on it and put the person from the FIRST image there instead.\n\n" +
  "REQUIREMENTS:\n" +
  "- The person on the output magazine cover MUST look identical to the person in the FIRST image: same face, same hair, same skin tone, same body build, same clothing, same pose.\n" +
  "- The output MUST have a different person than the SECOND image's original person. Their face must change.\n" +
  "- KEEP the SECOND image's masthead, cover lines, headline, pull-quote, barcode, price, background, and layout EXACTLY unchanged. Do NOT remove, alter, blur, translate, or reposition any text.\n" +
  "- The output must still be a magazine cover — same shape, same text, same typography as the SECOND image.\n\n" +
  "NEGATIVE CONSTRAINTS (do NOT do these):\n" +
  "- Do NOT return the SECOND image unchanged.\n" +
  "- Do NOT keep the SECOND image's original person's face.\n" +
  "- Do NOT blend the two people's faces together.\n" +
  "- Do NOT add extra people. There must be exactly one person.\n" +
  "- Do NOT change, remove, or blank out any text on the cover.\n\n" +
  "Only the person is different. Everything else on the cover stays.";

function singleImagePromptFor(moodHint: string | undefined): string {
  const mood = (moodHint ?? "editorial").trim();
  return (
    "Create a 9:16 portrait magazine cover photograph from the input image. " +
    "Preserve the person's face, hair, skin tone, and outfit EXACTLY. " +
    "Do not change their identity. Keep the face fully visible; do not crop above the chin. " +
    `Photographic mood: ${mood}. Clean editorial lighting, tasteful color grading. ` +
    "Add cream #F3EEE4 padding at the top so there is headroom for a masthead. " +
    "DO NOT ADD ANY TEXT, LOGOS, LETTERS, NUMBERS, MASTHEADS, HEADLINES, CAPTIONS, WATERMARKS, or BORDERS. " +
    "Only return the photograph."
  );
}

async function callNanoBananaEdit(
  FAL_KEY: string,
  image_urls: string[],
  prompt: string,
): Promise<string> {
  // v26: schema for fal-ai/nano-banana/edit is ONLY {prompt, image_urls,
  // num_images}. Extra fields (image_size, output_format, sync_mode) are
  // silently ignored and were removed.
  const payload = { prompt, image_urls, num_images: 1 };
  console.log("nano_banana_edit_request", JSON.stringify({
    n_images: image_urls.length,
    first_url_preview: (image_urls[0] ?? "").slice(0, 80),
    second_url_preview: (image_urls[1] ?? "").slice(0, 80),
    prompt_preview: prompt.slice(0, 160),
    prompt_len: prompt.length,
  }));
  const resp = await fetch("https://fal.run/fal-ai/nano-banana/edit", {
    method: "POST",
    headers: { Authorization: `Key ${FAL_KEY}`, "content-type": "application/json" },
    body: JSON.stringify(payload),
  });
  const rawText = await resp.text();
  console.log("nano_banana_edit_response", JSON.stringify({
    status: resp.status,
    body_preview: rawText.slice(0, 300),
  }));

  if (!resp.ok) {
    if (isContentPolicyError(resp.status, rawText)) {
      console.error("fal_content_policy", JSON.stringify({
        status: resp.status,
        preview: rawText.slice(0, 400),
      }));
      throw new FalContentPolicyError(rawText.slice(0, 400));
    }
    throw new Error(`fal_render_error ${resp.status}: ${rawText.slice(0, 400)}`);
  }
  let js: any = null;
  try { js = JSON.parse(rawText); } catch { throw new Error(`fal_render_bad_json: ${rawText.slice(0, 300)}`); }
  if (js?.detail && typeof js.detail === "string" && isContentPolicyError(200, js.detail)) {
    throw new FalContentPolicyError(js.detail.slice(0, 400));
  }
  const image_url = js?.images?.[0]?.url ?? js?.image?.url ?? null;
  if (!image_url) throw new Error(`fal_render_no_image: ${rawText.slice(0, 300)}`);
  return image_url;
}

// v27: generic multi-image edit caller. Same request/response shape as
// nano-banana/edit — {prompt, image_urls, num_images} → {images:[{url}]}.
// Works for: fal-ai/nano-banana/edit, fal-ai/gemini-25-flash-image/edit,
// fal-ai/flux-pro/kontext/max/multi (all Fal image-edit endpoints share
// this shape).
async function callFalImageEdit(
  FAL_KEY: string,
  endpoint: string,
  image_urls: string[],
  prompt: string,
  extra?: Record<string, unknown>,
): Promise<string> {
  const payload: Record<string, unknown> = {
    prompt,
    image_urls,
    num_images: 1,
    ...(extra ?? {}),
  };
  console.log("fal_edit_request", JSON.stringify({
    endpoint,
    n_images: image_urls.length,
    first_url_preview: (image_urls[0] ?? "").slice(0, 80),
    second_url_preview: (image_urls[1] ?? "").slice(0, 80),
    prompt_preview: prompt.slice(0, 160),
    prompt_len: prompt.length,
    extra_keys: Object.keys(extra ?? {}),
  }));
  const resp = await fetch(`https://fal.run/${endpoint}`, {
    method: "POST",
    headers: { Authorization: `Key ${FAL_KEY}`, "content-type": "application/json" },
    body: JSON.stringify(payload),
  });
  const rawText = await resp.text();
  console.log("fal_edit_response", JSON.stringify({
    endpoint, status: resp.status, body_preview: rawText.slice(0, 300),
  }));
  if (!resp.ok) {
    if (isContentPolicyError(resp.status, rawText)) {
      throw new FalContentPolicyError(rawText.slice(0, 400));
    }
    throw new Error(`fal_edit_error ${endpoint} ${resp.status}: ${rawText.slice(0, 400)}`);
  }
  let js: any = null;
  try { js = JSON.parse(rawText); } catch { throw new Error(`fal_edit_bad_json ${endpoint}: ${rawText.slice(0, 300)}`); }
  if (js?.detail && typeof js.detail === "string" && isContentPolicyError(200, js.detail)) {
    throw new FalContentPolicyError(js.detail.slice(0, 400));
  }
  const image_url = js?.images?.[0]?.url ?? js?.image?.url ?? null;
  if (!image_url) throw new Error(`fal_edit_no_image ${endpoint}: ${rawText.slice(0, 300)}`);
  return image_url;
}

// v34: fal-ai/face-swap caller. Different schema from image-edit
// endpoints — takes {base_image_url, swap_image_url} and returns
// {image: {url}}. base = the host image whose face gets replaced;
// swap = the identity donor (the new face to insert).
async function callFalFaceSwap(
  FAL_KEY: string,
  baseImageUrl: string,   // reference cover (host)
  swapImageUrl: string,   // subject (identity donor)
): Promise<string> {
  const payload = { base_image_url: baseImageUrl, swap_image_url: swapImageUrl };
  console.log("face_swap_request", JSON.stringify({
    base_preview: baseImageUrl.slice(0, 80),
    swap_preview: swapImageUrl.slice(0, 80),
  }));
  const t0 = Date.now();
  const resp = await fetch("https://fal.run/fal-ai/face-swap", {
    method: "POST",
    headers: { Authorization: `Key ${FAL_KEY}`, "content-type": "application/json" },
    body: JSON.stringify(payload),
  });
  const rawText = await resp.text();
  console.log("face_swap_response", JSON.stringify({
    status: resp.status, ms: Date.now() - t0, body_preview: rawText.slice(0, 300),
  }));
  if (!resp.ok) {
    if (isContentPolicyError(resp.status, rawText)) {
      throw new FalContentPolicyError(rawText.slice(0, 400));
    }
    throw new Error(`face_swap_error ${resp.status}: ${rawText.slice(0, 400)}`);
  }
  let js: any = null;
  try { js = JSON.parse(rawText); } catch { throw new Error(`face_swap_bad_json: ${rawText.slice(0, 300)}`); }
  const image_url = js?.image?.url ?? js?.images?.[0]?.url ?? null;
  if (!image_url) throw new Error(`face_swap_no_image: ${rawText.slice(0, 300)}`);
  return image_url;
}

// v27: hex-encode a SHA-256 of the given bytes.
async function sha256Hex(bytes: Uint8Array): Promise<string> {
  const buf = await crypto.subtle.digest("SHA-256", bytes);
  return Array.from(new Uint8Array(buf)).map((x) => x.toString(16).padStart(2, "0")).join("");
}

// v33: SINGLE-CALL multi-image vision compare. Uses gemini-pro-1.5's
// multi-image capability (image_urls: [a, b], same pattern used in
// score-outfit/index.ts) to DIRECTLY ask "are these two the same
// person?" and get an authoritative answer.
//
// Returns:
//   "same"     → identity unchanged (swap failed, retry)
//   "different"→ identity changed (swap succeeded, ship)
//   "unknown"  → could not determine (FAIL-CLOSED: treat as "same",
//                do not ship a possibly-unchanged image)
async function verifyPersonChanged(
  FAL_KEY: string,
  referenceUrl: string,
  outputUrl: string,
  subjectUrl: string,
): Promise<{ verdict: "same" | "different" | "unknown"; raw: string; parsed: any }> {
  try {
    const systemPrompt =
      "You are a strict face-identity verifier. Two images are given. " +
      "IMAGE 1 is the REFERENCE magazine cover. " +
      "IMAGE 2 is the CANDIDATE OUTPUT magazine cover. " +
      "Compare only face identity (bone structure, eyes, nose, jawline). " +
      "IGNORE clothing, hair styling, lighting, pose, cropping. " +
      "Output MUST be strict JSON. No prose. No markdown fences.";
    const userPrompt =
      "Is the person visible in IMAGE 2 the SAME identity as the person in IMAGE 1? " +
      "If you are not confident either way, answer 'unsure'. " +
      'Return JSON exactly: {"answer": "same" | "different" | "unsure", "confidence": "high|medium|low", "explanation": "one short sentence"}. ' +
      "Answer strictly by face identity, not by clothing or hair.";

    // Multi-image request via image_urls[]. Do NOT include image_url when
    // image_urls is set — the shim only reads one or the other and mixing
    // can cause the model to see only the first image.
    const resp = await fetch("https://fal.run/fal-ai/any-llm/vision", {
      method: "POST",
      headers: { Authorization: `Key ${FAL_KEY}`, "content-type": "application/json" },
      body: JSON.stringify({
        model: "google/gemini-pro-1.5",
        prompt: userPrompt,
        image_urls: [referenceUrl, outputUrl],
        system_prompt: systemPrompt,
      }),
    });
    // subjectUrl is currently reserved for a future 3-image variant.
    void subjectUrl;
    const rawText = await resp.text();
    if (!resp.ok) {
      console.error("verify_vision_http_error", JSON.stringify({ status: resp.status, body: rawText.slice(0, 300) }));
      return { verdict: "unknown", raw: rawText.slice(0, 300), parsed: null };
    }
    let js: any = null;
    try { js = JSON.parse(rawText); } catch { /* noop */ }
    const modelText = js?.output ?? js?.text ?? js?.response ?? (typeof js === "string" ? js : rawText);
    const parsed = extractJson(modelText) ?? {};

    const answer = String(parsed?.answer ?? "").toLowerCase().trim();

    let verdict: "same" | "different" | "unknown";
    if (answer === "same") verdict = "same";
    else if (answer === "different") verdict = "different";
    else verdict = "unknown";

    console.log("verify_person_changed_v33", JSON.stringify({
      answer,
      confidence: parsed?.confidence,
      explanation: String(parsed?.explanation ?? "").slice(0, 120),
      verdict,
      raw_preview: String(modelText).slice(0, 200),
    }));

    return { verdict, raw: String(modelText).slice(0, 400), parsed };
  } catch (e) {
    console.error("verify_person_changed_failed", String(e).slice(0, 200));
    return { verdict: "unknown", raw: String(e).slice(0, 200), parsed: null };
  }
}

// v33: Upscale + gently sharpen the subject photo before feeding to the
// swap chain. Uses fal-ai/clarity-upscaler with modest creativity so the
// face stays faithful. Cost ~$0.02. If it fails, we return the original
// URL — never block the pipeline on this.
async function upscaleSubject(
  FAL_KEY: string,
  subjectUrl: string,
): Promise<{ url: string; upscaled: boolean; error?: string }> {
  try {
    const t0 = Date.now();
    const resp = await fetch("https://fal.run/fal-ai/clarity-upscaler", {
      method: "POST",
      headers: { Authorization: `Key ${FAL_KEY}`, "content-type": "application/json" },
      body: JSON.stringify({
        image_url: subjectUrl,
        scale: 2,
        creativity: 0.2,
        resemblance: 0.9,
        prompt: "sharp, clear portrait photograph, high detail, natural skin texture",
        negative_prompt: "blurry, low quality, distorted, deformed face, extra limbs, text, watermark",
        num_inference_steps: 18,
      }),
    });
    const rawText = await resp.text();
    if (!resp.ok) {
      console.error("upscale_http_error", JSON.stringify({ status: resp.status, body: rawText.slice(0, 300) }));
      return { url: subjectUrl, upscaled: false, error: `http_${resp.status}` };
    }
    let js: any = null;
    try { js = JSON.parse(rawText); } catch {
      return { url: subjectUrl, upscaled: false, error: "bad_json" };
    }
    const outUrl = js?.image?.url ?? js?.images?.[0]?.url ?? null;
    if (!outUrl) {
      return { url: subjectUrl, upscaled: false, error: "no_url" };
    }
    console.log("upscale_ok", JSON.stringify({
      in_preview: subjectUrl.slice(0, 80),
      out_preview: outUrl.slice(0, 80),
      timing_ms: Date.now() - t0,
    }));
    return { url: outUrl, upscaled: true };
  } catch (e) {
    console.error("upscale_failed", String(e).slice(0, 200));
    return { url: subjectUrl, upscaled: false, error: String(e).slice(0, 200) };
  }
}

// v27: Branch A strategy chain. Runs up to 4 attempts, each with a
// different model / image-order / prompt combination. After each attempt
// we (a) hash-compare vs the reference (must NOT be byte-identical) and
// (b) vision-compare the person's identifying features (must have
// changed). Returns {url, bytes, strategy} of the first attempt that
// passes both checks. If ALL 4 attempts fail, returns the last attempt
// so we still ship something.
type SwapAttempt = {
  name: string;
  kind: "face_swap" | "image_edit";
  endpoint: string;
  order: "ref_first" | "subject_first";
  prompt: string;
  extra?: Record<string, unknown>;
};

async function runReferenceSwapChain(
  FAL_KEY: string,
  reference_cover_url: string,
  source_image_url: string,
  userPromptSuffix: string,
  refBytesHash: string,
): Promise<{
  url: string;
  bytes: Uint8Array;
  strategy: string;
  attempts: Array<{
    name: string;
    ok: boolean;
    reason: string;
    out_sha256_prefix: string;
    identical_to_ref: boolean;
    person_check: string;
    error?: string;
  }>;
  content_policy_error?: string;
}> {
  // v34: PRIMARY is now fal-ai/face-swap (dedicated face-swap model that
  // masks & replaces the face region only, preserving the entire cover).
  // This is what "swap the model" actually means. Image-edit models are
  // relegated to fallbacks — they tend to produce collage-style outputs.
  const attempts: SwapAttempt[] = [
    {
      name: "face-swap-primary",
      kind: "face_swap",
      endpoint: "fal-ai/face-swap",
      order: "ref_first",
      prompt: "", // face-swap is prompt-less
    },
    {
      name: "flux-kontext-max-multi",
      kind: "image_edit",
      endpoint: "fal-ai/flux-pro/kontext/max/multi",
      order: "ref_first",
      prompt: REFERENCE_SWAP_PROMPT_REF_FIRST + userPromptSuffix,
      // safety_tolerance on flux-pro/kontext runs "1" (strictest) to "6"
      // (most permissive), default "2"; image-input requests are capped at
      // "2" upstream anyway. We were sending "5" — the loosest end. Pinned
      // to the conservative default.
      extra: { aspect_ratio: "9:16", safety_tolerance: "2" },
    },
    {
      name: "nano-banana-ref-first",
      kind: "image_edit",
      endpoint: "fal-ai/nano-banana/edit",
      order: "ref_first",
      prompt: REFERENCE_SWAP_PROMPT_REF_FIRST + userPromptSuffix,
    },
    {
      name: "nano-banana-subject-first",
      kind: "image_edit",
      endpoint: "fal-ai/nano-banana/edit",
      order: "subject_first",
      prompt: REFERENCE_SWAP_PROMPT_SUBJECT_FIRST + userPromptSuffix,
    },
  ];

  const results: Array<{
    name: string; ok: boolean; reason: string; out_sha256_prefix: string;
    identical_to_ref: boolean; person_check: string; error?: string;
  }> = [];

  let contentPolicyError: string | undefined;

  // v33: keep track of the FIRST attempt whose bytes differ from the
  // reference but whose vision verdict was "unknown". If all four
  // attempts hit vision-unknown (indicating the vision service is
  // flaky, not that swaps failed), we ship this as a last resort —
  // better than dropping a real swap on the floor because gemini
  // couldn't parse its own JSON.
  let visionUnknownFallback: { url: string; bytes: Uint8Array; name: string } | null = null;

  for (const a of attempts) {
    const t0 = Date.now();
    const image_urls = a.order === "ref_first"
      ? [reference_cover_url, source_image_url]
      : [source_image_url, reference_cover_url];

    let renderedUrl: string;
    try {
      if (a.kind === "face_swap") {
        // face-swap: base = reference (host), swap = subject (donor).
        renderedUrl = await callFalFaceSwap(FAL_KEY, reference_cover_url, source_image_url);
      } else {
        renderedUrl = await callFalImageEdit(FAL_KEY, a.endpoint, image_urls, a.prompt, a.extra);
      }
    } catch (e) {
      if (e instanceof FalContentPolicyError) {
        // Content policy is terminal — don't retry with other endpoints.
        contentPolicyError = e.raw;
        results.push({
          name: a.name, ok: false, reason: "content_policy",
          out_sha256_prefix: "", identical_to_ref: false,
          person_check: "n/a", error: e.raw.slice(0, 200),
        });
        break;
      }
      results.push({
        name: a.name, ok: false, reason: "call_error",
        out_sha256_prefix: "", identical_to_ref: false,
        person_check: "n/a", error: String(e).slice(0, 200),
      });
      continue;
    }

    let bytes: Uint8Array;
    try {
      const r = await fetch(renderedUrl);
      if (!r.ok) throw new Error(`download_${r.status}`);
      bytes = new Uint8Array(await r.arrayBuffer());
    } catch (e) {
      results.push({
        name: a.name, ok: false, reason: "download_error",
        out_sha256_prefix: "", identical_to_ref: false,
        person_check: "n/a", error: String(e).slice(0, 200),
      });
      continue;
    }

    const outHash = await sha256Hex(bytes);
    const identical = refBytesHash.length > 0 && outHash === refBytesHash;

    if (identical) {
      console.log("attempt_byte_identical", JSON.stringify({
        name: a.name, out_hash_prefix: outHash.slice(0, 16),
        ref_hash_prefix: refBytesHash.slice(0, 16), timing_ms: Date.now() - t0,
      }));
      results.push({
        name: a.name, ok: false, reason: "byte_identical_to_reference",
        out_sha256_prefix: outHash.slice(0, 16),
        identical_to_ref: true, person_check: "skipped",
      });
      continue;
    }

    // Not byte-identical. Now verify the person actually changed via vision.
    const verify = await verifyPersonChanged(FAL_KEY, reference_cover_url, renderedUrl, source_image_url);
    console.log("attempt_verification", JSON.stringify({
      name: a.name,
      kind: a.kind,
      endpoint: a.endpoint,
      order: a.order,
      out_hash_prefix: outHash.slice(0, 16),
      ref_hash_prefix: refBytesHash.slice(0, 16),
      identical_to_ref: identical,
      verdict: verify.verdict,
      verdict_raw_preview: verify.raw.slice(0, 160),
      timing_ms: Date.now() - t0,
    }));

    if (verify.verdict === "same") {
      results.push({
        name: a.name, ok: false, reason: "person_still_same_by_vision",
        out_sha256_prefix: outHash.slice(0, 16),
        identical_to_ref: false, person_check: "same",
      });
      continue;
    }
    if (verify.verdict === "unknown") {
      // v33: within the chain, treat unknown as "retry" — try the next
      // model. But remember the first non-identical output as a
      // last-resort fallback in case ALL attempts hit vision-unknown
      // (indicating the vision service is flaky rather than swaps
      // failing).
      if (!visionUnknownFallback) {
        visionUnknownFallback = { url: renderedUrl, bytes, name: a.name };
      }
      results.push({
        name: a.name, ok: false, reason: "vision_uncertain_retry",
        out_sha256_prefix: outHash.slice(0, 16),
        identical_to_ref: false, person_check: "unknown",
      });
      continue;
    }

    // "different" → ship it.
    results.push({
      name: a.name, ok: true, reason: "passed",
      out_sha256_prefix: outHash.slice(0, 16),
      identical_to_ref: false, person_check: "different",
    });
    return { url: renderedUrl, bytes, strategy: a.name, attempts: results, content_policy_error: contentPolicyError };
  }

  // v33: ALL attempts failed the primary verification. If we saw any
  // "vision_uncertain" attempt with non-identical bytes, ship that as a
  // last resort — it means the vision verifier was flaky, not that the
  // swap failed. Otherwise return a hard failure so the client can show
  // the "swap_failed" message.
  if (visionUnknownFallback) {
    console.log("shipping_vision_unknown_fallback", JSON.stringify({
      name: visionUnknownFallback.name,
      bytes_len: visionUnknownFallback.bytes.length,
    }));
    return {
      url: visionUnknownFallback.url,
      bytes: visionUnknownFallback.bytes,
      strategy: `${visionUnknownFallback.name}_vision_unknown_fallback`,
      attempts: results,
      content_policy_error: contentPolicyError,
    };
  }
  return {
    url: "", bytes: new Uint8Array(),
    strategy: "none",
    attempts: results,
    content_policy_error: contentPolicyError,
  };
}

// ==================================================================================
// CHROME RENDERING (Branch B only)
// ==================================================================================

const INK = "#141210";
const CREAM = "#F3EEE4";
const BRONZE = "#B4813E";

const FONT_URLS = {
  serif: "https://raw.githubusercontent.com/google/fonts/main/ofl/playfairdisplay/PlayfairDisplay%5Bwght%5D.ttf",
  sans:  "https://raw.githubusercontent.com/google/fonts/main/ofl/inter/Inter%5Bopsz%2Cwght%5D.ttf",
};

let FONT_SERIF: Uint8Array | null = null;
let FONT_SANS: Uint8Array | null = null;

async function loadFonts(): Promise<{ serif: Uint8Array; sans: Uint8Array }> {
  if (!FONT_SERIF) {
    const r = await fetch(FONT_URLS.serif);
    if (!r.ok) throw new Error(`font_serif_fetch: ${r.status}`);
    FONT_SERIF = new Uint8Array(await r.arrayBuffer());
  }
  if (!FONT_SANS) {
    const r = await fetch(FONT_URLS.sans);
    if (!r.ok) throw new Error(`font_sans_fetch: ${r.status}`);
    FONT_SANS = new Uint8Array(await r.arrayBuffer());
  }
  return { serif: FONT_SERIF!, sans: FONT_SANS! };
}

type Mood =
  | "quiet luxury" | "editorial" | "brutalist" | "romantic"
  | "sport" | "minimal" | "punk" | "maximalist";

type MoodSpec = {
  mastheadFont: "serif" | "sans";
  mastheadTracking: number;
  headlineFont: "serif" | "sans";
  headlineTracking: number;
  headlineSize: number;
  coverLineFont: "serif" | "sans";
  grain: boolean;
  rules: boolean;
  hardEdges: boolean;
};

const MOOD_DEFAULT: MoodSpec = {
  mastheadFont: "serif", mastheadTracking: 10,
  headlineFont: "serif", headlineTracking: 2, headlineSize: 90,
  coverLineFont: "serif",
  grain: true, rules: true, hardEdges: false,
};

function specForMood(mood: string | undefined): MoodSpec {
  const m = (mood ?? "").toLowerCase().trim() as Mood;
  switch (m) {
    case "quiet luxury":
      return { ...MOOD_DEFAULT, mastheadTracking: 16, headlineTracking: 8, headlineSize: 80, grain: false };
    case "brutalist":
      return { ...MOOD_DEFAULT, mastheadFont: "sans", mastheadTracking: 2,
        headlineFont: "sans", headlineTracking: 0, headlineSize: 110,
        coverLineFont: "sans", rules: false, hardEdges: true };
    case "romantic":
      return { ...MOOD_DEFAULT, mastheadTracking: 14, headlineTracking: 10, headlineSize: 90, grain: false };
    case "sport":
      return { ...MOOD_DEFAULT, mastheadFont: "sans", mastheadTracking: 4,
        headlineFont: "sans", headlineTracking: 2, headlineSize: 120,
        coverLineFont: "sans", grain: false, rules: false, hardEdges: true };
    case "minimal":
      return { ...MOOD_DEFAULT, mastheadFont: "sans", mastheadTracking: 12,
        headlineFont: "sans", headlineTracking: 2, headlineSize: 70,
        coverLineFont: "sans", grain: false, rules: false };
    case "punk":
      return { ...MOOD_DEFAULT, mastheadFont: "sans", mastheadTracking: 6,
        headlineFont: "sans", headlineTracking: 0, headlineSize: 90,
        coverLineFont: "sans", hardEdges: true };
    case "maximalist":
      return { ...MOOD_DEFAULT, headlineTracking: 0, headlineSize: 130 };
    case "editorial":
    default:
      return { ...MOOD_DEFAULT };
  }
}

function hexToRgba(hex: string): number {
  const h = hex.replace("#", "");
  const r = parseInt(h.slice(0, 2), 16);
  const g = parseInt(h.slice(2, 4), 16);
  const b = parseInt(h.slice(4, 6), 16);
  return ((r << 24) | (g << 16) | (b << 8) | 0xFF) >>> 0;
}

function hexToColor(hex: string, alpha = 0xFF): number {
  const h = hex.replace("#", "");
  const r = parseInt(h.slice(0, 2), 16);
  const g = parseInt(h.slice(2, 4), 16);
  const b = parseInt(h.slice(4, 6), 16);
  return ((r << 24) | (g << 16) | (b << 8) | (alpha & 0xFF)) >>> 0;
}

function luminance(hex: string): number {
  const h = hex.replace("#", "");
  const r = parseInt(h.slice(0, 2), 16) / 255;
  const g = parseInt(h.slice(2, 4), 16) / 255;
  const b = parseInt(h.slice(4, 6), 16) / 255;
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

function bandLuminance(img: any, y0: number, y1: number): number {
  const W = img.width;
  const H = img.height;
  const yStart = Math.max(0, Math.min(H - 1, Math.floor(y0)));
  const yEnd = Math.max(yStart + 1, Math.min(H, Math.floor(y1)));
  const step = Math.max(4, Math.floor((yEnd - yStart) / 12));
  const xStep = Math.max(8, Math.floor(W / 24));
  let total = 0;
  let n = 0;
  for (let y = yStart; y < yEnd; y += step) {
    for (let x = 0; x < W; x += xStep) {
      const px = img.getPixelAt(x + 1, y + 1);
      const r = ((px >>> 24) & 0xFF) / 255;
      const g = ((px >>> 16) & 0xFF) / 255;
      const b = ((px >>> 8) & 0xFF) / 255;
      total += 0.2126 * r + 0.7152 * g + 0.0722 * b;
      n++;
    }
  }
  return n === 0 ? 0.5 : total / n;
}

function buildBarcodeSVG(x: number, y: number, w: number, h: number, seed: string): string {
  let s = 0;
  for (let i = 0; i < seed.length; i++) s = (s * 31 + seed.charCodeAt(i)) >>> 0;
  const rand = () => { s = (s * 1103515245 + 12345) >>> 0; return (s & 0x7fffffff) / 0x7fffffff; };
  const bars = 12;
  const parts: string[] = [];
  let cx = x;
  const widths: number[] = [];
  let sum = 0;
  for (let i = 0; i < bars; i++) {
    const wi = 1 + Math.floor(rand() * 5);
    widths.push(wi);
    sum += wi + 1;
  }
  const unit = w / sum;
  for (let i = 0; i < bars; i++) {
    const bw = widths[i] * unit;
    const fill = i % 3 === 0 ? "#000000" : "#111111";
    parts.push(`<rect x="${cx.toFixed(2)}" y="${y}" width="${bw.toFixed(2)}" height="${h}" fill="${fill}"/>`);
    cx += bw + unit;
  }
  return parts.join("");
}

function buildBarcodeOnlySVG(W: number, H: number, seed: string): string {
  return `<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}">
  <rect x="60" y="${H - 170}" width="180" height="90" fill="#FFFFFF" fill-opacity="0.92" rx="4"/>
  ${buildBarcodeSVG(72, H - 158, 156, 60, seed)}
</svg>`;
}

function approxTextWidth(text: string, size: number, font: "serif" | "sans", tracking = 0): number {
  const per = font === "sans" ? 0.58 : 0.55;
  return text.length * size * per + Math.max(0, text.length - 1) * tracking;
}

function fitSize(text: string, maxWidth: number, startPx: number, minPx: number, font: "serif" | "sans", tracking = 0): number {
  let size = startPx;
  while (size > minPx && approxTextWidth(text, size, font, tracking) > maxWidth) size -= 4;
  return size;
}

function wrapByWidth(text: string, maxWidth: number, size: number, font: "serif" | "sans", maxLines = 2): string[] {
  const words = text.split(/\s+/).filter(Boolean);
  const lines: string[] = [];
  let cur = "";
  for (const w of words) {
    const trial = cur ? `${cur} ${w}` : w;
    if (approxTextWidth(trial, size, font) <= maxWidth || !cur) {
      cur = trial;
    } else {
      lines.push(cur);
      cur = w;
      if (lines.length >= maxLines - 1) break;
    }
  }
  if (cur) lines.push(cur);
  return lines.slice(0, maxLines);
}

function drawText(
  canvas: any,
  fonts: { serif: Uint8Array; sans: Uint8Array },
  text: string,
  x: number, y: number,
  size: number,
  font: "serif" | "sans",
  color: number,
  align: "left" | "center" | "right" = "left",
  tracking = 0,
  halo = true,
): void {
  if (!text) return;
  const fontBytes = font === "sans" ? fonts.sans : fonts.serif;
  const layout = new TextLayout({ maxWidth: 10000, horizontalAlign: "top" });

  const renderPass = (px: number, py: number, col: number) => {
    if (tracking <= 0) {
      try {
        const img = Image.renderText(fontBytes, size, text, col, layout);
        let ox = Math.round(px);
        if (align === "center") ox = Math.round(px - img.width / 2);
        else if (align === "right") ox = Math.round(px - img.width);
        canvas.composite(img, ox, Math.round(py));
      } catch (e) {
        console.error("renderText_failed", JSON.stringify({ text_preview: text.slice(0, 40), size, err: String(e).slice(0, 120) }));
      }
      return;
    }
    const spaceAdvance = Math.round(size * 0.35);
    const glyphs: { img: any | null; w: number }[] = [];
    let totalW = 0;
    for (const ch of text) {
      if (ch === " " || ch === "\t") {
        glyphs.push({ img: null, w: spaceAdvance });
        totalW += spaceAdvance;
        continue;
      }
      try {
        const g = Image.renderText(fontBytes, size, ch, col, layout);
        glyphs.push({ img: g, w: g.width });
        totalW += g.width;
      } catch {
        glyphs.push({ img: null, w: spaceAdvance });
        totalW += spaceAdvance;
      }
    }
    totalW += tracking * Math.max(0, glyphs.length - 1);
    let ox = Math.round(px);
    if (align === "center") ox = Math.round(px - totalW / 2);
    else if (align === "right") ox = Math.round(px - totalW);
    let cx = ox;
    for (const g of glyphs) {
      if (g.img) canvas.composite(g.img, cx, Math.round(py));
      cx += g.w + tracking;
    }
  };

  void halo;
  renderPass(x, y, color);
}

async function renderFullCover(
  photoBytes: Uint8Array,
  masthead: string,
  metaLine: string,
  headline: string,
  pullQuote: string,
  coverLines: string[],
  bg: string,
  layout: "top" | "center" | "bottom",
  spec: MoodSpec,
  includeBarcode: boolean,
  barcodeSeed: string,
): Promise<Uint8Array> {
  const W = 1080;
  const H = 1920;

  const canvas = new Image(W, H).fill(hexToRgba(bg));

  let topLum = luminance(bg);
  let bottomLum = luminance(bg);
  try {
    const base = await Image.decode(photoBytes);
    const scale = Math.max(W / base.width, H / base.height);
    const rw = Math.round(base.width * scale);
    const rh = Math.round(base.height * scale);
    const resized = base.resize(rw, rh);
    const ox = Math.floor((W - rw) / 2);
    const oy = Math.floor((H - rh) / 2);
    canvas.composite(resized, ox, oy);
    topLum = bandLuminance(canvas, 0, Math.floor(H * 0.20));
    bottomLum = bandLuminance(canvas, Math.floor(H * 0.55), H);
  } catch (e) {
    console.error("photo_decode_failed", String(e).slice(0, 200));
  }

  const mastheadSize = fitSize(masthead, W - 80, 200, 100, spec.mastheadFont, spec.mastheadTracking);
  const mastheadTopY = layout === "bottom"
    ? H - 300
    : layout === "center"
    ? Math.floor(H / 2) - 100
    : 60;
  const metaSize = Math.max(22, Math.floor(mastheadSize * 0.18));
  const metaY = mastheadTopY + Math.floor(mastheadSize * 0.95) + 20;

  let headlineSize = spec.headlineSize;
  const headlineLines = headline ? wrapByWidth(headline, W - 120, headlineSize, spec.headlineFont, 3) : [];
  for (const ln of headlineLines) {
    headlineSize = Math.min(headlineSize, fitSize(ln, W - 120, headlineSize, 48, spec.headlineFont, spec.headlineTracking));
  }
  const headlineLineH = Math.floor(headlineSize * 1.05);
  const headlineBlockH = headlineLineH * headlineLines.length;

  let headlineTopY: number;
  if (layout === "top") headlineTopY = Math.floor(H * 0.72) - headlineBlockH;
  else if (layout === "bottom") headlineTopY = Math.floor(H * 0.30);
  else headlineTopY = Math.floor(H * 0.58);

  const quoteSize = Math.max(22, Math.floor(headlineSize * 0.28));
  const quoteLineH = Math.floor(quoteSize * 1.3);
  const quoteLines = pullQuote ? wrapByWidth(pullQuote, W - 180, quoteSize, "serif", 2) : [];
  const quoteTopY = headlineTopY + headlineBlockH + 20;

  const topInk = topLum < 0.5 ? CREAM : INK;
  const bottomInk = bottomLum < 0.5 ? CREAM : INK;
  const topAccent = topLum < 0.5 ? "#E7C58A" : BRONZE;
  const bottomAccent = bottomLum < 0.5 ? "#E7C58A" : BRONZE;

  if (includeBarcode) {
    try {
      const svg = buildBarcodeOnlySVG(W, H, barcodeSeed);
      const overlay = Image.renderSVG(svg, 1, Image.SVG_MODE_SCALE);
      canvas.composite(overlay, 0, 0);
    } catch (e) {
      console.error("barcode_svg_failed:", String(e).slice(0, 200));
    }
  }

  const fonts = await loadFonts();

  const topInkColor = hexToColor(topInk);
  const topAccentColor = hexToColor(topAccent);
  const bottomInkColor = hexToColor(bottomInk);
  const bottomAccentColor = hexToColor(bottomAccent);

  drawText(canvas, fonts, masthead, W / 2, mastheadTopY, mastheadSize,
    spec.mastheadFont, topInkColor, "center", spec.mastheadTracking);
  drawText(canvas, fonts, metaLine, W / 2, metaY, metaSize,
    "serif", topAccentColor, "center", 3);

  for (let i = 0; i < headlineLines.length; i++) {
    drawText(canvas, fonts, headlineLines[i], W / 2, headlineTopY + i * headlineLineH,
      headlineSize, spec.headlineFont, bottomInkColor, "center", spec.headlineTracking);
  }
  for (let i = 0; i < quoteLines.length; i++) {
    drawText(canvas, fonts, quoteLines[i], W / 2, quoteTopY + i * quoteLineH,
      quoteSize, "serif", bottomInkColor, "center", 0);
  }

  const clSize = coverLines.length > 3 ? 34 : 40;
  const clStartY = Math.floor(H * 0.32);
  const clGap = Math.floor(clSize * 2.0);
  for (let i = 0; i < coverLines.length; i++) {
    const y = clStartY + i * clGap;
    const rowIsDark = y < H * 0.55 ? topLum < 0.5 : bottomLum < 0.5;
    const rowInkColor = rowIsDark ? hexToColor(CREAM) : hexToColor(INK);
    const rowAccentColor = rowIsDark ? hexToColor("#E7C58A") : hexToColor(BRONZE);
    const color = i % 2 === 0 ? rowInkColor : rowAccentColor;
    drawText(canvas, fonts, coverLines[i], W - 60, y, clSize,
      spec.coverLineFont, color, "right", 2);
  }

  if (includeBarcode) {
    drawText(canvas, fonts, barcodeSeed.slice(0, 12).toUpperCase(),
      150, H - 95, 14, "sans", hexToColor("#000000"), "center", 2, false);
  }

  drawText(canvas, fonts, "fitrater.ai", W - 60, H - 120, 26,
    "serif", bottomAccentColor, "right", 0);

  return await canvas.encodeJPEG(92);
}

// -------------------------- v24: Chrome-preservation safety net --------------------------

// Fast vision pass: does the image still contain visible text / a masthead?
// Returns {has_text, has_masthead}. On any error we default to true/true
// (assume chrome is fine — do not needlessly overlay).
async function checkChromePresent(
  FAL_KEY: string,
  imageUrl: string,
): Promise<{ has_text: boolean; has_masthead: boolean }> {
  try {
    const systemPrompt =
      "You are an image analysis assistant. Look carefully at the image. " +
      "Output MUST be strict JSON. No prose outside the JSON. No markdown fences.";
    const userPrompt =
      "Does this image contain any visible text, masthead, or magazine typography? " +
      'Return JSON: {"has_text": bool, "has_masthead": bool}';
    const text = await falCallVision(FAL_KEY, imageUrl, systemPrompt, userPrompt);
    const parsed = extractJson(text) ?? {};
    const has_text = parsed?.has_text === true;
    const has_masthead = parsed?.has_masthead === true;
    // If the vision output is unparseable/missing, default to "chrome present"
    // so we do NOT overlay defensively.
    if (parsed?.has_text === undefined && parsed?.has_masthead === undefined) {
      return { has_text: true, has_masthead: true };
    }
    return { has_text, has_masthead };
  } catch (e) {
    console.error("chrome_vision_check_failed", String(e).slice(0, 200));
    return { has_text: true, has_masthead: true };
  }
}

// Fallback: overlay a MINIMAL masthead + fitrater.ai onto the image.
// We do NOT know the reference's original cover lines, so we don't invent them.
// This is a safety net so users don't get a chrome-less photo.
async function overlayMinimalChrome(
  photoBytes: Uint8Array,
  masthead: string,
): Promise<Uint8Array> {
  const base = await Image.decode(photoBytes);
  const W = base.width;
  const H = base.height;
  const fonts = await loadFonts();

  // Sample luminance of top and bottom bands so text remains readable.
  const topLum = bandLuminance(base, 0, Math.floor(H * 0.18));
  const bottomLum = bandLuminance(base, Math.floor(H * 0.90), H);

  const topInk = topLum < 0.5 ? CREAM : INK;
  const bottomInk = bottomLum < 0.5 ? CREAM : INK;

  const mastheadSize = fitSize(masthead, W - 80, Math.floor(W * 0.18), Math.floor(W * 0.09), "serif", 10);
  const mastheadY = Math.floor(H * 0.035);

  drawText(base, fonts, masthead, W / 2, mastheadY, mastheadSize,
    "serif", hexToColor(topInk), "center", 10);

  const wmSize = Math.max(20, Math.floor(W * 0.024));
  drawText(base, fonts, "fitrater.ai", W - Math.floor(W * 0.055), H - Math.floor(H * 0.06),
    wmSize, "serif", hexToColor(bottomInk), "right", 0);

  return await base.encodeJPEG(92);
}

// -------------------------- Reference-template lookup --------------------------

type TemplateRow = {
  masthead: string | null;
  headline: string | null;
  pull_quote: string | null;
  vol_number: number | null;
  image_path: string | null;
};

async function fetchReferenceTemplate(
  admin: any,
  templateId: string,
  userId: string,
): Promise<TemplateRow | null> {
  const { data, error } = await admin
    .from("magazine_covers")
    .select("headline, pull_quote, vol_number, image_path, user_id")
    .eq("id", templateId)
    .eq("user_id", userId)
    .maybeSingle();
  if (error) {
    console.error("reference_template_fetch_error", error.message);
    return null;
  }
  if (!data) return null;
  return {
    masthead: null,
    headline: data.headline ?? null,
    pull_quote: data.pull_quote ?? null,
    vol_number: typeof data.vol_number === "number" ? data.vol_number : null,
    image_path: data.image_path ?? null,
  };
}

// -------------------------- HTTP handler --------------------------

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });

  const url = new URL(req.url);
  const debug = url.searchParams.get("debug") === "1";

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

  const source_image_url = (body?.source_image_url ?? "").toString().trim();
  const reference_template_id = body?.reference_template_id
    ? String(body.reference_template_id).trim() || null
    : null;
  let reference_cover_url = body?.reference_cover_url
    ? String(body.reference_cover_url).trim() || null
    : null;
  const outfit_id = body?.outfit_id ? String(body.outfit_id) : null;
  const user_name = body?.user_name ? String(body.user_name) : undefined;
  const seed_headline = body?.seed_headline ? String(body.seed_headline) : undefined;
  const custom_headline = body?.custom_headline ? String(body.custom_headline) : undefined;
  const custom_pull_quote = body?.custom_pull_quote ? String(body.custom_pull_quote) : undefined;
  const moodExplicit = body?.mood ? String(body.mood) : undefined;
  const mastheadExplicit = body?.masthead ? String(body.masthead) : undefined;
  const customMasthead = body?.custom_masthead ? String(body.custom_masthead) : undefined;
  const userPrompt = body?.user_prompt ? String(body.user_prompt).trim() : "";
  const userPromptSuffix = userPrompt
    ? `\n\nAdditional user direction: "${userPrompt.replace(/"/g, "'")}"`
    : "";
  const layoutExplicit = body?.layout ? String(body.layout) : undefined;
  const price = sanitizePrice(body?.price ? String(body.price) : undefined);
  const includeBarcode = body?.include_barcode === false ? false : true;

  const rawCoverLines: unknown = body?.cover_lines;
  const explicitCoverLines: string[] | null = Array.isArray(rawCoverLines)
    ? rawCoverLines
        .filter((x) => typeof x === "string")
        .map((s) => sanitizeCoverLine(s as string))
        .filter((s) => s.length > 0)
        .slice(0, 4)
    : null;

  if (!source_image_url) return json(400, { error: "missing_source_image_url" });

  const userClient = createClient(SUPABASE_URL, SERVICE_ROLE, {
    global: { headers: { Authorization: auth } },
  });
  const { data: userData, error: userErr } = await userClient.auth.getUser();
  if (userErr || !userData?.user?.id) {
    return json(401, { error: "unauthorized", detail: userErr?.message });
  }
  const user_id = userData.user.id;

  // ---------- Safety: user-typed cover text ----------
  // Runs before anything is generated or billed. Separate from the BANNED
  // cliche list, which only shapes model-written headlines.
  const userTypedCoverText: Array<string | null | undefined> = [
    custom_headline,
    custom_pull_quote,
    mastheadExplicit,
    customMasthead,
    ...(Array.isArray(rawCoverLines)
      ? (rawCoverLines.filter((x) => typeof x === "string") as string[])
      : []),
  ];
  const prohibited = findProhibitedInAny(userTypedCoverText);
  if (prohibited) {
    console.log("cover_text_rejected", JSON.stringify({ user_id, term: prohibited }));
    return json(200, {
      cover_url: null,
      headline: "",
      pull_quote: "",
      vol_number: weekOfYear(new Date()),
      cover_id: null,
      error: "text_rejected",
      detail:
        "Cover text can't include profanity or slurs. Edit the wording and try again.",
    });
  }

  const admin = createClient(SUPABASE_URL, SERVICE_ROLE);

  // ---------- Template lookup: resolves template_id → reference URL ----------
  let templateRow: TemplateRow | null = null;
  if (reference_template_id) {
    templateRow = await fetchReferenceTemplate(admin, reference_template_id, user_id);
    if (templateRow?.image_path && !reference_cover_url) {
      const { data: pub } = admin.storage.from("magazine_covers").getPublicUrl(templateRow.image_path);
      reference_cover_url = pub?.publicUrl ?? null;
    }
  }

  const now = new Date();
  const vol_number = templateRow?.vol_number ?? weekOfYear(now);

  // =========================================================================
  // BRANCH A — REFERENCE PROVIDED: single nano-banana person-swap, no chrome.
  // =========================================================================
  if (reference_cover_url) {
    // v33: pre-hash the reference AND pre-upscale the subject.
    // These two run in parallel to save wall-time.
    const [refHashResult, upscaleResult] = await Promise.all([
      (async () => {
        try {
          const refResp = await fetch(reference_cover_url!);
          if (refResp.ok) {
            const refBytes = new Uint8Array(await refResp.arrayBuffer());
            return await sha256Hex(refBytes);
          }
        } catch (e) {
          console.error("ref_prefetch_failed", String(e).slice(0, 200));
        }
        return "";
      })(),
      // v35 (Aug 2026): face upscaling / person "sharpening" removed per
      // product decision — it altered subjects too much and users disliked
      // the plasticky retouched look. Feed the original subject URL through.
      Promise.resolve({ url: source_image_url, upscaled: false } as { url: string; upscaled: boolean; error?: string }),
    ]);
    const refBytesHash = refHashResult;
    const effectiveSubjectUrl = upscaleResult.url;

    console.log("branch_a_prep", JSON.stringify({
      ref_hash_prefix: refBytesHash.slice(0, 16),
      subject_upscaled: upscaleResult.upscaled,
      subject_upscale_error: upscaleResult.error,
      subject_orig_preview: source_image_url.slice(0, 80),
      subject_upscaled_preview: effectiveSubjectUrl.slice(0, 80),
    }));

    const chain = await runReferenceSwapChain(
      FAL_KEY,
      reference_cover_url,
      effectiveSubjectUrl,
      userPromptSuffix,
      refBytesHash,
    );

    console.log("branch_a_swap_chain", JSON.stringify({
      chosen_strategy: chain.strategy,
      attempts: chain.attempts,
      ref_sha256_prefix: refBytesHash.slice(0, 16),
    }));

    if (chain.content_policy_error) {
      return json(200, {
        cover_url: null,
        headline: "",
        pull_quote: "",
        vol_number,
        cover_id: null,
        error: "content_flagged",
        detail:
          "The photo you uploaded was flagged by our image model's safety filter. Try a photo with a shirt on.",
      });
    }
    if (!chain.url || chain.bytes.length === 0) {
      // v33: hard failure. Do NOT ship the reference untouched.
      return json(200, {
        cover_url: null,
        headline: "",
        pull_quote: "",
        vol_number,
        cover_id: null,
        error: "swap_failed",
        detail:
          "Couldn't compose the swap — try a clearer subject photo or a different reference.",
        attempts: chain.attempts,
      });
    }

    const renderedUrl = chain.url;
    let finalBytes = chain.bytes;

    // v35 (Aug 2026): if the user typed a custom masthead in the compose sheet,
    // ALWAYS overlay it verbatim on the final cover. Previously the reference
    // cover's masthead survived the face-swap and the user's text was ignored,
    // which felt broken ("I typed FITRATER but the cover still says REJ").
    // We use overlayMinimalChrome which paints a masked band + text at the top.
    const userMasthead = (body?.custom_masthead ? String(body.custom_masthead) : "")
      || (body?.masthead ? String(body.masthead) : "");
    let userMastheadOverlayApplied = false;
    if (userMasthead && userMasthead.trim().length > 0) {
      try {
        finalBytes = await overlayMinimalChrome(finalBytes, sanitizeMasthead(userMasthead));
        userMastheadOverlayApplied = true;
      } catch (e) {
        console.error("user_masthead_overlay_failed", String(e).slice(0, 200));
      }
    }

    // v24 Tier 2: verify chrome survived. If nano-banana stripped it, overlay
    // a minimal masthead + fitrater.ai as a safety net.
    let chromeStripped = false;
    let fallbackApplied = false;
    try {
      const chk = await checkChromePresent(FAL_KEY, renderedUrl);
      chromeStripped = !chk.has_text && !chk.has_masthead;
      // Skip the fallback if we already overlaid the user's masthead above.
      if (chromeStripped && !userMastheadOverlayApplied) {
        try {
          const fallbackMasthead = sanitizeMasthead(
            body?.custom_masthead ? String(body.custom_masthead) : undefined,
          );
          finalBytes = await overlayMinimalChrome(finalBytes, fallbackMasthead);
          fallbackApplied = true;
        } catch (e) {
          console.error("fallback_overlay_failed", String(e).slice(0, 200));
        }
      }
    } catch (e) {
      console.error("chrome_check_wrap_failed", String(e).slice(0, 200));
    }

    console.log("compose_cover_v34_branch_a", JSON.stringify({
      used_template: !!templateRow,
      used_reference_url: true,
      vol_number,
      chrome_stripped: chromeStripped,
      fallback_applied: fallbackApplied,
      strategy: chain.strategy,
    }));

    if (debug) {
      return new Response(finalBytes, {
        status: 200,
        headers: {
          ...CORS,
          "content-type": "image/jpeg",
          "x-branch": "A",
          "x-vol": String(vol_number),
          "x-used-template": templateRow ? "1" : "0",
          "x-swap-strategy": chain.strategy,
        },
      });
    }

    const objectKey = `${user_id}/${outfit_id ?? crypto.randomUUID()}.jpg`;
    const { error: upErr } = await admin.storage
      .from("magazine_covers")
      .upload(objectKey, finalBytes, {
        contentType: "image/jpeg",
        upsert: true,
      });
    if (upErr) return json(502, { error: "upload_failed", detail: upErr.message });

    const { data: pub } = admin.storage.from("magazine_covers").getPublicUrl(objectKey);
    const cover_url = pub?.publicUrl ?? null;

    const { data: inserted, error: insErr } = await admin
      .from("magazine_covers")
      .insert({
        outfit_id,
        user_id,
        headline: "",
        pull_quote: "",
        vol_number,
        image_path: objectKey,
      })
      .select("id")
      .single();
    if (insErr) {
      return json(200, {
        cover_url, headline: "", pull_quote: "", vol_number, cover_id: null,
        error: `db_insert_failed: ${insErr.message}`,
      });
    }

    return json(200, {
      cover_url,
      headline: "",
      pull_quote: "",
      vol_number,
      cover_id: inserted?.id ?? null,
      used_template: !!templateRow,
    });
  }

  // =========================================================================
  // BRANCH B — NO REFERENCE: nano-banana editorial photo + FITRATER chrome.
  // =========================================================================
  const masthead = sanitizeMasthead(customMasthead ?? mastheadExplicit ?? undefined);
  const mood = moodExplicit ?? "editorial";
  const spec = specForMood(mood);
  const layoutRaw = layoutExplicit ?? "top";
  const layout: "top" | "center" | "bottom" =
    layoutRaw === "center" || layoutRaw === "bottom" ? layoutRaw : "top";

  const monthYear = `${MONTHS[now.getUTCMonth()]} ${now.getUTCFullYear()}`;
  const metaLine = `${monthYear} · VOL ${vol_number} · ${price}`;

  let headline: string;
  let pull_quote: string;
  try {
    if (custom_headline || custom_pull_quote) {
      headline = custom_headline ? sanitizeHeadline(custom_headline) || "" : "";
      pull_quote = custom_pull_quote ? sanitizePullQuote(custom_pull_quote) : "";
      if (!headline || !pull_quote) {
        try {
          const g = await generateHeadlineAndQuote(FAL_KEY, source_image_url, user_name, mood);
          if (!headline) headline = g.headline;
          if (!pull_quote) pull_quote = g.pull_quote;
        } catch {
          if (!headline) headline = "MODERN FORM";
          if (!pull_quote) pull_quote = "A quiet study in proportion, palette, and restraint.";
        }
      }
    } else if (seed_headline && seed_headline.trim().length > 0) {
      headline = sanitizeHeadline(seed_headline) || "MODERN FORM";
      try {
        const g = await generateHeadlineAndQuote(FAL_KEY, source_image_url, user_name, mood);
        pull_quote = g.pull_quote;
      } catch {
        pull_quote = "A quiet study in proportion, palette, and restraint.";
      }
    } else {
      const g = await generateHeadlineAndQuote(FAL_KEY, source_image_url, user_name, mood);
      headline = g.headline;
      pull_quote = g.pull_quote;
    }
  } catch (e) {
    return json(502, { error: "headline_failed", detail: String(e).slice(0, 400) });
  }

  const coverLines: string[] = explicitCoverLines && explicitCoverLines.length > 0
    ? explicitCoverLines
    : ["THE EDIT", "FIELD STUDY", "SILHOUETTE", "NEW FORM"];

  let renderedUrl: string;
  try {
    renderedUrl = await callNanoBananaEdit(
      FAL_KEY, [source_image_url], singleImagePromptFor(mood) + userPromptSuffix,
    );
  } catch (e) {
    if (e instanceof FalContentPolicyError) {
      return json(200, {
        cover_url: null,
        headline: "",
        pull_quote: "",
        vol_number,
        cover_id: null,
        error: "content_flagged",
        detail:
          "The photo you uploaded was flagged by our image model's safety filter. Try a photo with a shirt on.",
      });
    }
    return json(502, { error: "render_failed", detail: String(e).slice(0, 400) });
  }

  let baseBytes: Uint8Array;
  try {
    const resp = await fetch(renderedUrl);
    if (!resp.ok) throw new Error(`download_status_${resp.status}`);
    baseBytes = new Uint8Array(await resp.arrayBuffer());
  } catch (e) {
    return json(502, { error: "download_failed", detail: String(e).slice(0, 200) });
  }

  const bg = CREAM;
  const barcodeSeed = `${user_id}-${crypto.randomUUID()}`;
  let finalBytes: Uint8Array;
  try {
    finalBytes = await renderFullCover(
      baseBytes, masthead, metaLine, headline, pull_quote, coverLines,
      bg, layout, spec, includeBarcode, barcodeSeed,
    );
  } catch (e) {
    console.error("compose-cover chrome render failed:", String(e).slice(0, 400));
    finalBytes = baseBytes;
  }

  console.log("compose_cover_v34_branch_b", JSON.stringify({
    cover_lines: coverLines.length,
    layout,
    mood,
    masthead,
    headline_len: headline.length,
    vol_number,
  }));

  if (debug) {
    return new Response(finalBytes, {
      status: 200,
      headers: {
        ...CORS,
        "content-type": "image/jpeg",
        "x-branch": "B",
        "x-headline": headline,
        "x-masthead": masthead,
        "x-vol": String(vol_number),
      },
    });
  }

  const objectKey = `${user_id}/${outfit_id ?? crypto.randomUUID()}.jpg`;
  const { error: upErr } = await admin.storage
    .from("magazine_covers")
    .upload(objectKey, finalBytes, {
      contentType: "image/jpeg",
      upsert: true,
    });
  if (upErr) return json(502, { error: "upload_failed", detail: upErr.message });

  const { data: pub } = admin.storage.from("magazine_covers").getPublicUrl(objectKey);
  const cover_url = pub?.publicUrl ?? null;

  const { data: inserted, error: insErr } = await admin
    .from("magazine_covers")
    .insert({
      outfit_id,
      user_id,
      headline,
      pull_quote,
      vol_number,
      image_path: objectKey,
    })
    .select("id")
    .single();
  if (insErr) {
    return json(200, {
      cover_url, headline, pull_quote, vol_number, cover_id: null,
      error: `db_insert_failed: ${insErr.message}`,
    });
  }

  return json(200, {
    cover_url, headline, pull_quote, vol_number,
    cover_id: inserted?.id ?? null,
    used_template: false,
  });
});
