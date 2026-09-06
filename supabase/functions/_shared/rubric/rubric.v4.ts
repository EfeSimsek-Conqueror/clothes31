// Scoring v4 — the rubric data. Pure data, no logic; `weights.ts` is the
// algorithm that reads it. Clients hold a hand-ported copy of these tables ONLY
// to render the "grading hardest on …" preview strip before a credit is spent —
// never to compute a score. `test/weights_test.ts` pins every combination.

import type {
  AxisKey,
  Occasion,
  OnFeet,
  Role,
  Room,
  RuleMode,
  TimeOfDay,
  Venue,
  WeatherBand,
} from "./types.ts";

export const RUBRIC_VERSION = 1;

export const AXIS_LABELS: Record<AxisKey, string> = {
  SIL: "Silhouette",
  FIT: "Fit",
  COL: "Colour",
  TEX: "Fabric",
  CTX: "Dress code",
  DTL: "Detail",
  SHO: "Footwear",
  PRS: "Upkeep",
  POV: "Point of view",
};

/**
 * Base weight vectors at the neutral dial pair (f=3, p=3). Each column sums to
 * 1.000.
 *
 * Work — workwear is competence signalling: dress code and fit dominate, and
 *   standing out is a liability.
 * Date — attraction is legibility of shape and personality: point of view and
 *   silhouette lead, dress code is only a sanity check.
 * Wedding — the event has published rules and it isn't your day. CTX carries
 *   the single heaviest weight in the system; point of view is nearly zeroed.
 * Casual — leisure is where taste is actually visible. CTX exists only to catch
 *   a tuxedo at a picnic.
 * Everyday — "does this work all day and look maintained": fit, silhouette and
 *   footwear split it.
 */
export const BASE_WEIGHTS: Record<Occasion, Record<AxisKey, number>> = {
  work:     { SIL: 0.13, FIT: 0.17, COL: 0.10, TEX: 0.09, CTX: 0.17, DTL: 0.12, SHO: 0.09, PRS: 0.07, POV: 0.06 },
  date:     { SIL: 0.15, FIT: 0.15, COL: 0.12, TEX: 0.12, CTX: 0.10, DTL: 0.10, SHO: 0.07, PRS: 0.05, POV: 0.14 },
  wedding:  { SIL: 0.13, FIT: 0.15, COL: 0.10, TEX: 0.09, CTX: 0.26, DTL: 0.12, SHO: 0.08, PRS: 0.05, POV: 0.02 },
  casual:   { SIL: 0.17, FIT: 0.12, COL: 0.13, TEX: 0.13, CTX: 0.03, DTL: 0.09, SHO: 0.11, PRS: 0.06, POV: 0.16 },
  everyday: { SIL: 0.15, FIT: 0.17, COL: 0.12, TEX: 0.11, CTX: 0.05, DTL: 0.09, SHO: 0.14, PRS: 0.07, POV: 0.10 },
};

/**
 * Dial modulation. `m[a] = (1 + α·(f−3)/2) · (1 + β·(p−3)/2)`.
 *
 * In one sentence for the "how is this calculated?" sheet: dressier ⇒
 * correctness, tailoring and upkeep matter more and self-expression matters
 * less; louder ⇒ colour, detail and point of view matter more and correctness
 * and upkeep matter less.
 */
export const ALPHA: Record<AxisKey, number> = {
  SIL: 0.00, FIT: 0.35, COL: -0.10, TEX: 0.10, CTX: 0.60, DTL: 0.25, SHO: 0.15, PRS: 0.45, POV: -0.35,
};

export const BETA: Record<AxisKey, number> = {
  SIL: 0.20, FIT: -0.20, COL: 0.45, TEX: 0.10, CTX: -0.30, DTL: 0.35, SHO: 0.00, PRS: -0.15, POV: 0.70,
};

/** Bounds on the modulation multiplier. Stops a corner (f=5, p=5) from
 * cancelling itself out. */
export const M_MIN = 0.55;
export const M_MAX = 1.75;

/**
 * Bounds on a raw weight, applied AFTER modulation and the band-4 deltas.
 * The ceiling is 0.40, not 0.32: at 0.32 a wedding at f=4 (0.26 × 1.30 = 0.338)
 * and f=5 (0.26 × 1.60 = 0.416) both clamped to the same number and the
 * flagship dial did nothing.
 */
export const W_MIN = 0.02;
export const W_MAX = 0.40;

/** Seeded dial positions per occasion. Tapping an occasion chip snaps both
 * dials — the first visible proof that the chip does something. */
export const SEED_FORMALITY: Record<Occasion, number> = {
  work: 3, date: 3, wedding: 4, casual: 2, everyday: 2,
};

export const SEED_PRESENCE: Record<Occasion, number> = {
  work: 2, date: 4, wedding: 3, casual: 3, everyday: 3,
};

/** Band-2 captions. The product surface — edit here, not in the clients. */
export const FORMALITY_CAPTIONS: Record<Occasion, string[]> = {
  work:     ["Working from home", "Casual Friday", "Business casual", "Client-facing", "Boardroom or interview"],
  date:     ["Coffee or a walk", "A casual bar", "Dinner out", "Somewhere nice", "A black-tie event"],
  wedding:  ["Beach or backyard", "Garden party", "Semi-formal", "Cocktail", "Black tie"],
  casual:   ["Errands", "Friends, brunch", "A day out", "Dinner or a gig", "A party"],
  everyday: ["Around the house", "Around town", "Out all day", "Day into evening", "Something on tonight"],
};

/**
 * The dial is occasion-RELATIVE ("Boardroom or interview" is the top of the
 * work ladder); the model's `formality_read` is ABSOLUTE (5 means a tuxedo or
 * a floor-length gown). Comparing the two directly made every boardroom look
 * permanently one rung under-dressed, because no work outfit is ever a 5.
 *
 * This table translates a dial position into the absolute rung the brief
 * actually implies, and it is what the dress-code gap is measured against.
 * Adjacent positions may share a rung — a wedding's "semi-formal" and
 * "cocktail" genuinely do — and the difference between them then lives in the
 * weights and in the cocktail rule instead.
 */
export const TARGET_FORMALITY: Record<Occasion, number[]> = {
  work: [1, 2, 3, 4, 4],
  date: [1, 2, 3, 4, 5],
  wedding: [2, 3, 4, 4, 5],
  casual: [1, 2, 2, 3, 4],
  everyday: [1, 2, 2, 3, 4],
};

export const PRESENCE_CAPTIONS = [
  "Invisible", "Quiet", "Balanced", "Noticed", "Be looked at",
];

/** Where an outfit ITSELF sits, as reported by the model. Used for copy only. */
export const FORMALITY_READ_LABELS = [
  "Loungewear", "Everyday casual", "Smart casual", "Dressed", "Formal",
];

export const ROLE_LABELS: Record<Role, string> = {
  guest: "Guest",
  wedding_party: "Wedding party",
  couple: "The couple",
};

export const VENUE_LABELS: Record<Venue, string> = {
  ballroom: "Ballroom or hotel",
  garden: "Garden or outdoors",
  beach: "Beach",
  registry_or_worship: "Registry or place of worship",
};

export const ROOM_LABELS: Record<Room, string> = {
  creative: "Creative",
  business_casual: "Business casual",
  corporate: "Corporate",
  client_facing: "Client-facing",
};

export const ON_FEET_LABELS: Record<OnFeet, string> = {
  most_of_day: "On my feet most of the day",
  some: "Some walking",
  seated: "Mostly seated",
};

export const WEATHER_LABELS: Record<WeatherBand, string> = {
  cold: "Cold", mild: "Mild", hot: "Hot",
};

export const TIME_LABELS: Record<TimeOfDay, string> = {
  daytime: "Daytime", evening: "Evening",
};

type Delta = Partial<Record<AxisKey, number>>;

/**
 * Band-4 and cross-cutting weight deltas, additive on the modulated weight and
 * applied before the clamp. The table does not need to sum to zero — the
 * normaliser absorbs it.
 */
export const ROLE_DELTAS: Record<Role, Delta> = {
  guest: {},
  // You are in every photograph for the rest of these people's lives.
  wedding_party: { DTL: 0.03, PRS: 0.04, POV: -0.02, COL: -0.05 },
  // It IS your day: the code relaxes, self-expression is the point.
  couple: { DTL: 0.05, POV: 0.04, CTX: -0.03, PRS: 0.02, COL: -0.02 },
};

export const VENUE_DELTAS: Record<Venue, Delta> = {
  ballroom: {},
  garden: { SHO: 0.03, TEX: 0.02, DTL: -0.02, COL: -0.03 },
  beach: { SHO: 0.03, TEX: 0.03, DTL: -0.03, COL: -0.03, PRS: -0.02 },
  registry_or_worship: { CTX: 0.02, PRS: 0.02, POV: -0.02, COL: -0.02 },
};

export const ROOM_DELTAS: Record<Room, Delta> = {
  creative: { POV: 0.05, COL: 0.02, CTX: -0.05, PRS: -0.02 },
  business_casual: {},
  corporate: { CTX: 0.04, FIT: 0.02, POV: -0.03, TEX: -0.03 },
  client_facing: { CTX: 0.03, PRS: 0.03, POV: -0.02, COL: -0.02, TEX: -0.02 },
};

export const ON_FEET_DELTAS: Record<OnFeet, Delta> = {
  most_of_day: { SHO: 0.04, COL: -0.02, POV: -0.02 },
  some: {},
  seated: { SHO: -0.05, DTL: 0.03, PRS: 0.02 },
};

export const WEATHER_DELTAS: Record<WeatherBand, Delta> = {
  cold: { TEX: 0.03, CTX: 0.02, COL: -0.02, POV: -0.03 },
  mild: {},
  hot: { TEX: 0.03, PRS: -0.02, SIL: -0.01 },
};

export const PRECIP_DELTA: Delta = { SHO: 0.03, TEX: 0.01, COL: -0.02, DTL: -0.02 };

export const TIME_DELTAS: Record<TimeOfDay, Delta> = {
  daytime: { TEX: 0.02, COL: 0.02, DTL: -0.02, POV: -0.02 },
  evening: {},
};

/** An explicit intent buys its axis five points of weight, taken from the two
 * lightest OTHER axes. */
export const INTENT_WEIGHT_BONUS = 0.05;

/** Without a back view the fit read is a partial one, and it is weighted as
 * such. R-EVIDENCE also ceilings the axis at 8.5. */
export const NO_BACK_FIT_MULTIPLIER = 0.80;

/** Tappable intent chips, and the axis each maps to. Free text is mapped by the
 * model, which returns `intent_axis`. */
export const INTENT_CHIPS: Array<{ label: string; axis: AxisKey }> = [
  { label: "Look taller", axis: "SIL" },
  { label: "Look expensive", axis: "TEX" },
  { label: "Look like I didn't try", axis: "POV" },
  { label: "Hide my arms", axis: "FIT" },
];

/**
 * Dress-code gap coefficients. Under-dressing a wedding costs a quarter extra;
 * under-dressing a casual or everyday brief costs half. Over-dressing is a real
 * miss everywhere — a matched suit at a picnic is a misread of the room, not
 * extra effort.
 */
export const UNDER_COEFF: Record<Occasion, number> = {
  work: 1.00, date: 1.00, wedding: 1.25, casual: 0.50, everyday: 0.50,
};

export const OVER_COEFF: Record<Occasion, number> = {
  work: 0.75, date: 0.90, wedding: 1.00, casual: 1.00, everyday: 1.00,
};

/**
 * Default rule modes.
 *
 * `cap` fires as arithmetic. `caveat_only` renders as a line of copy with zero
 * numeric effect. `off` is silent. Overridable at runtime through the
 * SCORE_RULE_MODES env var so a misbehaving rule degrades without an app
 * release.
 *
 * R-WHITE ships in `caveat_only`. It is the most emotionally loaded rule in the
 * app and a false positive on a pale-gold dress in warm light hands a
 * legitimate 8.1 a 5.0 with a moralising line attached. It flips to `cap` only
 * after a labelled precision eval clears 0.95.
 */
export const DEFAULT_RULE_MODES: Record<string, RuleMode> = {
  "R-WHITE": "caveat_only",
  "R-BT": "cap",
  "R-BT2": "cap",
  "R-GROUND": "cap",
  "R-COCKTAIL": "cap",
  "R-CORP": "cap",
  "R-CREATIVE": "cap",
  "R-INFORM": "cap",
  "R-FEET": "cap",
  "R-COLD": "cap",
  "R-HOT": "cap",
  "R-SHINE": "cap",
  "R-FITGATE": "cap",
  "R-UPKEEP": "cap",
  "R-PROP": "cap",
  "R-EVIDENCE": "cap",
  "R-INTENT": "cap",
  "R-ANTI": "cap",
  "R-FLOOR": "cap",
};

/** Brief-line templates with named slots — never concatenation, so word order
 * survives translation. */
export const BRIEF_FORMATS: Record<string, string> = {
  en: "{occasion} look{role}{qualifier}, meant to read {presence}.",
  tr: "{presence} durmasını istediğin{qualifier} bir {occasion} görünümü{role}.",
};
