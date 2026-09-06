// Test harness — replays a frozen JUDGE response through the engine.
//
// The model is nondeterministic; the engine is not. Splitting the test surface
// at that seam is what makes "same photo, different answers, different number"
// something you can assert rather than eyeball.

import { parseRequest } from "../intake.ts";
import { normalizeJudge } from "../judge.ts";
import { runEngine } from "../engine.ts";
import { sanitizePalette } from "../sanitize.ts";
import type { Intake } from "../../_shared/rubric/types.ts";

// deno-lint-ignore no-explicit-any
export async function loadFixture(name: string): Promise<any> {
  const url = new URL(`./fixtures/judge/${name}.json`, import.meta.url);
  return JSON.parse(await Deno.readTextFile(url));
}

// deno-lint-ignore no-explicit-any
export function intakeOf(partial: Record<string, unknown>, back = false): Intake {
  return parseRequest({
    image_url: "x",
    back_url: back ? "y" : "",
    intake: partial,
    client_features: ["axes_v4"],
  }).intake;
}

// deno-lint-ignore no-explicit-any
export function score(fixture: any, partial: Record<string, unknown>, back = false) {
  const palette = sanitizePalette(
    String(fixture.signals_list?.find((s: { key: string }) => s.key === "palette_hex")?.value ?? "").split(","),
  );
  const judge = normalizeJudge(fixture, palette);
  return runEngine(intakeOf(partial, back), judge);
}
