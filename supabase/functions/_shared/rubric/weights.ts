// Scoring v4 — the weight algorithm.
//
// Pure. No I/O, no Deno APIs, no dates, no randomness: the same intake always
// produces the same weight vector, on the server and in the client's preview
// strip. This is the file the golden fixtures pin.

import {
  ALPHA,
  BASE_WEIGHTS,
  BETA,
  INTENT_WEIGHT_BONUS,
  M_MAX,
  M_MIN,
  NO_BACK_FIT_MULTIPLIER,
  ON_FEET_DELTAS,
  PRECIP_DELTA,
  ROLE_DELTAS,
  ROOM_DELTAS,
  TIME_DELTAS,
  VENUE_DELTAS,
  W_MAX,
  W_MIN,
  WEATHER_DELTAS,
} from "./rubric.v4.ts";
import { AXIS_KEYS, type AxisKey, type BuiltWeights, type Intake, type WeightDelta } from "./types.ts";

type Delta = Partial<Record<AxisKey, number>>;

interface Source {
  key: string;
  reason: string;
  kind: WeightDelta["kind"];
  deltas: Delta;
}

export interface WeightOptions {
  /** Axis the wearer's stated intent maps to. */
  intentAxis?: AxisKey | null;
}

function zeroed(): Record<AxisKey, number> {
  return Object.fromEntries(AXIS_KEYS.map((k) => [k, 0])) as Record<AxisKey, number>;
}

function clamp(n: number, lo: number, hi: number): number {
  return Math.max(lo, Math.min(hi, n));
}

/** Every band-4 and cross-cutting source that applies to this intake, in the
 * order they are disclosed to the user. */
function sourcesFor(intake: Intake): Source[] {
  const out: Source[] = [];
  if (intake.occasion === "wedding" && intake.role) {
    out.push({ key: `role:${intake.role}`, reason: `Role: ${intake.role.replace(/_/g, " ")}`, kind: "band4", deltas: ROLE_DELTAS[intake.role] });
  }
  if (intake.occasion === "wedding" && intake.venue) {
    out.push({ key: `venue:${intake.venue}`, reason: `Venue: ${intake.venue.replace(/_/g, " ")}`, kind: "band4", deltas: VENUE_DELTAS[intake.venue] });
  }
  if (intake.occasion === "work" && intake.room) {
    out.push({ key: `room:${intake.room}`, reason: `Room: ${intake.room.replace(/_/g, " ")}`, kind: "band4", deltas: ROOM_DELTAS[intake.room] });
  }
  if (intake.occasion === "everyday" && intake.on_feet) {
    out.push({ key: `on_feet:${intake.on_feet}`, reason: `On your feet: ${intake.on_feet.replace(/_/g, " ")}`, kind: "band4", deltas: ON_FEET_DELTAS[intake.on_feet] });
  }
  if (intake.weather) {
    out.push({ key: `weather:${intake.weather.band}`, reason: `Weather: ${intake.weather.band}`, kind: "cross", deltas: WEATHER_DELTAS[intake.weather.band] });
    if (intake.weather.precip) {
      out.push({ key: "weather:rain", reason: "Rain", kind: "cross", deltas: PRECIP_DELTA });
    }
  }
  out.push({ key: `tod:${intake.time_of_day}`, reason: `Graded as ${intake.time_of_day}`, kind: "cross", deltas: TIME_DELTAS[intake.time_of_day] });
  return out;
}

interface PassOptions {
  skipDial?: boolean;
  skipSource?: string;
  skipIntent?: boolean;
  skipNoBack?: boolean;
  skipNull?: boolean;
}

/** One full pass of the weight pipeline. Deterministic. */
function pass(
  intake: Intake,
  nullAxes: AxisKey[],
  opts: WeightOptions,
  pOpts: PassOptions,
): Record<AxisKey, number> {
  const base = BASE_WEIGHTS[intake.occasion];
  const w = zeroed();

  // 1 — dial modulation.
  for (const a of AXIS_KEYS) {
    let m = 1;
    if (!pOpts.skipDial) {
      m = (1 + ALPHA[a] * (intake.formality - 3) / 2) * (1 + BETA[a] * (intake.presence - 3) / 2);
      m = clamp(m, M_MIN, M_MAX);
    }
    w[a] = base[a] * m;
  }

  // 2 — band-4 and cross-cutting deltas.
  for (const s of sourcesFor(intake)) {
    if (pOpts.skipSource === s.key) continue;
    for (const [axis, d] of Object.entries(s.deltas)) {
      w[axis as AxisKey] += d as number;
    }
  }

  // 3 — intent buys its axis weight from the two lightest OTHER axes, so an
  //     intent that lands on an already-light axis cannot donate to itself.
  const intentAxis = opts.intentAxis ?? null;
  if (intentAxis && !pOpts.skipIntent && !nullAxes.includes(intentAxis)) {
    const donors = AXIS_KEYS
      .filter((a) => a !== intentAxis && !nullAxes.includes(a))
      .sort((a, b) => w[a] - w[b])
      .slice(0, 2);
    const donorTotal = donors.reduce((s, a) => s + w[a], 0);
    if (donorTotal > 0) {
      w[intentAxis] += INTENT_WEIGHT_BONUS;
      for (const d of donors) {
        w[d] -= INTENT_WEIGHT_BONUS * (w[d] / donorTotal);
      }
    }
  }

  // 4 — a fit read without a back view is a partial one.
  if (!intake.has_back && !pOpts.skipNoBack) w.FIT *= NO_BACK_FIT_MULTIPLIER;

  // 5 — clamp AFTER modulation, not before. Clamping first collapsed a wedding
  //     at cocktail and a wedding at black tie onto the same vector.
  for (const a of AXIS_KEYS) w[a] = clamp(w[a], W_MIN, W_MAX);

  // 6 — an axis the photo cannot support is dropped, never guessed at 5.
  if (!pOpts.skipNull) for (const a of nullAxes) w[a] = 0;

  // 7 — normalise over what is left.
  const total = AXIS_KEYS.reduce((s, a) => s + w[a], 0);
  if (total > 0) for (const a of AXIS_KEYS) w[a] = w[a] / total;

  // 8 — the dress-code floor, applied AFTER normalisation so the guarantee is
  //     the one the UI states: CTX never falls below half its occasion share,
  //     which is what stops presence=5 silently cancelling formality=5.
  if (!(pOpts.skipNull ? false : nullAxes.includes("CTX"))) {
    const floor = 0.5 * base.CTX;
    if (w.CTX < floor) {
      const rest = AXIS_KEYS.filter((a) => a !== "CTX");
      const restTotal = rest.reduce((s, a) => s + w[a], 0);
      if (restTotal > 0) {
        const scale = (1 - floor) / restTotal;
        for (const a of rest) w[a] *= scale;
        w.CTX = floor;
      }
    }
  }

  return w;
}

function round3(n: number): number {
  return Math.round(n * 1000) / 1000;
}

/**
 * Build the final weight vector plus a leave-one-out attribution of what each
 * intake answer actually did to it. The attribution is computed by re-running
 * the pipeline with that source removed and diffing the NORMALISED vectors, so
 * every number shown to the user is the real post-normalisation effect rather
 * than a raw delta that the normaliser later ate.
 */
export function buildWeights(
  intake: Intake,
  nullAxes: AxisKey[] = [],
  opts: WeightOptions = {},
): BuiltWeights {
  const full = pass(intake, nullAxes, opts, {});
  const deltas: WeightDelta[] = [];

  const attribute = (pOpts: PassOptions, reason: string, kind: WeightDelta["kind"], limit = 2) => {
    const without = pass(intake, nullAxes, opts, pOpts);
    const moved = AXIS_KEYS
      .map((a) => ({ axis: a, delta: full[a] - without[a] }))
      .filter((d) => Math.abs(d.delta) >= 0.005)
      .sort((a, b) => Math.abs(b.delta) - Math.abs(a.delta))
      .slice(0, limit);
    for (const m of moved) {
      deltas.push({ reason, axis: m.axis, delta: round3(m.delta), kind });
    }
  };

  attribute({ skipDial: true }, `Dialled to ${intake.formality} of 5, presence ${intake.presence} of 5`, "dial", 3);
  for (const s of sourcesFor(intake)) {
    attribute({ skipSource: s.key }, s.reason, s.kind);
  }
  if (opts.intentAxis) attribute({ skipIntent: true }, "What you're going for", "intent", 1);
  if (!intake.has_back) attribute({ skipNoBack: true }, "No back view", "evidence", 1);
  if (nullAxes.length > 0) attribute({ skipNull: true }, "Not visible in the photo", "null_redistribution", 3);

  const w = Object.fromEntries(AXIS_KEYS.map((a) => [a, round3(full[a])])) as Record<AxisKey, number>;

  // Rounding to 3dp can shift the sum off 1.000 by a thousandth. Push the
  // residual onto the heaviest axis so Σw == 1.000 exactly, always.
  const order = [...AXIS_KEYS].filter((a) => w[a] > 0).sort((a, b) => w[b] - w[a]);
  if (order.length > 0) {
    const sum = order.reduce((s, a) => s + w[a], 0);
    w[order[0]] = round3(w[order[0]] + (1 - sum));
  }

  return { w, deltas, nullAxes, order };
}

/** The four axes this intake grades hardest on — the client's preview strip. */
export function primaryAxes(intake: Intake, count = 4): AxisKey[] {
  const { w } = buildWeights(intake, []);
  return [...AXIS_KEYS].sort((a, b) => w[b] - w[a]).slice(0, count);
}
