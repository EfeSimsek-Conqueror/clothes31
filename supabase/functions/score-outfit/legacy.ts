// deno-lint-ignore-file no-explicit-any
// Scoring v4 — the v3 envelope.
//
// Shipped iOS and Android builds are live and will stay live for months. They
// throw without `score`, they decode `subscores` as four optional numbers, they
// iterate `swaps` as a flat string array, and their `Annotation` decoder has no
// tolerance for a missing key. Every one of those shapes is frozen here, and
// `test/compat_test.ts` is what stops a future edit from breaking a build
// nobody can patch.

export interface SwapV2 {
  from: string;
  to: string;
  axis: string;
  why: string;
  effort: string;
}

export interface LegacyAnnotation {
  x_pct: number;
  y_pct: number;
  label: string;
  score: number;
  note: string;
}

const EFFORTS = new Set(["swap", "tailor", "buy_or_rent"]);

export function normalizeSwaps(raw: any): SwapV2[] {
  if (!Array.isArray(raw)) return [];
  return raw.slice(0, 4).map((s) => ({
    from: String(s?.from ?? "").slice(0, 80),
    to: String(s?.to ?? "").slice(0, 80),
    axis: String(s?.axis ?? "").toUpperCase().slice(0, 4),
    why: String(s?.why ?? "").slice(0, 200),
    effort: EFFORTS.has(String(s?.effort)) ? String(s.effort) : "swap",
  })).filter((s) => s.to.length > 0);
}

/** The flat `swaps: string[]` the shipped detail screen renders as bullets. */
export function legacySwaps(swaps: SwapV2[]): string[] {
  return swaps.slice(0, 2).map((s) => (s.from ? `${s.from} → ${s.to}` : s.to));
}

/**
 * The v3 `annotations` array is not optional in practice: a shipped build with
 * an empty x-ray looks broken rather than empty. If the drawing call fails we
 * synthesise one row from the engine's own lever, and every one of the five
 * keys is always present — the shipped iOS `Annotation` uses a synthesised
 * decoder, so a missing key fails the WHOLE response, not just that row.
 */
export function legacyAnnotations(
  pieces: Array<{ label: string; x_pct: number; y_pct: number; score: number; note: string }>,
  leverLabel: string | null,
  topSwap: SwapV2 | undefined,
): LegacyAnnotation[] {
  if (pieces.length > 0) {
    return pieces.map((p) => ({
      x_pct: p.x_pct,
      y_pct: p.y_pct,
      label: p.label.slice(0, 24) || "Piece",
      score: p.score,
      note: (p.note || "One change moves this most.").slice(0, 60),
    }));
  }
  return [{
    x_pct: 50,
    y_pct: 50,
    label: (leverLabel ?? "The look").slice(0, 24),
    score: 5,
    note: (topSwap ? topSwap.to : "One change moves this most.").slice(0, 60),
  }];
}
