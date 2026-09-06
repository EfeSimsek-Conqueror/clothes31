package com.fitrater.app.ui.screens.studio

/**
 * Composition options for the Studio 2.0 outfit wizard — order of selection is
 * the generation order.
 *
 * Direct port of the iOS `OutfitPiece` enum (ios/FitScore/FitScore/Studio/
 * StudioCreateView.swift). Every string here — especially [placementInstruction]
 * and [promptToken] — is copied verbatim from iOS so both platforms feed the
 * image model identical prompts. Do NOT reword these.
 */
enum class OutfitPiece(val key: String) {
    // Tops
    TSHIRT("tshirt"),
    SHIRT("shirt"),
    HOODIE("hoodie"),
    CARDIGAN("cardigan"),
    BLAZER("blazer"),

    // Bottoms
    PANTS("pants"),
    JEANS("jeans"),
    SHORTS("shorts"),
    SKIRT("skirt"),
    SWEATSUIT("sweatsuit"),

    // Whole-body
    DRESS("dress"),

    // Outerwear
    JACKET("jacket"),

    // Footwear
    SHOES("shoes"),

    // Accessories (each with distinct placement)
    BAG("bag"),
    SUNGLASSES("sunglasses"),
    BELT("belt"),
    SCARF("scarf"),
    HAT("hat"),
    JEWELRY("jewelry"),
    ;

    val emoji: String
        get() = when (this) {
            TSHIRT -> "👕"
            SHIRT -> "👔"
            HOODIE -> "🧥"
            CARDIGAN -> "🧶"
            BLAZER -> "🧥"
            PANTS -> "👖"
            JEANS -> "👖"
            SHORTS -> "🩳"
            SKIRT -> "👗"
            SWEATSUIT -> "🩳"
            DRESS -> "👗"
            JACKET -> "🧥"
            SHOES -> "👟"
            BAG -> "👜"
            SUNGLASSES -> "🕶️"
            BELT -> "🎗️"
            SCARF -> "🧣"
            HAT -> "🎩"
            JEWELRY -> "💍"
        }

    /** Maps to StudioCreateScreen TYPES / closet categories. */
    val closetCategory: String
        get() = when (this) {
            TSHIRT, SHIRT, HOODIE, CARDIGAN, BLAZER -> "top"
            PANTS, JEANS, SHORTS, SKIRT, SWEATSUIT -> "bottom"
            DRESS -> "dress"
            JACKET -> "outerwear"
            SHOES -> "shoes"
            BAG, SUNGLASSES, BELT, SCARF, HAT, JEWELRY -> "accessory"
        }

    /**
     * The subcategory string used both to preset StudioCreateScreen's Step 1
     * AND to filter the closet picker by subtype (case-insensitive contains).
     */
    val subtypeHint: String
        get() = when (this) {
            TSHIRT -> "T-shirt"
            SHIRT -> "Shirt"
            HOODIE -> "Hoodie"
            CARDIGAN -> "Cardigan"
            BLAZER -> "Blazer"
            PANTS -> "Trousers"
            JEANS -> "Jeans"
            SHORTS -> "Shorts"
            SKIRT -> "Skirt"
            SWEATSUIT -> "Sweats"
            DRESS -> "Midi"
            JACKET -> "Blazer"
            SHOES -> "Sneakers"
            BAG -> "Bag"
            SUNGLASSES -> "Sunglasses"
            BELT -> "Belt"
            SCARF -> "Scarf"
            HAT -> "Hat"
            JEWELRY -> "Jewelry"
        }

    /** Short natural-language token used inside prompts. */
    val promptToken: String
        get() = when (this) {
            TSHIRT -> "t-shirt"
            SHIRT -> "button-up shirt"
            HOODIE -> "hoodie"
            CARDIGAN -> "cardigan"
            BLAZER -> "blazer"
            PANTS -> "trousers"
            JEANS -> "jeans"
            SHORTS -> "shorts"
            SKIRT -> "skirt"
            SWEATSUIT -> "matching sweatsuit"
            DRESS -> "dress"
            JACKET -> "jacket"
            SHOES -> "pair of shoes"
            BAG -> "handbag"
            SUNGLASSES -> "pair of sunglasses"
            BELT -> "belt"
            SCARF -> "scarf"
            HAT -> "hat"
            JEWELRY -> "jewelry piece"
        }

    /** Maps to `StudioCreateScreen.presetType` (one of TYPES at that file's top). */
    val presetType: String
        get() = when (this) {
            TSHIRT, SHIRT, HOODIE, CARDIGAN, BLAZER -> "Top"
            PANTS, JEANS, SHORTS, SKIRT, SWEATSUIT -> "Bottom"
            DRESS -> "Dress"
            JACKET -> "Outerwear"
            SHOES -> "Shoes"
            BAG, SUNGLASSES, BELT, SCARF, HAT, JEWELRY -> "Accessory"
        }

    /** Localized display label (English) used in the wizard UI. */
    val displayName: String
        get() = when (this) {
            TSHIRT -> "T-Shirt"
            SHIRT -> "Shirt"
            HOODIE -> "Hoodie"
            CARDIGAN -> "Cardigan"
            BLAZER -> "Blazer"
            PANTS -> "Trousers"
            JEANS -> "Jeans"
            SHORTS -> "Shorts"
            SKIRT -> "Skirt"
            SWEATSUIT -> "Sweatsuit"
            DRESS -> "Dress"
            JACKET -> "Jacket"
            SHOES -> "Shoes"
            BAG -> "Bag"
            SUNGLASSES -> "Sunglasses"
            BELT -> "Belt"
            SCARF -> "Scarf"
            HAT -> "Hat"
            JEWELRY -> "Jewelry"
        }

    /**
     * How this piece is worn/held on a HEADLESS mannequin. Used by the combine
     * prompt so the model puts each item in the right place. Verbatim from iOS.
     */
    val placementInstruction: String?
        get() = when (this) {
            // worn on torso — default layering handles it
            TSHIRT, SHIRT, HOODIE, CARDIGAN, BLAZER, JACKET, DRESS, SWEATSUIT -> null
            // worn on legs — default handles it
            PANTS, JEANS, SHORTS, SKIRT -> null
            SHOES -> "shoes on the feet, full footwear visible"
            BAG -> "the bag is HELD by the strap or hanging from the shoulder — clearly rendered, not hidden"
            SUNGLASSES ->
                "the sunglasses HANG from the neckline of the top garment or from the mannequin's collar area " +
                    "(mannequin is headless — do NOT attempt to place them on a face)"
            BELT -> "the belt is worn at the natural waist, threaded through belt loops of any bottom or laid over any dress"
            SCARF -> "the scarf is draped around the neck stump and shoulders"
            HAT ->
                "the hat is HELD in the mannequin's hand at hip level " +
                    "(mannequin is headless — do NOT attempt to place it on a head)"
            JEWELRY ->
                "jewelry is placed on appropriate joints: necklace at the neck stump, " +
                    "bracelets/watches on wrists, rings on fingers"
        }

    /** Groups pieces for the picker grid. */
    val group: String
        get() = when (this) {
            TSHIRT, SHIRT, HOODIE, CARDIGAN, BLAZER -> "Tops"
            PANTS, JEANS, SHORTS, SKIRT, SWEATSUIT -> "Bottoms"
            DRESS, JACKET -> "One-piece & Outerwear"
            SHOES -> "Footwear"
            BAG, SUNGLASSES, BELT, SCARF, HAT, JEWELRY -> "Accessories"
        }
}

/** Ordered groups for the composer picker — mirrors iOS `pieceGroups`. */
data class OutfitPieceGroup(val name: String, val pieces: List<OutfitPiece>)

val OUTFIT_PIECE_GROUPS: List<OutfitPieceGroup> = listOf(
    OutfitPieceGroup(
        "Tops",
        listOf(OutfitPiece.TSHIRT, OutfitPiece.SHIRT, OutfitPiece.HOODIE, OutfitPiece.CARDIGAN, OutfitPiece.BLAZER),
    ),
    OutfitPieceGroup(
        "Bottoms",
        listOf(OutfitPiece.PANTS, OutfitPiece.JEANS, OutfitPiece.SHORTS, OutfitPiece.SKIRT, OutfitPiece.SWEATSUIT),
    ),
    OutfitPieceGroup("One-piece & Outerwear", listOf(OutfitPiece.DRESS, OutfitPiece.JACKET)),
    OutfitPieceGroup("Footwear", listOf(OutfitPiece.SHOES)),
    OutfitPieceGroup(
        "Accessories",
        listOf(
            OutfitPiece.BAG,
            OutfitPiece.SUNGLASSES,
            OutfitPiece.BELT,
            OutfitPiece.SCARF,
            OutfitPiece.HAT,
            OutfitPiece.JEWELRY,
        ),
    ),
)
