// Scoring v4 — the engine.
//
// Order of operations, and it is load-bearing:
//   A  validate the model's axes; drop anything it could not see, and anything
//      it graded on formality (formality is measured separately)
//   B  build weights from the intake, drop nulls, renormalise
//   C  compute CTX from the formality gap — the server's own axis
//   D  apply axis deltas   (presence gap, then the delta rules)
//   E  apply axis caps and floors
//   F  raw = Σ w·score
//   G  overall deltas, then every armed overall cap
//   H  anti-inflation, then the floor, then round
//
// Every rule's cost is an ordered waterfall in fire order, so
// `raw_weighted + Σ costs == final` is an identity rather than an aspiration.

import { AXIS_LABELS, INTENT_CHIPS } from "../_shared/rubric/rubric.v4.ts";
import { buildWeights } from "../_shared/rubric/weights.ts";
import { AXIS_KEYS, type AxisKey, type Caveat, type Intake } from "../_shared/rubric/types.ts";
import { computeCtx, type CtxResult } from "./ctx.ts";
import { type Effect, evaluateRules, type FiredRule, type SignalMap } from "./rules.ts";

const MODEL_AXES: AxisKey[] = ["SIL", "FIT", "COL", "TEX", "DTL", "SHO", "PRS", "POV"];

/** Formality words inside an axis evidence string mean the model graded the
 * same thing twice. The axis is discarded rather than double-counted. */
const FORMALITY_LEAK = /\btoo casual\b|\bnot dressy\b|\btoo informal\b|\bnot formal enough\b|\bunderdressed\b/i;

function clamp(n: number, lo: number, hi: number): number {
  return Math.max(lo, Math.min(hi, n));
}

function round1(n: number): number {
  return Math.round(n * 10) / 10;
}

function round2(n: number): number {
  return Math.round(n * 100) / 100;
}

export interface AxisOut {
  key: AxisKey;
  label: string;
  weight: number;
  raw_score: number | null;
  score: number | null;
  contribution: number | null;
  evidence: string;
  source: "model" | "engine";
  rank: number;
  capped_by?: string;
  ceiling?: number;
  ceiling_rule?: string;
  floored_by?: string;
  unjudgeable?: boolean;
  is_intent_axis?: boolean;
}

export interface EngineResult {
  headline: number;
  weighted_mean: number;
  band: string;
  reason: string;
  axes: AxisOut[];
  weights: Record<AxisKey, number>;
  ctx: CtxResult;
  presence: { read: number; claimed: number; gap: number; line: string };
  rules_fired: Array<{
    id: string; name: string; trigger: string; effect: string;
    axis: AxisKey | null; before: number | null; after: number | null;
    headline_cost: number; mode: string; confidence: number;
  }>;
  downgraded_to_caveat: Array<{ id: string; name: string; copy: string; confidence: number }>;
  caps_applied: Array<{ id: string; scope: string; to: number; binding: boolean }>;
  weight_deltas: ReturnType<typeof buildWeights>["deltas"];
  null_axes: AxisKey[];
  discarded_axes: AxisKey[];
  raw_weighted: number;
  why_this_number: string;
  lever: {
    axis: AxisKey; label: string; from: number; to: number;
    projected: number; delta: number; copy: string;
  } | null;
  caveats: Caveat[];
  brief_verdict: { state: "on_brief" | "nearly" | "off_brief"; copy: string };
  subscores: Record<string, number>;
  intent_axis: AxisKey | null;
}

interface JudgeAxis {
  score: number | null;
  evidence: string;
}

export interface JudgeInput {
  axes: Partial<Record<AxisKey, JudgeAxis>>;
  formality_read: number;
  formality_evidence?: string;
  presence_read: number;
  presence_evidence?: string;
  interp: number;
  signals: SignalMap;
  intent_axis?: string | null;
  intent_met?: boolean | null;
  intent_note?: string | null;
  caveats?: string[];
  palette_hex?: string[];
}

/** Map a free-text intent onto an axis. The model's own mapping wins; the chip
 * list is the fallback so a tapped chip never depends on the model. */
function resolveIntentAxis(intake: Intake, judge: JudgeInput): AxisKey | null {
  if (!intake.intent) return null;
  const chip = INTENT_CHIPS.find((c) => c.label.toLowerCase() === intake.intent!.toLowerCase());
  if (chip) return chip.axis;
  const claimed = String(judge.intent_axis ?? "").toUpperCase() as AxisKey;
  return AXIS_KEYS.includes(claimed) ? claimed : null;
}

export function runEngine(intake: Intake, judge: JudgeInput): EngineResult {
  // ── A. Validate what the model returned ──────────────────────────────────
  const rawScores: Partial<Record<AxisKey, number>> = {};
  const evidence: Partial<Record<AxisKey, string>> = {};
  const nullAxes: AxisKey[] = [];
  const discardedAxes: AxisKey[] = [];

  for (const a of MODEL_AXES) {
    const entry = judge.axes[a];
    const ev = String(entry?.evidence ?? "").slice(0, 400);
    evidence[a] = ev;
    const s = Number(entry?.score);
    if (entry == null || entry.score == null || !isFinite(s)) {
      nullAxes.push(a);
      continue;
    }
    if (FORMALITY_LEAK.test(ev)) {
      discardedAxes.push(a);
      evidence[a] = `${ev} — discarded: this axis was graded on formality, which is measured separately.`;
      continue;
    }
    rawScores[a] = clamp(s, 1, 10);
  }

  const droppedAxes = [...nullAxes, ...discardedAxes];

  // ── C (early). The dress-code axis, computed not judged. ─────────────────
  const ctx = computeCtx(judge.formality_read, intake.formality, intake.occasion, judge.interp);
  rawScores.CTX = ctx.score;
  evidence.CTX = String(judge.formality_evidence ?? "").slice(0, 400) || ctx.line;

  // ── Presence gap. Deltas, never caps, and always framed as a mismatch with
  //    the wearer's own goal rather than as bad taste. ──────────────────────
  const presenceRead = clamp(Math.round(judge.presence_read), 1, 5);
  const q = presenceRead - intake.presence;
  const qAbs = Math.min(Math.abs(q), 2);
  const presenceDeltas: Effect[] = [];
  // A presence mismatch charges the SCORE of the axes involved while the dial
  // has already raised their WEIGHT — the same answer landing twice. The right
  // bound for that is smaller coefficients and a clamp on |q|, NOT cancelling
  // the weight boost: dialling "be looked at" has to make point of view the
  // heaviest axis, which is the entire reason the dial exists.
  if (q === 0) {
    // Matching the dial has upside, or the middle setting is strictly dominant.
    presenceDeltas.push({ kind: "axis_delta", axis: "POV", value: 0.3 });
  } else if (q > 0) {
    presenceDeltas.push({ kind: "axis_delta", axis: "COL", value: -0.40 * qAbs });
    presenceDeltas.push({ kind: "axis_delta", axis: "DTL", value: -0.30 * qAbs });
  } else {
    presenceDeltas.push({ kind: "axis_delta", axis: "POV", value: -0.45 * qAbs });
    presenceDeltas.push({ kind: "axis_delta", axis: "COL", value: -0.25 * qAbs });
  }
  if (Math.abs(q) >= 2) {
    presenceDeltas.push({ kind: "overall_cap", value: 10 - 0.5 * (Math.min(Math.abs(q), 3) - 1) });
  }

  const intentAxis = resolveIntentAxis(intake, judge);

  // ── B. Weights. ──────────────────────────────────────────────────────────
  const built = buildWeights(intake, droppedAxes, { intentAxis });
  const w = built.w;

  // ── The waterfall. ───────────────────────────────────────────────────────
  const { fired, caveated } = evaluateRules({
    intake,
    signals: judge.signals,
    ctx,
    weights: w,
    nullAxes,
    intentAxis,
    intentMet: judge.intent_met ?? null,
    paletteHex: judge.palette_hex ?? [],
  });

  const presenceRule: FiredRule = {
    id: "R-PRESENCE",
    name: q === 0 ? "Pitched where you asked" : q > 0 ? "Louder than you asked" : "Quieter than you asked",
    trigger: `you asked for ${intake.presence} of 5, the photograph reads ${presenceRead}`,
    effect: q === 0 ? "Point of view +0.3" : q > 0 ? "Colour and detail pulled back" : "Point of view and colour pulled back",
    effects: presenceDeltas,
    caveat: "",
    mode: "cap",
    confidence: 1,
  };
  const waterfall: FiredRule[] = [presenceRule, ...fired];

  interface Applied {
    scores: Partial<Record<AxisKey, number>>;
    caps: Partial<Record<AxisKey, { value: number; by: string }>>;
    floors: Partial<Record<AxisKey, { value: number; by: string }>>;
    overallCaps: Array<{ id: string; value: number }>;
    overallDelta: number;
    raw: number;
    headline: number;
  }

  /** Replay steps D→G over the first `upTo` rules, optionally overriding one
   * axis's raw score and suppressing the rules that cap it. Used both for the
   * waterfall costs and for the counterfactual behind "the one thing". */
  const apply = (
    upTo: number,
    override?: { axis: AxisKey; to: number },
    suppress?: Set<string>,
  ): Applied => {
    const scores: Partial<Record<AxisKey, number>> = { ...rawScores };
    if (override && scores[override.axis] != null) scores[override.axis] = override.to;
    const caps: Applied["caps"] = {};
    const floors: Applied["floors"] = {};
    const overallCaps: Applied["overallCaps"] = [];
    let overallDelta = 0;

    const active = waterfall.slice(0, upTo).filter((r) => !suppress?.has(r.id));

    // D — deltas.
    for (const r of active) {
      for (const e of r.effects) {
        if (e.kind === "axis_delta" && scores[e.axis] != null) {
          scores[e.axis] = clamp(scores[e.axis]! + e.value, 1, 10);
        }
      }
    }
    // E — caps and floors. The tightest cap and the highest floor win.
    for (const r of active) {
      for (const e of r.effects) {
        if (e.kind === "axis_cap") {
          const cur = caps[e.axis];
          if (!cur || e.value < cur.value) caps[e.axis] = { value: e.value, by: r.id };
        } else if (e.kind === "axis_floor") {
          const cur = floors[e.axis];
          if (!cur || e.value > cur.value) floors[e.axis] = { value: e.value, by: r.id };
        } else if (e.kind === "overall_delta") {
          overallDelta += e.value;
        } else if (e.kind === "overall_cap") {
          overallCaps.push({ id: r.id, value: e.value });
        }
      }
    }
    for (const a of AXIS_KEYS) {
      if (scores[a] == null) continue;
      const cap = caps[a];
      if (cap) scores[a] = Math.min(scores[a]!, cap.value);
      const floor = floors[a];
      if (floor) scores[a] = Math.max(scores[a]!, floor.value);
    }

    // F — the weighted mean.
    let raw = 0;
    for (const a of AXIS_KEYS) {
      if (scores[a] == null || w[a] === 0) continue;
      raw += w[a] * scores[a]!;
    }

    // G — overall deltas, then every armed cap including the gap ceiling.
    let headline = raw + overallDelta;
    const allCaps = [...overallCaps, { id: "gap", value: ctx.overallCap }];
    for (const c of allCaps) headline = Math.min(headline, c.value);

    return { scores, caps, floors, overallCaps: allCaps, overallDelta, raw, headline };
  };

  const base = apply(0);
  const rawWeighted = base.raw;

  const rulesFired: EngineResult["rules_fired"] = [];
  let running = base.headline;
  for (let i = 0; i < waterfall.length; i++) {
    const r = waterfall[i];
    const after = apply(i + 1);
    const cost = after.headline - running;
    running = after.headline;
    const axis = r.effects.find((e) => e.kind !== "overall_cap" && e.kind !== "overall_delta");
    const axisKey = axis && "axis" in axis ? axis.axis : null;
    const before = i === 0 ? rawScores[axisKey ?? "CTX"] ?? null : apply(i).scores[axisKey ?? "CTX"] ?? null;
    rulesFired.push({
      id: r.id,
      name: r.name,
      trigger: r.trigger,
      effect: r.effect,
      axis: axisKey,
      before: before == null ? null : round1(before),
      after: axisKey == null ? null : round1(after.scores[axisKey] ?? 0),
      headline_cost: round2(cost),
      mode: r.mode,
      confidence: round2(r.confidence),
    });
  }

  const full = apply(waterfall.length);
  let headline = full.headline;

  // ── H. Anti-inflation, then the floor. ───────────────────────────────────
  const scored = AXIS_KEYS.filter((a) => full.scores[a] != null && w[a] > 0);
  const values = scored.map((a) => full.scores[a]!);
  const maxAxis = values.length ? Math.max(...values) : 0;

  // Only an axis that actually carries weight can veto the whole read. A 4%
  // colour axis capping a wedding at 6.0 was never defensible.
  const heavyFailing = scored.filter((a) => full.scores[a]! <= 3 && w[a] >= 0.10);
  if (heavyFailing.length > 0) {
    const cap = Math.min(maxAxis - 0.5, 8.0);
    if (headline > cap) {
      rulesFired.push({
        id: "R-ANTI", name: "Anti-inflation",
        trigger: `${AXIS_LABELS[heavyFailing[0]].toLowerCase()} is at ${round1(full.scores[heavyFailing[0]]!)} and carries ${Math.round(w[heavyFailing[0]] * 100)}%`,
        effect: `Whole read held at ${round1(cap)}`,
        axis: heavyFailing[0], before: round1(headline), after: round1(cap),
        headline_cost: round2(cap - headline), mode: "cap", confidence: 1,
      });
      headline = cap;
    }
  }
  const nineOrBetter = values.filter((v) => v >= 9).length;
  const anyBelowSeven = values.some((v) => v < 7);
  if (headline > 9.9 && (nineOrBetter < 3 || anyBelowSeven)) {
    const cap = 9.9;
    rulesFired.push({
      id: "R-ANTI", name: "Anti-inflation",
      trigger: "a ten needs three axes at nine or better and nothing below seven",
      effect: "Whole read held at 9.9",
      axis: null, before: round1(headline), after: cap,
      headline_cost: round2(cap - headline), mode: "cap", confidence: 1,
    });
    headline = cap;
  }

  // The structural defence against an honest everyday outfit being talked into
  // the fives by a misunderstood dial.
  const hardFault =
    (full.caps.FIT?.by === "R-FITGATE" && (full.scores.FIT ?? 10) <= 3) ||
    full.caps.PRS?.by === "R-UPKEEP";
  const informal = intake.occasion === "casual" || intake.occasion === "everyday";
  if (informal && !hardFault && headline < 6.0) {
    rulesFired.push({
      id: "R-FLOOR", name: "Floor",
      trigger: `${intake.occasion} with no fit or upkeep fault`,
      effect: "Whole read floored at 6.0",
      axis: null, before: round1(headline), after: 6.0,
      headline_cost: round2(6.0 - headline), mode: "cap", confidence: 1,
    });
    headline = 6.0;
  }
  if (headline < 2.0) {
    rulesFired.push({
      id: "R-FLOOR", name: "Floor", trigger: "absolute floor",
      effect: "Whole read floored at 2.0",
      axis: null, before: round1(headline), after: 2.0,
      headline_cost: round2(2.0 - headline), mode: "cap", confidence: 1,
    });
    headline = 2.0;
  }
  headline = round1(clamp(headline, 1, 10));

  // ── Axis rows for the breakdown. ─────────────────────────────────────────
  const ordered = [...AXIS_KEYS].sort((a, b) => w[b] - w[a]);
  const axes: AxisOut[] = ordered.map((key, i) => {
    const unjudgeable = droppedAxes.includes(key);
    const score = unjudgeable ? null : full.scores[key] ?? null;
    const cap = full.caps[key];
    const floor = full.floors[key];
    const row: AxisOut = {
      key,
      label: AXIS_LABELS[key],
      weight: w[key],
      raw_score: unjudgeable ? null : round1(rawScores[key] ?? 0),
      score: score == null ? null : round1(score),
      contribution: score == null ? null : round2(w[key] * score),
      evidence: evidence[key] ?? "",
      source: key === "CTX" ? "engine" : "model",
      rank: i + 1,
    };
    if (unjudgeable) row.unjudgeable = true;
    if (cap && score != null && round1(score) <= cap.value + 0.001) {
      if (cap.by === "R-EVIDENCE") {
        row.ceiling = cap.value;
        row.ceiling_rule = cap.by;
      } else if ((rawScores[key] ?? 0) > cap.value) {
        row.capped_by = cap.by;
      }
    }
    if (floor && (rawScores[key] ?? 10) < floor.value) row.floored_by = floor.by;
    if (key === intentAxis) row.is_intent_axis = true;
    return row;
  });

  // ── "The one thing". ─────────────────────────────────────────────────────
  let lever: EngineResult["lever"] = null;
  for (const a of AXIS_KEYS) {
    if (full.scores[a] == null || w[a] === 0) continue;
    const from = full.scores[a]!;
    if (from >= 8.8) continue;
    // Fixing an axis means the rules that cap it stop applying — otherwise the
    // lever would tell a black-tie miss that a better shoe rescues them.
    const suppress = new Set(
      waterfall.filter((r) => r.effects.some((e) => "axis" in e && e.axis === a)).map((r) => r.id),
    );
    const projectedRaw = apply(waterfall.length, { axis: a, to: 9.0 }, suppress).headline;
    const projected = round1(clamp(projectedRaw, 1, 10));
    const delta = round1(projected - headline);
    if (delta > (lever?.delta ?? 0.05)) {
      lever = {
        axis: a, label: AXIS_LABELS[a], from: round1(from), to: 9.0,
        projected, delta,
        copy: `As things stand, that would put you at ${projected.toFixed(1)}.`,
      };
    }
  }

  // ── Copy the server owns. ────────────────────────────────────────────────
  // Derived from the FINAL dress-code score, not the raw gap, so the floors and
  // caps that already forgave the gap are not contradicted by the stamp above
  // the number. Without this, an honest 7.7 everyday look got branded OFF BRIEF
  // for the crime of being a suit on a Tuesday, after the informality rule had
  // explicitly decided not to charge it.
  const finalCtx = full.scores.CTX ?? ctx.score;
  const briefState: EngineResult["brief_verdict"]["state"] =
    finalCtx >= 8.0 && Math.abs(q) <= 1 ? "on_brief" : finalCtx >= 5.5 ? "nearly" : "off_brief";
  const briefCopy = briefState === "on_brief"
    ? "You asked for a thing and this is that thing."
    : briefState === "nearly"
    ? "Close to the brief you set, and one decision away from it."
    : ctx.direction === "over"
    ? "You are dressed correctly for a more formal event than the one you named."
    : "You are dressed correctly for a different event.";

  const heaviest = axes.find((r) => r.score != null) ?? axes[0];
  const bindingCap = full.overallCaps
    .filter((c) => c.value < full.raw + full.overallDelta - 0.001)
    .sort((a, b) => a.value - b.value)[0];
  const whyParts = [
    `${scored.length} axes landed between ${round1(Math.min(...values))} and ${round1(Math.max(...values))}, which weighs out at ${rawWeighted.toFixed(2)}.`,
    `${heaviest.label} carries ${Math.round(heaviest.weight * 100)}% of this brief.`,
  ];
  if (bindingCap) {
    const named = rulesFired.find((r) => r.id === bindingCap.id);
    whyParts.push(bindingCap.id === "gap"
      ? `The distance between what you asked for and what the photograph reads holds the whole read at ${round1(bindingCap.value)}.`
      : `${named?.name ?? bindingCap.id} holds the whole read at ${round1(bindingCap.value)}.`);
  }

  const bands: Array<[number, string]> = [
    [9.0, "editorial"], [8.0, "considered"], [7.0, "solid"],
    [6.0, "works, forgettable"], [5.0, "a fixable miss"], [0, "wrong pieces"],
  ];
  const band = briefState === "off_brief" ? "misses the code" : bands.find(([t]) => headline >= t)![1];

  // ── Caveats. ─────────────────────────────────────────────────────────────
  const caveats: Caveat[] = [];
  if (!intake.has_back) {
    caveats.push({
      kind: "no_back_view",
      copy: "No back view — I can't see the seat, the shoulder-blade drape or the rear hem.",
      cost: "Fit held at 8.5 and weighted a fifth lighter.",
    });
  }
  for (const a of nullAxes) {
    caveats.push({
      kind: "null_axis",
      axis: a,
      copy: `I couldn't grade ${AXIS_LABELS[a].toLowerCase()} from this photograph, so this read is less complete than it could be.`,
      cost: `Its share went to the other axes, and the whole read took a small deduction for the missing evidence.`,
    });
  }
  for (const a of discardedAxes) {
    caveats.push({
      kind: "discarded_axis",
      axis: a,
      copy: `I threw out my own ${AXIS_LABELS[a].toLowerCase()} read — it was grading how dressy this is, which is measured separately.`,
    });
  }
  if (!intake.weather) {
    caveats.push({
      kind: "no_weather",
      copy: "I don't know what weather you're in, so I didn't grade fabric against a season.",
    });
  }
  for (const c of caveated) {
    caveats.push({ kind: `rule:${c.id}`, copy: c.caveat });
  }
  for (const c of judge.caveats ?? []) {
    if (typeof c === "string" && c.trim()) caveats.push({ kind: "model", copy: c.slice(0, 200) });
  }

  return {
    headline,
    weighted_mean: round2(rawWeighted),
    band,
    reason: whyParts[0],
    axes,
    weights: w,
    ctx,
    presence: {
      read: presenceRead, claimed: intake.presence, gap: q,
      line: q === 0
        ? "Pitched exactly where you asked."
        : q > 0
        ? "This is louder than you asked me to grade. Nothing here is wrong — it just isn't the brief."
        : "This is quieter than you asked me to grade. Nothing here is wrong — it just isn't the brief.",
    },
    rules_fired: rulesFired,
    downgraded_to_caveat: caveated.map((c) => ({ id: c.id, name: c.name, copy: c.caveat, confidence: round2(c.confidence) })),
    caps_applied: full.overallCaps.map((c) => ({
      id: c.id, scope: "overall", to: round1(c.value),
      binding: Math.abs(c.value - full.headline) < 0.001,
    })),
    weight_deltas: built.deltas,
    null_axes: nullAxes,
    discarded_axes: discardedAxes,
    raw_weighted: round2(rawWeighted),
    why_this_number: whyParts.join(" "),
    lever,
    caveats,
    brief_verdict: { state: briefState, copy: briefCopy },
    subscores: projectSubscores(axes, w, headline),
    intent_axis: intentAxis,
  };
}

/**
 * The v3 `subscores` projection. Shipped builds average these four numbers and
 * show the result as the headline, so the projection has to land on the real
 * headline or two builds disagree about the same look.
 *
 * Iterates to convergence rather than the two fixed passes the spec started
 * with: at extreme headlines the clamp absorbs the shift and a fixed pass count
 * leaves the mean off by more than the compat test allows.
 */
export function projectSubscores(
  axes: AxisOut[],
  w: Record<AxisKey, number>,
  headline: number,
): Record<string, number> {
  const byKey = new Map(axes.map((a) => [a.key, a]));
  const groupMean = (keys: AxisKey[]): number | null => {
    let num = 0, den = 0;
    for (const k of keys) {
      const a = byKey.get(k);
      if (!a || a.score == null || w[k] === 0) continue;
      num += a.score * w[k];
      den += w[k];
    }
    return den > 0 ? num / den : null;
  };

  const groups: Array<[string, AxisKey[]]> = [
    ["color", ["COL"]],
    ["fit", ["FIT"]],
    ["style_match", ["CTX", "DTL", "POV"]],
    ["seasonal", ["TEX", "PRS"]],
  ];

  const vals: Array<[string, number]> = [];
  for (const [key, axesIn] of groups) {
    const m = groupMean(axesIn);
    // A group whose every axis is null is OMITTED, never zeroed. `Number(x)||0`
    // is what silently deflated every legacy client's mean by a quarter.
    if (m != null) vals.push([key, m]);
  }
  if (vals.length === 0) return {};

  for (let i = 0; i < 24; i++) {
    const mean = vals.reduce((s, [, v]) => s + v, 0) / vals.length;
    const delta = headline - mean;
    if (Math.abs(delta) < 0.005) break;
    let moved = false;
    for (const v of vals) {
      const next = clamp(v[1] + delta, 1, 10);
      if (Math.abs(next - v[1]) > 1e-9) moved = true;
      v[1] = next;
    }
    if (!moved) break;
  }

  return Object.fromEntries(vals.map(([k, v]) => [k, round1(v)]));
}
