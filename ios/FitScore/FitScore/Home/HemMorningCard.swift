import SwiftUI

/// "HEM · THIS MORNING" tan-bordered card containing an italic serif pull
/// quote of the latest hem_note body. Rendered above LATEST when present.
struct HemMorningCard: View {
    let body_: String

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Eyebrow(text: "HEM · THIS MORNING")
            TanCard {
                Text("\u{201C}\(body_)\u{201D}")
                    .font(Serif.italic(20))
                    .foregroundStyle(Palette.ink)
                    .fixedSize(horizontal: false, vertical: true)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }
}
