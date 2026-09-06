import SwiftUI

/// Shows a freshly saved row's output the moment a tool finishes, on top of the
/// tool that made it.
///
/// The tools live inside `MainTabView`'s own `fullScreenCover`, so presenting a
/// second cover from one of them tears down the outer presentation (the same
/// trap documented on `ScoreDetailView`'s X-ray). An overlay slides in over the
/// tool instead, and dismissing it returns to the tool with its own result
/// still on screen.
struct JustSavedOverlay: ViewModifier {
    @Binding var outfitId: String?

    @State private var outfit: Outfit?
    @State private var loading = false

    func body(content: Content) -> some View {
        content.overlay {
            if outfitId != nil {
                ZStack {
                    Palette.paper.ignoresSafeArea()
                    if let o = outfit {
                        if let summary = o.versusSummary {
                            VersusDetailView(outfit: o, summary: summary, onClose: dismiss)
                        } else {
                            ToolResultView(outfit: o, onClose: dismiss)
                        }
                    } else {
                        VStack(spacing: 14) {
                            ProgressView().tint(Palette.bronze)
                            Text("Saving to your journal…")
                                .font(Serif.body(14))
                                .foregroundStyle(Palette.muted)
                        }
                    }
                }
                .transition(.move(edge: .bottom).combined(with: .opacity))
                .zIndex(20)
                .task(id: outfitId) { await load() }
            }
        }
        .animation(.spring(response: 0.42, dampingFraction: 0.9), value: outfitId)
    }

    private func dismiss() {
        Haptic.tap()
        outfit = nil
        outfitId = nil
    }

    private func load() async {
        guard let id = outfitId, !loading else { return }
        loading = true
        outfit = try? await Repo.shared.outfitById(id)
        loading = false
    }
}

extension View {
    /// Present the output of a row the tool just wrote. Set the binding to the
    /// new row's id; clearing it returns to the tool.
    func justSaved(_ outfitId: Binding<String?>) -> some View {
        modifier(JustSavedOverlay(outfitId: outfitId))
    }
}
