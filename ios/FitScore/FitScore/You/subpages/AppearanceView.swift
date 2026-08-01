import SwiftUI

/// Top-level app-wide preference singleton read by the theme layer.
/// Persisted to Supabase via `Repo.updateAppearance` — this only holds the
/// in-memory copy.
@MainActor
final class AppPrefs: ObservableObject {
    static let shared = AppPrefs()
    private init() {}
    @Published var theme: String = "system"
    @Published var textScale: Double = 1.0
    @Published var reduceMotion: Bool = false
}

struct AppearanceView: View {
    @EnvironmentObject var toasts: ToastBus
    @ObservedObject private var prefs = AppPrefs.shared

    @State private var loaded = false
    @State private var theme = "system"
    @State private var scale = 1.0
    @State private var reduceMotion = false

    var body: some View {
        SubpageScaffold(eyebrow: "SETTINGS", title: "Appearance") {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Eyebrow(text: "THEME")
                    Segmented(options: [("system","System"),("light","Light"),("dark","Dark")], selected: theme) {
                        theme = $0; prefs.theme = $0; persist()
                    }

                    Eyebrow(text: "TEXT SIZE")
                    let scaleKey: String = scale <= 0.9 ? "small" : (scale >= 1.15 ? "large" : "regular")
                    Segmented(options: [("small","Small"),("regular","Regular"),("large","Large")], selected: scaleKey) { k in
                        scale = k == "small" ? 0.9 : (k == "large" ? 1.2 : 1.0)
                        prefs.textScale = scale
                        persist()
                    }

                    Eyebrow(text: "MOTION")
                    Hairline()
                    Toggle(isOn: $reduceMotion) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Reduce animations").font(Serif.body(15, weight: .semibold)).foregroundStyle(Palette.ink)
                            Text("Skip transitions and shimmer effects.").font(Serif.body(13)).foregroundStyle(Palette.muted)
                        }
                    }
                    .tint(Palette.ink)
                    .onChange(of: reduceMotion) { _, v in prefs.reduceMotion = v; persist() }
                    Hairline()

                    Text("Text size and theme apply on next app launch.")
                        .font(Serif.body(12)).foregroundStyle(Palette.muted)
                    Spacer().frame(height: 40)
                }
                .padding(20)
            }
        }
        .task { await load() }
    }

    private func load() async {
        if let p = try? await Repo.shared.currentProfile() {
            theme = p.theme ?? "system"
            scale = p.text_scale ?? 1.0
            reduceMotion = p.reduce_motion ?? false
        }
        prefs.theme = theme
        prefs.textScale = scale
        prefs.reduceMotion = reduceMotion
        loaded = true
    }
    private func persist() {
        Task {
            do {
                try await Repo.shared.updateAppearance(theme: theme, textScale: scale, reduceMotion: reduceMotion)
            } catch {
                toasts.post("Couldn't save: \(error.localizedDescription)")
            }
        }
    }
}

private struct Segmented: View {
    let options: [(String, String)]
    let selected: String
    let onSelect: (String) -> Void
    var body: some View {
        HStack(spacing: 0) {
            ForEach(options, id: \.0) { (key, label) in
                let sel = key == selected
                Text(label.uppercased())
                    .font(.system(size: 11, weight: .semibold)).tracking(2)
                    .foregroundStyle(sel ? .white : Palette.ink)
                    .frame(maxWidth: .infinity)
                    .frame(height: 44)
                    .background(sel ? Palette.ink : Color.clear)
                    .contentShape(Rectangle())
                    .onTapGesture { onSelect(key) }
            }
        }
        .overlay(RoundedRectangle(cornerRadius: 10).stroke(Palette.hairline, lineWidth: 1))
        .clipShape(RoundedRectangle(cornerRadius: 10))
    }
}
