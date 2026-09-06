// deno-lint-ignore-file no-explicit-any
// tryon-outfit v4 — FLUX everywhere.
//
// The rule the whole file exists to enforce: PUT ON EXACTLY WHAT WAS GIVEN.
// Every item visible in the reference goes on — down to the hat — and nothing
// that is not visible in it may be invented.
//
// That rule is what decides the engine, because FLUX VTO is structurally a
// SINGLE-garment model: it takes one human, one garment and a category of
// tops | bottoms | one-pieces. It cannot put on a hat, and it cannot put on a
// coat and trousers and shoes together. So:
//
// Everything the inventory sees → `flux-pro/v1/vto`. A try-on model cannot
// produce the failure a general editor produces — garments laid out beside the
// person instead of worn — because its output is the human wearing the
// reference by construction. It has no category parameter, and its schema states
// that multiple garments are supported as one merged composite, which is exactly
// what the extraction stage already builds. FASHN v1.6, then the editor, are
// fallbacks only.
//
// Which of those applies is not guessed from a category string: when the client
// cannot name the item (an upload), the reference is inventoried by a vision
// pass first. Naming the inventory back to the editor is the whole mechanism
// for "don't add what I didn't give" — a vague "wear this outfit" is exactly
// the instruction that invites a model to invent shoes.
//
// Request:
//   { person_url: string, garment_url: string,
//     category?: string (raw closet category, e.g. "hat", "shirt", "sneakers"),
//     mode?: "performance" | "balanced" | "quality" }
//
// Response: { image_url, version, engine } | { error, ... }

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers":
    "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...CORS, "content-type": "application/json" },
  });
}

// FASHN's fixed vocabulary.
type FashnCat = "tops" | "bottoms" | "one-pieces" | "auto";

type EngineName = "flux-vto" | "fashn" | "flux-kontext";

type SlotSpec = {
  // Short "where it goes" phrase (used in the wrapper prompt).
  where: string;
  // Slot-specific *natural adaptation* instructions. Encourages the model to
  // reshape, resize, and re-light the item to fit the person — not paste it.
  adapt: string;
};

// Classify a raw client category token into an engine + hint.
function classify(raw: string): {
  engine: "garment" | "accessory";
  fashnCategory?: FashnCat;
  slot?: SlotSpec;
} {
  const c = raw.toLowerCase().trim();

  // Direct FASHN tokens.
  if (c === "tops" || c === "bottoms" || c === "one-pieces") {
    return { engine: "garment", fashnCategory: c as FashnCat };
  }

  // Closet-model garment tokens → FASHN.
  const tops = new Set([
    "top", "shirt", "t-shirt", "tee", "tank", "polo", "hoodie",
    "sweater", "sweatshirt", "cardigan", "blazer", "jacket",
    "coat", "outerwear", "vest",
  ]);
  const bottoms = new Set([
    "bottom", "bottoms", "trousers", "pants", "jeans", "shorts",
    "skirt", "cargo", "cargos", "sweats", "sweatpants", "chinos",
    "leggings", "joggers",
  ]);
  const onePieces = new Set([
    "dress", "gown", "jumpsuit", "romper", "overalls", "one-piece",
  ]);
  if (tops.has(c)) return { engine: "garment", fashnCategory: "tops" };
  if (bottoms.has(c)) return { engine: "garment", fashnCategory: "bottoms" };
  if (onePieces.has(c)) return { engine: "garment", fashnCategory: "one-pieces" };

  // Accessory slot specs. `adapt` is the key part — it tells the model to
  // reshape / reangle / relight the item so it looks *worn*, not overlaid.
  const HAT: SlotSpec = {
    where: "on the head",
    adapt:
      "Rest the hat on the head with correct depth and tilt; the brim should follow the head's angle and face orientation. " +
      "Scale the hat to the person's head size, wrap it around the skull with proper perspective, and adjust the hair silhouette so the hat sits naturally on top. " +
      "Cast a soft shadow from the brim onto the forehead where appropriate.",
  };
  const GLASSES: SlotSpec = {
    where: "on the face, over the eyes",
    adapt:
      "Place the glasses on the face aligned with the eyes and resting on the nose bridge and ears. " +
      "Scale to the person's face width, respect head rotation and tilt so both temples follow the sides of the head in correct perspective, and add subtle shadow under the frames. " +
      "If the lenses are tinted, keep tint; if clear, let the eyes show through with a faint reflection.",
  };
  const NECKWEAR_TIE: SlotSpec = {
    where: "around the neck under the collar",
    adapt:
      "Drape the tie naturally down the front of the chest, following the body's contour and posture. " +
      "Add realistic fabric folds at the knot and along the length; match the tie's angle to the person's stance.",
  };
  const NECKWEAR_SCARF: SlotSpec = {
    where: "around the neck",
    adapt:
      "Wrap the scarf naturally around the neck and shoulders, with soft folds, drape, and shadow that follow the body's contours and pose.",
  };
  const NECKLACE: SlotSpec = {
    where: "around the neck, resting on the chest/collarbones",
    adapt:
      "Curve the chain to follow the neckline and collarbones in correct perspective. " +
      "Scale to the person's neck; add small highlights and a soft shadow on the skin beneath.",
  };
  const EARRINGS: SlotSpec = {
    where: "on the earlobes",
    adapt:
      "Place one earring on each visible earlobe, scaled to ear size, oriented with the head's tilt and rotation. " +
      "Add gentle specular highlights and a small shadow on the neck if the earring dangles.",
  };
  const WATCH: SlotSpec = {
    where: "on the left wrist",
    adapt:
      "Wrap the watch band around the wrist in correct perspective, matching the arm's angle and rotation. " +
      "Scale to wrist size; add contact shadow between band and skin; keep the dial facing outward as it naturally would.",
  };
  const BRACELET: SlotSpec = {
    where: "on the wrist",
    adapt:
      "Wrap the bracelet around the wrist with correct curvature and perspective; scale to wrist size; add soft contact shadow.",
  };
  const RING: SlotSpec = {
    where: "on the ring finger",
    adapt:
      "Slip the ring onto the ring finger with correct finger perspective and scale; add a subtle shadow and highlight where it meets skin.",
  };
  const BELT: SlotSpec = {
    where: "around the waist over the waistband",
    adapt:
      "Wrap the belt around the waist following the body's curve; align the buckle centered on the front; add a soft shadow above and below the belt on the garment.",
  };
  const BAG: SlotSpec = {
    where: "carried on the shoulder or held in hand",
    adapt:
      "Position the bag naturally against the body — on a shoulder, in a hand, or at the side — with a strap that follows the shoulder and torso in correct perspective. " +
      "Scale to the person, add contact shadow against clothing, and let the bag hang with realistic weight.",
  };
  const BACKPACK: SlotSpec = {
    where: "worn on the back with straps over both shoulders",
    adapt:
      "Fit the backpack against the upper back with both straps following the shoulders in correct perspective; " +
      "scale to torso size and add shadow where the straps meet the shirt.",
  };
  const SHOES: SlotSpec = {
    where: "on the feet",
    adapt:
      "Fit the shoes onto both feet following the feet's placement, angle, and ground perspective. " +
      "Scale to foot size; align the sole flat with the ground plane; add contact shadow under each shoe.",
  };
  const SOCKS: SlotSpec = {
    where: "on the feet under any footwear",
    adapt:
      "Wrap the socks around each foot and ankle in correct perspective; only the visible portion above the shoe should show.",
  };
  const GLOVES: SlotSpec = {
    where: "on the hands",
    adapt:
      "Fit the gloves onto both hands, wrapping each finger with correct perspective and following the hand's pose; add subtle shadow at the cuff.",
  };
  const HEADBAND: SlotSpec = {
    where: "on the head",
    adapt:
      "Wrap the headband around the head above the ears, following the head's angle and hairline; scale to head size.",
  };
  const GENERIC_JEWELRY: SlotSpec = {
    where: "worn as jewelry in the appropriate place",
    adapt:
      "Place the item on the correct body part with realistic scale, perspective, contact shadow, and highlights.",
  };

  const slotMap: Record<string, SlotSpec> = {
    hat: HAT, cap: HAT, beanie: HAT, headwear: HAT,
    headband: HEADBAND,
    glasses: GLASSES, sunglasses: GLASSES, eyewear: GLASSES,
    scarf: NECKWEAR_SCARF,
    tie: NECKWEAR_TIE, necktie: NECKWEAR_TIE,
    necklace: NECKLACE,
    jewelry: GENERIC_JEWELRY,
    earrings: EARRINGS,
    watch: WATCH,
    bracelet: BRACELET,
    ring: RING,
    belt: BELT,
    bag: BAG, handbag: BAG, purse: BAG,
    backpack: BACKPACK,
    shoes: SHOES, sneakers: SHOES, boots: SHOES, heels: SHOES,
    sandals: SHOES, footwear: SHOES,
    socks: SOCKS,
    gloves: GLOVES,
  };
  if (slotMap[c]) return { engine: "accessory", slot: slotMap[c] };

  if (c === "accessory" || c === "accessories" || c === "other") {
    return {
      engine: "accessory",
      slot: {
        where: "in its natural, correct wearing position",
        adapt:
          "Place the item on the correct body part with realistic scale, angle, perspective, contact shadow, and lighting so it looks worn — not overlaid.",
      },
    };
  }
  return { engine: "garment", fashnCategory: "auto" };
}

/** One wearable thing seen in the reference photograph. */
type Item = { slot: string; name: string };

/** Slots FLUX VTO can actually handle on its own. */
const VTO_SLOTS: Record<string, FashnCat> = {
  top: "tops",
  outerwear: "tops",
  bottom: "bottoms",
  one_piece: "one-pieces",
};

const SLOT_PLACEMENT: Record<string, string> = {
  headwear: "on the head",
  eyewear: "on the face, over the eyes",
  neckwear: "around the neck",
  top: "on the torso",
  outerwear: "over the top, as the outer layer",
  bottom: "on the legs",
  one_piece: "on the body",
  footwear: "on the feet",
  bag: "carried on the shoulder or in the hand",
  jewellery: "in its natural position on the body",
  belt: "around the waist",
  gloves: "on the hands",
  socks: "on the feet, under the footwear",
};

/**
 * Inventory the reference: what is ACTUALLY in this picture?
 *
 * Run only when the client could not name the item — a Studio piece arrives
 * with a real category and does not need a second opinion. On any failure the
 * caller falls back to the category-only route, so this never blocks a render.
 */
async function inventory(
  FAL_KEY: string,
  garment_url: string,
): Promise<{ items: Item[]; hasPerson: boolean } | null> {
  const prompt =
    `List every wearable item visible in this photograph — garments AND accessories: ` +
    `headwear, eyewear, neckwear, tops, outerwear, bottoms, one-pieces, footwear, bags, jewellery, belts, gloves, socks.\n\n` +
    `Return ONLY JSON: {"has_person": true|false, "items":[{"slot":"...","name":"..."}]}\n` +
    `has_person is true if a human being is visible wearing or holding the items, false for a flat lay ` +
    `or a product shot with no person in it.\n` +
    `slot is one of: headwear, eyewear, neckwear, top, outerwear, bottom, one_piece, footwear, bag, jewellery, belt, gloves, socks.\n` +
    `name is a short concrete description, three or four words: "black wool flat cap", "cream ribbed knit".\n\n` +
    `Only list what you can actually SEE, and be strict about it: an item you are not sure is there ` +
    `should be LEFT OUT. A phantom entry here becomes something the person is actually dressed in ` +
    `later, so omission is always the safer error. In particular do not list eyewear unless ` +
    `spectacles are plainly visible, and do not list jewellery unless you can point to it. ` +
    `Do not infer a garment that is implied but out of frame. ` +
    `If a person is wearing the items, list what they are wearing.\n\n` +
    `Never list a PART of a garment as its own item. A hood, drawstring, collar, cuff, button, zip, ` +
    `pocket, lapel, hem or lining belongs to the garment it is attached to — a hoodie with a drawstring ` +
    `is ONE item, not a top plus a neckwear. Only list neckwear when there is a genuinely separate ` +
    `scarf, tie or necklace.\n` +
    `List each slot at most once, except jewellery and bags where there may honestly be several.`;

  try {
    const resp = await fetch("https://fal.run/fal-ai/any-llm/vision", {
      method: "POST",
      headers: { Authorization: `Key ${FAL_KEY}`, "content-type": "application/json" },
      body: JSON.stringify({
        model: "google/gemini-pro-1.5",
        prompt,
        image_url: garment_url,
      }),
    });
    if (!resp.ok) return null;
    const js: any = await resp.json();
    const text = String(js?.output ?? js?.text ?? js?.response ?? "");
    const a = text.indexOf("{"), b = text.lastIndexOf("}");
    if (a < 0 || b <= a) return null;
    const parsed = JSON.parse(text.slice(a, b + 1));
    const items: Item[] = (Array.isArray(parsed?.items) ? parsed.items : [])
      .map((it: any) => ({
        slot: String(it?.slot ?? "").toLowerCase().trim(),
        name: String(it?.name ?? "").trim().slice(0, 60),
      }))
      .filter((it: Item) => it.slot.length > 0 && it.name.length > 0);

    // A structural slot can only be occupied once. Two "tops" or a top plus a
    // phantom "neckwear" read off the same hoodie is the model double-counting
    // one garment, and every phantom item becomes something the editor is told
    // to add. Jewellery and bags may honestly repeat.
    const multiOk = new Set(["jewellery", "bag"]);
    const seen = new Set<string>();
    const deduped = items.filter((it) => {
      if (multiOk.has(it.slot)) return true;
      if (seen.has(it.slot)) return false;
      seen.add(it.slot);
      return true;
    }).slice(0, 10);
    if (deduped.length === 0) return null;
    return { items: deduped, hasPerson: parsed?.has_person === true };
  } catch {
    return null;
  }
}

/** The aspect ratios `kontext` will accept. */
const RATIOS: Array<[string, number]> = [
  ["21:9", 21 / 9], ["16:9", 16 / 9], ["4:3", 4 / 3], ["3:2", 3 / 2],
  ["1:1", 1], ["2:3", 2 / 3], ["3:4", 3 / 4], ["9:16", 9 / 16], ["9:21", 9 / 21],
];

/**
 * Read a photograph's dimensions from its header alone.
 *
 * Needed because leaving `aspect_ratio` unset lets the editor choose, and it
 * chooses square: a portrait photo came back 1024x1024, reframed and with the
 * subject regenerated rather than edited. Pinning the ratio to the source is
 * what tells it there is a canvas to respect.
 *
 * Header-only on purpose. Decoding the image to ask its size would mean holding
 * a full phone photo in an edge function's memory and paying the CPU for it,
 * when the answer is 24 bytes in.
 */
async function sourceAspect(url: string): Promise<string | null> {
  try {
    const resp = await fetch(url, { headers: { Range: "bytes=0-65535" } });
    if (!resp.ok && resp.status !== 206) return null;
    const b = new Uint8Array(await resp.arrayBuffer());
    let w = 0, h = 0;

    if (b[0] === 0x89 && b[1] === 0x50) {
      // PNG: IHDR carries width then height as big-endian u32 at byte 16.
      const dv = new DataView(b.buffer);
      w = dv.getUint32(16);
      h = dv.getUint32(20);
    } else if (b[0] === 0xff && b[1] === 0xd8) {
      // JPEG: walk the marker chain to a start-of-frame, which is the only
      // segment that states the raster size.
      let i = 2;
      while (i + 9 < b.length) {
        if (b[i] !== 0xff) { i++; continue; }
        const marker = b[i + 1];
        const len = (b[i + 2] << 8) | b[i + 3];
        const isSof = marker >= 0xc0 && marker <= 0xcf &&
          marker !== 0xc4 && marker !== 0xc8 && marker !== 0xcc;
        if (isSof) {
          h = (b[i + 5] << 8) | b[i + 6];
          w = (b[i + 7] << 8) | b[i + 8];
          break;
        }
        i += 2 + len;
      }
    }
    if (!w || !h) return null;

    const target = w / h;
    let best = RATIOS[0];
    for (const r of RATIOS) {
      if (Math.abs(r[1] - target) < Math.abs(best[1] - target)) best = r;
    }
    return best[0];
  } catch {
    return null;
  }
}

/**
 * Lift the garments off whoever is wearing them.
 *
 * This is the answer to the actual complaint: an uploaded reference is usually a
 * photograph of a DIFFERENT PERSON in the outfit, and handing that straight to a
 * general image editor lets their face, hair, skin tone or body leak into the
 * result. Text-prompted segmentation cuts along the garments and drops
 * everything else, so there is no longer a person in the picture to leak.
 *
 * Done generatively rather than by segmentation, and that was measured rather
 * than assumed: `evf-sam` with `mask_only: false` does not cut the garment out,
 * it tints the matched region on top of the original frame — the wearer stays,
 * the background stays, and the garment's colour is destroyed. Worse than doing
 * nothing. Compositing a binary mask ourselves was the alternative, and that
 * means a per-pixel loop over a full-size phone photo inside an edge function,
 * which is how you get killed on CPU time after the user has already paid.
 *
 * Returns null on any failure; the caller then dresses from the raw reference,
 * exactly as it did before. An extraction problem must never cost a paid render.
 */
async function extractGarments(
  FAL_KEY: string,
  garment_url: string,
  items: Item[],
): Promise<string | null> {
  // Named, not described as "the clothing": the inventory has already read this
  // picture, and listing the actual garments is what stops the model deciding
  // for itself which of them count.
  const listed = items.map((it) => `- ${it.name}`).join("\n");

  const prompt =
    `Remove the person from this photograph completely and keep only what they are wearing.\n\n` +
    `Present these items as a clean flat-lay product photograph on a plain light grey background:\n` +
    `${listed}\n\n` +
    `Rules:\n` +
    `1. No person, no body, no mannequin, no face, no hands, no hair, no skin anywhere in the result. ` +
    `Nothing of the original background survives either.\n` +
    `2. Every item listed above must appear, laid out separately and fully visible — the small ones ` +
    `as much as the large. Do not drop any of them, and do not add a piece that is not on the list ` +
    `to round the set out.\n` +
    `3. Reproduce each item EXACTLY as photographed: the same colour, the same fabric, the same print ` +
    `or logo, the same seams, panels, hems, collar shape, hardware and proportions. Do not restyle, ` +
    `simplify, tidy, straighten or substitute anything. This is the same garment, photographed flat.\n` +
    `4. Where an item was partly hidden by an arm, a pocket or hair, complete it as the same garment ` +
    `plainly would be — do not invent a different cut.\n` +
    `5. Add nothing that was not on the person. No extra garments, no props, no packaging, no text.\n` +
    `6. Each listed item appears EXACTLY ONCE. Do not duplicate an item, do not lay out a second pair ` +
    `or an alternate version of it, and do not split one garment into two.\n\n` +
    `Even studio lighting, no harsh shadow, garments arranged with space between them.`;

  const r = await callFal(FAL_KEY, "fal-ai/flux-pro/kontext/max", {
    image_url: garment_url,
    prompt,
    num_images: 1,
    output_format: "png",
    safety_tolerance: "2",
    // Above the 3.5 default: this stage is judged on obedience to rule 3, not on
    // how pretty the result is, and adherence is exactly what the knob buys.
    guidance_scale: 5.0,
  });
  if (!r.ok) {
    console.log("extract_failed", JSON.stringify(r.error).slice(0, 300));
    return null;
  }
  return r.image_url;
}

/**
 * The whole-look path.
 *
 * The itemised list is load-bearing. Told to "put on this outfit" a model will
 * cheerfully add footwear that was never in the reference; told "these four
 * items, and no fifth", it has something concrete to be held to. The other half
 * is the leave-alone rule: a slot the reference says nothing about keeps
 * whatever the person already had on.
 */
async function runFluxOutfit(
  FAL_KEY: string,
  person_url: string,
  garment_url: string,
  items: Item[],
  /** True when the reference has already been cut down to garments only. */
  extracted: boolean,
  /** The source photograph's shape. Null lets the model reframe, which it does. */
  aspect: string | null,
): Promise<EngineResult> {
  const listed = items
    .map((it) => `- ${it.name} — ${SLOT_PLACEMENT[it.slot] ?? "in its natural position"}`)
    .join("\n");
  const covered = [...new Set(items.map((it) => it.slot.replace(/_/g, " ")))].join(", ");

  // Whichever of these is true, say it plainly. The failure this defends against
  // is the editor treating the reference as a person to copy rather than a rail
  // of clothes to take from — and when the reference still HAS a person in it,
  // a flat denial would be a lie the model can see through.
  const referenceNote = extracted
    ? `The second image is a reference plate: the garments alone, already cut away from ` +
      `whoever was wearing them. There is no person in it. Read the clothes from it and nothing else.\n\n`
    : `The second image is a reference. If a person appears in it, they are NOT the subject: ` +
      `ignore their face, hair, skin tone, body shape and pose entirely. They are only a rail for ` +
      `the clothes. Nothing about them may appear in the output.\n\n`;

  const prompt =
    `Edit the FIRST photograph. The output is that same photograph with different ` +
    `clothes on the person in it — same face, same framing, same background, same shot. ` +
    `Do not generate a new picture and do not put a different person in it.\n\n` +
    referenceNote +
    `That reference contains EXACTLY these ${items.length} item(s):\n${listed}\n\n` +
    `Dress the person in the first image in exactly these ${items.length} item(s) and nothing else.\n\n` +
    `Rules:\n` +
    `1. Put on EVERY item listed above. Do not omit any of them, including headwear, eyewear and footwear.\n` +
    `2. Do NOT add any garment or accessory that is not on that list. No invented shoes, jackets, bags, hats, ` +
    `jewellery, scarves or extra layers. If it is not in the list, it does not appear.\n` +
    `3. The listed items cover: ${covered}. For each of those, remove what the person is currently wearing there and ` +
    `replace it with the listed item.\n` +
    `4. Anything the list does not cover stays exactly as it is in the original photograph — do not restyle it, ` +
    `do not remove it, do not swap it.\n` +
    `5. Keep the FIRST image's person completely unchanged: their face, hair, skin tone, body ` +
    `proportions, pose and background stay exactly as photographed. The output is that person, ` +
    `never anyone else.\n` +
    `6. Fit each item to this person: match scale, angle and perspective to their pose, and match the lighting, ` +
    `contact shadows and highlights of the original photograph. Nothing pasted flat or cut out.\n\n` +
    `Photorealistic. Same framing as the original photograph.`;

  return await callFal(FAL_KEY, "fal-ai/flux-pro/kontext/max/multi", {
    prompt,
    image_urls: [person_url, garment_url],
    num_images: 1,
    output_format: "png",
    safety_tolerance: "2",
    ...(aspect ? { aspect_ratio: aspect } : {}),
    // The default 3.5 left the instruction to preserve the wearer as a
    // suggestion. This stage is judged on obedience, not on prettiness.
    guidance_scale: 5.0,
  });
}

type EngineResult =
  | { ok: true; image_url: string }
  | { ok: false; error: Record<string, unknown> };

/** One POST to Fal, with the response shapes every one of these models uses. */
async function callFal(
  FAL_KEY: string,
  endpoint: string,
  payload: Record<string, unknown>,
): Promise<EngineResult> {
  const resp = await fetch(`https://fal.run/${endpoint}`, {
    method: "POST",
    headers: { Authorization: `Key ${FAL_KEY}`, "content-type": "application/json" },
    body: JSON.stringify(payload),
  });
  const rawText = await resp.text();
  if (!resp.ok) {
    return {
      ok: false,
      error: { error: "fal_error", endpoint, status: resp.status, detail: rawText.slice(0, 800) },
    };
  }
  let js: any = null;
  try { js = JSON.parse(rawText); } catch {
    return { ok: false, error: { error: "fal_bad_json", endpoint, raw: rawText.slice(0, 500) } };
  }
  // Moderation returns HTTP 200 with a real URL pointing at a blacked-out
  // image. Left unread, the caller sees a URL, reports success, and the client
  // charges 20 credits for a black square — so a flagged result is a failure
  // here, not a result.
  const flagged = Array.isArray(js?.has_nsfw_concepts)
    ? js.has_nsfw_concepts.some(Boolean)
    : js?.has_nsfw_concepts === true;
  if (flagged) return { ok: false, error: { error: "moderated", endpoint } };

  const image_url = js?.images?.[0]?.url ?? js?.image?.url ?? js?.output ?? null;
  if (!image_url) return { ok: false, error: { error: "no_image", endpoint, raw: js } };
  return { ok: true, image_url };
}

/**
 * FLUX Virtual Try-On — the garment path.
 *
 * The prompt is required by the endpoint and is styling direction, not a
 * description of the garment: the model already has the garment as an image, so
 * spending the prompt describing it only invites it to redraw what it can see.
 */
/**
 * FLUX Virtual Try-On — the only stage that STRUCTURALLY guarantees the clothes
 * end up worn.
 *
 * This is the fix for the reported defect. A general image editor handed a
 * person and a garment plate will sometimes dress the person and sometimes lay
 * the garments out beside or below them, because combining two pictures onto one
 * canvas is what it does; no amount of prompt is a guarantee. A try-on model
 * cannot produce that image at all — its output is the human wearing the
 * reference, by construction.
 *
 * The whole-outfit case is supported here and was the thing being missed: the
 * schema states plainly that "multiple garments must be merged into a single
 * composite image before submission", and `extractGarments()` already builds
 * exactly that composite. It was being handed to the editor instead.
 *
 * Note there is NO category parameter on this endpoint — the earlier belief that
 * it took tops|bottoms|one-pieces came from FASHN and is what made a whole
 * outfit look impossible here. `fit` below is prose in the styling prompt, which
 * is what the endpoint actually asks for.
 */
async function runFluxVto(
  FAL_KEY: string,
  person_url: string,
  garment_url: string,
  /** Null for a whole-outfit plate; a FASHN slot when one garment is known. */
  category: FashnCat | null,
  items: Item[],
  /** One piece off a multi-item plate, by name. */
  only = "",
  /** Where the piece goes and how it should sit, when the slot is known. */
  slot: SlotSpec | null = null,
): Promise<EngineResult> {
  // A slot spec beats a category guess: it says where the thing goes and how it
  // has to be adapted to sit there, which is the whole difference between
  // glasses on a face and glasses across a waistband.
  const fit = slot
    ? `Put it ${slot.where}. ${slot.adapt}`
    : category === "bottoms"
    ? "Sit the waistband where it naturally falls and keep the original leg break and hem length."
    : category === "one-pieces"
    ? "Keep the original hem length and the way the fabric falls from the shoulder."
    : category
    ? "Keep the shoulder seam on the shoulder and the original sleeve and hem length."
    : "";

  // Naming the pieces is what stops a plate being read as "one garment": the
  // reference holds several, and the styling prompt is where that is said.
  // Naming a CATEGORY the list does not contain is an instruction to invent one.
  // An earlier version of this prompt said "…and the footwear, headwear and
  // eyewear too" unconditionally, and the model duly added spectacles to a man
  // who owned none. Only the pieces actually read out of the reference are ever
  // named, and the closing rule forbids everything else.
  const names = items.map((it) => it.name).join(", ");
  // Each piece told where it belongs. Naming them alone leaves placement to the
  // model, and placement is exactly what goes wrong — a hat rendered as a bag,
  // eyewear rendered on a waistband.
  const placed = items
    .map((it) => `the ${it.name} ${SLOT_PLACEMENT[it.slot] ?? "in its natural position"}`)
    .join("; ");
  const listed = only
    ? `The reference shows one piece: the ${only}. Put on ONLY that. ` +
      `Everything else the person is already wearing stays exactly as it is. ` +
      `Add nothing else whatsoever. `
    : items.length > 1
    ? `The reference is a flat lay of exactly ${items.length} pieces: ${names}. ` +
      `Put on every one of those ${items.length} and NOTHING else — ${placed}. ` +
      `Fit each one to this person properly: scale it to them, follow the angle of the body part ` +
      `it sits on, and let it wrap and fall as that object really would. Nothing pasted flat, ` +
      `nothing at the wrong size, nothing in the wrong place. ` +
      `Do not add any garment or accessory that is not in that list — nothing on the face, ` +
      `nothing on the head, nothing on the wrists, no extra layer — and do not invent one to ` +
      `complete the look. If it is not in the list, it does not appear. ` +
      `Where the list covers what the person is already wearing, replace it; everywhere else, ` +
      `leave their own clothing untouched. `
    : items.length === 1
    ? `The reference shows one piece: ${names}. Put on that and nothing else. ` +
      `Add no other garment and no accessory of any kind. `
    : "";

  return await callFal(FAL_KEY, "fal-ai/flux-pro/v1/vto", {
    human_image_url: person_url,
    garment_image_url: garment_url,
    prompt:
      `Change ONLY the clothing. ${listed}${fit} ` +
      `The person themselves must come through completely untouched: the same face, the same ` +
      `facial features and expression, the same eyes, the same hair and hairline, the same skin ` +
      `tone, the same beard or lack of one, the same body proportions, the same pose, the same ` +
      `hands, the same background and the same lighting. Do not restyle, beautify, slim, age or ` +
      `re-render the face. It is a photograph of this specific person and it stays that way. ` +
      `Match drape, contact shadows and highlights to the camera angle of that photograph. ` +
      `Photorealistic, same framing as the original.`,
    output_format: "png",
  });
}


async function runFashn(
  FAL_KEY: string,
  person_url: string,
  garment_url: string,
  category: FashnCat,
  mode: string,
) {
  const resp = await fetch("https://fal.run/fal-ai/fashn/tryon/v1.6", {
    method: "POST",
    headers: {
      Authorization: `Key ${FAL_KEY}`,
      "content-type": "application/json",
    },
    body: JSON.stringify({
      model_image: person_url,
      garment_image: garment_url,
      category,
      mode,
      num_samples: 1,
      output_format: "png",
    }),
  });
  const rawText = await resp.text();
  if (!resp.ok) {
    return {
      ok: false as const,
      error: { error: "fal_error", status: resp.status, detail: rawText.slice(0, 800) },
    };
  }
  let js: any = null;
  try { js = JSON.parse(rawText); } catch {
    return { ok: false as const, error: { error: "fal_bad_json", raw: rawText.slice(0, 500) } };
  }
  const image_url =
    js?.images?.[0]?.url ?? js?.image?.url ?? js?.output ?? null;
  if (!image_url) {
    return { ok: false as const, error: { error: "no_image", raw: js } };
  }
  return { ok: true as const, image_url };
}

/**
 * The accessory path — hats, glasses, jewellery, bags, shoes, watches.
 *
 * VTO takes a human and a garment and nothing else, so anything that is not a
 * garment still needs a general multi-image editor. This is FLUX Kontext [max]
 * in that role, replacing nano-banana.
 */
async function runFluxKontext(
  FAL_KEY: string,
  person_url: string,
  garment_url: string,
  slot: SlotSpec,
): Promise<EngineResult> {
  // Wrapper prompt: keep the person untouched, but *encourage* the model to
  // adapt the item to the person's pose/lighting/perspective rather than
  // pasting it verbatim.
  const prompt =
    `Edit the first image (the person) so they are naturally wearing the item shown in the second image, ${slot.where}. ` +
    `Keep the person's face, pose, body, hair, skin tone, existing clothing, and background exactly the same. ` +
    `Naturally fit the item to the person: adapt its scale, angle, and perspective to match the person's pose, head tilt, and face orientation. ` +
    `Match the lighting, shadows, highlights, and camera angle of the person photo — do not paste the item flat or as a cut-out. ` +
    `Keep the item recognizable (same rough color family, general silhouette, and distinctive details such as brand marks, patterns, or textures), but reshape and re-render it as needed so it looks worn on this specific person, not overlaid. ` +
    `${slot.adapt} ` +
    `Return a single photorealistic image with the same framing as the original person photo.`;

  return await callFal(FAL_KEY, "fal-ai/flux-pro/kontext/max/multi", {
    prompt,
    image_urls: [person_url, garment_url],
    num_images: 1,
    output_format: "png",
    // Kontext runs "1" (strictest) to "6"; image-input requests are capped at
    // "2" upstream regardless, so anything looser is a lie in the payload.
    safety_tolerance: "2",
  });
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });

  const auth = req.headers.get("Authorization") ?? "";
  if (!auth.toLowerCase().startsWith("bearer ")) {
    return json(401, { error: "unauthorized" });
  }

  const FAL_KEY = Deno.env.get("FAL_KEY");
  if (!FAL_KEY) return json(500, { error: "server_misconfigured" });

  let body: any = {};
  try { body = await req.json(); } catch { /* noop */ }

  const person_url = (body?.person_url ?? "").toString().trim();
  const garment_url = (body?.garment_url ?? "").toString().trim();
  const category = (body?.category ?? "auto").toString().trim();
  const mode = (body?.mode ?? "balanced").toString().trim();
  // Set when the wearer picked a single piece off the plate. The plate still
  // holds the whole look, so without this the isolate would re-dress all of it.
  const only = (body?.only ?? "").toString().trim().slice(0, 60);

  if (!person_url || !garment_url) {
    return json(400, { error: "missing_urls" });
  }

  // Isolating one piece off a look.
  //
  // Handing the whole plate to the try-on model and asking in words for "only
  // the trousers" does not work — measured, not assumed: it dressed the person
  // in the hoodie as well. A try-on model wears what it is shown. So the piece
  // gets its own plate cut from the original reference, and then there is
  // nothing else in the picture to put on.
  if (only) {
    const one: Item[] = [{ slot: category || "top", name: only }];
    const solo = await extractGarments(FAL_KEY, garment_url, one);
    const flux = await runFluxVto(FAL_KEY, person_url, solo ?? garment_url, null, one, only);
    if (flux.ok) {
      return json(200, {
        image_url: flux.image_url, version: "v6", engine: "flux-vto",
        items: one, plate: solo ? "extracted" : "failed", plate_url: solo,
      });
    }
    return json(502, flux.error);
  }

  // A Studio piece arrives already named ("hat", "shirt"), and those really are
  // single items — no reason to pay for a second opinion. An upload arrives as
  // "auto", which is precisely the case where the reference may be a whole look,
  // so that one gets inventoried.
  const named = category !== "" && category !== "auto" && category !== "outfit";
  const read = named ? null : await inventory(FAL_KEY, garment_url);

  if (read && read.items.length > 0) {
    const { items, hasPerson } = read;

    // The cut runs ALONGSIDE the dressing, not before it, and that is a real
    // architectural point rather than a speed trick.
    //
    // A try-on model does its own garment extraction — that is the job — and it
    // cannot put the reference's wearer into the output, because its output is
    // by construction the target human. So the dressing call does not need the
    // plate at all, and making it wait for one cost ~35s and blew the 150s
    // function timeout.
    //
    // The plate is still worth producing: it is the flat lay shown under the
    // result, and the thing the wearer picks single pieces from. It just has no
    // business being on the critical path.
    const platePromise = hasPerson
      ? extractGarments(FAL_KEY, garment_url, items)
      : Promise.resolve(null);

    // Inspect-only: returns the read and the cut without paying for a dressing
    // render. Exists so the extraction can be eyeballed rather than trusted.
    if (new URL(req.url).searchParams.get("plate_only") === "1") {
      const only = await platePromise;
      return json(200, {
        plate: hasPerson ? (only ? "extracted" : "failed") : "not_needed",
        plate_url: only,
        items,
        has_person: hasPerson,
      });
    }

    // Everything the inventory found goes through the try-on model, whether that
    // is one garment or a whole look. The plate IS the composite the endpoint
    // asks for, so there is no reason left to hand a whole outfit to an editor
    // that might lay it out beside the person instead of on them.
    const single = items.length === 1 ? VTO_SLOTS[items[0].slot] ?? null : null;
    const [flux, plate] = await Promise.all([
      runFluxVto(FAL_KEY, person_url, garment_url, single, items),
      platePromise,
    ]);
    const plateState = hasPerson ? (plate ? "extracted" : "failed") : "not_needed";
    const reference = plate ?? garment_url;
    if (flux.ok) {
      return json(200, {
        image_url: flux.image_url, version: "v6", engine: "flux-vto",
        items, plate: plateState, plate_url: plate,
      });
    }
    console.log("flux_vto_failed", JSON.stringify(flux.error).slice(0, 400));

    if (single) {
      const fashn = await runFashn(FAL_KEY, person_url, reference, single, mode);
      if (fashn.ok) {
        return json(200, {
          image_url: fashn.image_url, version: "v6", engine: "fashn",
          items, plate: plateState, plate_url: plate,
        });
      }
      console.log("fashn_failed", JSON.stringify(fashn.error).slice(0, 400));
    }

    // Last resort only. The editor is the thing that produced the pasted-beside
    // results, so it is no longer the primary path for a look — but a bad
    // minute at one provider should not cost a paid render.
    const aspect = await sourceAspect(person_url);
    const outfit = await runFluxOutfit(FAL_KEY, person_url, reference, items, plate !== null, aspect);
    if (!outfit.ok) return json(502, outfit.error);
    return json(200, {
      image_url: outfit.image_url,
      version: "v6",
      engine: "flux-outfit",
      items,
      plate: plateState,
      plate_url: plate,
    });
  }

  const decision = classify(category);

  if (decision.engine === "garment") {
    const fashnCategory = decision.fashnCategory ?? "auto";
    const flux = await runFluxVto(FAL_KEY, person_url, garment_url, fashnCategory, [], only);
    if (flux.ok) {
      return json(200, { image_url: flux.image_url, version: "v4", engine: "flux-vto" });
    }
    // The render is already paid for by the time we get here, so a provider
    // hiccup falls through to FASHN rather than costing the user their credits.
    console.log("flux_vto_failed", JSON.stringify(flux.error).slice(0, 400));
    const fashn = await runFashn(FAL_KEY, person_url, garment_url, fashnCategory, mode);
    if (!fashn.ok) return json(502, { ...fashn.error, flux_error: flux.error });
    return json(200, { image_url: fashn.image_url, version: "v4", engine: "fashn" });
  }

  // Accessories go through the try-on model as well.
  //
  // They used to go straight to the editor, and the editor did what an editor
  // does: a pair of sunglasses arrived pasted across the wearer's hips at four
  // times life size. The try-on model cannot produce that image — it puts things
  // ON the person — and given the slot's own placement prose it puts glasses on
  // the face, a hat on the head, a watch on the wrist, at the right scale and
  // angle. The editor is kept only as a fallback.
  const slot = decision.slot ?? {
    where: "in its natural, correct wearing position",
    adapt: "Place it with realistic scale, angle, perspective, contact shadow and lighting so it looks worn rather than overlaid.",
  };
  const viaVto = await runFluxVto(FAL_KEY, person_url, garment_url, null, [], only, slot);
  if (viaVto.ok) {
    return json(200, { image_url: viaVto.image_url, version: "v6", engine: "flux-vto" });
  }
  console.log("accessory_vto_failed", JSON.stringify(viaVto.error).slice(0, 300));

  const r = await runFluxKontext(FAL_KEY, person_url, garment_url, slot);
  if (!r.ok) return json(502, r.error);
  return json(200, { image_url: r.image_url, version: "v6", engine: "flux-kontext" });
});
