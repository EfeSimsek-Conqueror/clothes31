// The contract with builds that are already in people's hands.
//
// A shipped iOS build throws without `score`; its `Annotation` uses a
// synthesised decoder, so ONE missing key fails the entire response, not just
// that row; its `Subscores` reads four optional numbers and averages whichever
// are present, then shows that average as the headline. Android's hand-written
// `SubscoresSerializer` returns an empty object for anything that is not a JSON
// object of numbers.
//
// None of those builds can be patched. These assertions are the reason a v4
// edit cannot silently break them.

import { assert, assertAlmostEquals, assertEquals } from "jsr:@std/assert@1";
import { legacyAnnotations, legacySwaps, normalizeSwaps } from "../legacy.ts";
import { loadFixture, score } from "./harness.ts";

const fixture = await loadFixture("charcoal_suit");

Deno.test("subscores are four plain numbers, never null and never nested", () => {
  const allowed = new Set(["color", "fit", "style_match", "seasonal"]);
  for (const occasion of ["work", "date", "wedding", "casual", "everyday"]) {
    const r = score(fixture, { occasion });
    const round = JSON.parse(JSON.stringify(r.subscores));
    for (const [k, v] of Object.entries(round)) {
      assert(allowed.has(k), `unknown legacy subscore key ${k}`);
      assertEquals(typeof v, "number", `${k} must be a bare number`);
      assert(isFinite(v as number) && (v as number) > 0, `${k} must be finite and positive`);
    }
  }
});

Deno.test("a subscore group with nothing to average is omitted, never zeroed", () => {
  // `Number(x) || 0` in v3 turned a missing axis into a zero and quietly
  // deflated every shipped client's mean by a quarter.
  const blind = structuredClone(fixture);
  blind.axes.COL = { score: null, evidence: "Colour is unreadable at this exposure." };
  const r = score(blind, { occasion: "everyday" });

  assertEquals(r.subscores.color, undefined, "an unjudgeable group must be absent");
  const vals = Object.values(r.subscores);
  assertEquals(vals.length, 3);
  assertAlmostEquals(
    vals.reduce((s, v) => s + v, 0) / vals.length,
    r.headline,
    0.05,
    "the surviving keys must still average to the headline",
  );
});

Deno.test("the shipped mean and the v4 headline never disagree", () => {
  // A shipped build shows mean(subscores); a v4 build shows `headline`. Two
  // numbers for one look on two phones is the worst outcome available.
  for (const formality of [1, 2, 3, 4, 5]) {
    for (const presence of [1, 3, 5]) {
      const r = score(fixture, { occasion: "wedding", formality, presence, role: "guest", venue: "ballroom" });
      const vals = Object.values(r.subscores);
      assertAlmostEquals(
        vals.reduce((s, v) => s + v, 0) / vals.length,
        r.headline,
        0.05,
        `wedding f${formality} p${presence}`,
      );
    }
  }
});

Deno.test("swaps degrade to a flat string array", () => {
  const swaps = normalizeSwaps(fixture.swaps);
  const flat = legacySwaps(swaps);
  assert(flat.length > 0 && flat.length <= 2);
  for (const s of flat) assertEquals(typeof s, "string");
});

Deno.test("swaps survive a model that returns junk", () => {
  assertEquals(legacySwaps(normalizeSwaps(null)), []);
  assertEquals(legacySwaps(normalizeSwaps("nonsense")), []);
  assertEquals(legacySwaps(normalizeSwaps([{ to: "" }, { from: "x" }])), []);
  assertEquals(legacySwaps(normalizeSwaps([{ to: "A bow tie" }])), ["A bow tie"]);
});

Deno.test("annotations always carry all five keys, even when the drawing call fails", () => {
  for (
    const rows of [
      legacyAnnotations([], null, undefined),
      legacyAnnotations([], "Dress code", { from: "Long tie", to: "A bow tie", axis: "CTX", why: "", effort: "swap" }),
      legacyAnnotations([{ label: "", x_pct: 10, y_pct: 20, score: 8, note: "" }], null, undefined),
    ]
  ) {
    assert(rows.length >= 1, "a shipped build must never get an empty x-ray");
    for (const a of rows) {
      for (const key of ["x_pct", "y_pct", "label", "score", "note"] as const) {
        assert(a[key] !== undefined && a[key] !== null, `annotation is missing ${key}`);
      }
      assertEquals(typeof a.label, "string");
      assert(a.label.length > 0, "label must be non-empty");
      assert(a.note.length > 0, "note must be non-empty");
      assert(a.x_pct >= 0 && a.x_pct <= 100);
      assert(a.score >= 0 && a.score <= 10);
    }
  }
});

Deno.test("a legacy client sending no intake still gets a real read", () => {
  // A stale Android build sends only image_url, occasion and honesty. It must
  // get base weights, a sane number, and copy that never references a dress
  // code it was never asked about.
  const r = score(fixture, {});
  assert(r.headline >= 1 && r.headline <= 10);
  assertEquals(Object.keys(r.subscores).length, 4);
  assert(r.axes.length === 9);
});
