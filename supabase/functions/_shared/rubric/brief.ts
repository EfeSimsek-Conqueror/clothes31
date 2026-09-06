// Scoring v4 — the brief line and the rubric id.
//
// The brief is composed from a locale format string with named slots, never by
// concatenation, so word order survives translation.

import {
  BRIEF_FORMATS,
  FORMALITY_CAPTIONS,
  RUBRIC_VERSION,
} from "./rubric.v4.ts";
import type { Intake } from "./types.ts";

const OCCASION_NP: Record<string, string> = {
  work: "A work",
  date: "A date",
  wedding: "A wedding",
  casual: "A casual",
  everyday: "An everyday",
};

const ROLE_SLOT: Record<string, string> = {
  guest: ", as a guest",
  wedding_party: ", in the wedding party",
  couple: ", as the couple",
};

/** "meant to read ___" — adjectives, not the dial captions, because "meant to
 * read be looked at" is not a sentence. */
const PRESENCE_ADJ = ["invisible", "quiet", "balanced", "noticed", "unmissable"];

function fill(template: string, slots: Record<string, string>): string {
  return template.replace(/\{(\w+)\}/g, (_m, k) => slots[k] ?? "");
}

export function briefLine(intake: Intake, locale = "en"): string {
  const caption = FORMALITY_CAPTIONS[intake.occasion][intake.formality - 1] ?? "";
  const slots: Record<string, string> = {
    occasion: OCCASION_NP[intake.occasion] ?? "A",
    role: intake.occasion === "wedding" && intake.role ? ROLE_SLOT[intake.role] ?? "" : "",
    qualifier: caption ? `, for ${caption.toLowerCase()}` : "",
    presence: PRESENCE_ADJ[intake.presence - 1] ?? "balanced",
  };
  const template = BRIEF_FORMATS[locale] ?? BRIEF_FORMATS.en;
  return fill(template, slots).replace(/\s+/g, " ").trim();
}

/** Stable, human-readable id for the exact rubric a look was graded against.
 * Stored on the row so two scores are only ever compared like for like. */
export function rubricId(intake: Intake): string {
  const band4 = intake.venue ?? intake.room ?? intake.on_feet ?? "none";
  const role = intake.role ?? "none";
  return [
    intake.occasion,
    role,
    `f${intake.formality}`,
    `p${intake.presence}`,
    band4,
    `v${RUBRIC_VERSION}`,
  ].join(".");
}

/** "Wedding · black tie · guest" — the label under the score. */
export function rubricLabel(intake: Intake): string {
  const parts: string[] = [
    intake.occasion.charAt(0).toUpperCase() + intake.occasion.slice(1),
    (FORMALITY_CAPTIONS[intake.occasion][intake.formality - 1] ?? "").toLowerCase(),
  ];
  if (intake.occasion === "wedding" && intake.role) parts.push(intake.role.replace(/_/g, " "));
  if (intake.occasion === "work" && intake.room) parts.push(intake.room.replace(/_/g, " "));
  return parts.filter(Boolean).join(" · ");
}
