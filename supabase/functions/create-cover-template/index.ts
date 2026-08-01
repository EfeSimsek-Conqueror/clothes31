// deno-lint-ignore-file no-explicit-any
// create-cover-template v4: Vogue-like magazine cover mockup for Fitrater.
//
// v3 was DOA on two fronts:
//   (a) Fal `flux/schnell` produced abstract color bands, not a person.
//   (b) All <text> inside the SVG rendered invisible because imagescript's
//       renderSVG uses resvg-wasm with NO fonts loaded — every text element
//       resolves to nothing regardless of font-family. Only <rect>/<line>
//       (the barcode) survived. Verified by reading imagescript source.
//
// v4 fixes:
//   1. Switch to `fal-ai/flux/dev` (28 inference steps, better prompt).
//   2. Render ALL text via `Image.renderText` with an actual TTF font
//      bundled at cold start. SVG is now used ONLY for shapes (bg, veils,
//      slabs, rules, barcode).
//   3. Add `?debug=1` query flag → returns raw SVG source as text/plain
//      instead of processing further.
//
// Fonts fetched on cold start (module scope, so only paid on cold boot):
//   - Playfair Display (serif display)   ~300KB
//   - Inter (sans)                       ~880KB
//   Both OFL from google/fonts on GitHub raw.

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

function sanitizeHeadline(raw: string): string {
  const cleaned = raw
    .replace(/["“”‘’'`]/g, "")
    .replace(/[^A-Za-z0-9\s\-&·]/g, " ")
    .replace(/\s+/g, " ")
    .trim()
    .toUpperCase();
  const words = cleaned.split(" ").filter(Boolean).slice(0, 6);
  return words.join(" ");
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

function sanitizePose(raw: string | undefined): string {
  const allowed = new Set([
    "standing", "seated", "walking", "editorial_portrait", "detail",
  ]);
  const s = (raw ?? "").toLowerCase().trim();
  return allowed.has(s) ? s : "editorial_portrait";
}

function normalizeHex(raw: string | undefined, fallback: string): string {
  if (!raw) return fallback;
  const s = raw.trim();
  if (/^#[0-9A-Fa-f]{6}$/.test(s)) return s.toUpperCase();
  if (/^[0-9A-Fa-f]{6}$/.test(s)) return `#${s.toUpperCase()}`;
  return fallback;
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

// -------------------------- Palette --------------------------

const INK = "#141210";
const CREAM = "#F3EEE4";
const BRONZE = "#B4813E";

// -------------------------- Fonts (cold-start fetch) --------------------------
//
// We MUST bundle real TTF bytes because imagescript's renderSVG (resvg-wasm)
// has zero fonts registered — <text> becomes invisible. Instead we render each
// text run with `Image.renderText` and composite the bitmap onto the canvas.
//
// Fonts are fetched lazily and cached in module scope so repeat invocations
// (warm-start) skip the download.

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

// -------------------------- Mood --------------------------

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
  photoStyle: string;
};

const MOOD_DEFAULT: MoodSpec = {
  mastheadFont: "serif", mastheadTracking: 10,
  headlineFont: "serif", headlineTracking: 2, headlineSize: 90,
  coverLineFont: "serif",
  grain: true, rules: true, hardEdges: false,
  photoStyle: "editorial fashion photograph, cinematic natural light",
};

function specForMood(mood: string | undefined): MoodSpec {
  const m = (mood ?? "").toLowerCase().trim() as Mood;
  switch (m) {
    case "quiet luxury":
      return { ...MOOD_DEFAULT, mastheadTracking: 16, headlineTracking: 8, headlineSize: 80,
        grain: false, photoStyle: "quiet luxury editorial, muted palette, soft natural light, cashmere textures" };
    case "brutalist":
      return { ...MOOD_DEFAULT, mastheadFont: "sans", mastheadTracking: 2,
        headlineFont: "sans", headlineTracking: 0, headlineSize: 110,
        coverLineFont: "sans", rules: false, hardEdges: true,
        photoStyle: "brutalist editorial, hard shadows, concrete backdrop, high contrast monochrome" };
    case "romantic":
      return { ...MOOD_DEFAULT, mastheadTracking: 14,
        headlineTracking: 10, headlineSize: 90, grain: false,
        photoStyle: "romantic editorial, soft candlelight, silk and lace, painterly haze" };
    case "sport":
      return { ...MOOD_DEFAULT, mastheadFont: "sans", mastheadTracking: 4,
        headlineFont: "sans", headlineTracking: 2, headlineSize: 120,
        coverLineFont: "sans", grain: false, rules: false, hardEdges: true,
        photoStyle: "dynamic sport editorial, motion, athletic wear, stadium lighting" };
    case "minimal":
      return { ...MOOD_DEFAULT, mastheadFont: "sans", mastheadTracking: 12,
        headlineFont: "sans", headlineTracking: 2, headlineSize: 70,
        coverLineFont: "sans", grain: false, rules: false,
        photoStyle: "minimal editorial, flat studio backdrop, single accent color, hairline shadows" };
    case "punk":
      return { ...MOOD_DEFAULT, mastheadFont: "sans", mastheadTracking: 6,
        headlineFont: "sans", headlineTracking: 0, headlineSize: 90,
        coverLineFont: "sans", hardEdges: true,
        photoStyle: "punk editorial, torn zine aesthetic, xerox contrast, safety pins, grit" };
    case "maximalist":
      return { ...MOOD_DEFAULT, headlineTracking: 0, headlineSize: 130,
        photoStyle: "maximalist editorial, saturated palette, layered patterns, baroque set design" };
    case "editorial":
    default:
      return { ...MOOD_DEFAULT };
  }
}

// -------------------------- Pass 1: Fal placeholder --------------------------
//
// Switched from flux/schnell (4 steps, produced abstract color bands with no
// subject) to flux/dev (28 steps, ~$0.025/call). Better prompt engineering
// explicitly asks for a full-body person facing camera.

// Latest Fal image URL for debug — set as a side effect of the last call.
let LAST_FAL_IMAGE_URL: string | null = null;

async function generateSubjectImage(
  FAL_KEY: string,
  pose: string,
  mood: string | undefined,
  spec: MoodSpec,
  color: string,
): Promise<Uint8Array | null> {
  const poseText = pose.replace("_", " ");
  const moodText = (mood ?? "editorial").toLowerCase();
  // NOTE: the previous prompt asked for a "colored backdrop" with a hex,
  // which flux interpreted as "just fill the image with this color". Now we
  // lead with the SUBJECT and describe the backdrop as a *setting* not a fill.
  const prompt =
    `Full-body editorial fashion photograph of one model, ${poseText} pose, ` +
    `facing the camera, wearing high-fashion clothing. ` +
    `${moodText} magazine aesthetic. ${spec.photoStyle}. ` +
    `Shot on medium-format film, cinematic dramatic lighting, ` +
    `sharp focus on the model, shallow depth of field. ` +
    `Vogue-style cover photography, 9:16 portrait crop, hyperrealistic, ` +
    `professional fashion editorial. No text, no logos, no watermarks, ` +
    `no borders, no color blocks.`;

  console.log("fal_dev_prompt", JSON.stringify({ prompt_preview: prompt.slice(0, 240) }));

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 60_000);
  try {
    const resp = await fetch("https://fal.run/fal-ai/flux/dev", {
      method: "POST",
      headers: {
        Authorization: `Key ${FAL_KEY}`,
        "content-type": "application/json",
      },
      body: JSON.stringify({
        prompt,
        image_size: "portrait_16_9",
        num_images: 1,
        num_inference_steps: 28,
        guidance_scale: 3.5,
        enable_safety_checker: true,
        output_format: "jpeg",
      }),
      signal: controller.signal,
    });
    if (!resp.ok) {
      console.error("fal_dev_error", resp.status, (await resp.text()).slice(0, 400));
      return null;
    }
    const js: any = await resp.json();
    const image_url = js?.images?.[0]?.url ?? js?.image?.url ?? null;
    if (!image_url) {
      console.error("fal_dev_no_image", JSON.stringify(js).slice(0, 300));
      return null;
    }
    LAST_FAL_IMAGE_URL = image_url;
    console.log("fal_dev_image_url", image_url);
    const imgResp = await fetch(image_url);
    if (!imgResp.ok) return null;
    return new Uint8Array(await imgResp.arrayBuffer());
  } catch (e) {
    console.error("fal_dev_exception", String(e).slice(0, 200));
    return null;
  } finally {
    clearTimeout(timer);
  }
}

// -------------------------- Shape SVG (no text) --------------------------

function xmlEscape(s: string): string {
  return s
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&apos;");
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

type ShapeArgs = {
  W: number; H: number; bg: string;
  hasPhoto: boolean;
  topLum: number; bottomLum: number;
  spec: MoodSpec;
  mastheadSlabRect: { x: number; y: number; w: number; h: number } | null;
  headlineSlabRect: { x: number; y: number; w: number; h: number } | null;
  includeBarcode: boolean;
  barcodeSeed: string;
};

function buildShapeSVG(a: ShapeArgs): string {
  const { W, H, bg, hasPhoto, topLum, bottomLum, spec,
    mastheadSlabRect, headlineSlabRect, includeBarcode, barcodeSeed } = a;

  const topInk = topLum < 0.5 ? CREAM : INK;
  const bottomInk = bottomLum < 0.5 ? CREAM : INK;

  // Only paint solid bg when there's no photo. If we have a photo it was
  // already composited on the canvas — the SVG must not obscure it.
  const bgRect = hasPhoto ? "" : `<rect x="0" y="0" width="${W}" height="${H}" fill="${bg}"/>`;

  const veils = hasPhoto
    ? `<defs>
         <linearGradient id="veilTop" x1="0" x2="0" y1="0" y2="1">
           <stop offset="0" stop-color="${topLum < 0.5 ? "#000000" : "#FFFFFF"}" stop-opacity="0.30"/>
           <stop offset="1" stop-color="${topLum < 0.5 ? "#000000" : "#FFFFFF"}" stop-opacity="0"/>
         </linearGradient>
         <linearGradient id="veilBot" x1="0" x2="0" y1="1" y2="0">
           <stop offset="0" stop-color="${bottomLum < 0.5 ? "#000000" : "#FFFFFF"}" stop-opacity="0.45"/>
           <stop offset="1" stop-color="${bottomLum < 0.5 ? "#000000" : "#FFFFFF"}" stop-opacity="0"/>
         </linearGradient>
       </defs>
       <rect x="0" y="0" width="${W}" height="${Math.floor(H * 0.22)}" fill="url(#veilTop)"/>
       <rect x="0" y="${Math.floor(H * 0.55)}" width="${W}" height="${Math.floor(H * 0.45)}" fill="url(#veilBot)"/>`
    : "";

  const rules = spec.rules
    ? `<line x1="60" y1="80" x2="${W - 60}" y2="80" stroke="${topInk}" stroke-opacity="0.35" stroke-width="1"/>
       <line x1="60" y1="${H - 80}" x2="${W - 60}" y2="${H - 80}" stroke="${bottomInk}" stroke-opacity="0.35" stroke-width="1"/>`
    : "";

  const mastheadSlab = (hasPhoto && mastheadSlabRect)
    ? `<rect x="${mastheadSlabRect.x}" y="${mastheadSlabRect.y}" width="${mastheadSlabRect.w}" height="${mastheadSlabRect.h}" fill="${topLum < 0.5 ? "#000000" : "#FFFFFF"}" fill-opacity="0.35"/>`
    : "";

  const headlineSlab = (hasPhoto && headlineSlabRect)
    ? `<rect x="${headlineSlabRect.x}" y="${headlineSlabRect.y}" width="${headlineSlabRect.w}" height="${headlineSlabRect.h}" fill="${bottomLum < 0.5 ? "#000000" : "#FFFFFF"}" fill-opacity="0.40"/>`
    : "";

  const barcode = includeBarcode
    ? `<g>
         <rect x="60" y="${H - 170}" width="180" height="90" fill="#FFFFFF" fill-opacity="0.92" rx="4"/>
         ${buildBarcodeSVG(72, H - 158, 156, 60, barcodeSeed)}
       </g>`
    : "";

  return `<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}">
  ${bgRect}
  ${veils}
  ${rules}
  ${mastheadSlab}
  ${headlineSlab}
  ${barcode}
</svg>`;
}

// -------------------------- Text via renderText --------------------------

// Approx text width using font metrics (empirical for Playfair/Inter).
// Playfair caps ~0.55em, Inter ~0.58em.
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

// Composite text with letter-spacing (draw glyph-by-glyph). imagescript doesn't
// expose native tracking, so we render each char separately when tracking > 0.
function drawText(
  canvas: any,
  fonts: { serif: Uint8Array; sans: Uint8Array },
  text: string,
  x: number, y: number, // baseline-ish top-left origin
  size: number,
  font: "serif" | "sans",
  color: number,
  align: "left" | "center" | "right" = "left",
  tracking = 0,
): void {
  if (!text) return;
  const fontBytes = font === "sans" ? fonts.sans : fonts.serif;
  const layout = new TextLayout({ maxWidth: 10000, horizontalAlign: "top" });

  if (tracking <= 0) {
    try {
      const img = Image.renderText(fontBytes, size, text, color, layout);
      let ox = Math.round(x);
      if (align === "center") ox = Math.round(x - img.width / 2);
      else if (align === "right") ox = Math.round(x - img.width);
      canvas.composite(img, ox, Math.round(y));
    } catch (e) {
      console.error("renderText_failed", JSON.stringify({ text_preview: text.slice(0, 40), size, err: String(e).slice(0, 120) }));
    }
    return;
  }

  // Tracked: render each glyph, place with tracking. Space characters
  // (and any glyph that yields zero-width in imagescript) are handled as
  // pure advance-only — renderText throws on empty output, so we detect and
  // synthesise a width equal to ~0.35em.
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
      const g = Image.renderText(fontBytes, size, ch, color, layout);
      glyphs.push({ img: g, w: g.width });
      totalW += g.width;
    } catch {
      // Unrenderable char (rare) → treat as space.
      glyphs.push({ img: null, w: spaceAdvance });
      totalW += spaceAdvance;
    }
  }
  totalW += tracking * Math.max(0, glyphs.length - 1);
  let ox = Math.round(x);
  if (align === "center") ox = Math.round(x - totalW / 2);
  else if (align === "right") ox = Math.round(x - totalW);
  let cx = ox;
  for (const g of glyphs) {
    if (g.img) canvas.composite(g.img, cx, Math.round(y));
    cx += g.w + tracking;
  }
}

// -------------------------- Compose --------------------------

async function renderTemplate(
  photoBytes: Uint8Array | null,
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
  debug: boolean,
): Promise<{ bytes: Uint8Array; svgKb: number; usedPhoto: boolean; svgSource?: string }> {
  const W = 1080;
  const H = 1920;

  const canvas = new Image(W, H).fill(hexToRgba(bg));

  let usedPhoto = false;
  let topLum = luminance(bg);
  let bottomLum = luminance(bg);
  if (photoBytes) {
    try {
      const base = await Image.decode(photoBytes);
      const scale = Math.max(W / base.width, H / base.height);
      const rw = Math.round(base.width * scale);
      const rh = Math.round(base.height * scale);
      const resized = base.resize(rw, rh);
      const ox = Math.floor((W - rw) / 2);
      const oy = Math.floor((H - rh) / 2);
      canvas.composite(resized, ox, oy);
      usedPhoto = true;
      topLum = bandLuminance(canvas, 0, Math.floor(H * 0.20));
      bottomLum = bandLuminance(canvas, Math.floor(H * 0.55), H);
    } catch (e) {
      console.error("photo_decode_failed", String(e).slice(0, 200));
    }
  }

  // -------- Compute text positions BEFORE we build shape SVG (need slab boxes) --------

  // Masthead
  const mastheadSize = fitSize(masthead, W - 80, 200, 100, spec.mastheadFont, spec.mastheadTracking);
  const mastheadTopY = layout === "bottom"
    ? H - 300
    : layout === "center"
    ? Math.floor(H / 2) - 100
    : 60;
  const metaSize = Math.max(22, Math.floor(mastheadSize * 0.18));
  const metaY = mastheadTopY + Math.floor(mastheadSize * 0.95) + 20;

  // Headline
  let headlineSize = spec.headlineSize;
  const headlineLines = wrapByWidth(headline, W - 120, headlineSize, spec.headlineFont, 3);
  for (const ln of headlineLines) {
    headlineSize = Math.min(headlineSize, fitSize(ln, W - 120, headlineSize, 48, spec.headlineFont, spec.headlineTracking));
  }
  const headlineLineH = Math.floor(headlineSize * 1.05);
  const headlineBlockH = headlineLineH * headlineLines.length;

  let headlineTopY: number;
  if (layout === "top") headlineTopY = Math.floor(H * 0.66) - headlineBlockH;
  else if (layout === "bottom") headlineTopY = Math.floor(H * 0.30);
  else headlineTopY = Math.floor(H * 0.55);

  // Pull-quote
  const quoteSize = Math.max(22, Math.floor(headlineSize * 0.28));
  const quoteLineH = Math.floor(quoteSize * 1.3);
  const quoteLines = pullQuote ? wrapByWidth(pullQuote, W - 180, quoteSize, "serif", 2) : [];
  const quoteTopY = headlineTopY + headlineBlockH + 20;

  const topInk = topLum < 0.5 ? CREAM : INK;
  const bottomInk = bottomLum < 0.5 ? CREAM : INK;
  const topAccent = topLum < 0.5 ? "#E7C58A" : BRONZE;
  const bottomAccent = bottomLum < 0.5 ? "#E7C58A" : BRONZE;

  // Slab rectangles for readability.
  const mastheadSlabRect = {
    x: 0, y: Math.max(0, mastheadTopY - 20),
    w: W, h: mastheadSize + metaSize + 80,
  };
  const quoteBlockH = quoteLines.length ? quoteLines.length * quoteLineH + 20 : 0;
  const headlineSlabRect = {
    x: 0, y: Math.max(0, headlineTopY - 30),
    w: W, h: headlineBlockH + quoteBlockH + 60,
  };

  // -------- Build & rasterize SHAPE SVG (no text) --------
  const svg = buildShapeSVG({
    W, H, bg, hasPhoto: usedPhoto, topLum, bottomLum, spec,
    mastheadSlabRect, headlineSlabRect, includeBarcode, barcodeSeed,
  });
  const svgKb = Math.round((new TextEncoder().encode(svg).length / 1024) * 10) / 10;

  if (debug) return { bytes: new Uint8Array(), svgKb, usedPhoto, svgSource: svg };

  try {
    const overlay = Image.renderSVG(svg, 1, Image.SVG_MODE_SCALE);
    canvas.composite(overlay, 0, 0);
  } catch (e) {
    console.error("shape_svg_render_failed:", String(e).slice(0, 400));
  }

  // -------- Load fonts, render text bitmaps, composite --------
  const fonts = await loadFonts();

  const topInkColor = hexToColor(topInk);
  const topAccentColor = hexToColor(topAccent);
  const bottomInkColor = hexToColor(bottomInk);
  const bottomAccentColor = hexToColor(bottomAccent);

  // Masthead
  drawText(canvas, fonts, masthead, W / 2, mastheadTopY, mastheadSize,
    spec.mastheadFont, topInkColor, "center", spec.mastheadTracking);
  // Meta
  drawText(canvas, fonts, metaLine, W / 2, metaY, metaSize,
    "serif", topAccentColor, "center", 3);
  // Headline
  for (let i = 0; i < headlineLines.length; i++) {
    drawText(canvas, fonts, headlineLines[i], W / 2, headlineTopY + i * headlineLineH,
      headlineSize, spec.headlineFont, bottomInkColor, "center", spec.headlineTracking);
  }
  // Pull-quote
  for (let i = 0; i < quoteLines.length; i++) {
    drawText(canvas, fonts, quoteLines[i], W / 2, quoteTopY + i * quoteLineH,
      quoteSize, "serif", bottomInkColor, "center", 0);
  }
  // Cover lines (right-rail)
  const clSize = coverLines.length > 3 ? 34 : 40;
  const clStartY = Math.floor(H * 0.30);
  const clGap = Math.floor(clSize * 2.0);
  for (let i = 0; i < coverLines.length; i++) {
    const y = clStartY + i * clGap;
    const rowIsDark = y < H * 0.55 ? topLum < 0.5 : bottomLum < 0.5;
    const rowInkColor = rowIsDark ? hexToColor(CREAM) : hexToColor(INK);
    const rowAccentColor = rowIsDark ? hexToColor("#E7C58A") : hexToColor(BRONZE);
    const color = i % 2 === 0 ? rowInkColor : rowAccentColor;
    drawText(canvas, fonts, coverLines[i], W - 60, y, clSize,
      spec.coverLineFont, color, "right", 2);
    // "inside" tag below each cover line (small italic accent)
    drawText(canvas, fonts, "inside", W - 60, y + clSize + 4,
      Math.max(14, Math.floor(clSize * 0.38)), "serif", rowAccentColor, "right", 0);
  }
  // Barcode label
  if (includeBarcode) {
    drawText(canvas, fonts, barcodeSeed.slice(0, 12).toUpperCase(),
      150, H - 95, 14, "sans", hexToColor("#000000"), "center", 2);
  }
  // URL
  drawText(canvas, fonts, "fitrater.ai", W - 60, H - 120, 26,
    "serif", bottomAccentColor, "right", 0);

  const bytes = await canvas.encodeJPEG(92);
  return { bytes, svgKb, usedPhoto };
}

// -------------------------- HTTP handler --------------------------

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });

  const url = new URL(req.url);
  const debugParam = url.searchParams.get("debug") ?? "";
  const debug = debugParam === "1"; // returns raw shape SVG
  const debugRender = debugParam === "2"; // returns JPEG bytes, skips upload/db
  const debugAny = debug || debugRender;

  const auth = req.headers.get("Authorization") ?? "";
  if (!auth.toLowerCase().startsWith("bearer ")) {
    return json(401, { error: "unauthorized" });
  }

  const SUPABASE_URL = Deno.env.get("SUPABASE_URL");
  const SERVICE_ROLE = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  const FAL_KEY = Deno.env.get("FAL_KEY");
  if (!SUPABASE_URL || !SERVICE_ROLE) {
    return json(500, { error: "server_misconfigured" });
  }

  let body: any = {};
  try { body = await req.json(); } catch { /* noop */ }

  const rawHeadline = body?.headline ? String(body.headline).trim() : "";
  if (!rawHeadline) return json(400, { error: "missing_headline" });

  const headline = sanitizeHeadline(rawHeadline);
  if (!headline) return json(400, { error: "invalid_headline" });

  const masthead = sanitizeMasthead(body?.masthead ? String(body.masthead) : undefined);
  const pull_quote = body?.pull_quote ? sanitizePullQuote(String(body.pull_quote)) : "";
  const mood = body?.mood ? String(body.mood) : undefined;
  const color = normalizeHex(body?.color ? String(body.color) : undefined, CREAM);
  const rawLayout = body?.layout ? String(body.layout) : "top";
  const layoutV: "top" | "center" | "bottom" =
    rawLayout === "center" || rawLayout === "bottom" ? rawLayout : "top";
  const pose = sanitizePose(body?.pose ? String(body.pose) : undefined);
  const price = sanitizePrice(body?.price ? String(body.price) : undefined);
  const includeBarcode = body?.include_barcode === false ? false : true;

  const rawCoverLines: unknown = body?.cover_lines;
  const coverLines: string[] = Array.isArray(rawCoverLines)
    ? rawCoverLines
        .filter((x) => typeof x === "string")
        .map((s) => sanitizeCoverLine(s as string))
        .filter((s) => s.length > 0)
        .slice(0, 4)
    : [];

  const spec = specForMood(mood);

  let user_id: string;
  if (debugAny) {
    user_id = "00000000-0000-0000-0000-000000000000";
  } else {
    const userClient = createClient(SUPABASE_URL, SERVICE_ROLE, {
      global: { headers: { Authorization: auth } },
    });
    const { data: userData, error: userErr } = await userClient.auth.getUser();
    if (userErr || !userData?.user?.id) {
      return json(401, { error: "unauthorized", detail: userErr?.message });
    }
    user_id = userData.user.id;
  }

  const admin = createClient(SUPABASE_URL, SERVICE_ROLE);

  const now = new Date();
  const vol_number = weekOfYear(now);
  const monthYear = `${MONTHS[now.getUTCMonth()]} ${now.getUTCFullYear()}`;
  const metaLine = `${monthYear} · VOL ${vol_number} · ${price}`;

  let photoBytes: Uint8Array | null = null;
  if (FAL_KEY && !debug) {
    // debug=1 (raw SVG) skips Fal; debug=2 (JPEG render) still needs the photo.
    photoBytes = await generateSubjectImage(FAL_KEY, pose, mood, spec, color);
  }

  let render: { bytes: Uint8Array; svgKb: number; usedPhoto: boolean; svgSource?: string };
  try {
    render = await renderTemplate(
      photoBytes, masthead, metaLine, headline, pull_quote, coverLines,
      color, layoutV, spec, includeBarcode, user_id, debug,
    );
  } catch (e) {
    return json(502, { error: "render_failed", detail: String(e).slice(0, 400) });
  }

  if (debug && render.svgSource) {
    return new Response(render.svgSource, {
      status: 200,
      headers: { ...CORS, "content-type": "image/svg+xml" },
    });
  }
  if (debugRender) {
    return new Response(render.bytes, {
      status: 200,
      headers: {
        ...CORS,
        "content-type": "image/jpeg",
        "x-fal-key-present": FAL_KEY ? "1" : "0",
        "x-used-photo": render.usedPhoto ? "1" : "0",
        "x-svg-kb": String(render.svgKb),
        "x-fal-image-url": LAST_FAL_IMAGE_URL ?? "",
      },
    });
  }

  console.log("template_render", JSON.stringify({
    used_photo: render.usedPhoto,
    svg_kb: render.svgKb,
    cover_lines: coverLines.length,
    mood: mood ?? "editorial",
  }));

  const objectKey = `${user_id}/templates/${crypto.randomUUID()}.jpg`;
  const { error: upErr } = await admin.storage
    .from("magazine_covers")
    .upload(objectKey, render.bytes, {
      contentType: "image/jpeg",
      upsert: true,
    });
  if (upErr) {
    return json(502, { error: "upload_failed", detail: upErr.message });
  }
  const { data: pub } = admin.storage.from("magazine_covers").getPublicUrl(objectKey);
  const cover_url = pub?.publicUrl ?? null;

  const { data: inserted, error: insErr } = await admin
    .from("magazine_covers")
    .insert({
      outfit_id: null,
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
      cover_url,
      cover_id: null,
      error: `db_insert_failed: ${insErr.message}`,
    });
  }

  return json(200, {
    cover_url,
    cover_id: inserted?.id ?? null,
  });
});
