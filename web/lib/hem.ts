/**
 * Mock scoring output — the shape a real LLM/vision service would return.
 * Deterministic, so the same input renders the same score in dev.
 */

export type HemPieceScore = {
  label: string;
  category: string;
  score: number;
  note: string;
};

export type HemSubscore = {
  key: string;
  label: string;
  value: number;
};

export type HemContextScore = {
  occasion: string;
  score: number;
  reason: string;
};

export type HemScoreResult = {
  overall: number;
  verdict: string;
  quote: string;
  pieces: HemPieceScore[];
  subscores: HemSubscore[];
  context: HemContextScore[];
  swaps: { swap: string; because: string }[];
};

function hash(str: string): number {
  let h = 2166136261;
  for (let i = 0; i < str.length; i++) {
    h ^= str.charCodeAt(i);
    h = Math.imul(h, 16777619);
  }
  return Math.abs(h);
}

export function scoreLook(seed: string = "today"): HemScoreResult {
  const h = hash(seed);
  const overall = 6 + ((h % 40) / 10); // 6.0 – 9.9
  const round1 = (n: number) => Math.round(n * 10) / 10;

  return {
    overall: round1(overall),
    verdict: overall > 8.5 ? "Sharp." : overall > 7 ? "Considered." : "Try again.",
    quote:
      overall > 8.5
        ? "The trousers do the talking. Let them."
        : overall > 7
          ? "Almost. Trade the belt for something quieter."
          : "The idea is there — the proportions aren't.",
    pieces: [
      {
        label: "Linen camp shirt",
        category: "Top",
        score: round1(overall + 0.2),
        note: "Correct colour for the season.",
      },
      {
        label: "Pleated trouser",
        category: "Bottom",
        score: round1(overall - 0.4),
        note: "Break is a touch long.",
      },
      {
        label: "Leather loafer",
        category: "Shoe",
        score: round1(overall + 0.1),
        note: "Handsome, but scuffed.",
      },
    ],
    subscores: [
      { key: "fit", label: "Fit", value: round1(overall - 0.1) },
      { key: "colour", label: "Colour", value: round1(overall + 0.3) },
      { key: "proportion", label: "Proportion", value: round1(overall - 0.5) },
      { key: "occasion", label: "Occasion", value: round1(overall) },
    ],
    context: [
      { occasion: "Dinner", score: round1(overall + 0.1), reason: "Reads elegant." },
      { occasion: "Office", score: round1(overall - 0.3), reason: "A hair casual." },
      { occasion: "Weekend", score: round1(overall + 0.4), reason: "Effortless." },
    ],
    swaps: [
      { swap: "Tuck the shirt", because: "Restores waistline." },
      { swap: "Swap belt to woven bronze", because: "Ties into the loafer." },
    ],
  };
}
