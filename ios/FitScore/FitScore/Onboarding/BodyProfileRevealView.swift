import SwiftUI

/// Post-calibration reveal + persistent viewer. Reads from the passed-in
/// BodyProfile and renders three editorial cards (Shape / Coloring / What this
/// changes). Used both as the onboarding reveal screen and from the You sheet
/// as the persistent body-profile viewer.
struct BodyProfileRevealView: View {
    let profile: BodyProfile
    var onDone: () -> Void
    var onRecalibrate: (() -> Void)? = nil

    var body: some View {
        ZStack {
            Palette.paper.ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    Spacer(minLength: 20)
                    Eyebrow(text: "YOUR BASELINE")
                    Text("Locked in.")
                        .font(Serif.display(34))
                        .foregroundStyle(Palette.ink)
                        .padding(.bottom, 6)

                    shapeCard
                    coloringCard
                    impactCard

                    Spacer(minLength: 20)

                    PrimaryButton(title: "Continue", action: onDone)

                    if let onRecalibrate {
                        Button(action: onRecalibrate) {
                            Text("Recalibrate")
                                .font(Serif.body(14))
                                .foregroundStyle(Palette.muted)
                                .frame(maxWidth: .infinity)
                                .padding(.top, 10)
                        }
                    }
                    Spacer(minLength: 30)
                }
                .padding(.horizontal, 24)
            }
        }
    }

    // MARK: - Cards

    private var shapeCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Eyebrow(text: "SHAPE")
            HStack(alignment: .top, spacing: 14) {
                ShapeBadge(shape: profile.body_shape ?? "rectangle")
                    .frame(width: 60, height: 88)
                VStack(alignment: .leading, spacing: 4) {
                    Text(displayShapeName(profile.body_shape))
                        .font(Serif.display(20))
                        .foregroundStyle(Palette.ink)
                    if let s = profile.shoulder_hip_ratio, let t = profile.torso_leg_ratio {
                        Text(String(format: "Shoulder:hip %.2f  ·  Torso:leg %.2f", s, t))
                            .font(Serif.body(12))
                            .foregroundStyle(Palette.muted)
                    }
                    if let n = profile.notes, !n.isEmpty {
                        Text(n)
                            .font(Serif.italic(14))
                            .foregroundStyle(Palette.ink.opacity(0.75))
                            .padding(.top, 2)
                    }
                }
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Palette.card)
        .clipShape(RoundedRectangle(cornerRadius: 18))
        .overlay(RoundedRectangle(cornerRadius: 18).stroke(Palette.hairline, lineWidth: 1))
    }

    private var coloringCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            Eyebrow(text: "COLORING")
            HStack(spacing: 6) {
                ForEach(0..<6, id: \.self) { i in
                    let hex = (profile.palette_hex ?? [])[safe: i] ?? "#D9CDBF"
                    RoundedRectangle(cornerRadius: 6)
                        .fill(Color(paletteHex: hex) ?? Palette.card)
                        .aspectRatio(1, contentMode: .fit)
                        .overlay(RoundedRectangle(cornerRadius: 6).stroke(Palette.hairline, lineWidth: 0.5))
                }
            }
            .padding(.vertical, 4)
            Text(displaySeasonName(profile.coloring_season))
                .font(Serif.display(18))
                .foregroundStyle(Palette.ink)
            if let u = profile.skin_undertone {
                Text("\(u.capitalized) undertone.")
                    .font(Serif.italic(14))
                    .foregroundStyle(Palette.muted)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Palette.card)
        .clipShape(RoundedRectangle(cornerRadius: 18))
        .overlay(RoundedRectangle(cornerRadius: 18).stroke(Palette.hairline, lineWidth: 1))
    }

    private var impactCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            Eyebrow(text: "WHAT THIS CHANGES")
            VStack(alignment: .leading, spacing: 8) {
                bullet("Every score is measured against your proportions.")
                bullet("Try-on results use your actual scale.")
                bullet("Studio favors your palette.")
                bullet("Sunday Letter reads for your body type.")
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Palette.card)
        .clipShape(RoundedRectangle(cornerRadius: 18))
        .overlay(RoundedRectangle(cornerRadius: 18).stroke(Palette.hairline, lineWidth: 1))
    }

    private func bullet(_ text: String) -> some View {
        HStack(alignment: .top, spacing: 10) {
            Circle().fill(Palette.bronze).frame(width: 5, height: 5).padding(.top, 8)
            Text(text)
                .font(Serif.body(14))
                .foregroundStyle(Palette.ink)
        }
    }

    // MARK: - Labels

    private func displayShapeName(_ raw: String?) -> String {
        switch raw {
        case "inverted_triangle": return "Inverted triangle."
        case "triangle":          return "Triangle."
        case "hourglass":         return "Hourglass."
        case "rectangle":         return "Rectangle."
        case "apple":             return "Round."
        default:                  return "Balanced."
        }
    }

    private func displaySeasonName(_ raw: String?) -> String {
        switch raw {
        case "spring": return "Spring."
        case "summer": return "Summer."
        case "autumn": return "Autumn."
        case "winter": return "Winter."
        default:       return "Neutral palette."
        }
    }
}

// MARK: - Shape badge

private struct ShapeBadge: View {
    let shape: String
    var body: some View {
        Canvas { ctx, size in
            let w = size.width, h = size.height
            let stroke = Palette.bronze
            var path = Path()
            switch shape {
            case "inverted_triangle":
                path.move(to: CGPoint(x: 0, y: 0))
                path.addLine(to: CGPoint(x: w, y: 0))
                path.addLine(to: CGPoint(x: w * 0.5, y: h))
                path.closeSubpath()
            case "triangle":
                path.move(to: CGPoint(x: w * 0.5, y: 0))
                path.addLine(to: CGPoint(x: 0, y: h))
                path.addLine(to: CGPoint(x: w, y: h))
                path.closeSubpath()
            case "hourglass":
                path.move(to: CGPoint(x: 0, y: 0))
                path.addLine(to: CGPoint(x: w, y: 0))
                path.addLine(to: CGPoint(x: w * 0.35, y: h * 0.5))
                path.addLine(to: CGPoint(x: w, y: h))
                path.addLine(to: CGPoint(x: 0, y: h))
                path.addLine(to: CGPoint(x: w * 0.65, y: h * 0.5))
                path.closeSubpath()
            case "apple":
                path.addEllipse(in: CGRect(x: 0, y: h * 0.1, width: w, height: h * 0.8))
            default: // rectangle
                path.addRect(CGRect(x: w * 0.15, y: 0, width: w * 0.7, height: h))
            }
            ctx.stroke(path, with: .color(stroke), lineWidth: 1.5)
        }
    }
}

// MARK: - Small helpers

private extension Array {
    subscript(safe index: Int) -> Element? {
        indices.contains(index) ? self[index] : nil
    }
}

private extension Color {
    init?(paletteHex: String) {
        var s = paletteHex.trimmingCharacters(in: .whitespaces)
        if s.hasPrefix("#") { s.removeFirst() }
        guard s.count == 6, let v = UInt32(s, radix: 16) else { return nil }
        let r = Double((v >> 16) & 0xff) / 255.0
        let g = Double((v >> 8) & 0xff) / 255.0
        let b = Double(v & 0xff) / 255.0
        self = Color(red: r, green: g, blue: b)
    }
}
