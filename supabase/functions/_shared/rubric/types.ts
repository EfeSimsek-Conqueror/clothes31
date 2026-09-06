// Scoring v4 — shared types.
//
// The engine is TypeScript, not the model. Every weight, coefficient, cap and
// floor lives here or in `rubric.v4.ts`; none of it is ever interpolated into a
// prompt. The model is a witness (it reports axis scores and literal signals);
// the server is the judge (it does all the arithmetic).

export const AXIS_KEYS = [
  "SIL",
  "FIT",
  "COL",
  "TEX",
  "CTX",
  "DTL",
  "SHO",
  "PRS",
  "POV",
] as const;

export type AxisKey = typeof AXIS_KEYS[number];

export type Occasion = "work" | "date" | "wedding" | "casual" | "everyday";

/** Wedding only. Gates R-WHITE. */
export type Role = "guest" | "wedding_party" | "couple";

/**
 * Wedding only. Collapsed from the original 7 (ballroom·day, ballroom·evening,
 * garden·day, …) to 4 — the day/evening halves produced identical weight
 * vectors because the hour already arrives on `time_of_day`.
 */
export type Venue = "ballroom" | "garden" | "beach" | "registry_or_worship";

/** Work only. A creative studio and a corporate floor sit at the same formality
 * and invert the dress-code penalty direction, which no dial can express. */
export type Room = "creative" | "business_casual" | "corporate" | "client_facing";

/** Everyday only. Gates R-FEET. */
export type OnFeet = "most_of_day" | "some" | "seated";

/** Collapsed from 5 bands — `freezing`/`warm` moved the headline less than the
 * display rounding, so they are accepted on the wire and folded in. */
export type WeatherBand = "cold" | "mild" | "hot";

export type TimeOfDay = "daytime" | "evening";

export interface Weather {
  band: WeatherBand;
  precip: boolean;
}

export interface Intake {
  occasion: Occasion;
  /** 1..5 — how dressed up the wearer intended to be. */
  formality: number;
  /** 1..5 — how much the wearer wanted to be looked at. */
  presence: number;
  role: Role | null;
  venue: Venue | null;
  room: Room | null;
  on_feet: OnFeet | null;
  intent: string | null;
  /** Omitted entirely when location is denied — never guessed. */
  weather: Weather | null;
  time_of_day: TimeOfDay;
  /** True when a back view was supplied. Drives R-EVIDENCE. */
  has_back: boolean;
}

export type RuleMode = "cap" | "caveat_only" | "off";

export interface WeightDelta {
  reason: string;
  axis: AxisKey;
  delta: number;
  kind: "dial" | "band4" | "cross" | "intent" | "evidence" | "null_redistribution";
}

export interface BuiltWeights {
  w: Record<AxisKey, number>;
  deltas: WeightDelta[];
  /** Axis keys the model could not judge; weight 0, excluded from the mean. */
  nullAxes: AxisKey[];
  /** Axes ordered by final weight, descending. */
  order: AxisKey[];
}

export interface RuleResult {
  id: string;
  name: string;
  trigger: string;
  effect: string;
  axis: AxisKey | null;
  before: number | null;
  after: number | null;
  /** Filled by the engine as an ordered waterfall, in fire order. */
  headline_cost: number;
  mode: RuleMode;
}

export interface Caveat {
  kind: string;
  copy: string;
  cost?: string;
  axis?: AxisKey;
}
