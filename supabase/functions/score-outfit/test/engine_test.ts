// The proof the redesign was asked for: ONE photograph, many briefs, and a
// different — reproducible — number every time.
//
// The model is nondeterministic, so it is not in the loop here. Every case
// below replays the SAME frozen JUDGE response through the engine with a
// different intake. Anything that moves is the intake's doing and nothing
// else's.

import { assert, assertAlmostEquals, assertEquals } from "jsr:@std/assert@1";
import { loadFixture, score } from "./harness.ts";
import { AXIS_KEYS } from "../../_shared/rubric/types.ts";

const fixture = await loadFixture("charcoal_suit");

interface Case {
  name: string;
  intake: Record<string, unknown>;
  back?: boolean;
  headline: number;
  topAxis: string;
  briefState: string;
  expectRules?: string[];
  forbidRules?: string[];
}

const WEDDING_BALLROOM = { role: "guest", venue: "ballroom", time_of_day: "evening" };

const CASES: Case[] = [
  {
    name: "wedding · black tie · the suit is not a tuxedo",
    intake: { occasion: "wedding", formality: 5, presence: 3, ...WEDDING_BALLROOM },
    headline: 6.5, topAxis: "CTX", briefState: "off_brief", expectRules: ["R-BT"],
  },
  {
    name: "wedding · cocktail · the same suit, the right room",
    intake: { occasion: "wedding", formality: 4, presence: 3, ...WEDDING_BALLROOM },
    headline: 8.3, topAxis: "CTX", briefState: "on_brief", forbidRules: ["R-BT"],
  },
  {
    name: "wedding · black tie · as the couple, the white rule cannot arm",
    intake: { occasion: "wedding", formality: 5, presence: 3, ...WEDDING_BALLROOM, role: "couple" },
    headline: 6.5, topAxis: "CTX", briefState: "off_brief", forbidRules: ["R-WHITE"],
  },
  {
    name: "work · boardroom · corporate",
    intake: { occasion: "work", formality: 5, presence: 2, room: "corporate" },
    headline: 8.3, topAxis: "CTX", briefState: "on_brief", forbidRules: ["R-CORP"],
  },
  {
    name: "work · casual Friday · creative room punishes the over-dress",
    intake: { occasion: "work", formality: 2, presence: 3, room: "creative" },
    headline: 7.4, topAxis: "COL", briefState: "nearly", expectRules: ["R-CREATIVE"],
  },
  {
    name: "casual · brunch · informality immunity",
    intake: { occasion: "casual", formality: 2, presence: 3 },
    headline: 7.6, topAxis: "SIL", briefState: "nearly", expectRules: ["R-INFORM"],
  },
  {
    name: "casual · brunch · dialled to be looked at, and it isn't",
    intake: { occasion: "casual", formality: 2, presence: 5 },
    // Dialling the presence up has to make point of view the heaviest axis —
    // otherwise the dial is decoration.
    headline: 7.1, topAxis: "POV", briefState: "nearly", expectRules: ["R-PRESENCE"],
  },
  {
    name: "everyday · nothing tapped",
    intake: { occasion: "everyday" },
    headline: 7.7, topAxis: "SIL", briefState: "nearly",
  },
  {
    name: "everyday · on your feet all day, in a stiff dress shoe",
    intake: { occasion: "everyday", formality: 2, on_feet: "most_of_day" },
    headline: 6.7, topAxis: "SHO", briefState: "nearly", expectRules: ["R-FEET"],
  },
  {
    name: "date · dinner · dialled to be noticed",
    intake: { occasion: "date", formality: 3, presence: 4 },
    headline: 7.4, topAxis: "POV", briefState: "nearly",
  },
  {
    name: "wedding · black tie · with a back view, the evidence ceiling lifts",
    intake: { occasion: "wedding", formality: 5, presence: 3, ...WEDDING_BALLROOM },
    back: true,
    headline: 6.5, topAxis: "CTX", briefState: "off_brief", forbidRules: ["R-EVIDENCE"],
  },
];

for (const c of CASES) {
  Deno.test(c.name, () => {
    const r = score(fixture, c.intake, c.back ?? false);

    assertEquals(r.headline, c.headline, `headline for ${c.name}`);
    assertEquals(r.axes[0].key, c.topAxis, "heaviest axis");
    assertEquals(r.brief_verdict.state, c.briefState, "brief verdict");

    const ids = r.rules_fired.map((x) => x.id);
    for (const want of c.expectRules ?? []) assert(ids.includes(want), `expected ${want}, got ${ids.join(",")}`);
    for (const no of c.forbidRules ?? []) assert(!ids.includes(no), `did not expect ${no}`);

    // ── Invariants that hold on every case, always ──────────────────────────
    const sum = AXIS_KEYS.reduce((s, a) => s + r.weights[a], 0);
    assertAlmostEquals(sum, 1.0, 0.001, "weights must sum to 1");

    assert(r.headline >= 1 && r.headline <= 10, "headline in range");

    // The waterfall is an identity, not an aspiration: the raw weighted mean
    // plus every rule's cost in fire order IS the final number.
    const costs = r.rules_fired.reduce((s, x) => s + x.headline_cost, 0);
    assertAlmostEquals(r.raw_weighted + costs, r.headline, 0.06, "rule costs must reconcile to the headline");

    // The v3 projection contract. A shipped build averages these four numbers
    // and shows the result as the headline; if this drifts, two builds show
    // two different scores for one look.
    const vals = Object.values(r.subscores);
    assert(vals.length > 0, "subscores must not be empty");
    for (const v of vals) {
      assert(typeof v === "number" && isFinite(v), "subscore must be finite");
      assert(v >= 1 && v <= 10, "subscore in range");
    }
    assertAlmostEquals(
      vals.reduce((s, v) => s + v, 0) / vals.length,
      r.headline,
      0.05,
      "legacy subscore mean must land on the headline",
    );

    // Nothing null is ever scored, and nothing scored carries zero weight.
    for (const a of r.axes) {
      if (a.unjudgeable) assertEquals(a.score, null, `${a.key} unjudgeable but scored`);
      if (a.score != null) assert(a.weight > 0, `${a.key} scored at zero weight`);
    }
  });
}

Deno.test("the same photograph produces materially different numbers per brief", () => {
  const headlines = CASES.map((c) => score(fixture, c.intake, c.back ?? false).headline);
  const spread = Math.max(...headlines) - Math.min(...headlines);
  assert(spread >= 1.5, `intake must move the headline by more than rounding; spread was ${spread}`);
});

Deno.test("every occasion grades a different axis hardest", () => {
  const tops = ["work", "date", "wedding", "casual", "everyday"].map((occasion) =>
    score(fixture, { occasion }).axes[0].key
  );
  assert(new Set(tops).size >= 3, `occasions collapsed onto the same axis: ${tops.join(",")}`);
});

Deno.test("the formality dial moves the dress-code weight at every step", () => {
  const weights = [1, 2, 3, 4, 5].map((formality) =>
    score(fixture, { occasion: "wedding", formality, ...WEDDING_BALLROOM }).weights.CTX
  );
  for (let i = 1; i < weights.length; i++) {
    assert(
      weights[i] > weights[i - 1] + 0.004,
      `dial position ${i + 1} did not out-weight ${i}: ${weights.join(", ")}`,
    );
  }
});

Deno.test("an axis the photo cannot support is dropped, not guessed", () => {
  const cropped = structuredClone(fixture);
  cropped.axes.SHO = { score: null, evidence: "Feet are cropped at the frame edge." };
  const r = score(cropped, { occasion: "everyday" });

  assertEquals(r.weights.SHO, 0, "a null axis carries no weight");
  assertAlmostEquals(AXIS_KEYS.reduce((s, a) => s + r.weights[a], 0), 1.0, 0.001);
  assert(r.null_axes.includes("SHO"));
  assert(r.caveats.some((c) => c.kind === "null_axis"), "the user is told what was missed");

  // Cropping your shoes must never be profitable.
  const whole = score(fixture, { occasion: "everyday" });
  assert(r.headline <= whole.headline, "an incomplete read outscored a complete one");
});

Deno.test("an axis graded on formality is discarded rather than double-counted", () => {
  const leaky = structuredClone(fixture);
  leaky.axes.COL = { score: 4, evidence: "The palette is fine but this is too casual for the event." };
  const r = score(leaky, { occasion: "wedding", formality: 4, ...WEDDING_BALLROOM });

  assert(r.discarded_axes.includes("COL"), "formality leaked into an axis and was kept");
  assertEquals(r.weights.COL, 0);
});

Deno.test("an honest informal look cannot be talked below six", () => {
  const poor = structuredClone(fixture);
  for (const k of ["SIL", "FIT", "COL", "TEX", "DTL", "SHO", "PRS", "POV"]) {
    poor.axes[k] = { score: 5, evidence: "Adequate." };
  }
  poor.presence_read = 5;
  const r = score(poor, { occasion: "everyday", formality: 1, presence: 1 });
  assert(r.headline >= 6.0, `everyday floor breached at ${r.headline}`);
});

Deno.test("the lever respects the caps — fixing the wrong thing buys nothing", () => {
  const r = score(fixture, { occasion: "wedding", formality: 5, presence: 3, ...WEDDING_BALLROOM });
  assertEquals(r.lever?.axis, "CTX", "with black tie missed, nothing but the code moves the number");
  assert((r.lever?.delta ?? 0) > 1.0, "the lever must be worth taking");
});

Deno.test("both dials change both the number and the rubric, at every occasion", () => {
  // Two things have to be true for a dial to be worth its place on the sheet:
  // the number moves, and the ORDER the axes are graded in moves. The second is
  // the stronger claim and the one the breakdown screen renders.
  //
  // The magnitudes differ by occasion on purpose. Standing out at work is a
  // liability, so the presence dial legitimately does less there than it does
  // on a date; a casual brief's dress code carries 3%, so its formality dial
  // does less than a wedding's. What none of them may do is nothing.
  for (const occasion of ["work", "date", "wedding", "casual", "everyday"]) {
    const lowF = score(fixture, { occasion, formality: 1, presence: 3 });
    const highF = score(fixture, { occasion, formality: 5, presence: 3 });
    assert(
      Math.abs(highF.headline - lowF.headline) >= 0.2,
      `${occasion}: formality 1 → 5 moved the headline only ${Math.abs(highF.headline - lowF.headline).toFixed(2)}`,
    );
    assert(
      lowF.axes.map((a) => a.key).join() !== highF.axes.map((a) => a.key).join(),
      `${occasion}: formality 1 → 5 left the rubric order untouched`,
    );

    const quiet = score(fixture, { occasion, formality: 3, presence: 1 });
    const loud = score(fixture, { occasion, formality: 3, presence: 5 });
    assert(
      Math.abs(loud.headline - quiet.headline) >= 0.2,
      `${occasion}: presence 1 → 5 moved the headline only ${Math.abs(loud.headline - quiet.headline).toFixed(2)}`,
    );
    assert(
      quiet.axes.map((a) => a.key).join() !== loud.axes.map((a) => a.key).join(),
      `${occasion}: presence 1 → 5 left the rubric order untouched`,
    );
  }
});

Deno.test("the wedding formality dial is the biggest single lever in the app", () => {
  // Across the range, not end to end: the extremes are both wrong in opposite
  // directions (a suit is over-dressed for a backyard and under-dressed for
  // black tie), so 1-vs-5 understates the dial. What matters is the span
  // between the best rung and the worst.
  const headlines = [1, 2, 3, 4, 5].map((formality) =>
    score(fixture, { occasion: "wedding", formality, ...WEDDING_BALLROOM }).headline
  );
  const span = Math.max(...headlines) - Math.min(...headlines);
  assert(span >= 1.5, `the wedding dial spans only ${span.toFixed(2)}: ${headlines.join(", ")}`);
});
