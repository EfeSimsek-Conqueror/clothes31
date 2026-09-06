// Scoring v4 — response sanitation for everything the model draws.
// Moved verbatim from the v3 index.ts, plus `pieces`.

const MARKUP_TYPES = new Set(["arrow", "line", "focus", "swap"]);
export const FIT_COLS = 16;
export const FIT_ROWS = 32;

export function clamp01(n: unknown, fallback = 0.5): number {
  const v = Number(n);
  if (!isFinite(v)) return fallback;
  return Math.max(0, Math.min(1, v));
}

export function clampRange(n: unknown, lo: number, hi: number, fallback: number): number {
  const v = Number(n);
  if (!isFinite(v)) return fallback;
  return Math.max(lo, Math.min(hi, v));
}

// deno-lint-ignore no-explicit-any
function normalizePoint(p: any): [number, number] | null {
  if (!p) return null;
  if (Array.isArray(p) && p.length >= 2) return [clamp01(p[0]), clamp01(p[1])];
  if (typeof p === "object" && "x" in p && "y" in p) return [clamp01(p.x), clamp01(p.y)];
  return null;
}

// deno-lint-ignore no-explicit-any
export function sanitizeMarkupAnnotations(raw: any): any[] {
  if (!Array.isArray(raw)) return [];
  // deno-lint-ignore no-explicit-any
  const out: any[] = [];
  for (const a of raw.slice(0, 6)) {
    const type = String(a?.type ?? "").toLowerCase().trim();
    if (!MARKUP_TYPES.has(type)) continue;
    const note = String(a?.note ?? "").slice(0, 200);
    const confidence = clamp01(a?.confidence, 0.7);
    const coordsRaw = a?.coords ?? {};
    // deno-lint-ignore no-explicit-any
    const coords: any = {};
    if (type === "arrow" || type === "line") {
      const from = normalizePoint(coordsRaw.from);
      const to = normalizePoint(coordsRaw.to);
      if (!from || !to) continue;
      coords.from = from;
      coords.to = to;
    } else if (type === "focus") {
      const at = normalizePoint(coordsRaw.at);
      if (!at) continue;
      coords.at = at;
      coords.radius = clamp01(coordsRaw.radius, 0.08);
    } else if (type === "swap") {
      const at = normalizePoint(coordsRaw.at);
      if (!at) continue;
      coords.at = at;
    }
    out.push({ type, coords, note, confidence });
  }
  return out;
}

// deno-lint-ignore no-explicit-any
export function sanitizeFitMap(raw: any): any | null {
  if (!raw || typeof raw !== "object") return null;
  const gridIn = raw.grid;
  if (!Array.isArray(gridIn)) return null;

  const rowsIn = gridIn.length;
  const grid: number[][] = [];
  for (let r = 0; r < FIT_ROWS; r++) {
    const srcR = Math.min(rowsIn - 1, Math.floor((r / FIT_ROWS) * rowsIn));
    const srcRow = Array.isArray(gridIn[srcR]) ? gridIn[srcR] : [];
    const row: number[] = [];
    const colsIn = srcRow.length || 1;
    for (let c = 0; c < FIT_COLS; c++) {
      const srcC = Math.min(colsIn - 1, Math.floor((c / FIT_COLS) * colsIn));
      row.push(clampRange(srcRow[srcC], -1, 1, 0));
    }
    grid.push(row);
  }

  // deno-lint-ignore no-explicit-any
  const hotspotsIn: any[] = Array.isArray(raw.hotspots) ? raw.hotspots : [];
  const hotspots = hotspotsIn.slice(0, 3).map((h) => {
    const at = normalizePoint(h?.at) ?? [0.5, 0.5];
    const label = String(h?.label ?? "").slice(0, 80);
    const severity = clampRange(h?.severity, -1, 1, 0);
    return { at, label, severity };
  }).filter((h) => h.label.length > 0);

  return { resolution: [FIT_COLS, FIT_ROWS], grid, hotspots };
}

export interface Piece {
  label: string;
  x_pct: number;
  y_pct: number;
  score: number;
  verdict: string;
  note: string;
}

const PIECE_VERDICTS = new Set(["works", "works_elsewhere", "drags"]);

// deno-lint-ignore no-explicit-any
export function sanitizePieces(raw: any): Piece[] {
  if (!Array.isArray(raw)) return [];
  return raw.slice(0, 6).map((p) => {
    const verdict = String(p?.verdict ?? "").toLowerCase().trim();
    return {
      label: String(p?.label ?? "").slice(0, 40),
      x_pct: clampRange(p?.x_pct, 0, 100, 50),
      y_pct: clampRange(p?.y_pct, 0, 100, 50),
      score: clampRange(p?.score, 0, 10, 5),
      verdict: PIECE_VERDICTS.has(verdict) ? verdict : "works",
      note: String(p?.note ?? "").slice(0, 60),
    };
  }).filter((p) => p.label.length > 0);
}

export function sanitizePalette(raw: unknown): string[] {
  if (!Array.isArray(raw)) return [];
  return raw
    .map((h) => String(h ?? "").trim())
    .filter((h) => /^#?[0-9a-f]{6}$/i.test(h))
    .map((h) => (h.startsWith("#") ? h : `#${h}`).toLowerCase())
    .slice(0, 5);
}
