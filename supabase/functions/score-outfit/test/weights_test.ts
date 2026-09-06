// The weight matrix, exhaustively. Every occasion × formality × presence ×
// band-4 combination is a finite set, so there is no excuse for not checking it.
//
// These are acceptance criteria, not a rubber stamp: a dial position that moves
// nothing is a lie told to the user before they spend a credit.

import { assert, assertAlmostEquals } from "jsr:@std/assert@1";
import { buildWeights, primaryAxes } from "../../_shared/rubric/weights.ts";
import { BASE_WEIGHTS } from "../../_shared/rubric/rubric.v4.ts";
import { AXIS_KEYS, type AxisKey, type Intake, type Occasion } from "../../_shared/rubric/types.ts";
import { intakeOf } from "./harness.ts";

const OCCASIONS: Occasion[] = ["work", "date", "wedding", "casual", "everyday"];

const BAND4: Record<Occasion, Array<Record<string, unknown>>> = {
  work: [{ room: "creative" }, { room: "business_casual" }, { room: "corporate" }, { room: "client_facing" }],
  date: [{}],
  wedding: [
    { role: "guest", venue: "ballroom" }, { role: "guest", venue: "garden" },
    { role: "guest", venue: "beach" }, { role: "guest", venue: "registry_or_worship" },
    { role: "wedding_party", venue: "ballroom" }, { role: "couple", venue: "ballroom" },
  ],
  casual: [{}],
  everyday: [{ on_feet: "most_of_day" }, { on_feet: "some" }, { on_feet: "seated" }],
};

function* everyCombination(): Generator<Intake> {
  for (const occasion of OCCASIONS) {
    for (const band4 of BAND4[occasion]) {
      for (let formality = 1; formality <= 5; formality++) {
        for (let presence = 1; presence <= 5; presence++) {
          yield intakeOf({ occasion, formality, presence, ...band4 });
        }
      }
    }
  }
}

Deno.test("every weight vector sums to exactly one", () => {
  let rows = 0;
  for (const intake of everyCombination()) {
    const { w } = buildWeights(intake);
    const sum = AXIS_KEYS.reduce((s, a) => s + w[a], 0);
    assertAlmostEquals(sum, 1.0, 0.0005, `Σw for ${JSON.stringify(intake)}`);
    for (const a of AXIS_KEYS) {
      assert(w[a] > 0, `${a} vanished entirely for ${JSON.stringify(intake)}`);
      assert(w[a] < 0.45, `${a} ran away with the vector for ${JSON.stringify(intake)}`);
    }
    rows++;
  }
  assert(rows >= 350, `expected the full matrix, walked only ${rows} rows`);
});

Deno.test("every formality step moves at least one axis materially", () => {
  for (const occasion of OCCASIONS) {
    for (let f = 1; f < 5; f++) {
      const a = buildWeights(intakeOf({ occasion, formality: f, presence: 3 })).w;
      const b = buildWeights(intakeOf({ occasion, formality: f + 1, presence: 3 })).w;
      const biggest = Math.max(...AXIS_KEYS.map((k) => Math.abs(a[k] - b[k])));
      // 1.5 points of weight, not 2: at `everyday` the dress code starts at 5%,
      // so the formality dial legitimately does less to the WEIGHTS there. Its
      // real work at that occasion is on the dress-code SCORE, through the gap
      // arithmetic — which `engine_test.ts` asserts on the headline directly.
      assert(biggest >= 0.015, `${occasion} formality ${f}→${f + 1} moved nothing (max ${biggest.toFixed(4)})`);
    }
  }
});

Deno.test("every presence step moves at least one axis materially", () => {
  for (const occasion of OCCASIONS) {
    for (let p = 1; p < 5; p++) {
      const a = buildWeights(intakeOf({ occasion, formality: 3, presence: p })).w;
      const b = buildWeights(intakeOf({ occasion, formality: 3, presence: p + 1 })).w;
      const biggest = Math.max(...AXIS_KEYS.map((k) => Math.abs(a[k] - b[k])));
      assert(biggest >= 0.02, `${occasion} presence ${p}→${p + 1} moved nothing (max ${biggest.toFixed(4)})`);
    }
  }
});

Deno.test("every band-4 option is worth asking — no decorative answers", () => {
  for (const occasion of OCCASIONS) {
    const options = BAND4[occasion];
    if (options.length < 2) continue;
    const vectors = options.map((o) => buildWeights(intakeOf({ occasion, formality: 3, presence: 3, ...o })).w);
    for (let i = 0; i < vectors.length; i++) {
      for (let j = i + 1; j < vectors.length; j++) {
        const biggest = Math.max(...AXIS_KEYS.map((k) => Math.abs(vectors[i][k] - vectors[j][k])));
        assert(
          biggest >= 0.01,
          `${occasion}: ${JSON.stringify(options[i])} and ${JSON.stringify(options[j])} grade identically`,
        );
      }
    }
  }
});

Deno.test("the dress code can never be dialled away", () => {
  for (const occasion of OCCASIONS) {
    for (let p = 1; p <= 5; p++) {
      for (let f = 1; f <= 5; f++) {
        const { w } = buildWeights(intakeOf({ occasion, formality: f, presence: p }));
        assert(
          w.CTX >= 0.5 * BASE_WEIGHTS[occasion].CTX - 0.0005,
          `${occasion} f${f} p${p}: CTX fell to ${w.CTX}`,
        );
      }
    }
  }
});

Deno.test("no two occasions grade in the same order at their seeds", () => {
  // Work and a wedding legitimately share a first axis — both are occasions
  // with published rules. What they must not share is the whole ordering, or
  // the occasion chip is decoration.
  const seen = new Map<string, Occasion>();
  for (const occasion of OCCASIONS) {
    const order = primaryAxes(intakeOf({ occasion }), AXIS_KEYS.length).join(",");
    const clash = seen.get(order);
    assert(!clash, `${occasion} and ${clash} grade identically: ${order}`);
    seen.set(order, occasion);
  }
});

Deno.test("a stated intent buys its axis weight, and never from itself", () => {
  const plain = buildWeights(intakeOf({ occasion: "everyday" })).w;
  for (const [axis, intent] of [["SIL", "Look taller"], ["TEX", "Look expensive"], ["POV", "Look like I didn't try"], ["FIT", "Hide my arms"]] as Array<[AxisKey, string]>) {
    const withIntent = buildWeights(intakeOf({ occasion: "everyday", intent }), [], { intentAxis: axis }).w;
    assert(withIntent[axis] > plain[axis] + 0.01, `${intent} did not lift ${axis}`);
    assertAlmostEquals(AXIS_KEYS.reduce((s, a) => s + withIntent[a], 0), 1.0, 0.0005);
  }
});
