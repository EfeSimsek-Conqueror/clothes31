import SwiftUI

/// Cold-start empty state on Home. Two big cards prompting the two on-ramps
/// (camera / studio). Renders when the profile has `first_run_done=false` and
/// no outfits + no closet items exist yet.
struct FirstRunBanner: View {
    var onOpenCamera: () -> Void = {}
    var onOpenStudio: () -> Void = {}

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Let's start your closet.")
                .font(Serif.display(34))
                .foregroundStyle(Palette.ink)
                .fixedSize(horizontal: false, vertical: true)
            Text("Two quick ways in — pick whichever suits you today.")
                .font(Serif.body(15))
                .foregroundStyle(Palette.muted)
            Spacer(minLength: 12)

            card(eyebrow: "01 · CAMERA",
                 title: "Snap your first look",
                 subtitle: "Score a fit you're wearing.",
                 primary: true) {
                Haptic.tap()
                onOpenCamera()
            }

            card(eyebrow: "02 · STUDIO",
                 title: "Design a piece",
                 subtitle: "Generate a garment from scratch.",
                 primary: false) {
                Haptic.tap()
                onOpenStudio()
            }
        }
    }

    @ViewBuilder
    private func card(eyebrow: String, title: String, subtitle: String, primary: Bool, action: @escaping () -> Void) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Eyebrow(text: eyebrow)
            Text(title)
                .font(Serif.display(22))
                .foregroundStyle(Palette.ink)
            Text(subtitle)
                .font(Serif.body(14))
                .foregroundStyle(Palette.muted)
            Spacer(minLength: 12)
            if primary {
                PrimaryButton(title: "Open camera", action: action)
            } else {
                SecondaryButton(title: "Open studio", action: action)
            }
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Palette.card)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(Palette.hairline, lineWidth: 1))
    }
}
