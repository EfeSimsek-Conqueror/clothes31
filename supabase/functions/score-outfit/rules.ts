// Scoring v4 — the deterministic rules.
//
// Every rule is a pure function of (intake, signals, reads). None of them is
// visible to the model. Each returns its trigger, its effect and the signals it
// leaned on, so the result screen can show a receipt instead of a verdict.
//
// The confidence gate lives at one choke point, `gate()`. A rule whose worst
// case costs more than a point of headline needs two corroborating signals, not
// one — that is the difference between a defensible app and a hostile one.

import { DEFAULT_RULE_MODES } from "../_shared/rubric/rubric.v4.ts";
import type { AxisKey, Intake, RuleMode } from "../_shared/rubric/types.ts";
import type { CtxResult } from "./ctx.ts";

// ── Signals ────────────────────────────────────────────────────────────────

export interface Signal {
  value: string;
  confidence: number;
}

export type SignalMap = Record<string, Signal>;

const TRUE_WORDS = new Set(["true", "yes", "1", "present"]);

export function sigOf(signals: SignalMap, key: string): Signal | undefined {
  const s = signals[key];
  if (!s) return undefined;
  if (!isFinite(s.confidence)) return undefined;
  return s;
}

export function sigBool(signals: SignalMap, key: string): boolean {
  const s = sigOf(signals, key);
  return s ? TRUE_WORDS.has(s.value.toLowerCase().trim()) : false;
}

export function sigEnum(signals: SignalMap, key: string): string {
  const s = sigOf(signals, key);
  return s ? s.value.toLowerCase().trim() : "";
}

export function sigList(signals: SignalMap, key: string): string[] {
  const s = sigOf(signals, key);
  if (!s || !s.value.trim()) return [];
  return s.value.split(/[,;]/).map((x) => x.toLowerCase().trim()).filter(Boolean);
}

export function sigInt(signals: SignalMap, key: string): number | null {
  const s = sigOf(signals, key);
  if (!s) return null;
  const n = Number(s.value);
  return isFinite(n) ? n : null;
}

function conf(signals: SignalMap, keys: string[]): number {
  const vals = keys.map((k) => sigOf(signals, k)?.confidence).filter((c): c is number => c != null);
  if (vals.length === 0) return 0;
  return Math.min(...vals);
}

/**
 * Is a garment hex actually near-white? Used only to corroborate the model's
 * own white call — and deliberately strict, because the palette can also pick
 * up a wall, a bedsheet or overexposed daylight.
 */
export function nearWhite(hex: string): boolean {
  const m = /^#?([0-9a-f]{6})$/i.exec(hex.trim());
  if (!m) return false;
  const n = parseInt(m[1], 16);
  const r = ((n >> 16) & 255) / 255, g = ((n >> 8) & 255) / 255, b = (n & 255) / 255;
  const max = Math.max(r, g, b), min = Math.min(r, g, b);
  const sat = max === 0 ? 0 : (max - min) / max;
  return max > 0.90 && sat < 0.12;
}

// ── Effects ────────────────────────────────────────────────────────────────

export type Effect =
  | { kind: "axis_delta"; axis: AxisKey; value: number }
  | { kind: "axis_cap"; axis: AxisKey; value: number }
  | { kind: "axis_floor"; axis: AxisKey; value: number }
  | { kind: "overall_delta"; value: number }
  | { kind: "overall_cap"; value: number };

export interface FiredRule {
  id: string;
  name: string;
  trigger: string;
  effect: string;
  effects: Effect[];
  /** Copy shown when the rule is downgraded to a caveat. */
  caveat: string;
  mode: RuleMode;
  confidence: number;
}

export interface RuleContext {
  intake: Intake;
  signals: SignalMap;
  ctx: CtxResult;
  weights: Record<AxisKey, number>;
  nullAxes: AxisKey[];
  intentAxis: AxisKey | null;
  intentMet: boolean | null;
  paletteHex: string[];
}

interface RuleSpec {
  id: string;
  name: string;
  /** Signal keys the rule reads. Empty means the rule is pure engine logic and
   * is not confidence-gated. */
  uses: string[];
  /** Worst-case headline cost. Above 1.0 the rule needs corroboration. */
  maxCost: number;
  evaluate: (c: RuleContext) => { trigger: string; effect: string; effects: Effect[]; caveat: string; corroborated?: boolean } | null;
}

function ruleModes(): Record<string, RuleMode> {
  // A rule that misbehaves in the wild degrades to a caveat without an app
  // release. Wrapped because the engine must also run in a test process that
  // was granted no environment access at all.
  try {
    const override = Deno.env.get("SCORE_RULE_MODES");
    if (!override) return DEFAULT_RULE_MODES;
    return { ...DEFAULT_RULE_MODES, ...JSON.parse(override) };
  } catch {
    return DEFAULT_RULE_MODES;
  }
}

/**
 * `fire` applies the arithmetic. `caveat` renders a line of copy with zero
 * numeric effect. `skip` is silent.
 */
function gate(spec: RuleSpec, confidence: number, corroborated: boolean, modes: Record<string, RuleMode>): "fire" | "caveat" | "skip" {
  const mode = modes[spec.id] ?? "cap";
  if (mode === "off") return "skip";
  if (spec.uses.length === 0) return mode === "caveat_only" ? "caveat" : "fire";
  if (confidence < 0.45) return "skip";
  if (mode === "caveat_only") return "caveat";
  if (confidence < 0.70) return "caveat";
  if (spec.maxCost > 1.0 && !corroborated) return "caveat";
  return "fire";
}

// ── The rules ──────────────────────────────────────────────────────────────

const SPECS: RuleSpec[] = [
  {
    id: "R-WHITE",
    name: "The white rule",
    uses: ["white_coverage"],
    maxCost: 2.5,
    evaluate: (c) => {
      if (c.intake.occasion !== "wedding" || c.intake.role === "couple") return null;
      if (sigEnum(c.signals, "white_coverage") !== "dominant") return null;
      // Corroboration: the model's call AND a near-white garment hex. One
      // signal is not enough to tell someone they wore white to a wedding.
      const corroborated = c.paletteHex.some(nearWhite);
      return {
        trigger: "wedding, not the couple, and the look reads predominantly white",
        effect: "Dress code held at 3.0, whole read held at 5.0",
        effects: [
          { kind: "axis_cap", axis: "CTX", value: 3.0 },
          { kind: "overall_cap", value: 5.0 },
        ],
        caveat: "If that is white — and I am not certain from this photograph — it is the one colour a guest cannot wear. Retake it in daylight and I will be sure.",
        corroborated,
      };
    },
  },
  {
    id: "R-BT",
    name: "Black tie, absent",
    uses: ["black_tie_markers"],
    maxCost: 2.0,
    evaluate: (c) => {
      if (c.intake.occasion !== "wedding" || c.intake.formality !== 5) return null;
      if (sigEnum(c.signals, "black_tie_markers") !== "none") return null;
      const corroborated = c.ctx.read <= 4;
      return {
        trigger: "black tie asked for, and not one black-tie marker in the frame",
        effect: "Dress code held at 4.0, whole read held at 6.5",
        effects: [
          { kind: "axis_cap", axis: "CTX", value: 4.0 },
          { kind: "overall_cap", value: 6.5 },
        ],
        caveat: "I cannot find a faced lapel, a bow tie or a floor-length hem, which is what black tie asks for.",
        corroborated,
      };
    },
  },
  {
    id: "R-BT2",
    name: "Black tie, approximated",
    uses: ["black_tie_markers", "bt_code_errors"],
    maxCost: 1.0,
    evaluate: (c) => {
      if (c.intake.occasion !== "wedding" || c.intake.formality !== 5) return null;
      const markers = sigEnum(c.signals, "black_tie_markers");
      if (markers !== "partial" && markers !== "full") return null;
      const errors = sigList(c.signals, "bt_code_errors");
      if (errors.length === 0) return null;
      return {
        trigger: `black tie attempted, with ${errors.join(", ").replace(/_/g, " ")}`,
        effect: "Dress code held at 6.5, whole read held at 7.8",
        effects: [
          { kind: "axis_cap", axis: "CTX", value: 6.5 },
          { kind: "overall_cap", value: 7.8 },
        ],
        caveat: "The shape is right; the details are a rung below the code.",
      };
    },
  },
  {
    id: "R-GROUND",
    name: "Ground truth",
    uses: ["heel_type"],
    maxCost: 1.0,
    evaluate: (c) => {
      if (c.intake.occasion !== "wedding") return null;
      if (c.intake.venue !== "garden" && c.intake.venue !== "beach") return null;
      const heel = sigEnum(c.signals, "heel_type");
      if (heel !== "stiletto" && heel !== "kitten") return null;
      return {
        trigger: `${c.intake.venue === "beach" ? "a beach" : "a garden"} and a thin heel`,
        effect: "Dress code −2.0, footwear −2.0",
        effects: [
          { kind: "axis_delta", axis: "CTX", value: -2.0 },
          { kind: "axis_delta", axis: "SHO", value: -2.0 },
        ],
        caveat: "A thin heel on grass or sand sinks. A block or a wedge reads the same and stays on top of the ground.",
      };
    },
  },
  {
    id: "R-COCKTAIL",
    name: "Cocktail, both directions",
    uses: ["hem_length", "denim_present"],
    maxCost: 1.5,
    evaluate: (c) => {
      if (c.intake.occasion !== "wedding" || c.intake.formality !== 4) return null;
      if (sigBool(c.signals, "denim_present")) {
        return {
          trigger: "cocktail asked for, denim in the frame",
          effect: "Dress code held at 3.0, whole read held at 6.0",
          effects: [
            { kind: "axis_cap", axis: "CTX", value: 3.0 },
            { kind: "overall_cap", value: 6.0 },
          ],
          caveat: "Denim is the one fabric cocktail does not stretch to.",
          corroborated: c.ctx.read <= 3,
        };
      }
      if (sigEnum(c.signals, "hem_length") === "floor") {
        return {
          trigger: "cocktail asked for, floor-length hem",
          effect: "Dress code held at 6.0",
          effects: [{ kind: "axis_cap", axis: "CTX", value: 6.0 }],
          caveat: "Floor length is a rung above cocktail — a beautiful over-dress is still an over-dress.",
        };
      }
      return null;
    },
  },
  {
    id: "R-CORP",
    name: "Corporate under-dress",
    uses: ["denim_present", "athletic_sneakers", "graphic_or_slogan", "distressing", "visible_shorts"],
    maxCost: 2.0,
    evaluate: (c) => {
      if (c.intake.occasion !== "work") return null;
      // Armed off the higher of what was claimed and what the photo reads, so
      // dialling the formality down to escape the rule does not work.
      const effective = Math.max(c.intake.formality, c.ctx.read);
      const strict = c.intake.room === "corporate" || c.intake.room === "client_facing" || effective >= 4;
      if (!strict) return null;
      const strikes = [
        sigBool(c.signals, "denim_present") ? "denim" : "",
        sigBool(c.signals, "athletic_sneakers") ? "athletic sneakers" : "",
        sigBool(c.signals, "graphic_or_slogan") ? "a slogan or graphic" : "",
        sigBool(c.signals, "distressing") ? "distressing" : "",
        sigBool(c.signals, "visible_shorts") ? "shorts" : "",
      ].filter(Boolean);
      if (strikes.length === 0) return null;
      const hard = strikes.length >= 2;
      return {
        trigger: `${c.intake.room ? c.intake.room.replace(/_/g, " ") : "a dressed"} room, and ${strikes.join(" plus ")}`,
        effect: hard ? "Dress code held at 3.0, whole read held at 6.0" : "Dress code held at 5.0, whole read held at 7.0",
        effects: hard
          ? [{ kind: "axis_cap", axis: "CTX", value: 3.0 }, { kind: "overall_cap", value: 6.0 }]
          : [{ kind: "axis_cap", axis: "CTX", value: 5.0 }, { kind: "overall_cap", value: 7.0 }],
        caveat: `In that room, ${strikes[0]} is the thing people notice before they notice you.`,
        corroborated: strikes.length >= 2 || c.ctx.read < c.intake.formality,
      };
    },
  },
  {
    id: "R-CREATIVE",
    name: "Creative over-dress",
    uses: [],
    maxCost: 1.0,
    evaluate: (c) => {
      if (c.intake.occasion !== "work" || c.intake.room !== "creative") return null;
      if (c.ctx.gap < 2) return null;
      return {
        trigger: "a creative room, dressed two rungs above it",
        effect: "Dress code held at 6.0, point of view held at 5.0",
        effects: [
          { kind: "axis_cap", axis: "CTX", value: 6.0 },
          { kind: "axis_cap", axis: "POV", value: 5.0 },
        ],
        caveat: "This is not too much effort — it is the wrong room read. In a creative studio the suit is the costume.",
      };
    },
  },
  {
    id: "R-INFORM",
    name: "Informality immunity",
    uses: [],
    maxCost: 0,
    evaluate: (c) => {
      if (c.intake.occasion !== "casual" && c.intake.occasion !== "everyday") return null;
      if (c.intake.formality > 2) return null;
      return {
        trigger: `${c.intake.occasion}, dialled low`,
        effect: "Dress code floored at 7.0",
        effects: [{ kind: "axis_floor", axis: "CTX", value: 7.0 }],
        caveat: "Not being dressy is not a fault here.",
      };
    },
  },
  {
    id: "R-FEET",
    name: "Standing all day",
    uses: ["heel_height", "heel_type"],
    maxCost: 0.9,
    evaluate: (c) => {
      if (c.intake.occasion !== "everyday" || c.intake.on_feet !== "most_of_day") return null;
      const height = sigEnum(c.signals, "heel_height");
      const type = sigEnum(c.signals, "heel_type");
      const punishing = height === "high" || type === "stiletto" || type === "backless_mule" || type === "stiff_dress_shoe";
      if (!punishing) return null;
      return {
        trigger: "on your feet most of the day, in a shoe that does not want to be",
        effect: "Footwear held at 4.0, whole read −0.4",
        effects: [
          { kind: "axis_cap", axis: "SHO", value: 4.0 },
          { kind: "overall_delta", value: -0.4 },
        ],
        caveat: "You told me you are walking all day. By four o'clock this shoe will have opinions.",
      };
    },
  },
  {
    id: "R-COLD",
    name: "Winter exposure",
    uses: ["outer_layer_present", "bare_limbs"],
    maxCost: 1.0,
    evaluate: (c) => {
      if (c.intake.weather?.band !== "cold") return null;
      if (sigBool(c.signals, "outer_layer_present")) return null;
      if (!sigBool(c.signals, "bare_limbs")) return null;
      return {
        trigger: "cold outside, no outer layer, bare construction",
        effect: "Fabric −2.0, dress code −1.5, whole read held at 8.0",
        effects: [
          { kind: "axis_delta", axis: "TEX", value: -2.0 },
          { kind: "axis_delta", axis: "CTX", value: -1.5 },
          { kind: "overall_cap", value: 8.0 },
        ],
        caveat: "Dressing for a room you have to walk to is half a plan.",
      };
    },
  },
  {
    id: "R-HOT",
    name: "Summer weight",
    uses: ["heavy_construction"],
    maxCost: 0.6,
    evaluate: (c) => {
      if (c.intake.weather?.band !== "hot") return null;
      if (!sigBool(c.signals, "heavy_construction")) return null;
      return {
        trigger: "hot outside, heavy cloth or stacked layers",
        effect: "Fabric −1.5, dress code −1.0",
        effects: [
          { kind: "axis_delta", axis: "TEX", value: -1.5 },
          { kind: "axis_delta", axis: "CTX", value: -1.0 },
        ],
        caveat: "This is a beautiful cloth for a different month.",
      };
    },
  },
  {
    id: "R-SHINE",
    name: "Daylight fabric",
    uses: ["high_shine"],
    maxCost: 0.5,
    evaluate: (c) => {
      if (sigEnum(c.signals, "high_shine") !== "dominant") return null;
      if (c.intake.time_of_day === "daytime") {
        return {
          trigger: "heavy shine, in daylight",
          effect: "Fabric −1.0, dress code −1.0",
          effects: [
            { kind: "axis_delta", axis: "TEX", value: -1.0 },
            { kind: "axis_delta", axis: "CTX", value: -1.0 },
          ],
          caveat: "Sequins and liquid satin are evening fabrics. In daylight they read as last night.",
        };
      }
      return {
        trigger: "heavy shine, in the evening",
        effect: "Fabric +0.5",
        effects: [{ kind: "axis_delta", axis: "TEX", value: 0.5 }],
        caveat: "The shine is doing exactly what evening shine is for.",
      };
    },
  },
  {
    id: "R-FITGATE",
    name: "Fit fault gate",
    uses: ["fit_faults"],
    maxCost: 1.5,
    evaluate: (c) => {
      const faults = sigList(c.signals, "fit_faults");
      if (faults.length === 0) return null;
      const hard = faults.length >= 2;
      const pretty = faults.map((f) => f.replace(/_/g, " ")).join(", ");
      return {
        trigger: hard ? `${faults.length} fit faults: ${pretty}` : `a fit fault: ${pretty}`,
        effect: hard ? "Fit held at 3.0, whole read held at 7.0" : "Fit held at 5.0",
        effects: hard
          ? [{ kind: "axis_cap", axis: "FIT", value: 3.0 }, { kind: "overall_cap", value: 7.0 }]
          : [{ kind: "axis_cap", axis: "FIT", value: 5.0 }],
        caveat: `The garment is fighting the body at ${pretty}.`,
        corroborated: hard,
      };
    },
  },
  {
    id: "R-UPKEEP",
    name: "Upkeep gate",
    uses: ["upkeep_faults"],
    maxCost: 1.2,
    evaluate: (c) => {
      const faults = sigList(c.signals, "upkeep_faults");
      if (faults.length < 3) return null;
      return {
        trigger: `${faults.length} upkeep faults: ${faults.map((f) => f.replace(/_/g, " ")).join(", ")}`,
        effect: "Upkeep held at 3.0, whole read held at 7.5",
        effects: [
          { kind: "axis_cap", axis: "PRS", value: 3.0 },
          { kind: "overall_cap", value: 7.5 },
        ],
        caveat: "Nothing here needs money. It needs ten minutes and an iron.",
        corroborated: faults.length >= 3,
      };
    },
  },
  {
    id: "R-PROP",
    name: "Proportion gate",
    uses: ["volume_balance", "break_point_present"],
    maxCost: 0.8,
    evaluate: (c) => {
      const balance = sigEnum(c.signals, "volume_balance");
      if (balance !== "both_loose" && balance !== "both_tight") return null;
      if (sigBool(c.signals, "break_point_present")) return null;
      return {
        trigger: `${balance.replace(/_/g, " ")}, with no break point`,
        effect: "Silhouette held at 5.0",
        effects: [{ kind: "axis_cap", axis: "SIL", value: 5.0 }],
        caveat: "Top and bottom are doing the same thing, so the eye has nowhere to land.",
      };
    },
  },
  {
    id: "R-EVIDENCE",
    name: "Evidence ceiling",
    uses: [],
    maxCost: 0.3,
    evaluate: (c) => {
      if (c.intake.has_back) return null;
      return {
        trigger: "front view only",
        effect: "Fit ceilinged at 8.5 and weighted a fifth lighter",
        effects: [{ kind: "axis_cap", axis: "FIT", value: 8.5 }],
        caveat: "Without a back view I cannot check the seat, the rear hem or how it sits across the shoulder blades.",
      };
    },
  },
  {
    id: "R-FRAME",
    name: "Out of frame",
    uses: [],
    maxCost: 0.5,
    evaluate: (c) => {
      if (c.nullAxes.length === 0) return null;
      // Redistributing a missing axis's weight must never be profitable, or
      // cropping your shoes becomes a scoring strategy.
      const penalty = -Math.min(0.5, 0.25 * c.nullAxes.length);
      return {
        trigger: `${c.nullAxes.length} axis${c.nullAxes.length > 1 ? "es" : ""} not visible in the frame`,
        effect: `Whole read ${penalty.toFixed(2)} — an incomplete read is not a better one`,
        effects: [{ kind: "overall_delta", value: penalty }],
        caveat: "Part of the look is outside the frame, so this read is less complete than it could be.",
      };
    },
  },
  {
    id: "R-INTENT",
    name: "The ask",
    uses: [],
    maxCost: 1.0,
    evaluate: (c) => {
      if (!c.intake.intent || !c.intentAxis || c.intentMet !== false) return null;
      return {
        trigger: `you asked for "${c.intake.intent}", and this does not do it`,
        effect: `${c.intentAxis} −1.0, floored at 4.0`,
        effects: [
          { kind: "axis_delta", axis: c.intentAxis, value: -1.0 },
          { kind: "axis_floor", axis: c.intentAxis, value: 4.0 },
        ],
        caveat: "Nothing here is wrong. It is just not the thing you asked me to grade.",
      };
    },
  },
];

export function evaluateRules(c: RuleContext): { fired: FiredRule[]; caveated: FiredRule[] } {
  const modes = ruleModes();
  const fired: FiredRule[] = [];
  const caveated: FiredRule[] = [];
  for (const spec of SPECS) {
    const out = spec.evaluate(c);
    if (!out) continue;
    const confidence = spec.uses.length === 0 ? 1 : conf(c.signals, spec.uses);
    const decision = gate(spec, confidence, out.corroborated ?? false, modes);
    if (decision === "skip") continue;
    const rule: FiredRule = {
      id: spec.id,
      name: spec.name,
      trigger: out.trigger,
      effect: decision === "fire" ? out.effect : "noted, but not counted",
      effects: decision === "fire" ? out.effects : [],
      caveat: out.caveat,
      mode: modes[spec.id] ?? "cap",
      confidence,
    };
    (decision === "fire" ? fired : caveated).push(rule);
  }
  return { fired, caveated };
}
