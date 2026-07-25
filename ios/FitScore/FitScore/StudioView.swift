import SwiftUI

struct StudioView: View {
    @EnvironmentObject var session: SessionStore
    @Binding var showPaywall: Bool

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                HStack(alignment: .firstTextBaseline) {
                    Text("Studio")
                        .font(Serif.display(40))
                        .foregroundStyle(Palette.ink)
                    Spacer()
                    Button { showPaywall = true } label: {
                        Text("✦ \(session.credits)")
                            .font(Serif.body(13, weight: .semibold))
                            .foregroundStyle(Palette.ink)
                            .padding(.horizontal, 12).padding(.vertical, 6)
                            .overlay(Capsule().stroke(Palette.bronze, lineWidth: 1))
                    }
                }
                Text("Build looks, try pieces on your photo, and let Hem sanity-check them.")
                    .font(Serif.body(15)).foregroundStyle(Palette.muted)

                Eyebrow(text: "YOUR PIECES")

                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 14) {
                        ForEach(Seeds.pieces) { p in
                            VStack(alignment: .leading, spacing: 10) {
                                RoundedRectangle(cornerRadius: 4)
                                    .fill(p.tint.opacity(0.85))
                                    .frame(width: 160, height: 200)
                                Text(p.name).font(Serif.body(13, weight: .medium))
                                    .foregroundStyle(Palette.ink)
                                    .lineLimit(2)
                                    .frame(width: 160, alignment: .leading)
                            }
                        }
                    }
                    .padding(.horizontal, 2)
                }

                VStack(spacing: 0) {
                    Hairline()
                    HStack(spacing: 0) {
                        studioCell(title: "+  Create New", subtitle: "Style an outfit")
                        Rectangle().fill(Palette.hairline).frame(width: 0.5, height: 76)
                        studioCell(title: "↑  Import", subtitle: "From camera roll")
                    }
                    Hairline()
                }
            }
            .padding(22)
        }
        .background(Palette.paper.ignoresSafeArea())
    }

    @ViewBuilder
    private func studioCell(title: String, subtitle: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title).font(Serif.display(18)).foregroundStyle(Palette.ink)
            Text(subtitle).font(Serif.body(12)).foregroundStyle(Palette.muted)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.vertical, 18).padding(.horizontal, 4)
    }
}
