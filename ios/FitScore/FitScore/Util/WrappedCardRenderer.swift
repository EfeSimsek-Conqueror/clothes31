import SwiftUI
import UIKit

/// 1080x1920 Wrapped Card SwiftUI view + PNG renderer. Designed for IG story
/// share (9:16). Keep this file self-contained so the renderer stays fast.
struct WrappedCard: View {
    let dna: StyleDna
    let heroImage: UIImage?

    var body: some View {
        ZStack {
            Palette.paper.ignoresSafeArea()

            // Ornamental corner marks
            VStack {
                HStack {
                    cornerMark
                    Spacer()
                    cornerMark
                }
                Spacer()
                HStack {
                    cornerMark
                    Spacer()
                    cornerMark
                }
            }
            .padding(48)

            VStack(spacing: 32) {
                Spacer().frame(height: 40)

                // Masthead
                VStack(spacing: 8) {
                    Text("YOUR STYLE")
                        .font(.system(size: 20, weight: .semibold))
                        .tracking(4)
                        .foregroundStyle(Palette.bronze)
                    Text(monthTitle)
                        .font(Serif.display(64))
                        .foregroundStyle(Palette.ink)
                }

                // Subline
                Text(dna.subline)
                    .font(Serif.body(24).italic())
                    .foregroundStyle(Palette.muted)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 60)

                // Hero fit
                if let img = heroImage {
                    Image(uiImage: img)
                        .resizable()
                        .scaledToFill()
                        .frame(width: 520, height: 680)
                        .clipShape(RoundedRectangle(cornerRadius: 6))
                        .shadow(color: Palette.ink.opacity(0.12), radius: 20, x: 0, y: 8)
                } else {
                    RoundedRectangle(cornerRadius: 6)
                        .fill(Palette.muted.opacity(0.15))
                        .frame(width: 520, height: 680)
                        .overlay(
                            Text("YOUR MONTH")
                                .font(.system(size: 24, weight: .semibold))
                                .tracking(3)
                                .foregroundStyle(Palette.muted)
                        )
                }

                // Palette strip
                if !dna.paletteHex.isEmpty {
                    HStack(spacing: 12) {
                        ForEach(dna.paletteHex, id: \.self) { hex in
                            Circle()
                                .fill(Color(hex: hex) ?? Palette.muted.opacity(0.3))
                                .frame(width: 44, height: 44)
                                .overlay(Circle().stroke(Palette.ink.opacity(0.15), lineWidth: 1))
                        }
                    }
                }

                // Stats trio
                HStack(spacing: 40) {
                    statCol("FITS", "\(dna.fitCount)")
                    if let a = dna.avgScore {
                        statCol("AVG", String(format: "%.1f", a))
                    }
                    if let b = dna.bestScore {
                        statCol("BEST", String(format: "%.1f", b))
                    }
                    if let occ = dna.topOccasion {
                        statCol("MOSTLY", occ.uppercased())
                    }
                }
                .padding(.top, 12)

                Spacer()

                // Footer
                VStack(spacing: 6) {
                    HStack(spacing: 6) {
                        Text("\u{2726}")
                            .font(.system(size: 18))
                            .foregroundStyle(Palette.bronze)
                        Text("FITRATER")
                            .font(.system(size: 16, weight: .semibold))
                            .tracking(4)
                            .foregroundStyle(Palette.ink)
                    }
                    Text("hem.fit")
                        .font(Serif.body(15).italic())
                        .foregroundStyle(Palette.muted)
                }
                .padding(.bottom, 60)
            }
            .padding(.horizontal, 40)
        }
        .frame(width: 1080, height: 1920)
    }

    private var monthTitle: String {
        if dna.month.isEmpty { return "All-Time" }
        let parts = dna.month.split(separator: "-")
        guard parts.count == 2, let m = Int(parts[1]), (1...12).contains(m) else { return dna.month }
        let names = ["January","February","March","April","May","June","July","August","September","October","November","December"]
        return "\(names[m-1]) \(parts[0])"
    }

    private var cornerMark: some View {
        VStack(alignment: .leading, spacing: 0) {
            Rectangle().fill(Palette.bronze).frame(width: 40, height: 1)
            Rectangle().fill(Palette.bronze).frame(width: 1, height: 40)
        }
        .fixedSize()
    }

    private func statCol(_ label: String, _ value: String) -> some View {
        VStack(spacing: 6) {
            Text(label)
                .font(.system(size: 12, weight: .semibold))
                .tracking(2)
                .foregroundStyle(Palette.bronze)
            Text(value)
                .font(Serif.display(28))
                .foregroundStyle(Palette.ink)
        }
    }
}

@MainActor
enum WrappedCardRenderer {
    static func render(dna: StyleDna, heroImage: UIImage?) -> UIImage? {
        let card = WrappedCard(dna: dna, heroImage: heroImage)
        let renderer = ImageRenderer(content: card)
        renderer.scale = 1
        renderer.proposedSize = ProposedViewSize(width: 1080, height: 1920)
        return renderer.uiImage
    }
}

// Small Color hex helper
extension Color {
    init?(hex: String) {
        var s = hex
        if s.hasPrefix("#") { s.removeFirst() }
        guard s.count == 6, let v = Int(s, radix: 16) else { return nil }
        let r = Double((v >> 16) & 0xFF) / 255
        let g = Double((v >> 8) & 0xFF) / 255
        let b = Double(v & 0xFF) / 255
        self.init(red: r, green: g, blue: b)
    }
}
