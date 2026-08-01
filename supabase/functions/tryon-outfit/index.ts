// deno-lint-ignore-file no-explicit-any
// tryon-outfit v3: garments → Fal FASHN (purpose-built try-on);
// accessories (hats, glasses, jewelry, bags, shoes, watches) → Fal nano-banana
// image edit with a targeted "put this on the person" prompt that prioritizes
// natural adaptation (angle, scale, perspective, lighting) over strict
// pixel-preservation of the item.
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

type SlotSpec = {
  // Short "where it goes" phrase (used in the wrapper prompt).
  where: string;
  // Slot-specific *natural adaptation* instructions. Encourages the model to
  // reshape, resize, and re-light the item to fit the person — not paste it.
  adapt: string;
};

// Classify a raw client category token into an engine + hint.
function classify(raw: string): {
  engine: "fashn" | "nano";
  fashnCategory?: FashnCat;
  slot?: SlotSpec;
} {
  const c = raw.toLowerCase().trim();

  // Direct FASHN tokens.
  if (c === "tops" || c === "bottoms" || c === "one-pieces") {
    return { engine: "fashn", fashnCategory: c as FashnCat };
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
  if (tops.has(c)) return { engine: "fashn", fashnCategory: "tops" };
  if (bottoms.has(c)) return { engine: "fashn", fashnCategory: "bottoms" };
  if (onePieces.has(c)) return { engine: "fashn", fashnCategory: "one-pieces" };

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
  if (slotMap[c]) return { engine: "nano", slot: slotMap[c] };

  if (c === "accessory" || c === "accessories" || c === "other") {
    return {
      engine: "nano",
      slot: {
        where: "in its natural, correct wearing position",
        adapt:
          "Place the item on the correct body part with realistic scale, angle, perspective, contact shadow, and lighting so it looks worn — not overlaid.",
      },
    };
  }
  return { engine: "fashn", fashnCategory: "auto" };
}

async function runFashn(
  FAL_KEY: string,
  person_url: string,
  garment_url: string,
  category: FashnCat,
  mode: string,
) {
  const resp = await fetch("https://fal.run/fal-ai/fashn/tryon/v1.5", {
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

async function runNano(
  FAL_KEY: string,
  person_url: string,
  garment_url: string,
  slot: SlotSpec,
) {
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

  const resp = await fetch("https://fal.run/fal-ai/nano-banana/edit", {
    method: "POST",
    headers: {
      Authorization: `Key ${FAL_KEY}`,
      "content-type": "application/json",
    },
    body: JSON.stringify({
      prompt,
      image_urls: [person_url, garment_url],
      num_images: 1,
      output_format: "png",
      sync_mode: false,
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
  const image_url = js?.images?.[0]?.url ?? js?.image?.url ?? null;
  if (!image_url) {
    return { ok: false as const, error: { error: "no_image", raw: js } };
  }
  return { ok: true as const, image_url };
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

  if (!person_url || !garment_url) {
    return json(400, { error: "missing_urls" });
  }

  const decision = classify(category);

  if (decision.engine === "fashn") {
    const r = await runFashn(
      FAL_KEY, person_url, garment_url,
      decision.fashnCategory ?? "auto", mode,
    );
    if (!r.ok) return json(502, r.error);
    return json(200, { image_url: r.image_url, version: "v3", engine: "fashn" });
  }

  const r = await runNano(
    FAL_KEY, person_url, garment_url,
    decision.slot ?? {
      where: "in the correct place",
      adapt: "Place with realistic scale, perspective, shadow, and lighting.",
    },
  );
  if (!r.ok) return json(502, r.error);
  return json(200, { image_url: r.image_url, version: "v3", engine: "nano-banana" });
});
