import SwiftUI

struct ScoreLookSheet: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                HStack {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Score a look.")
                            .font(Serif.display(30))
                            .foregroundStyle(Palette.ink)
                        Text("One photo of you, one of the fit — Hem does the rest.")
                            .font(Serif.body(14)).foregroundStyle(Palette.muted)
                    }
                    Spacer()
                    Button { dismiss() } label: {
                        Image(systemName: "xmark").foregroundStyle(Palette.ink).font(.system(size: 15, weight: .semibold))
                    }
                }

                Eyebrow(text: "SCORING")
                group([
                    ("Outfit score",       "How the whole thing lands",       "1 PHOTO"),
                    ("Piece-by-piece",     "Fit, color, proportion per item",  "1 PHOTO"),
                    ("Deep sub-scores",    "Silhouette, palette, occasion",    "1 PHOTO"),
                    ("Occasion score",     "Read against where you're going",  "1 PHOTO"),
                    ("Swap suggestions",   "Better pieces from your closet",   "2 PHOTOS"),
                    ("Before / after",     "See your swap on your body",       "2 PHOTOS"),
                ])

                Eyebrow(text: "MIRROR & CLOSET")
                group([
                    ("Virtual try-on",     "Try any piece on your photo",     "2 PHOTOS"),
                    ("Digital closet",     "Auto-tagged and searchable",      "NO CAMERA"),
                    ("Outfit builder",     "Drag-and-drop your fits",         "NO CAMERA"),
                    ("Weather outfit",     "Tuned to today's forecast",       "NO CAMERA"),
                ])
            }
            .padding(24)
            .padding(.bottom, 40)
        }
        .background(Palette.paper.ignoresSafeArea())
        .presentationDetents([.large])
    }

    @ViewBuilder
    private func group(_ rows: [(String,String,String)]) -> some View {
        VStack(spacing: 0) {
            Hairline()
            ForEach(rows.indices, id: \.self) { i in
                SectionRow(title: rows[i].0, subtitle: rows[i].1, badge: rows[i].2)
                Hairline()
            }
        }
    }
}
