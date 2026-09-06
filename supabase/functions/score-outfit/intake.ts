// Scoring v4 — request parsing.
//
// Anything unrecognised is coerced to the default and recorded as a warning;
// nothing here ever throws. Fields belonging to a different occasion are
// dropped rather than carried, so a `room` on a wedding cannot arm a work rule.

import {
  SEED_FORMALITY,
  SEED_PRESENCE,
} from "../_shared/rubric/rubric.v4.ts";
import type {
  Intake,
  Occasion,
  OnFeet,
  Role,
  Room,
  TimeOfDay,
  Venue,
  WeatherBand,
} from "../_shared/rubric/types.ts";

const OCCASIONS: Occasion[] = ["work", "date", "wedding", "casual", "everyday"];
const ROLES: Role[] = ["guest", "wedding_party", "couple"];
const VENUES: Venue[] = ["ballroom", "garden", "beach", "registry_or_worship"];
const ROOMS: Room[] = ["creative", "business_casual", "corporate", "client_facing"];
const ON_FEET: OnFeet[] = ["most_of_day", "some", "seated"];

/** Older venue vocabulary carried the hour in the venue enum. The hour now
 * arrives on `time_of_day`, so the day/evening halves fold together. */
const VENUE_ALIASES: Record<string, Venue> = {
  ballroom_day: "ballroom",
  ballroom_evening: "ballroom",
  garden_day: "garden",
  garden_evening: "garden",
  registry: "registry_or_worship",
  worship: "registry_or_worship",
};

/** `freezing` and `warm` moved the headline less than the display rounding, so
 * they are accepted on the wire and folded into the three live bands. */
const WEATHER_ALIASES: Record<string, WeatherBand> = {
  freezing: "cold",
  cold: "cold",
  mild: "mild",
  warm: "mild",
  hot: "hot",
};

export interface ParsedRequest {
  imageUrl: string;
  backUrl: string;
  intake: Intake;
  /** Present only when the client sent an `intake` object — a legacy client
   * gets base weights and copy that never references a code it was never
   * asked about. */
  isV4Client: boolean;
  wantsV4Envelope: boolean;
  wantsXray: boolean;
  bodyProfile: Record<string, unknown> | null;
  /** Capitalised legacy mirror, written to `outfits.occasion` unchanged. */
  legacyOccasion: string;
  warnings: string[];
  locale: string;
}

function pick<T extends string>(raw: unknown, allowed: T[], fallback: T, warnings: string[], field: string, aliases: Record<string, T> = {}): T {
  const v = String(raw ?? "").toLowerCase().trim().replace(/[\s-]+/g, "_");
  if (!v) return fallback;
  if ((allowed as string[]).includes(v)) return v as T;
  if (aliases[v]) return aliases[v];
  warnings.push(`${field}: "${v}" is not a value I know — used ${fallback}`);
  return fallback;
}

function dial(raw: unknown, fallback: number): number {
  const n = Math.round(Number(raw));
  if (!isFinite(n)) return fallback;
  return Math.max(1, Math.min(5, n));
}

// deno-lint-ignore no-explicit-any
export function parseRequest(body: any): ParsedRequest {
  const warnings: string[] = [];
  const raw = body?.intake && typeof body.intake === "object" ? body.intake : null;
  const isV4Client = raw !== null;

  const legacyOccasionRaw = String(body?.occasion ?? "").trim();
  const occasionSource = raw?.occasion ?? legacyOccasionRaw ?? "everyday";
  const occasion = pick<Occasion>(occasionSource, OCCASIONS, "everyday", warnings, "occasion");

  const formality = raw ? dial(raw.formality, SEED_FORMALITY[occasion]) : SEED_FORMALITY[occasion];
  const presence = raw ? dial(raw.presence, SEED_PRESENCE[occasion]) : SEED_PRESENCE[occasion];

  // Band 4 exists only where a dial provably cannot encode the answer.
  const role = occasion === "wedding"
    ? pick<Role>(raw?.role, ROLES, "guest", warnings, "role")
    : null;
  const venue = occasion === "wedding"
    ? pick<Venue>(raw?.venue, VENUES, "ballroom", warnings, "venue", VENUE_ALIASES)
    : null;
  const room = occasion === "work"
    ? pick<Room>(raw?.room, ROOMS, "business_casual", warnings, "room")
    : null;
  const onFeet = occasion === "everyday"
    ? pick<OnFeet>(raw?.on_feet, ON_FEET, "some", warnings, "on_feet")
    : null;

  // Weather is omitted entirely when the device could not supply it. It is
  // never guessed, and the weather rules disarm rather than fire on a default.
  let weather: Intake["weather"] = null;
  const wRaw = raw?.weather;
  if (wRaw && typeof wRaw === "object" && wRaw.band != null) {
    const band = WEATHER_ALIASES[String(wRaw.band).toLowerCase().trim()];
    if (band) weather = { band, precip: wRaw.precip === true };
    else warnings.push(`weather.band: "${wRaw.band}" is not a band I know — dropped`);
  }

  const timeOfDay = pick<TimeOfDay>(raw?.time_of_day, ["daytime", "evening"], "daytime", warnings, "time_of_day");

  const intentRaw = String(raw?.intent ?? body?.intent ?? "").trim().slice(0, 140);

  const backUrl = String(body?.back_url ?? "").trim();

  const intake: Intake = {
    occasion,
    formality,
    presence,
    role,
    venue,
    room,
    on_feet: onFeet,
    intent: intentRaw || null,
    weather,
    time_of_day: timeOfDay,
    has_back: backUrl.length > 0,
  };

  const features: string[] = Array.isArray(body?.client_features)
    ? body.client_features.map((f: unknown) => String(f))
    : [];

  return {
    imageUrl: String(body?.image_url ?? "").trim(),
    backUrl,
    intake,
    isV4Client,
    // Gated on the request, never on the response `version` — neither shipped
    // client reads `version`.
    wantsV4Envelope: features.includes("axes_v4"),
    wantsXray: features.includes("xray_v4") || !features.includes("axes_v4"),
    bodyProfile: body?.body_profile && typeof body.body_profile === "object" ? body.body_profile : null,
    legacyOccasion: legacyOccasionRaw || occasion.charAt(0).toUpperCase() + occasion.slice(1),
    warnings,
    locale: String(body?.locale ?? "en").slice(0, 5),
  };
}
