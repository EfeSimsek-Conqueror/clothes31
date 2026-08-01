import SwiftUI

private let GENDERS = ["Female", "Male", "Other"]
private let STYLE_TAGS = ["minimal","streetwear","classic","romantic","sporty","bold prints","earth tones","monochrome"]
private let FABRIC_OPTIONS = ["Cotton","Linen","Denim","Wool","Leather","Silk","Knit"]

private struct Swatch: Hashable { let name: String; let hex: String }
private let COLOR_OPTIONS: [Swatch] = [
    .init(name: "Bone", hex: "#EAE1D0"), .init(name: "Cream", hex: "#F3EEE4"), .init(name: "Sand", hex: "#D7C4A3"),
    .init(name: "Cocoa", hex: "#5B3A22"), .init(name: "Ink", hex: "#141210"), .init(name: "Bronze", hex: "#B0743A"),
    .init(name: "Rust", hex: "#9E4A22"), .init(name: "Sage", hex: "#93A187"), .init(name: "Olive", hex: "#5C6032"),
    .init(name: "Slate", hex: "#5A6B73"), .init(name: "Wine", hex: "#5A1E29"), .init(name: "Powder", hex: "#B7C9D9"),
]

struct StyleProfileSheet: View {
    let onClose: () -> Void
    @EnvironmentObject var toasts: ToastBus

    @State private var loaded = false
    @State private var gender: String? = nil
    @State private var tags: Set<String> = []
    @State private var fabrics: Set<String> = []
    @State private var colors: Set<String> = []
    @State private var sensitivities = ""
    @State private var saving = false

    @State private var savedGender: String? = nil
    @State private var savedTags: [String] = []

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                HStack(alignment: .top) {
                    VStack(alignment: .leading, spacing: 4) {
                        Eyebrow(text: "STYLE PROFILE")
                        Text("Style profile").font(Serif.display(30)).foregroundStyle(Palette.ink)
                    }
                    Spacer()
                    Button(action: onClose) {
                        Text("×").font(Serif.display(28)).foregroundStyle(Palette.ink)
                    }
                }
                Text("Hem uses this to tune every generation and score.")
                    .font(Serif.body(14)).foregroundStyle(Palette.muted)

                if loaded, savedGender != nil || !savedTags.isEmpty {
                    currentCard
                }

                Eyebrow(text: "GENDER")
                HStack(spacing: 8) {
                    ForEach(GENDERS, id: \.self) { g in
                        RadioPill(label: g, selected: gender == g) { gender = g }
                    }
                }

                Eyebrow(text: "STYLE TAGS")
                FlowLayout(spacing: 8) {
                    ForEach(STYLE_TAGS, id: \.self) { t in
                        SimpleChip(label: t, active: tags.contains(t)) {
                            if tags.contains(t) { tags.remove(t) } else { tags.insert(t) }
                        }
                    }
                }

                Eyebrow(text: "PREFERRED FABRICS")
                FlowLayout(spacing: 8) {
                    ForEach(FABRIC_OPTIONS, id: \.self) { f in
                        SimpleChip(label: f, active: fabrics.contains(f)) {
                            if fabrics.contains(f) { fabrics.remove(f) } else { fabrics.insert(f) }
                        }
                    }
                }

                Eyebrow(text: "PREFERRED COLORS")
                FlowLayout(spacing: 8) {
                    ForEach(COLOR_OPTIONS, id: \.self) { c in
                        SwatchChip(swatch: c, active: colors.contains(c.name)) {
                            if colors.contains(c.name) { colors.remove(c.name) } else { colors.insert(c.name) }
                        }
                    }
                }

                Eyebrow(text: "SENSITIVITIES")
                ZStack(alignment: .topLeading) {
                    RoundedRectangle(cornerRadius: 12).fill(Palette.card)
                        .frame(height: 80)
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Palette.hairline, lineWidth: 1))
                    if sensitivities.isEmpty {
                        Text("e.g. avoid animal materials, allergic to wool…")
                            .font(Serif.body(14)).foregroundStyle(Palette.muted)
                            .padding(14)
                    }
                    TextEditor(text: $sensitivities)
                        .font(Serif.body(14))
                        .foregroundStyle(Palette.ink)
                        .scrollContentBackground(.hidden)
                        .padding(8)
                        .frame(height: 80)
                }

                PrimaryButton(title: saving ? "Saving…" : "Save changes", enabled: loaded && !saving) {
                    save()
                }
                Spacer().frame(height: 24)
            }
            .padding(20)
        }
        .background(Palette.paper.ignoresSafeArea())
        .task { await load() }
    }

    private var currentCard: some View {
        VStack(alignment: .leading, spacing: 6) {
            Eyebrow(text: "CURRENT")
            Text(savedGender ?? "—").font(Serif.body(15, weight: .semibold)).foregroundStyle(Palette.ink)
            if !savedTags.isEmpty {
                FlowLayout(spacing: 6) {
                    ForEach(savedTags, id: \.self) { tag in
                        Text(tag).font(.system(size: 12)).foregroundStyle(Palette.muted)
                            .padding(.horizontal, 10).padding(.vertical, 4)
                            .overlay(Capsule().stroke(Palette.hairline, lineWidth: 1))
                    }
                }
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Palette.card)
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Palette.hairline, lineWidth: 1))
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    private func load() async {
        if let p = try? await Repo.shared.currentProfile() {
            gender = p.gender; savedGender = p.gender
            if let t = p.style_tags { tags = Set(t); savedTags = t }
            if let f = p.preferred_fabrics { fabrics = Set(f) }
            if let c = p.preferred_colors { colors = Set(c) }
            sensitivities = p.sensitivities ?? ""
        }
        loaded = true
    }
    private func save() {
        saving = true
        Task {
            do {
                try await Repo.shared.updateStyleProfile(
                    gender: gender,
                    styleTags: Array(tags),
                    preferredFabrics: Array(fabrics),
                    preferredColors: Array(colors),
                    sensitivities: sensitivities.isEmpty ? "" : sensitivities
                )
                toasts.post("Style profile updated")
                onClose()
            } catch {
                toasts.post("Couldn't save: \(error.localizedDescription)")
            }
            saving = false
        }
    }
}

private struct RadioPill: View {
    let label: String
    let selected: Bool
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Text(label)
                .font(Serif.body(14, weight: .medium))
                .foregroundStyle(selected ? .white : Palette.ink)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 12)
                .background(selected ? Palette.ink : Color.clear)
                .overlay(Capsule().stroke(Palette.ink.opacity(0.4), lineWidth: 1))
                .clipShape(Capsule())
        }
        .buttonStyle(.plain)
    }
}

private struct SimpleChip: View {
    let label: String
    let active: Bool
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Text(label)
                .font(Serif.body(13, weight: .medium))
                .foregroundStyle(active ? .white : Palette.ink)
                .padding(.horizontal, 14).padding(.vertical, 8)
                .background(active ? Palette.ink : Color.clear)
                .overlay(Capsule().stroke(Palette.ink.opacity(0.4), lineWidth: 1))
                .clipShape(Capsule())
        }
        .buttonStyle(.plain)
    }
}

private struct SwatchChip: View {
    let swatch: Swatch
    let active: Bool
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                Circle().fill(parseHexColor(swatch.hex) ?? Palette.muted)
                    .frame(width: 20, height: 20)
                    .overlay(Circle().stroke(.white.opacity(0.6), lineWidth: 1))
                Text(swatch.name)
                    .font(Serif.body(13, weight: .medium))
                    .foregroundStyle(active ? .white : Palette.ink)
            }
            .padding(.leading, 6).padding(.trailing, 12).padding(.vertical, 4)
            .background(active ? Palette.ink : Color.clear)
            .overlay(Capsule().stroke(Palette.ink.opacity(0.4), lineWidth: 1))
            .clipShape(Capsule())
        }
        .buttonStyle(.plain)
    }
}
