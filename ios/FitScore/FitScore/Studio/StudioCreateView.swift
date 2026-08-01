import SwiftUI
import PhotosUI
import Supabase

// MARK: - Constants and option pools (ported 1:1 from Android StudioCreateScreen.kt)

private let GEN_COST = 15

private let TYPES = ["Top", "Bottom", "Outerwear", "Dress", "Shoes", "Accessory"]

private let SUBTYPES: [String: [String]] = [
    "Top": ["T-shirt", "Hoodie", "Blazer", "Shirt", "Tank", "Polo", "Cardigan", "Turtleneck"],
    "Bottom": ["Trousers", "Jeans", "Shorts", "Skirt", "Cargo", "Sweats", "Chinos"],
    "Outerwear": ["Bomber", "Trench", "Blazer", "Puffer", "Denim jacket", "Moto", "Overcoat", "Vest"],
    "Dress": ["Mini", "Midi", "Maxi", "Slip", "Wrap", "Shirt-dress"],
    "Shoes": ["Sneakers", "Loafers", "Boots", "Heels", "Sandals", "Derbies"],
    "Accessory": ["Bag", "Belt", "Scarf", "Hat", "Jewelry", "Sunglasses"],
]

private let SILHOUETTES = ["Slim", "Regular", "Oversized", "Cropped", "Boxy", "Drape", "Wide", "Relaxed", "Tailored", "Deconstructed"]
private let SLEEVE = ["Sleeveless", "Short", "3/4", "Long", "Extra-long"]
private let BOTTOM_LENGTH = ["Micro", "Short", "Above-knee", "Below-knee", "Ankle", "Full", "Puddle"]
private let DRESS_LENGTH = ["Mini", "Above-knee", "Midi", "Below-knee", "Ankle", "Maxi"]

private let DETAILS_DEFAULT = [
    "Pocket", "Hood", "Zip", "Buttons", "Embroidery", "Print", "Distress", "Patchwork",
    "Drawstring", "Contrast piping", "Ribbed", "Quilted", "Fringe", "Lace", "Mesh", "Panel-blocking",
]
private let DETAILS_SHOES = [
    "Laces", "Buckles", "Straps", "Zipper", "Perforations", "Contrast sole",
    "Stitched welt", "Metal tips", "Rubber toe", "Chunky sole", "Platform", "Cleated grip",
]
private let DETAILS_ACCESSORY = [
    "Metal hardware", "Buckle", "Chain strap", "Woven strap", "Monogram print",
    "Embossed logo", "Contrast stitching", "Tassel", "Fringe", "Studs", "Pearl", "Braided",
]
private func detailsFor(_ type: String?) -> [String] {
    switch type {
    case "Shoes": return DETAILS_SHOES
    case "Accessory": return DETAILS_ACCESSORY
    default: return DETAILS_DEFAULT
    }
}

private let SHOE_SOLES = ["Flat", "Chunky", "Block", "Stiletto", "Platform", "Lug", "Wedge", "Cleated"]
private let SHOE_TOES = ["Round", "Pointed", "Square", "Almond", "Open", "Cap"]
private let SHOE_HEIGHTS = ["Ankle", "Mid-calf", "Knee", "Thigh", "Flat", "2cm", "5cm", "8cm", "10cm+"]
private let SHOE_CLOSURES = ["Laces", "Slip-on", "Zip", "Buckle", "Velcro", "Elastic"]

private let BAG_TYPES = ["Tote", "Crossbody", "Clutch", "Backpack", "Bucket", "Hobo", "Satchel"]
private let BAG_SIZES = ["Mini", "Small", "Medium", "Large", "Oversized"]
private let BAG_STRAPS = ["Chain", "Leather", "Woven", "Adjustable", "No strap"]
private let BAG_CLOSURES = ["Zip", "Magnetic", "Drawstring", "Buckle", "Open"]
private let BAG_HARDWARE = ["Gold", "Silver", "Matte black", "None"]

private func subtypeNeedsShoeHeight(_ subtype: String?) -> Bool {
    subtype == "Boots" || subtype == "Heels"
}

struct ColorPreset {
    let name: String
    let hex: String
}

private let COLOR_PRESETS: [ColorPreset] = [
    .init(name: "Bone", hex: "#EAE1D0"), .init(name: "Khaki", hex: "#8B7A56"), .init(name: "Cocoa", hex: "#5B3A22"),
    .init(name: "Ink", hex: "#141210"), .init(name: "Bronze", hex: "#B0743A"), .init(name: "Rust", hex: "#9E4A22"),
    .init(name: "Sage", hex: "#93A187"), .init(name: "Cream", hex: "#F3EEE4"), .init(name: "Charcoal", hex: "#3A3833"),
    .init(name: "Olive", hex: "#5C6032"), .init(name: "Butter", hex: "#EED89A"), .init(name: "Terracotta", hex: "#B25B3A"),
    .init(name: "Slate", hex: "#5A6B73"), .init(name: "Wine", hex: "#5A1E29"), .init(name: "Peach", hex: "#F0B39B"),
    .init(name: "Powder-blue", hex: "#B7C9D9"), .init(name: "Sand", hex: "#D7C4A3"), .init(name: "Forest", hex: "#264534"),
]

private let FABRICS_DEFAULT = [
    "Cotton", "Linen", "Denim", "Wool", "Leather", "Silk", "Knit", "Cashmere",
    "Nylon", "Velvet", "Tweed", "Gabardine", "Corduroy", "Suede", "Satin", "Jersey",
]
private let FABRICS_SHOES = [
    "Leather", "Suede", "Canvas", "Mesh", "Rubber", "Patent leather", "Nubuck", "Knit", "Vegan leather",
]
private let FABRICS_ACCESSORY = [
    "Leather", "Suede", "Canvas", "Nylon", "Silk", "Wool", "Cotton", "Metal", "Beaded", "Straw",
]
private func fabricsFor(_ type: String?) -> [String] {
    switch type {
    case "Shoes": return FABRICS_SHOES
    case "Accessory": return FABRICS_ACCESSORY
    default: return FABRICS_DEFAULT
    }
}
private let TEXTURES = ["Matte", "Glossy", "Heather", "Washed", "Coated", "Ribbed", "Slub"]
private let OCCASIONS = ["Everyday", "Work", "Date", "Wedding", "Gym", "Travel", "Party", "Formal", "Loungewear"]
private let SEASONS = ["Spring", "Summer", "Fall", "Winter", "All-year"]

private let PROMPT_ADDONS = [
    "Add embroidery on chest",
    "Longer sleeves",
    "Contrast stitching",
    "Vintage wash",
    "Modern minimal cut",
    "Feminine drape",
    "Streetwear proportions",
]

// MARK: - Variation axis

enum VaryAxis: String, CaseIterable {
    case colors, silhouette, fabric, details, surprise
    var title: String {
        switch self {
        case .colors: return "Colors"
        case .silhouette: return "Silhouette"
        case .fabric: return "Fabric"
        case .details: return "Details"
        case .surprise: return "Surprise me"
        }
    }
    var subtitle: String {
        switch self {
        case .colors: return "Same cut, three new palettes."
        case .silhouette: return "Same colors, three different fits."
        case .fabric: return "Same shape, three material stories."
        case .details: return "Same base, three added notes."
        case .surprise: return "Let Hem freewheel across every axis."
        }
    }
}

// MARK: - Wizard state

@Observable
final class WizardState {
    var type: String? = nil
    var subtype: String? = nil
    var silhouette: String? = nil
    var sleeveOrLength: String? = nil
    var details: [String] = []
    var colors: [String] = []
    var fabrics: [String] = []
    var texture: String? = nil
    var occasion: String = "Everyday"
    var season: String = "All-year"
    var freeText: String = ""
    var referenceUrl: String? = nil
    // Shoes
    var sole: String? = nil
    var toe: String? = nil
    var shoeHeight: String? = nil
    // Shared shoes + bag
    var closureType: String? = nil
    // Bag
    var bagType: String? = nil
    var bagSize: String? = nil
    var strap: String? = nil
    var hardware: String? = nil
}

private struct WizardSnapshot {
    let type: String?
    let subtype: String?
    var silhouette: String?
    let sleeveOrLength: String?
    var details: [String]
    var colors: [String]
    var fabrics: [String]
    let texture: String?
    let occasion: String
    let season: String
    let freeText: String
    let referenceUrl: String?
    let mannequinGender: String
    let prefFabrics: [String]
    let prefColors: [String]
    let sole: String?
    let toe: String?
    let shoeHeight: String?
    let closureType: String?
    let bagType: String?
    let bagSize: String?
    let strap: String?
    let hardware: String?
}

private extension WizardState {
    func snapshot(mannequin: String, prefFabrics: [String], prefColors: [String]) -> WizardSnapshot {
        WizardSnapshot(
            type: type, subtype: subtype, silhouette: silhouette,
            sleeveOrLength: sleeveOrLength, details: details, colors: colors,
            fabrics: fabrics, texture: texture, occasion: occasion, season: season,
            freeText: freeText, referenceUrl: referenceUrl,
            mannequinGender: mannequin, prefFabrics: prefFabrics, prefColors: prefColors,
            sole: sole, toe: toe, shoeHeight: shoeHeight, closureType: closureType,
            bagType: bagType, bagSize: bagSize, strap: strap, hardware: hardware
        )
    }
}

// MARK: - Prompt rendering

private func renderPrompt(_ s: WizardSnapshot) -> String {
    let subtypeToken = (s.subtype ?? s.type ?? "garment").lowercased()
    let silhouettePart = s.silhouette?.lowercased() ?? "well-cut"
    let colorPart: String = {
        let arr = s.colors.map { $0.lowercased() }
        return arr.isEmpty ? "neutral" : arr.joined(separator: " and ")
    }()
    let fabricPart: String = {
        let arr = s.fabrics.map { $0.lowercased() }
        return arr.isEmpty ? "cotton" : arr.joined(separator: " / ")
    }()
    let texturePart = s.texture.map { " with \($0.lowercased()) finish" } ?? ""
    let detailsPart = s.details.isEmpty ? "" : ", " + s.details.map { $0.lowercased() }.joined(separator: ", ")
    let lenPart = s.sleeveOrLength.map { ", \($0.lowercased()) length" } ?? ""
    let occasion = s.occasion.lowercased()
    let season = s.season.lowercased()
    let trailing = s.freeText.trimmingCharacters(in: .whitespaces).isEmpty ? "" : " \(s.freeText.trimmingCharacters(in: .whitespaces))"
    var hintPieces: [String] = []
    if s.colors.isEmpty && !s.prefColors.isEmpty {
        hintPieces.append("User leans toward \(s.prefColors.map { $0.lowercased() }.joined(separator: ", "))")
    }
    if s.fabrics.isEmpty && !s.prefFabrics.isEmpty {
        hintPieces.append("prefers \(s.prefFabrics.map { $0.lowercased() }.joined(separator: ", "))")
    }
    let prefHint = hintPieces.isEmpty ? "" : " " + hintPieces.joined(separator: ", ") + "."

    switch s.type {
    case "Shoes":
        let sole = s.sole?.lowercased() ?? "flat"
        let toe = s.toe?.lowercased() ?? "round"
        let heightPart = s.shoeHeight.map { " with \($0.lowercased()) height" } ?? ""
        let closure = s.closureType?.lowercased() ?? "slip-on"
        let subtypeShoe = s.subtype?.lowercased() ?? "shoe"
        return "Studio product photograph of a pair of \(subtypeShoe) with \(sole) sole, " +
            "\(toe) toe\(heightPart), \(closure) closure, in \(colorPart) tones, \(fabricPart)\(texturePart)\(detailsPart), " +
            "for \(occasion) \(season) wear. " +
            "Photographed at ground level from a 3/4 side angle. " +
            "Isolated on a clean cream studio backdrop — NO person, NO mannequin, NO legs, NO pants, " +
            "NO other garments in frame. Only the pair of shoes. Soft diffused studio lighting, " +
            "natural contact shadow, sharp material and stitching detail, editorial catalog aesthetic.\(trailing)\(prefHint)"
    case "Accessory":
        if s.subtype == "Bag" {
            let bt = s.bagType?.lowercased() ?? "tote"
            let bs = s.bagSize?.lowercased() ?? "medium"
            let strap = s.strap?.lowercased() ?? "leather"
            let closure = s.closureType?.lowercased() ?? "zip"
            let hardware = s.hardware?.lowercased() ?? "gold"
            return "Studio product photograph of a \(bs) \(bt) bag with \(strap) strap and \(closure) closure, " +
                "\(hardware) hardware, in \(colorPart) tones, \(fabricPart)\(texturePart)\(detailsPart), " +
                "for \(occasion) \(season) wear. " +
                "Isolated on a clean cream studio backdrop — NO person, NO mannequin, NO other garments in frame. " +
                "Only the bag itself, elegantly presented. Soft diffused studio lighting, subtle floor shadow, " +
                "sharp material and hardware detail, editorial catalog aesthetic.\(trailing)\(prefHint)"
        } else {
            return "Studio product photograph of a single \(subtypeToken) " +
                "in \(colorPart) tones, \(fabricPart)\(texturePart)\(detailsPart), for \(occasion) \(season) wear. " +
                "Isolated on a clean cream studio backdrop — NO person, NO mannequin, NO other garments in frame. " +
                "Only the accessory itself, elegantly presented. Soft diffused studio lighting, subtle shadow, " +
                "sharp material and hardware detail, editorial catalog aesthetic.\(trailing)\(prefHint)"
        }
    default:
        return "Editorial fashion photograph of a \(silhouettePart) \(subtypeToken) in \(colorPart) tones, " +
            "\(fabricPart)\(texturePart)\(detailsPart)\(lenPart), for \(occasion) \(season) wear — " +
            "worn on a minimalist headless matte-white \(s.mannequinGender)-form mannequin against a clean cream studio backdrop, " +
            "front-facing, magazine editorial styling, natural studio lighting, sharp fabric detail.\(trailing)\(prefHint)"
    }
}

private func colorVariations(_ originals: [String]) -> [[String]] {
    let base = originals.first?.lowercased() ?? "neutral"
    let moody = ["Ink", "Bronze"]
    let fresh = ["Sage", "Cream"]
    let warm = ["Rust", "Charcoal"]
    if base.contains("bone") || base.contains("cream") || base.contains("sand") {
        return [moody, fresh, warm]
    } else if base.contains("ink") || base.contains("charcoal") {
        return [fresh, ["Cocoa", "Bone"], ["Slate", "Cream"]]
    } else if base.contains("cocoa") || base.contains("bronze") || base.contains("rust") {
        return [fresh, moody, ["Sand", "Ink"]]
    }
    return [moody, fresh, warm]
}

private let SILHOUETTE_ALTS = ["Oversized", "Cropped", "Boxy", "Wide", "Tailored", "Relaxed"]
private let FABRIC_ALTS = ["Linen", "Denim", "Wool", "Silk", "Knit", "Leather"]
private let DETAIL_ALTS: [[String]] = [
    ["Contrast stitching"],
    ["Pocket", "Drawstring"],
    ["Embroidery"],
    ["Panel-blocking"],
    ["Ribbed"],
]

private func buildPromptFor(_ snap: WizardSnapshot, variationIndex i: Int, axis: VaryAxis) -> String {
    var s = snap
    switch axis {
    case .colors:
        let palettes = colorVariations(snap.colors)
        s.colors = palettes[i % palettes.count]
    case .silhouette:
        let cur = snap.silhouette
        let pool = SILHOUETTE_ALTS.filter { $0.caseInsensitiveCompare(cur ?? "") != .orderedSame }
        s.silhouette = pool[i % pool.count]
    case .fabric:
        let cur = snap.fabrics.first
        let pool = FABRIC_ALTS.filter { $0.caseInsensitiveCompare(cur ?? "") != .orderedSame }
        s.fabrics = [pool[i % pool.count]]
    case .details:
        let add = DETAIL_ALTS[i % DETAIL_ALTS.count]
        var merged = snap.details
        for d in add where !merged.contains(d) { merged.append(d) }
        s.details = merged
    case .surprise:
        let ordered: [VaryAxis] = [.colors, .silhouette, .fabric, .details]
        return buildPromptFor(snap, variationIndex: i, axis: ordered[i % ordered.count])
    }
    return renderPrompt(s)
}

// MARK: - Steps definition

/// Returns the ordered step IDs applicable to the current type/subtype.
private func applicableSteps(type: String?, subtype: String?) -> [Int] {
    switch type {
    case "Shoes":
        var head: [Int] = [1, 10, 11]
        if subtypeNeedsShoeHeight(subtype) { head.append(12) }
        head.append(13)
        head.append(contentsOf: [4, 5, 6, 7, 8, 9])
        return head
    case "Accessory":
        if subtype == "Bag" {
            return [1, 14, 15, 16, 17, 18, 5, 6, 7, 8, 9]
        }
        return [1, 4, 5, 6, 7, 8, 9]
    default:
        return Array(1...9)
    }
}

// MARK: - Root

struct StudioCreateView: View {
    let editingPieceId: String?
    let presetType: String?
    let presetReferenceUrl: String?
    let onDone: () -> Void

    @Environment(\.dismiss) private var dismiss

    @State private var state = WizardState()
    @State private var stepIndex = 0
    @State private var mannequinGender = "androgynous"
    @State private var prefFabrics: [String] = []
    @State private var prefColors: [String] = []

    @State private var referenceUploading = false
    @State private var referenceItem: PhotosPickerItem? = nil

    @State private var generating = false
    @State private var generatedUrl: String? = nil
    @State private var variations: [VariationResult] = []
    @State private var generatingVariations = false
    @State private var showAxisSheet = false
    @State private var error: String? = nil
    @State private var addingBusy = false
    @State private var addedOk = false
    // Session-scoped dedup
    @State private var savedJournalUrls: Set<String> = []

    private var currentStep: Int {
        let steps = applicableSteps(type: state.type, subtype: state.subtype)
        return steps.indices.contains(stepIndex) ? steps[stepIndex] : steps.last ?? 1
    }
    private var steps: [Int] { applicableSteps(type: state.type, subtype: state.subtype) }

    var body: some View {
        Group {
            if let url = generatedUrl {
                resultScreen(heroUrl: url)
            } else if generating {
                generatingScreen
            } else {
                wizardShell
            }
        }
        .background(Palette.paper.ignoresSafeArea())
        .task {
            if let p = try? await Repo.shared.currentProfile() {
                switch p.gender {
                case "Female": mannequinGender = "female"
                case "Male": mannequinGender = "male"
                default: mannequinGender = "androgynous"
                }
                prefFabrics = p.preferred_fabrics ?? []
                prefColors = p.preferred_colors ?? []
            }
            // Seed presets
            if let t = presetType, state.type == nil { state.type = t }
            if let r = presetReferenceUrl, state.referenceUrl == nil { state.referenceUrl = r }
        }
        .onChange(of: referenceItem) { _, new in
            guard let new else { return }
            uploadReference(item: new)
        }
        .sheet(isPresented: $showAxisSheet) {
            VariationAxisSheet { axis in
                showAxisSheet = false
                Task { await runVariations(axis: axis) }
            }
            .presentationDetents([.medium, .large])
        }
    }

    // MARK: - Wizard shell

    @ViewBuilder
    private var wizardShell: some View {
        VStack(spacing: 0) {
            // Progress hairline
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Rectangle().fill(Palette.hairline)
                    Rectangle()
                        .fill(Palette.bronze)
                        .frame(width: geo.size.width * progressFraction)
                }
            }
            .frame(height: 2)

            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    HStack(spacing: 6) {
                        Button {
                            if stepIndex == 0 { dismiss() } else { stepIndex -= 1 }
                        } label: {
                            Image(systemName: "arrow.left")
                                .foregroundStyle(Palette.ink)
                                .padding(6)
                        }
                        Eyebrow(text: "STEP \(stepIndex + 1) OF \(steps.count)")
                    }
                    .padding(.top, 12)

                    Text(editingPieceId != nil ? "Edit piece" : "Guided design")
                        .font(Serif.display(28))
                        .foregroundStyle(Palette.ink)
                        .padding(.top, 8)
                        .padding(.bottom, 20)

                    stepView(id: currentStep)
                        .id(currentStep)
                        .transition(.asymmetric(
                            insertion: .move(edge: .trailing).combined(with: .opacity),
                            removal: .move(edge: .leading).combined(with: .opacity)
                        ))
                        .animation(.spring(response: 0.35, dampingFraction: 0.85), value: currentStep)

                    if let error {
                        Text(error)
                            .font(Serif.body(13))
                            .foregroundStyle(Palette.bronze)
                            .padding(.top, 16)
                    }
                    Spacer(minLength: 40)
                }
                .padding(.horizontal, 20)
            }
            .onTapGesture { hideKeyboard() }

            bottomBar
        }
    }

    private var progressFraction: CGFloat {
        guard steps.count > 0 else { return 0 }
        return CGFloat(min(max(stepIndex + 1, 1), steps.count)) / CGFloat(steps.count)
    }

    // MARK: - Bottom bar

    private var bottomBar: some View {
        VStack(spacing: 0) {
            Hairline()
            HStack {
                Button {
                    if stepIndex == 0 { dismiss() } else { stepIndex -= 1 }
                } label: {
                    Text("Back")
                        .font(.system(size: 14, weight: .medium))
                        .foregroundStyle(Palette.ink)
                        .padding(.horizontal, 18).padding(.vertical, 10)
                        .overlay(Capsule().stroke(Palette.ink.opacity(0.5), lineWidth: 1))
                        .clipShape(Capsule())
                }
                Spacer()
                HStack(alignment: .lastTextBaseline, spacing: 0) {
                    Text("\(stepIndex + 1)")
                        .font(Serif.display(22))
                        .foregroundStyle(Palette.bronze)
                    Text("/\(steps.count)")
                        .font(Serif.body(14))
                        .foregroundStyle(Palette.muted)
                }
                Spacer()
                if currentStep == 9 {
                    let label = (state.referenceUrl?.isEmpty == false)
                        ? "Generate variation · \(GEN_COST)"
                        : "Generate · \(GEN_COST)"
                    Button {
                        Task { await runGeneration() }
                    } label: {
                        Text(label)
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(.white)
                            .padding(.horizontal, 18).padding(.vertical, 10)
                            .background(Palette.ink)
                            .clipShape(Capsule())
                    }
                } else {
                    let ok = canAdvance()
                    Button {
                        if ok { stepIndex += 1 }
                    } label: {
                        Text("Next")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(.white)
                            .padding(.horizontal, 18).padding(.vertical, 10)
                            .background(ok ? Palette.ink : Palette.muted)
                            .clipShape(Capsule())
                    }
                    .disabled(!ok)
                }
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 12)
            .background(Palette.paper)
        }
    }

    private func canAdvance() -> Bool {
        switch currentStep {
        case 1: return state.type != nil && state.subtype != nil
        case 2: return state.silhouette != nil
        case 3: return state.sleeveOrLength != nil
        case 4: return true
        case 5: return !state.colors.isEmpty
        case 6: return !state.fabrics.isEmpty
        case 7: return true
        case 8: return true
        case 9: return true
        case 10: return state.sole != nil
        case 11: return state.toe != nil
        case 12: return state.shoeHeight != nil
        case 13: return state.closureType != nil
        case 14: return state.bagType != nil
        case 15: return state.bagSize != nil
        case 16: return state.strap != nil
        case 17: return state.closureType != nil
        case 18: return state.hardware != nil
        default: return true
        }
    }

    // MARK: - Step routing

    @ViewBuilder
    private func stepView(id: Int) -> some View {
        switch id {
        case 1: Step1TypeAndReference(state: state, referenceUploading: referenceUploading, pickerItem: $referenceItem, onRemoveRef: { state.referenceUrl = nil })
        case 2: ChipSingleStep(title: "Silhouette", subtitle: "How does it sit on the body?", options: SILHOUETTES, selected: state.silhouette, onSelect: { state.silhouette = $0 })
        case 3:
            let isBottomOrDress = state.type == "Bottom" || state.type == "Dress"
            let opts: [String] = state.type == "Bottom" ? BOTTOM_LENGTH : state.type == "Dress" ? DRESS_LENGTH : SLEEVE
            ChipSingleStep(
                title: isBottomOrDress ? "Length" : "Sleeve",
                subtitle: isBottomOrDress ? "Where does the hem land?" : "How long are the sleeves?",
                options: opts, selected: state.sleeveOrLength, onSelect: { state.sleeveOrLength = $0 }
            )
        case 4: ChipMultiStep(title: "Details", subtitle: "Optional — up to 4.", options: detailsFor(state.type), selected: Binding(get: { state.details }, set: { state.details = $0 }), max: 4)
        case 5: ColorsStep(state: state)
        case 6: FabricStep(state: state)
        case 7: OccasionSeasonStep(state: state)
        case 8: RefinementsStep(text: Binding(get: { state.freeText }, set: { state.freeText = $0 }))
        case 9: PreviewStep(state: state, prompt: buildPrompt())
        case 10: ChipSingleStep(title: "Sole", subtitle: "What sits underneath?", options: SHOE_SOLES, selected: state.sole, onSelect: { state.sole = $0 })
        case 11: ChipSingleStep(title: "Toe shape", subtitle: "How does the front finish?", options: SHOE_TOES, selected: state.toe, onSelect: { state.toe = $0 })
        case 12: ChipSingleStep(title: "Height", subtitle: "How high does it rise?", options: SHOE_HEIGHTS, selected: state.shoeHeight, onSelect: { state.shoeHeight = $0 })
        case 13: ChipSingleStep(title: "Closure", subtitle: "How does it fasten?", options: SHOE_CLOSURES, selected: state.closureType, onSelect: { state.closureType = $0 })
        case 14: ChipSingleStep(title: "Bag type", subtitle: "What shape are you after?", options: BAG_TYPES, selected: state.bagType, onSelect: { state.bagType = $0 })
        case 15: ChipSingleStep(title: "Size", subtitle: "How much does it hold?", options: BAG_SIZES, selected: state.bagSize, onSelect: { state.bagSize = $0 })
        case 16: ChipSingleStep(title: "Strap", subtitle: "How is it carried?", options: BAG_STRAPS, selected: state.strap, onSelect: { state.strap = $0 })
        case 17: ChipSingleStep(title: "Closure", subtitle: "How does it fasten?", options: BAG_CLOSURES, selected: state.closureType, onSelect: { state.closureType = $0 })
        case 18: ChipSingleStep(title: "Hardware", subtitle: "Metal accents & finish.", options: BAG_HARDWARE, selected: state.hardware, onSelect: { state.hardware = $0 })
        default: EmptyView()
        }
    }

    private func buildPrompt() -> String {
        renderPrompt(state.snapshot(mannequin: mannequinGender, prefFabrics: prefFabrics, prefColors: prefColors))
    }

    // MARK: - Generation

    private func runGeneration() async {
        generating = true
        error = nil
        defer { generating = false }
        // Balance check (soft: if we have a balance and it's insufficient, notify).
        if let bal = CreditsBus.shared.balance, bal < GEN_COST {
            ToastBus.shared.post("Not enough credits.")
            return
        }
        let prompt = buildPrompt()
        let refs = [state.referenceUrl].compactMap { $0 }
        do {
            let resp = try await Repo.shared.generatePiece(prompt: prompt, imageUrls: refs)
            if let url = resp.image_url, !url.isEmpty {
                try? await Repo.shared.spendCredits(amount: GEN_COST, kind: "studio_gen")
                generatedUrl = url
                await autoSaveToJournal(url: url, source: "hero")
            } else {
                error = resp.error ?? "Generation failed"
                ToastBus.shared.post("Generation failed: \(resp.error ?? "no image")")
            }
        } catch {
            self.error = error.localizedDescription
            ToastBus.shared.post("Generation failed: \(error.localizedDescription)")
        }
    }

    private func runVariations(axis: VaryAxis) async {
        guard !generatingVariations else { return }
        generatingVariations = true
        error = nil
        defer { generatingVariations = false }
        let snap = state.snapshot(mannequin: mannequinGender, prefFabrics: prefFabrics, prefColors: prefColors)
        let refs = [state.referenceUrl].compactMap { $0 }
        // Parallel generation
        async let r0 = generateOne(snap: snap, index: 0, axis: axis, refs: refs)
        async let r1 = generateOne(snap: snap, index: 1, axis: axis, refs: refs)
        async let r2 = generateOne(snap: snap, index: 2, axis: axis, refs: refs)
        let results: [String?] = await [r0, r1, r2]
        let successful = results.compactMap { $0 }
        let failed = results.count - successful.count
        variations = successful.map { VariationResult(url: $0, savedOk: false) }
        // Persist each successful alternative to Journal so they survive past this session.
        for url in successful {
            await autoSaveToJournal(url: url, source: "variation-auto")
        }
        if failed > 0 {
            ToastBus.shared.post("\(failed) variation\(failed > 1 ? "s" : "") failed — kept \(GEN_COST * failed) credits")
        }
    }

    private func generateOne(snap: WizardSnapshot, index: Int, axis: VaryAxis, refs: [String]) async -> String? {
        let prompt = buildPromptFor(snap, variationIndex: index, axis: axis)
        do {
            let r = try await Repo.shared.generatePiece(prompt: prompt, imageUrls: refs)
            if let url = r.image_url, !url.isEmpty {
                try? await Repo.shared.spendCredits(amount: GEN_COST, kind: "studio_gen_variation")
                return url
            }
        } catch {
            // swallow — treated as failed
        }
        return nil
    }

    private func autoSaveToJournal(url: String, source: String) async {
        if savedJournalUrls.contains(url) { return }
        savedJournalUrls.insert(url)
        do {
            guard let uid = Repo.shared.userId else { return }
            let bytes = try await Repo.shared.downloadBytes(url)
            let path = try await Repo.shared.uploadOutfitPhoto(bytes: bytes, ext: "png")
            let name = niceName(state)
            _ = try await Repo.shared.insertOutfit(OutfitInsert(
                user_id: uid,
                photo_path: path,
                score: 0.0,
                occasion: "Studio",
                hem_comment: "Generated: \(name)",
                kind: "studio_gen"
            ))
        } catch {
            savedJournalUrls.remove(url)
        }
    }

    private func saveVariationToStudio(url: String) async -> Bool {
        guard let uid = Repo.shared.userId else { return false }
        do {
            let bytes = try await Repo.shared.downloadBytes(url)
            let path = try await Repo.shared.uploadClosetPhoto(bytes: bytes, ext: "png")
            _ = try await Repo.shared.insertClosetItem(ClosetItemInsert(
                user_id: uid,
                name: niceName(state),
                category: (state.type ?? "top").lowercased(),
                subcategory: state.subtype?.lowercased(),
                image_path: path,
                color_hex: nil,
                parent_id: editingPieceId,
                source: nil
            ))
            return true
        } catch {
            ToastBus.shared.post("Save failed: \(error.localizedDescription)")
            return false
        }
    }

    private func addCurrentToStudio() async {
        guard let heroUrl = generatedUrl else { return }
        if addingBusy { return }
        addingBusy = true
        error = nil
        defer { addingBusy = false }
        do {
            let bytes = try await Repo.shared.downloadBytes(heroUrl)
            let path = try await Repo.shared.uploadClosetPhoto(bytes: bytes, ext: "png")
            guard let uid = Repo.shared.userId else { throw RepoError.notSignedIn }
            _ = try await Repo.shared.insertClosetItem(ClosetItemInsert(
                user_id: uid,
                name: niceName(state),
                category: (state.type ?? "top").lowercased(),
                subcategory: state.subtype?.lowercased(),
                image_path: path,
                color_hex: nil,
                parent_id: editingPieceId,
                source: nil
            ))
            addedOk = true
            ToastBus.shared.post(editingPieceId != nil ? "Saved as variation" : "Saved to Studio")
        } catch {
            self.error = error.localizedDescription
            ToastBus.shared.post("Upload failed: \(error.localizedDescription)")
        }
    }

    /// Save the hero + every variation to Studio in one shot. Used when the
    /// user has generated alternatives and hits "Save all".
    private func addAllToStudio() async {
        if addingBusy { return }
        addingBusy = true
        error = nil
        defer { addingBusy = false }

        var savedCount = 0
        var urls: [String] = []
        if let hero = generatedUrl { urls.append(hero) }
        urls.append(contentsOf: variations.map { $0.url })

        for (idx, url) in urls.enumerated() {
            if idx == 0, generatedUrl != nil {
                // Hero — reuse the closet insert logic inline to stay consistent
                // with addCurrentToStudio's name/category/parent semantics.
                do {
                    let bytes = try await Repo.shared.downloadBytes(url)
                    let path = try await Repo.shared.uploadClosetPhoto(bytes: bytes, ext: "png")
                    guard let uid = Repo.shared.userId else { throw RepoError.notSignedIn }
                    _ = try await Repo.shared.insertClosetItem(ClosetItemInsert(
                        user_id: uid,
                        name: niceName(state),
                        category: (state.type ?? "top").lowercased(),
                        subcategory: state.subtype?.lowercased(),
                        image_path: path,
                        color_hex: nil,
                        parent_id: editingPieceId,
                        source: nil
                    ))
                    savedCount += 1
                } catch {
                    self.error = error.localizedDescription
                }
            } else {
                if await saveVariationToStudio(url: url) {
                    savedCount += 1
                    if let vIdx = variations.firstIndex(where: { $0.url == url }) {
                        variations[vIdx] = VariationResult(url: url, savedOk: true)
                    }
                }
            }
        }

        if savedCount > 0 {
            addedOk = true
            ToastBus.shared.post("Saved \(savedCount) to Studio")
        }
    }

    // MARK: - Reference upload

    private func uploadReference(item: PhotosPickerItem) {
        referenceUploading = true
        error = nil
        Task {
            defer { referenceUploading = false; referenceItem = nil }
            do {
                guard let bytes = try await item.loadTransferable(type: Data.self) else {
                    throw RepoError.notFound
                }
                guard let uid = Repo.shared.userId else { throw RepoError.notSignedIn }
                // RLS: uid MUST be first path segment.
                let path = "\(uid)/references/\(UUID().uuidString.lowercased()).jpg"
                _ = try await Supa.client.storage.from("closet").upload(
                    path, data: bytes,
                    options: FileOptions(contentType: "image/jpeg", upsert: false)
                )
                if let signed = try await Repo.shared.signedClosetUrl(path) {
                    state.referenceUrl = signed
                }
            } catch {
                self.error = error.localizedDescription
                ToastBus.shared.post("Reference upload failed: \(error.localizedDescription)")
            }
        }
    }

    // MARK: - Generating

    private var generatingScreen: some View {
        VStack {
            Spacer()
            Text("Sketching…")
                .font(Serif.display(30))
                .foregroundStyle(Palette.ink)
            Hairline().padding(.vertical, 12)
            Text("Hem is drawing your piece.")
                .font(Serif.body(14))
                .foregroundStyle(Palette.muted)
            ProgressView().tint(Palette.bronze).padding(.top, 16)
            Spacer()
        }
        .padding(.horizontal, 20)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    // MARK: - Result screen

    @ViewBuilder
    private func resultScreen(heroUrl: String) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 6) {
                    Button { dismiss() } label: {
                        Image(systemName: "arrow.left").foregroundStyle(Palette.ink).padding(6)
                    }
                    Eyebrow(text: "YOUR PIECE")
                }
                .padding(.top, 12)

                ZStack {
                    Palette.card
                    if let u = URL(string: heroUrl) {
                        AsyncImage(url: u) { phase in
                            switch phase {
                            case .success(let img): img.resizable().scaledToFill()
                            default: ProgressView().tint(Palette.muted)
                            }
                        }
                    }
                }
                .aspectRatio(1, contentMode: .fit)
                .clipShape(RoundedRectangle(cornerRadius: 16))
                .padding(.top, 12)

                if !variations.isEmpty || generatingVariations {
                    Eyebrow(text: "VARIATIONS").padding(.top, 16)
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 12) {
                            if generatingVariations && variations.isEmpty {
                                ForEach(0..<3, id: \.self) { _ in
                                    RoundedRectangle(cornerRadius: 10)
                                        .fill(Palette.card)
                                        .frame(width: 88, height: 88)
                                }
                            } else {
                                ForEach(variations.indices, id: \.self) { idx in
                                    let v = variations[idx]
                                    variationTile(
                                        v: v,
                                        isHero: v.url == heroUrl,
                                        onTap: {
                                            generatedUrl = v.url
                                            addedOk = false
                                            Task { await autoSaveToJournal(url: v.url, source: "variation-picked") }
                                        },
                                        onSave: {
                                            Task {
                                                if await saveVariationToStudio(url: v.url) {
                                                    variations[idx] = VariationResult(url: v.url, savedOk: true)
                                                    await autoSaveToJournal(url: v.url, source: "variation")
                                                    ToastBus.shared.post("Saved to Studio")
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                        .padding(.vertical, 8)
                    }
                }

                if let error {
                    Text(error).font(Serif.body(13)).foregroundStyle(Palette.bronze).padding(.top, 8)
                }
                if addedOk {
                    Text("Saved to Studio.").font(Serif.body(13)).foregroundStyle(Palette.bronze).padding(.top, 8)
                }

                Spacer(minLength: 20)

                PrimaryButton(
                    title: addingBusy
                        ? "Saving…"
                        : (addedOk ? "Saved" : (variations.isEmpty ? "Save current" : "Save all")),
                    enabled: !addingBusy && !addedOk,
                    action: {
                        Task {
                            if variations.isEmpty {
                                await addCurrentToStudio()
                            } else {
                                await addAllToStudio()
                            }
                        }
                    }
                )
                .padding(.top, 16)

                Button {
                    if let bal = CreditsBus.shared.balance, bal < GEN_COST * 3 {
                        ToastBus.shared.post("Not enough credits.")
                        return
                    }
                    showAxisSheet = true
                } label: {
                    Text(generatingVariations ? "Sketching 3 alternatives…" : "Generate alternatives · \(GEN_COST * 3)")
                        .font(.system(size: 14, weight: .medium))
                        .foregroundStyle(Palette.ink)
                        .frame(maxWidth: .infinity)
                        .frame(height: 48)
                        .overlay(Capsule().stroke(Palette.ink.opacity(0.5), lineWidth: 1))
                        .clipShape(Capsule())
                }
                .disabled(generatingVariations)
                .padding(.top, 8)

                Button {
                    generatedUrl = nil
                    variations = []
                    addedOk = false
                    error = nil
                    stepIndex = 0
                } label: {
                    Text("Try again")
                        .font(.system(size: 14, weight: .medium))
                        .foregroundStyle(Palette.ink)
                        .frame(maxWidth: .infinity)
                        .frame(height: 48)
                        .overlay(Capsule().stroke(Palette.ink.opacity(0.5), lineWidth: 1))
                        .clipShape(Capsule())
                }
                .padding(.top, 8)

                Button { onDone() } label: {
                    Text("Done")
                        .font(.system(size: 14, weight: .medium))
                        .foregroundStyle(Palette.muted)
                        .frame(maxWidth: .infinity, minHeight: 40)
                }
                .padding(.top, 4)

                Spacer(minLength: 40)
            }
            .padding(.horizontal, 20)
        }
    }

    private func variationTile(v: VariationResult, isHero: Bool, onTap: @escaping () -> Void, onSave: @escaping () -> Void) -> some View {
        ZStack(alignment: .topTrailing) {
            Button(action: onTap) {
                ZStack(alignment: .bottomLeading) {
                    ZStack {
                        Palette.card
                        if let u = URL(string: v.url) {
                            AsyncImage(url: u) { phase in
                                switch phase {
                                case .success(let img): img.resizable().scaledToFill()
                                default: Color.clear
                                }
                            }
                        }
                    }
                    .frame(width: 88, height: 88)
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                    .overlay(RoundedRectangle(cornerRadius: 10)
                        .stroke(isHero ? Palette.bronze : Palette.hairline, lineWidth: isHero ? 2 : 1))

                    if v.savedOk {
                        ZStack {
                            Circle().fill(Palette.bronze)
                            Text("✓").font(.system(size: 12, weight: .bold)).foregroundStyle(.white)
                        }
                        .frame(width: 20, height: 20)
                        .padding(4)
                    }
                }
            }
            .buttonStyle(.plain)

            Button(action: onSave) {
                ZStack {
                    Circle().fill(Palette.card)
                    Text(v.savedOk ? "✓" : "+").font(.system(size: 14, weight: .bold)).foregroundStyle(Palette.ink)
                }
                .frame(width: 24, height: 24)
                .overlay(Circle().stroke(Palette.hairline, lineWidth: 1))
            }
            .disabled(v.savedOk)
            .padding(4)
        }
    }
}

// MARK: - Variation result model

struct VariationResult: Hashable {
    let url: String
    let savedOk: Bool
}

// MARK: - Name

private func niceName(_ s: WizardState) -> String {
    let parts = [s.silhouette, s.colors.first, s.subtype ?? s.type].compactMap { $0 }
    let joined = parts.joined(separator: " ")
    return joined.isEmpty ? "Studio piece" : joined
}

// MARK: - Step 1: Type + subtype + reference

private struct Step1TypeAndReference: View {
    let state: WizardState
    let referenceUploading: Bool
    @Binding var pickerItem: PhotosPickerItem?
    let onRemoveRef: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("What are we making?")
                .font(Serif.display(22))
                .foregroundStyle(Palette.ink)

            FlowLayout(spacing: 8) {
                ForEach(TYPES, id: \.self) { t in
                    Chip(text: t, active: state.type == t) {
                        if state.type != t {
                            state.type = t
                            state.subtype = nil
                        }
                    }
                }
            }
            .padding(.top, 14)

            if let subs = SUBTYPES[state.type ?? ""], !subs.isEmpty {
                Eyebrow(text: "SUBCATEGORY").padding(.top, 16)
                FlowLayout(spacing: 8) {
                    ForEach(subs, id: \.self) { s in
                        Chip(text: s, active: state.subtype == s) { state.subtype = s }
                    }
                }
                .padding(.top, 8)
            }

            Eyebrow(text: "REFERENCE · OPTIONAL").padding(.top, 20)

            if let ref = state.referenceUrl, !ref.isEmpty {
                HStack {
                    ZStack {
                        Palette.card
                        if let u = URL(string: ref) {
                            AsyncImage(url: u) { phase in
                                switch phase {
                                case .success(let img): img.resizable().scaledToFill()
                                default: Color.clear
                                }
                            }
                        }
                    }
                    .frame(width: 40, height: 40)
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                    Text("Reference set").font(Serif.body(14)).foregroundStyle(Palette.ink)
                    Spacer()
                    Button(action: onRemoveRef) {
                        Text("×")
                            .font(.system(size: 14))
                            .padding(.horizontal, 10).padding(.vertical, 4)
                            .overlay(Circle().stroke(Palette.ink.opacity(0.4), lineWidth: 1))
                    }
                }
                .padding(.top, 8)
            } else {
                PhotosPicker(selection: $pickerItem, matching: .images, photoLibrary: .shared()) {
                    Text(referenceUploading ? "Uploading…" : "＋ Add reference photo")
                        .font(.system(size: 14, weight: .medium))
                        .foregroundStyle(Palette.ink)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Palette.ink.opacity(0.4), lineWidth: 1))
                }
                .disabled(referenceUploading)
                .padding(.top, 8)
            }
        }
    }
}

// MARK: - Chip step primitives

private struct ChipSingleStep: View {
    let title: String
    let subtitle: String
    let options: [String]
    let selected: String?
    let onSelect: (String) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(title).font(Serif.display(22)).foregroundStyle(Palette.ink)
            Text(subtitle).font(Serif.body(14)).foregroundStyle(Palette.muted).padding(.top, 4)
            FlowLayout(spacing: 8) {
                ForEach(options, id: \.self) { opt in
                    Chip(text: opt, active: selected == opt) { onSelect(opt) }
                }
            }
            .padding(.top, 14)
        }
    }
}

private struct ChipMultiStep: View {
    let title: String
    let subtitle: String
    let options: [String]
    @Binding var selected: [String]
    let max: Int

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(title).font(Serif.display(22)).foregroundStyle(Palette.ink)
            Text(subtitle).font(Serif.body(14)).foregroundStyle(Palette.muted).padding(.top, 4)
            FlowLayout(spacing: 8) {
                ForEach(options, id: \.self) { opt in
                    let active = selected.contains(opt)
                    Chip(text: opt, active: active) {
                        if active {
                            selected.removeAll { $0 == opt }
                        } else if selected.count < max {
                            selected.append(opt)
                        }
                    }
                }
            }
            .padding(.top, 14)
        }
    }
}

// MARK: - Colors step

private struct ColorsStep: View {
    @Bindable var state: WizardState
    @State private var hexInput: String = ""

    private var cleanedHex: String {
        let raw = hexInput.uppercased().filter { $0.isLetter || $0.isNumber }
        return "#" + String(raw.prefix(6))
    }
    private var validHex: Bool {
        cleanedHex.count == 7 && cleanedHex.dropFirst().allSatisfy { "0123456789ABCDEF".contains($0) }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("Colors").font(Serif.display(22)).foregroundStyle(Palette.ink)
            Text("Pick up to three. Two often reads cleaner.")
                .font(Serif.body(14)).foregroundStyle(Palette.muted).padding(.top, 4)

            if !state.colors.isEmpty {
                HStack(spacing: 8) {
                    ForEach(state.colors, id: \.self) { name in
                        Circle().fill(resolveNamedColor(name))
                            .frame(width: 40, height: 40)
                            .overlay(Circle().stroke(Palette.ink.opacity(0.3), lineWidth: 1))
                    }
                }
                .padding(.top, 12)
                if state.colors.count == 3 {
                    Text("Two colors read cleaner than three — try monochrome first.")
                        .font(.system(size: 12))
                        .foregroundStyle(Palette.bronze)
                        .padding(.top, 8)
                }
            }

            FlowLayout(spacing: 8) {
                ForEach(COLOR_PRESETS, id: \.name) { p in
                    let active = state.colors.contains(p.name)
                    ColorChip(label: p.name, swatch: resolveNamedColor(p.name), active: active) {
                        if active { state.colors.removeAll { $0 == p.name } }
                        else if state.colors.count < 3 { state.colors.append(p.name) }
                    }
                }
                ForEach(state.colors.filter { $0.hasPrefix("#") }, id: \.self) { hex in
                    ColorChip(label: hex.uppercased(), swatch: resolveNamedColor(hex), active: true) {
                        state.colors.removeAll { $0 == hex }
                    }
                }
            }
            .padding(.top, 12)

            HStack(spacing: 10) {
                Circle()
                    .fill(validHex ? resolveNamedColor(cleanedHex) : Palette.hairline)
                    .frame(width: 28, height: 28)
                    .overlay(Circle().stroke(Palette.ink.opacity(0.3), lineWidth: 1))
                ZStack(alignment: .leading) {
                    if hexInput.isEmpty {
                        Text("Custom hex (e.g. B0743A)")
                            .font(.system(size: 14))
                            .foregroundStyle(Palette.muted)
                    }
                    TextField("", text: $hexInput)
                        .font(.system(size: 14))
                        .foregroundStyle(Palette.ink)
                        .textInputAutocapitalization(.characters)
                        .autocorrectionDisabled(true)
                }
                .padding(.horizontal, 12).padding(.vertical, 10)
                .background(Palette.card)
                .overlay(Capsule().stroke(Palette.hairline, lineWidth: 1))
                .clipShape(Capsule())
                Button {
                    if validHex && state.colors.count < 3 {
                        state.colors.append(cleanedHex)
                        hexInput = ""
                    }
                } label: {
                    Text("+ Add")
                        .font(.system(size: 14, weight: .medium))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 14).padding(.vertical, 10)
                        .background(validHex ? Palette.ink : Palette.muted)
                        .clipShape(Capsule())
                }
                .disabled(!validHex)
            }
            .padding(.top, 16)
        }
    }
}

private func resolveNamedColor(_ nameOrHex: String) -> Color {
    if nameOrHex.hasPrefix("#") { return resolveTint(nameOrHex) }
    if let p = COLOR_PRESETS.first(where: { $0.name == nameOrHex }) {
        return resolveTint(p.hex)
    }
    return Palette.muted
}

// MARK: - Fabric

private struct FabricStep: View {
    @Bindable var state: WizardState
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("Fabric").font(Serif.display(22)).foregroundStyle(Palette.ink)
            Text("Pick up to two.").font(Serif.body(14)).foregroundStyle(Palette.muted).padding(.top, 4)
            FlowLayout(spacing: 8) {
                ForEach(fabricsFor(state.type), id: \.self) { f in
                    let active = state.fabrics.contains(f)
                    Chip(text: f, active: active) {
                        if active { state.fabrics.removeAll { $0 == f } }
                        else if state.fabrics.count < 2 { state.fabrics.append(f) }
                    }
                }
            }
            .padding(.top, 14)

            Eyebrow(text: "TEXTURE · OPTIONAL").padding(.top, 20)
            FlowLayout(spacing: 8) {
                ForEach(TEXTURES, id: \.self) { t in
                    Chip(text: t, active: state.texture == t) {
                        state.texture = state.texture == t ? nil : t
                    }
                }
            }
            .padding(.top, 8)
        }
    }
}

// MARK: - Occasion + Season

private struct OccasionSeasonStep: View {
    @Bindable var state: WizardState
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("Where + when").font(Serif.display(22)).foregroundStyle(Palette.ink)
            Text("This tunes weight and formality.")
                .font(Serif.body(14)).foregroundStyle(Palette.muted).padding(.top, 4)

            Eyebrow(text: "OCCASION").padding(.top, 16)
            FlowLayout(spacing: 8) {
                ForEach(OCCASIONS, id: \.self) { o in
                    Chip(text: o, active: state.occasion == o) { state.occasion = o }
                }
            }
            .padding(.top, 8)

            Eyebrow(text: "SEASON").padding(.top, 16)
            FlowLayout(spacing: 8) {
                ForEach(SEASONS, id: \.self) { s in
                    Chip(text: s, active: state.season == s) { state.season = s }
                }
            }
            .padding(.top, 8)
        }
    }
}

// MARK: - Refinements

private struct RefinementsStep: View {
    @Binding var text: String
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("Anything else Hem should know?")
                .font(Serif.display(22)).foregroundStyle(Palette.ink)
            Text("Optional. Tap a suggestion or write your own.")
                .font(Serif.body(14)).foregroundStyle(Palette.muted).padding(.top, 4)

            ZStack(alignment: .topLeading) {
                if text.isEmpty {
                    Text("e.g. cropped, boxy shoulders, subtle sheen…")
                        .font(.system(size: 14))
                        .foregroundStyle(Palette.muted)
                        .padding(16)
                }
                TextEditor(text: $text)
                    .font(.system(size: 14))
                    .scrollContentBackground(.hidden)
                    .padding(10)
            }
            .frame(height: 120)
            .background(Palette.card)
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Palette.hairline, lineWidth: 1))
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .padding(.top, 14)

            FlowLayout(spacing: 8) {
                ForEach(PROMPT_ADDONS, id: \.self) { p in
                    Chip(text: "+ \(p)", active: false) {
                        text = text.isEmpty ? p : "\(text) \(p)."
                    }
                }
            }
            .padding(.top, 14)
        }
    }
}

// MARK: - Preview / final

private struct PreviewStep: View {
    let state: WizardState
    let prompt: String
    @State private var showFull = false

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("Preview").font(Serif.display(22)).foregroundStyle(Palette.ink)
            Text("Read it back before we sketch.")
                .font(Serif.body(14)).foregroundStyle(Palette.muted).padding(.top, 4)

            Text(naturalSummary(state))
                .font(Serif.italic(16))
                .foregroundStyle(Palette.ink)
                .fixedSize(horizontal: false, vertical: true)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(16)
                .background(Palette.card)
                .overlay(RoundedRectangle(cornerRadius: 16).stroke(Palette.hairline, lineWidth: 1))
                .clipShape(RoundedRectangle(cornerRadius: 16))
                .padding(.top, 14)

            if !state.colors.isEmpty {
                Eyebrow(text: "PALETTE").padding(.top, 16)
                HStack(spacing: 8) {
                    ForEach(state.colors, id: \.self) { c in
                        Circle()
                            .fill(resolveNamedColor(c))
                            .frame(width: 28, height: 28)
                            .overlay(Circle().stroke(Palette.ink.opacity(0.3), lineWidth: 1))
                    }
                }
                .padding(.top, 8)
            }

            if let ref = state.referenceUrl, !ref.isEmpty {
                HStack {
                    ZStack {
                        Palette.card
                        if let u = URL(string: ref) {
                            AsyncImage(url: u) { phase in
                                switch phase {
                                case .success(let img): img.resizable().scaledToFill()
                                default: Color.clear
                                }
                            }
                        }
                    }
                    .frame(width: 44, height: 44)
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                    Text("Will be treated as a reference.")
                        .font(Serif.body(13))
                        .foregroundStyle(Palette.muted)
                }
                .padding(.top, 16)
            }

            Button {
                showFull.toggle()
            } label: {
                Text(showFull ? "▾ Hide full prompt" : "▸ Show full prompt")
                    .font(.system(size: 12))
                    .foregroundStyle(Palette.muted)
            }
            .buttonStyle(.plain)
            .padding(.top, 16)

            if showFull {
                Text(prompt)
                    .font(.system(size: 12))
                    .foregroundStyle(Palette.muted)
                    .padding(.top, 4)
            }
        }
    }
}

private func naturalSummary(_ s: WizardState) -> String {
    let sil = s.silhouette?.lowercased()
    let color = s.colors.map { $0.lowercased().hasPrefix("#") ? String($0.dropFirst()) : $0.lowercased() }.joined(separator: " and ")
    let fabric = s.fabrics.map { $0.lowercased() }.joined(separator: " and ")
    let piece = (s.subtype ?? s.type ?? "piece").lowercased()
    let len = s.sleeveOrLength.map { "with \($0.lowercased()) sleeves or hem" } ?? ""
    let details = s.details.isEmpty ? "" : " and " + s.details.map { $0.lowercased() }.joined(separator: ", ")
    let head = ["An", sil, color.isEmpty ? nil : color, fabric.isEmpty ? nil : fabric, piece]
        .compactMap { $0 }
        .joined(separator: " ")
    return "\(head) \(len)\(details), for \(s.occasion.lowercased()) \(s.season.lowercased()) wear."
        .replacingOccurrences(of: "  ", with: " ")
}

// MARK: - Chip UI

private struct Chip: View {
    let text: String
    let active: Bool
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            Text(text)
                .font(.system(size: 13, weight: .medium))
                .foregroundStyle(active ? .white : Palette.ink)
                .padding(.horizontal, 14).padding(.vertical, 10)
                .background(active ? Palette.ink : Color.clear)
                .overlay(Capsule().stroke(Palette.ink.opacity(0.4), lineWidth: 1))
                .clipShape(Capsule())
                .scaleEffect(active ? 1.04 : 1.0)
                .animation(.spring(response: 0.3, dampingFraction: 0.75), value: active)
        }
        .buttonStyle(.plain)
    }
}

private struct ColorChip: View {
    let label: String
    let swatch: Color
    let active: Bool
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 6) {
                Circle().fill(swatch).frame(width: 20, height: 20)
                    .overlay(Circle().stroke(Color.white.opacity(0.6), lineWidth: 1))
                Text(label)
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(active ? .white : Palette.ink)
            }
            .padding(.leading, 6).padding(.trailing, 12)
            .padding(.vertical, 4)
            .background(active ? Palette.ink : Color.clear)
            .overlay(Capsule().stroke(Palette.ink.opacity(0.4), lineWidth: 1))
            .clipShape(Capsule())
            .scaleEffect(active ? 1.03 : 1.0)
            .animation(.spring(response: 0.3, dampingFraction: 0.75), value: active)
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Variation axis sheet

struct VariationAxisSheet: View {
    let onPick: (VaryAxis) -> Void
    private let rows: [VaryAxis] = [.colors, .silhouette, .fabric, .details, .surprise]

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Eyebrow(text: "VARY BY").padding(.top, 20)
            Text("How should Hem riff on this?")
                .font(Serif.display(26))
                .foregroundStyle(Palette.ink)
                .padding(.top, 8)
                .padding(.bottom, 20)
            ForEach(rows.indices, id: \.self) { idx in
                let row = rows[idx]
                Button { onPick(row) } label: {
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(row.title)
                                .font(Serif.display(20))
                                .foregroundStyle(Palette.ink)
                            Text(row.subtitle)
                                .font(Serif.body(13))
                                .foregroundStyle(Palette.muted)
                        }
                        Spacer()
                        Text("3 × 15 CREDITS")
                            .font(.system(size: 10, weight: .semibold))
                            .tracking(1.5)
                            .foregroundStyle(Palette.bronze)
                    }
                    .padding(.vertical, 16)
                }
                .buttonStyle(.plain)
                if idx < rows.count - 1 { Hairline() }
            }
            Spacer(minLength: 40)
        }
        .padding(.horizontal, 20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Palette.paper)
    }
}

// MARK: - Keyboard helper (file-scoped for now; also exposed as `dismissKeyboard()`)

private func hideKeyboard() { dismissKeyboard() }

func dismissKeyboard() {
    #if canImport(UIKit)
    UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
    #endif
}
