import SwiftUI

// MARK: - Palette (matches Android /ui/theme/Palette.kt exactly)

enum Palette {
    static let paper    = Color(red: 0xF3/255, green: 0xEE/255, blue: 0xE4/255)
    static let card     = Color(red: 0xF7/255, green: 0xF2/255, blue: 0xE8/255)
    static let ink      = Color(red: 0x14/255, green: 0x12/255, blue: 0x10/255)
    static let muted    = Color(red: 0x6B/255, green: 0x64/255, blue: 0x59/255)
    static let bronze   = Color(red: 0xB0/255, green: 0x74/255, blue: 0x3A/255)
    static let goldA    = Color(red: 0xC9/255, green: 0x9A/255, blue: 0x5B/255)
    static let goldB    = Color(red: 0x8C/255, green: 0x60/255, blue: 0x33/255)
    static let hairline = Color.black.opacity(0.12)
    /// Deep red reserved for ROAST kind pills / destructive accents.
    static let roastRed = Color(red: 0xB2/255, green: 0x3A/255, blue: 0x2A/255)
}

extension LinearGradient {
    static let gold = LinearGradient(
        colors: [Palette.goldA, Palette.goldB],
        startPoint: .leading, endPoint: .trailing
    )
}

// MARK: - Typography

enum Serif {
    static func display(_ size: CGFloat, weight: Font.Weight = .regular) -> Font {
        .system(size: size, weight: weight, design: .serif)
    }
    static func body(_ size: CGFloat = 16, weight: Font.Weight = .regular) -> Font {
        .system(size: size, weight: weight, design: .default)
    }
    static func italic(_ size: CGFloat) -> Font {
        .system(size: size, weight: .regular, design: .serif).italic()
    }
}

// MARK: - Small text primitives

/// Small-caps, tracked bronze label. Used above every screen heading.
struct Eyebrow: View {
    let text: String
    var color: Color = Palette.bronze
    var body: some View {
        Text(text.uppercased())
            .font(.system(size: 11, weight: .semibold))
            .tracking(2)
            .foregroundStyle(color)
    }
}

struct Hairline: View {
    var body: some View { Rectangle().fill(Palette.hairline).frame(height: 0.5) }
}

// MARK: - Buttons

struct PrimaryButton: View {
    let title: String
    var icon: String? = nil
    var enabled: Bool = true
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            HStack(spacing: 10) {
                if let icon { Text(icon) }
                Text(title.uppercased()).tracking(2)
                    .font(.system(size: 13, weight: .semibold))
            }
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity)
            .frame(height: 56)
            .background(Palette.ink.opacity(enabled ? 1 : 0.4))
            .clipShape(RoundedRectangle(cornerRadius: 4))
        }
        .disabled(!enabled)
    }
}

struct SecondaryButton: View {
    let title: String
    var icon: String? = nil
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            HStack(spacing: 10) {
                if let icon { Text(icon) }
                Text(title.uppercased()).tracking(2)
                    .font(.system(size: 12, weight: .semibold))
            }
            .foregroundStyle(Palette.ink)
            .frame(maxWidth: .infinity)
            .frame(height: 56)
            .background(RoundedRectangle(cornerRadius: 4).stroke(Palette.ink, lineWidth: 1))
        }
    }
}

/// Hairline-bordered pill — used for tertiary actions inside cards.
struct OutlinePill: View {
    let title: String
    var icon: String? = nil
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                if let icon { Text(icon) }
                Text(title.uppercased()).tracking(1.4)
                    .font(.system(size: 11, weight: .semibold))
            }
            .foregroundStyle(Palette.ink)
            .padding(.horizontal, 14)
            .padding(.vertical, 8)
            .background(Palette.card)
            .overlay(Capsule().stroke(Palette.hairline, lineWidth: 1))
            .clipShape(Capsule())
        }
    }
}

// MARK: - Score + quote

struct ScoreChip: View {
    let score: Double
    var body: some View {
        Text(String(format: "%.1f", score))
            .font(Serif.display(20, weight: .semibold))
            .foregroundStyle(Palette.ink)
            .padding(.horizontal, 14).padding(.vertical, 6)
            .background(Color.white)
            .clipShape(Capsule())
            .shadow(color: .black.opacity(0.08), radius: 6, y: 2)
    }
}

struct PullQuote: View {
    let quote: String
    var attribution: String? = "— Hem"
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(quote)
                .font(Serif.italic(20))
                .foregroundStyle(Palette.ink)
                .fixedSize(horizontal: false, vertical: true)
            if let a = attribution {
                Text(a).font(Serif.italic(15)).foregroundStyle(Palette.muted)
            }
        }
    }
}

// MARK: - Rows + cards

struct SectionRow: View {
    let title: String
    var subtitle: String? = nil
    var trailing: String? = nil
    var badge: String? = nil
    var body: some View {
        HStack(alignment: .center, spacing: 12) {
            VStack(alignment: .leading, spacing: 3) {
                Text(title).font(Serif.body(16, weight: .medium)).foregroundStyle(Palette.ink)
                if let s = subtitle {
                    Text(s).font(Serif.body(13)).foregroundStyle(Palette.muted)
                }
            }
            Spacer()
            if let badge {
                Text(badge.uppercased())
                    .font(.system(size: 10, weight: .semibold))
                    .tracking(1.5)
                    .foregroundStyle(Palette.bronze)
                    .padding(.horizontal, 10).padding(.vertical, 5)
                    .overlay(Capsule().stroke(Palette.bronze, lineWidth: 1))
            }
            if let t = trailing {
                Text(t).font(Serif.display(18)).foregroundStyle(Palette.bronze)
            }
        }
        .padding(.vertical, 14)
    }
}

struct TanCard<Content: View>: View {
    @ViewBuilder let content: Content
    var body: some View {
        content
            .padding(20)
            .background(Palette.card)
            .overlay(RoundedRectangle(cornerRadius: 4).stroke(Palette.bronze.opacity(0.35), lineWidth: 1))
            .clipShape(RoundedRectangle(cornerRadius: 4))
    }
}

// MARK: - Kind pill (STUDIO / TRY-ON / ROAST / DECODED)

/// Small uppercase badge that classifies an outfit or piece by its `kind`.
/// Roast pills are red per the editorial spec; everything else is bronze.
struct KindPill: View {
    let kind: String
    var body: some View {
        let (label, bg) = Self.style(for: kind)
        return Text(label)
            .font(.system(size: 9.5, weight: .semibold))
            .tracking(1.5)
            .foregroundStyle(.white)
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(bg)
            .clipShape(Capsule())
    }

    private static func style(for kind: String) -> (String, Color) {
        switch kind.lowercased() {
        case "studio", "generate", "piece":       return ("STUDIO", Palette.bronze)
        case "tryon", "try_on", "try-on":         return ("TRY-ON", Palette.bronze)
        case "roast":                             return ("ROAST", Palette.roastRed)
        case "decode", "decoded":                 return ("DECODED", Palette.bronze)
        case "versus", "vs":                      return ("VERSUS", Palette.bronze)
        default:                                  return (kind.uppercased(), Palette.bronze)
        }
    }
}

// MARK: - Hem monogram (small bronze circle with serif "H")

/// Used inline in "Hem said:" style microcopy — sits next to italic quotes to
/// signal the stylist's voice.
struct HemMonogram: View {
    var size: CGFloat = 20
    var body: some View {
        Circle()
            .fill(Palette.bronze)
            .frame(width: size, height: size)
            .overlay(
                Text("H")
                    .font(.system(size: size * 0.55, weight: .semibold, design: .serif))
                    .foregroundStyle(.white)
            )
    }
}

// MARK: - Skeleton shimmer bar

/// Placeholder shimmer used while a card is loading. Kingfisher handles image
/// shimmer; this handles text / metric lines.
struct SkeletonBar: View {
    var width: CGFloat? = nil
    var height: CGFloat = 14
    var corner: CGFloat = 3

    @State private var phase: CGFloat = -1

    var body: some View {
        RoundedRectangle(cornerRadius: corner)
            .fill(Palette.hairline)
            .frame(width: width, height: height)
            .overlay(
                GeometryReader { geo in
                    LinearGradient(
                        colors: [
                            .clear,
                            Color.white.opacity(0.35),
                            .clear
                        ],
                        startPoint: .leading, endPoint: .trailing
                    )
                    .frame(width: geo.size.width * 0.6)
                    .offset(x: geo.size.width * phase)
                }
                .clipShape(RoundedRectangle(cornerRadius: corner))
            )
            .onAppear {
                withAnimation(.linear(duration: 1.2).repeatForever(autoreverses: false)) {
                    phase = 1.5
                }
            }
    }
}
