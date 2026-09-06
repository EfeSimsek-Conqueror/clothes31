// Scoring v4 — the dress-code axis.
//
// CTX is the one axis the model never scores. The model reports where the
// outfit ITSELF sits on a five-rung formality ladder; the server computes the
// distance from where the wearer said they were aiming. That is what makes the
// heaviest weight in the system arithmetic rather than taste.

import {
  FORMALITY_CAPTIONS,
  FORMALITY_READ_LABELS,
  OVER_COEFF,
  TARGET_FORMALITY,
  UNDER_COEFF,
} from "../_shared/rubric/rubric.v4.ts";
import type { Occasion } from "../_shared/rubric/types.ts";

export interface CtxResult {
  score: number;
  read: number;
  /** The dial position the wearer chose, 1-5, for display. */
  claimed: number;
  /** The absolute formality rung that dial position implies. The gap is
   * measured against THIS, not against the dial. */
  target: number;
  gap: number;
  /** Signed, occasion-weighted magnitude of the gap. */
  scaled: number;
  severity: "met" | "interpreted" | "near" | "miss";
  /** Overall ceiling armed by the size of the gap alone. */
  overallCap: number;
  direction: "under" | "over" | "none";
  line: string;
}

function clamp(n: number, lo: number, hi: number): number {
  return Math.max(lo, Math.min(hi, n));
}

export function computeCtx(
  formalityRead: number,
  formalityClaimed: number,
  occasion: Occasion,
  interp: number,
): CtxResult {
  const read = clamp(Math.round(formalityRead), 1, 5);
  const claimed = clamp(Math.round(formalityClaimed), 1, 5);
  const target = TARGET_FORMALITY[occasion][claimed - 1] ?? claimed;
  const gap = read - target;
  const coeff = gap < 0 ? UNDER_COEFF[occasion] : OVER_COEFF[occasion];
  const scaled = Math.abs(gap) * coeff;

  // Interpretation is the only path to a ten, and only when the look is
  // essentially on code. A look that misses the code cannot earn credit for
  // having a point of view about the code it missed.
  const interpBonus = scaled <= 0.5 ? clamp(interp, 0, 1) : 0;
  const score = clamp(9.0 - 1.9 * scaled + interpBonus, 1, 10);

  // A gap of one is a miss you can talk your way out of; beyond that the whole
  // read is ceilinged, because nothing else in the outfit can rescue being at
  // the wrong event.
  const overallCap = Math.max(5.5, 10 - 1.1 * Math.max(0, scaled - 1));

  let severity: CtxResult["severity"];
  if (gap === 0) severity = interpBonus >= 1 ? "interpreted" : "met";
  else if (scaled <= 1.0) severity = "near";
  else severity = "miss";

  const direction: CtxResult["direction"] = gap === 0 ? "none" : gap < 0 ? "under" : "over";
  const claimedCaption = FORMALITY_CAPTIONS[occasion][claimed - 1] ?? "";
  const readLabel = FORMALITY_READ_LABELS[read - 1] ?? "";

  let line: string;
  if (gap === 0) {
    line = `You dialled ${claimedCaption.toLowerCase()}, and that is exactly what this reads as.`;
  } else if (gap < 0) {
    line = `You dialled ${claimedCaption.toLowerCase()}. This reads ${readLabel.toLowerCase()} — ${Math.abs(gap) === 1 ? "one step" : `${Math.abs(gap)} steps`} under.`;
  } else {
    line = `You dialled ${claimedCaption.toLowerCase()}. This reads ${readLabel.toLowerCase()} — ${gap === 1 ? "one step" : `${gap} steps`} over.`;
  }

  return { score, read, claimed, target, gap, scaled, severity, overallCap, direction, line };
}
