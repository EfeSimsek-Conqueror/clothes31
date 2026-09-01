import SwiftUI

// MARK: - Vocabulary (mirrors the Studio piece wizard's data-driven step defs)

private let COVER_GENRES: [(String, String)] = [
    ("Fashion",    "High-fashion editorial, sharp typography, restrained styling."),
    ("Streetwear", "Drop-culture energy, casual grit, oversized text."),
    ("Sport",      "Sports-desk, dynamic, high contrast, muscular type."),
    ("Culture",    "Youth-culture voice — experimental, provocative, playful."),
    ("Lifestyle",  "Slow-living quarterly — quiet, hairline detail."),
    ("Music",      "Music press — moody, portraiture-forward."),
    ("Art",        "Gallery print — clean, generous whitespace."),
    ("Business",   "Design-and-business quarterly — architectural, condensed sans."),
]

private let COVER_MOODS: [(String, String)] = [
    ("Editorial",    "Classic fashion book: bold serif, tight columns, moody grade."),
    ("Quiet luxury", "Understated, cream backgrounds, generous whitespace."),
    ("Brutalist",    "Hard bars, condensed sans, high-contrast ink."),
    ("Romantic",     "Italic display, blush palette, cursive touches."),
    ("Sport",        "Dynamic italics, high-energy blocks, saturated color."),
    ("Minimal",      "One idea, tons of air, hairline rules."),
    ("Punk",         "Ransom cut, defaced type, grainy black-and-cream."),
    ("Maximalist",   "Everything on the page, layered type, saturated."),
]

private let COVER_LAYOUTS: [(String, String)] = [
    ("top",    "Masthead pinned at the top — classic layout."),
    ("center", "Big masthead centered like an art print."),
    ("bottom", "Masthead as a footer — modern, poster-style."),
]

private let COVER_COLORS: [(String, String, String)] = [
    ("Cream",       "#F3EEE4", "Editorial default — warm, safe."),
    ("Ink",         "#141210", "Ink cover, cream type — high drama."),
    ("Bronze",      "#B4813E", "Warm accent — Fitrater signature."),
    ("Bone",        "#EAE3D3", "Softer than cream, gallery-like."),
    ("Slate",       "#3E4552", "Muted, corporate, considered."),
    ("Terracotta",  "#B4573E", "Warm clay — slow-living quarterly."),
    ("Sage",        "#7F8D6E", "Botanical, calm, quiet luxury."),
    ("Blush",       "#E9C7BF", "Romantic, soft focus."),
]

// MARK: - Wizard state

private struct CoverWizardState {
    var genre: String? = nil
    var mood: String? = "Editorial"
    var masthead: String = "FITRATER"
    var layout: String = "top"
    var headline: String = ""
    var pullQuote: String = ""
    var color: String = "#F3EEE4"
    // NEW
    var coverLines: [String] = []
    var pose: String = "standing"
    var price: String = "$8.00"
}

private let POSES: [(String, String, String)] = [
    ("standing",           "Standing",   "Full-length, editorial front pose."),
    ("seated",             "Seated",     "On a chair or floor — considered, intimate."),
    ("walking",            "Walking",    "Motion, streetstyle-adjacent."),
    ("editorial_portrait", "Portrait",   "Bust / face-forward — the classic cover shot."),
    ("detail",             "Detail",     "Close-up on fabric, hands, or accessory."),
]

private let COVER_LINE_SUGGESTIONS: [String] = [
    "THE ART ISSUE",
    "50 IDEAS FOR FALL",
    "MEET THE NEW GUARD",
    "INSIDE THE ATELIER",
    "THE STYLE REPORT",
    "A NEW ROMANTICISM",
    "SEASON OF QUIET",
    "TALKING SHOP",
]

// MARK: - View

/// Studio-side wizard for creating a magazine-cover TEMPLATE. Mirrors the
/// step-by-step layout of `StudioCreateView` (progress hairline → step badge
/// → question view → bottom bar with Back / counter / Next-or-Generate).
///
/// Templates are stored in `magazine_covers` (outfit_id NULL) and surface
/// later in the Camera → Compose Cover flow as "My Covers" references.
struct CoverTemplateCreatorView: View {
    var onClose: () -> Void

    @EnvironmentObject var toasts: ToastBus

    @State private var state = CoverWizardState()
    @State private var stepIndex = 0
    @State private var busy = false
    @State private var error: String?
    @State private var result: ComposeCoverResponse?

    private var steps: [Int] { [1, 2, 3, 4, 5, 6, 7, 8] }
    private var currentStep: Int { steps[stepIndex] }

    var body: some View {
        ZStack {
            Palette.paper.ignoresSafeArea()

            if let result, let url = result.cover_url {
                resultScreen(url: url)
            } else {
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
                                    if stepIndex == 0 { onClose() } else { stepIndex -= 1 }
                                } label: {
                                    Image(systemName: "arrow.left")
                                        .foregroundStyle(Palette.ink)
                                        .padding(6)
                                }
                                Eyebrow(text: "STEP \(stepIndex + 1) OF \(steps.count)")
                            }
                            .padding(.top, 12)

                            Text("Design a cover")
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
                                Text(error).font(Serif.body(13))
                                    .foregroundStyle(Palette.bronze)
                                    .padding(.top, 16)
                            }
                            Spacer(minLength: 40)
                        }
                        .padding(.horizontal, 20)
                    }

                    bottomBar
                }
            }

            if busy {
                WaitingOverlay(
                    eyebrow: "TYPESETTING",
                    title: "Setting your template",
                    tips: [
                        "Choosing the type family…",
                        "Balancing the layout…",
                        "Adding the grain…",
                        "Free of charge — just typography.",
                    ]
                )
            }
        }
    }

    private var progressFraction: CGFloat {
        CGFloat(min(max(stepIndex + 1, 1), steps.count)) / CGFloat(steps.count)
    }

    // MARK: - Step router

    @ViewBuilder
    private func stepView(id: Int) -> some View {
        switch id {
        case 1: Step1Genre(state: $state)
        case 2: Step2Mood(state: $state)
        case 3: Step3Masthead(state: $state)
        case 4: Step4Copy(state: $state)
        case 5: Step5CoverLines(state: $state)
        case 6: Step6Pose(state: $state)
        case 7: Step7Palette(state: $state)
        case 8: Step8Review(state: state)
        default: EmptyView()
        }
    }

    private func canAdvance() -> Bool {
        switch currentStep {
        case 1: return state.genre != nil
        case 2: return state.mood != nil
        case 3: return !state.masthead.trimmingCharacters(in: .whitespaces).isEmpty
        case 4: return !state.headline.trimmingCharacters(in: .whitespaces).isEmpty
        case 5: return true                          // cover lines optional
        case 6: return !state.pose.isEmpty
        case 7: return true
        case 8: return true
        default: return true
        }
    }

    // MARK: - Bottom bar

    private var bottomBar: some View {
        VStack(spacing: 0) {
            Hairline()
            HStack {
                Button {
                    if stepIndex == 0 { onClose() } else { stepIndex -= 1 }
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
                if currentStep == 8 {
                    Button {
                        create()
                    } label: {
                        Text(busy ? "Setting…" : "Create · \(Supa.coverTemplateCost) credits")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(.white)
                            .padding(.horizontal, 18).padding(.vertical, 10)
                            .background(Palette.ink)
                            .clipShape(Capsule())
                    }
                    .disabled(busy)
                } else {
                    let ok = canAdvance()
                    Button {
                        if ok { withAnimation { stepIndex += 1 } }
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

    // MARK: - Result

    @ViewBuilder
    private func resultScreen(url: String) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                HStack {
                    Eyebrow(text: "SAVED")
                    Spacer()
                    Button(action: onClose) {
                        Image(systemName: "xmark").foregroundStyle(Palette.ink).frame(width: 36, height: 36)
                    }
                }
                Text("Your template is ready.")
                    .font(Serif.display(28))
                    .foregroundStyle(Palette.ink)
                AsyncImage(url: URL(string: url)) { img in
                    img.resizable().scaledToFit()
                } placeholder: {
                    RoundedRectangle(cornerRadius: 16).fill(Palette.card).aspectRatio(9.0/16.0, contentMode: .fit)
                }
                .clipShape(RoundedRectangle(cornerRadius: 16))
                .shadow(color: .black.opacity(0.15), radius: 12, y: 6)

                Text("Next time you Compose a Cover from your own photo, this template will appear in the REFERENCE picker as \"My covers\".")
                    .font(Serif.italic(14))
                    .foregroundStyle(Palette.muted)

                Button(action: {
                    result = nil
                    stepIndex = 0
                    state = CoverWizardState()
                }) { OutlinePillButton(title: "Make another") }
                Button(action: onClose) { OutlinePillButton(title: "Done") }
            }
            .padding(20)
        }
    }

    // MARK: - Create

    private func create() {
        busy = true
        error = nil
        Task {
            defer { busy = false }
            let gate = await CreditsGate.check(Supa.coverTemplateCost)
            if case .ok = gate {} else {
                _ = CreditsGate.explainAndBlock(gate)
                return
            }
            do {
                let resp = try await Repo.shared.createCoverTemplate(
                    masthead: state.masthead.trimmingCharacters(in: .whitespaces),
                    headline: state.headline.trimmingCharacters(in: .whitespaces),
                    pullQuote: state.pullQuote.isEmpty ? nil : state.pullQuote,
                    mood: state.mood?.lowercased(),
                    color: state.color,
                    layout: state.layout,
                    coverLines: state.coverLines.isEmpty ? nil : state.coverLines,
                    pose: state.pose,
                    price: state.price,
                    includeBarcode: true
                )
                if let err = resp.error {
                    let msg = err.contains("text_rejected")
                        ? "Cover text can't include profanity or slurs. Edit the wording and try again."
                        : err
                    throw NSError(domain: "Template", code: 1, userInfo: [NSLocalizedDescriptionKey: msg])
                }
                try? await Repo.shared.spendCredits(amount: Supa.coverTemplateCost, kind: "cover_template")
                Haptic.soft()
                result = resp
            } catch {
                self.error = error.localizedDescription
                toasts.post("Template failed: \(error.localizedDescription)")
            }
        }
    }
}

// MARK: - Steps

private struct Step1Genre: View {
    @Binding var state: CoverWizardState
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("What kind of magazine?")
                .font(Serif.display(22)).foregroundStyle(Palette.ink)
            Text("Sets the overall register — how tight, how loud, how quiet.")
                .font(Serif.italic(14)).foregroundStyle(Palette.muted)
            VStack(spacing: 8) {
                ForEach(COVER_GENRES, id: \.0) { name, desc in
                    OptionRow(title: name, subtitle: desc, on: state.genre == name) {
                        Haptic.chip()
                        state.genre = name
                        // gentle default mapping — sync mood to genre if user hasn't picked one
                        switch name {
                        case "Sport":      state.mood = "Sport"
                        case "Streetwear": state.mood = "Brutalist"
                        case "Lifestyle":  state.mood = "Quiet luxury"
                        case "Culture":    state.mood = "Punk"
                        case "Music":      state.mood = "Editorial"
                        case "Art":        state.mood = "Minimal"
                        case "Business":   state.mood = "Minimal"
                        default:           state.mood = "Editorial"
                        }
                    }
                }
            }
        }
    }
}

private struct Step2Mood: View {
    @Binding var state: CoverWizardState
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Pick the mood.")
                .font(Serif.display(22)).foregroundStyle(Palette.ink)
            Text("Drives the typography, spacing, and grain. Your genre pre-picked one — override if you want.")
                .font(Serif.italic(14)).foregroundStyle(Palette.muted)
            VStack(spacing: 8) {
                ForEach(COVER_MOODS, id: \.0) { name, desc in
                    OptionRow(title: name, subtitle: desc, on: state.mood == name) {
                        Haptic.chip()
                        state.mood = name
                    }
                }
            }
        }
    }
}

private struct Step3Masthead: View {
    @Binding var state: CoverWizardState
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Name the publication.")
                .font(Serif.display(22)).foregroundStyle(Palette.ink)
            Text("What's at the top of the cover? Keep it short, 3-12 characters.")
                .font(Serif.italic(14)).foregroundStyle(Palette.muted)
            TextField("FITRATER", text: $state.masthead)
                .font(Serif.display(22))
                .textInputAutocapitalization(.characters)
                .autocorrectionDisabled()
                .padding(14)
                .background(Palette.card)
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(Palette.hairline, lineWidth: 1))

            Eyebrow(text: "POSITION").padding(.top, 10)
            VStack(spacing: 8) {
                ForEach(COVER_LAYOUTS, id: \.0) { key, desc in
                    OptionRow(title: key.capitalized, subtitle: desc, on: state.layout == key) {
                        Haptic.chip()
                        state.layout = key
                    }
                }
            }
        }
    }
}

private struct Step4Copy: View {
    @Binding var state: CoverWizardState
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Write the headline.")
                .font(Serif.display(22)).foregroundStyle(Palette.ink)
            Text("The big line that grabs the reader. Uppercase reads best.")
                .font(Serif.italic(14)).foregroundStyle(Palette.muted)

            TextField("A STUDY IN BRONZE", text: $state.headline, axis: .vertical)
                .font(Serif.display(20))
                .lineLimit(2, reservesSpace: true)
                .textInputAutocapitalization(.characters)
                .autocorrectionDisabled()
                .padding(14)
                .background(Palette.card)
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(Palette.hairline, lineWidth: 1))

            Eyebrow(text: "PULL QUOTE (OPT)").padding(.top, 10)
            Text("An italic line beneath the headline. Short — 8 to 12 words.")
                .font(Serif.italic(13)).foregroundStyle(Palette.muted)
            TextField("She wore restraint and won the room.", text: $state.pullQuote, axis: .vertical)
                .font(Serif.italic(15))
                .lineLimit(2, reservesSpace: true)
                .autocorrectionDisabled()
                .padding(14)
                .background(Palette.card)
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(Palette.hairline, lineWidth: 1))
        }
    }
}

private struct Step5CoverLines: View {
    @Binding var state: CoverWizardState
    @State private var draft: String = ""

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Add cover lines.")
                .font(Serif.display(22)).foregroundStyle(Palette.ink)
            Text("The little tag lines scattered around the model on a fashion cover. Up to 4. Skip if you want it clean.")
                .font(Serif.italic(14)).foregroundStyle(Palette.muted)

            // Existing lines
            if !state.coverLines.isEmpty {
                VStack(spacing: 6) {
                    ForEach(Array(state.coverLines.enumerated()), id: \.offset) { idx, line in
                        HStack {
                            Text(line)
                                .font(Serif.body(14, weight: .semibold))
                                .foregroundStyle(Palette.ink)
                            Spacer()
                            Button(action: { state.coverLines.remove(at: idx) }) {
                                Image(systemName: "xmark").font(.system(size: 11)).foregroundStyle(Palette.muted)
                            }.buttonStyle(.plain)
                        }
                        .padding(10)
                        .background(Palette.card)
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                        .overlay(RoundedRectangle(cornerRadius: 10).stroke(Palette.hairline, lineWidth: 1))
                    }
                }
            }

            // Input row (up to 4)
            if state.coverLines.count < 4 {
                HStack {
                    TextField("THE STYLE REPORT", text: $draft)
                        .font(Serif.body(15, weight: .semibold))
                        .textInputAutocapitalization(.characters)
                        .autocorrectionDisabled()
                        .padding(12)
                        .background(Palette.card)
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                        .overlay(RoundedRectangle(cornerRadius: 10).stroke(Palette.hairline, lineWidth: 1))
                    Button(action: {
                        let cleaned = draft.trimmingCharacters(in: .whitespaces).uppercased()
                        if !cleaned.isEmpty {
                            Haptic.tap()
                            state.coverLines.append(cleaned)
                            draft = ""
                        }
                    }) {
                        Image(systemName: "plus")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundStyle(.white)
                            .frame(width: 40, height: 40)
                            .background(Palette.ink)
                            .clipShape(Circle())
                    }
                    .disabled(draft.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }

            // Suggestions
            Eyebrow(text: "SUGGESTIONS").padding(.top, 10)
            let picks = COVER_LINE_SUGGESTIONS.filter { !state.coverLines.contains($0) }.prefix(6)
            FlowLayout(spacing: 6) {
                ForEach(Array(picks), id: \.self) { s in
                    Button(action: {
                        Haptic.chip()
                        if state.coverLines.count < 4 {
                            state.coverLines.append(s)
                        }
                    }) {
                        Text(s)
                            .font(Serif.body(12))
                            .foregroundStyle(Palette.ink)
                            .padding(.horizontal, 10).padding(.vertical, 6)
                            .overlay(Capsule().stroke(Palette.hairline, lineWidth: 1))
                            .clipShape(Capsule())
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }
}

private struct Step6Pose: View {
    @Binding var state: CoverWizardState
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Placeholder pose.")
                .font(Serif.display(22)).foregroundStyle(Palette.ink)
            Text("What shape should the placeholder subject take? This is a stand-in — later, when you compose a real cover, your photo replaces it.")
                .font(Serif.italic(14)).foregroundStyle(Palette.muted)
            VStack(spacing: 8) {
                ForEach(POSES, id: \.0) { key, name, desc in
                    OptionRow(title: name, subtitle: desc, on: state.pose == key) {
                        Haptic.chip()
                        state.pose = key
                    }
                }
            }
        }
    }
}

private struct Step7Palette: View {
    @Binding var state: CoverWizardState
    private let cols = [GridItem(.flexible()), GridItem(.flexible())]
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Choose a background.")
                .font(Serif.display(22)).foregroundStyle(Palette.ink)
            Text("Type color is picked automatically to stay readable on the chosen background.")
                .font(Serif.italic(14)).foregroundStyle(Palette.muted)
            LazyVGrid(columns: cols, spacing: 10) {
                ForEach(COVER_COLORS, id: \.0) { name, hex, desc in
                    Button(action: {
                        Haptic.chip()
                        state.color = hex
                    }) {
                        HStack(spacing: 10) {
                            Circle()
                                .fill(swatchColor(hex) ?? .clear)
                                .frame(width: 32, height: 32)
                                .overlay(Circle().stroke(Palette.hairline, lineWidth: 1))
                            VStack(alignment: .leading, spacing: 2) {
                                Text(name)
                                    .font(Serif.body(14, weight: .semibold))
                                    .foregroundStyle(Palette.ink)
                                Text(desc)
                                    .font(Serif.italic(11))
                                    .foregroundStyle(Palette.muted)
                                    .lineLimit(2)
                            }
                            Spacer()
                        }
                        .padding(10)
                        .background(state.color.lowercased() == hex.lowercased() ? Palette.card : Color.clear)
                        .overlay(
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(state.color.lowercased() == hex.lowercased() ? Palette.bronze : Palette.hairline,
                                        lineWidth: state.color.lowercased() == hex.lowercased() ? 2 : 1)
                        )
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    private func swatchColor(_ hex: String) -> Color? {
        var s = hex; if s.hasPrefix("#") { s.removeFirst() }
        guard s.count == 6, let v = UInt32(s, radix: 16) else { return nil }
        return Color(red: Double((v >> 16) & 0xff) / 255,
                     green: Double((v >> 8) & 0xff) / 255,
                     blue: Double(v & 0xff) / 255)
    }
}

private struct Step8Review: View {
    let state: CoverWizardState
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Ready to set.")
                .font(Serif.display(22)).foregroundStyle(Palette.ink)
            Text("This is what your cover will look like. Nothing is saved yet — hit Create when you're happy.")
                .font(Serif.italic(14)).foregroundStyle(Palette.muted)

            LivePreviewCard(state: state)
                .padding(.top, 8)

            VStack(spacing: 8) {
                summaryRow("Genre",    state.genre ?? "—")
                summaryRow("Mood",     state.mood ?? "—")
                summaryRow("Masthead", state.masthead)
                summaryRow("Position", state.layout.capitalized)
                summaryRow("Headline", state.headline)
                if !state.pullQuote.isEmpty {
                    summaryRow("Pull",  "“\(state.pullQuote)”")
                }
                if !state.coverLines.isEmpty {
                    summaryRow("Lines", state.coverLines.joined(separator: " · "))
                }
                summaryRow("Pose",     state.pose.replacingOccurrences(of: "_", with: " ").capitalized)
                summaryRow("Color",    state.color.uppercased())
            }
            .padding(.top, 10)
        }
    }

    private func summaryRow(_ label: String, _ value: String) -> some View {
        HStack(alignment: .top, spacing: 12) {
            Text(label.uppercased())
                .font(.system(size: 10, weight: .semibold)).tracking(1.5)
                .foregroundStyle(Palette.bronze)
                .frame(width: 78, alignment: .leading)
            Text(value)
                .font(Serif.body(14))
                .foregroundStyle(Palette.ink)
                .multilineTextAlignment(.leading)
            Spacer()
        }
    }
}

// MARK: - Live preview swatch

private struct LivePreviewCard: View {
    let state: CoverWizardState
    var body: some View {
        let bg = swatchColor(state.color) ?? Palette.card
        let textColor: Color = isDark(state.color) ? .white : Palette.ink
        VStack(alignment: state.layout == "center" ? .center : .leading, spacing: 8) {
            if state.layout == "top" {
                mastheadBlock(textColor: textColor)
                Spacer()
            }
            if state.layout == "center" { Spacer() }
            if state.layout == "center" { mastheadBlock(textColor: textColor) }
            let hl = state.headline.isEmpty ? "YOUR HEADLINE" : state.headline
            Text(hl)
                .font(.system(size: 22, weight: .bold, design: .serif))
                .foregroundStyle(textColor)
                .kerning(1)
                .multilineTextAlignment(state.layout == "center" ? .center : .leading)
                .lineLimit(3)
            if !state.pullQuote.isEmpty {
                Text("“\(state.pullQuote)”")
                    .font(.system(size: 13, design: .serif))
                    .italic()
                    .foregroundStyle(textColor.opacity(0.7))
                    .multilineTextAlignment(state.layout == "center" ? .center : .leading)
                    .lineLimit(3)
            }
            if state.layout == "bottom" {
                Spacer()
                mastheadBlock(textColor: textColor)
            }
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: state.layout == "center" ? .center : .leading)
        .aspectRatio(9.0/16.0, contentMode: .fit)
        .background(bg)
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Palette.hairline, lineWidth: 1))
    }

    private func mastheadBlock(textColor: Color) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(state.masthead)
                .font(.system(size: 24, weight: .bold, design: .serif))
                .foregroundStyle(textColor)
                .kerning(3)
            Text("VOL 47 · JULY")
                .font(.system(size: 9, weight: .medium, design: .serif))
                .italic()
                .foregroundStyle(textColor.opacity(0.7))
        }
    }

    private func swatchColor(_ hex: String) -> Color? {
        var s = hex; if s.hasPrefix("#") { s.removeFirst() }
        guard s.count == 6, let v = UInt32(s, radix: 16) else { return nil }
        return Color(red: Double((v >> 16) & 0xff) / 255,
                     green: Double((v >> 8) & 0xff) / 255,
                     blue: Double(v & 0xff) / 255)
    }
    private func isDark(_ hex: String) -> Bool {
        var s = hex; if s.hasPrefix("#") { s.removeFirst() }
        guard s.count == 6, let v = UInt32(s, radix: 16) else { return false }
        let r = Double((v >> 16) & 0xff) / 255
        let g = Double((v >> 8) & 0xff) / 255
        let b = Double(v & 0xff) / 255
        return 0.299 * r + 0.587 * g + 0.114 * b < 0.5
    }
}

// MARK: - OptionRow (radio-style)

private struct OptionRow: View {
    let title: String
    let subtitle: String
    let on: Bool
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(alignment: .top, spacing: 12) {
                Circle()
                    .fill(on ? Palette.bronze : Color.clear)
                    .overlay(Circle().stroke(on ? Palette.bronze : Palette.hairline, lineWidth: 1))
                    .frame(width: 18, height: 18)
                    .padding(.top, 2)
                VStack(alignment: .leading, spacing: 3) {
                    Text(title)
                        .font(Serif.body(15, weight: .semibold))
                        .foregroundStyle(Palette.ink)
                    Text(subtitle)
                        .font(Serif.italic(13))
                        .foregroundStyle(Palette.muted)
                }
                Spacer()
            }
            .padding(12)
            .background(on ? Palette.card : Color.clear)
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(on ? Palette.bronze : Palette.hairline, lineWidth: on ? 2 : 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: 12))
        }
        .buttonStyle(.plain)
    }
}
