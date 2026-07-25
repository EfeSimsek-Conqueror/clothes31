import SwiftUI

// MARK: - Palette
enum Palette {
    static let paper  = Color(red: 0xF3/255, green: 0xEE/255, blue: 0xE4/255)
    static let card   = Color(red: 0xF7/255, green: 0xF2/255, blue: 0xE8/255)
    static let ink    = Color(red: 0x14/255, green: 0x12/255, blue: 0x10/255)
    static let muted  = Color(red: 0x6B/255, green: 0x64/255, blue: 0x59/255)
    static let bronze = Color(red: 0xB0/255, green: 0x74/255, blue: 0x3A/255)
    static let goldA  = Color(red: 0xC9/255, green: 0x9A/255, blue: 0x5B/255)
    static let goldB  = Color(red: 0x8C/255, green: 0x60/255, blue: 0x33/255)
    static let hairline = Color.black.opacity(0.12)
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

// MARK: - Eyebrow (small-caps, tracked, bronze)
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

// MARK: - Primary button (black fill, white uppercase tracked, 56pt, 4pt corner)
struct PrimaryButton: View {
    let title: String
    var icon: String? = nil
    var enabled: Bool = true
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            HStack(spacing: 10) {
                if let icon { Text(icon) }
                Text(title.uppercased())
                    .tracking(2)
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
                Text(title.uppercased())
                    .tracking(2)
                    .font(.system(size: 12, weight: .semibold))
            }
            .foregroundStyle(Palette.ink)
            .frame(maxWidth: .infinity)
            .frame(height: 56)
            .background(
                RoundedRectangle(cornerRadius: 4).stroke(Palette.ink, lineWidth: 1)
            )
        }
    }
}

// MARK: - Score chip (white pill with number)
struct ScoreChip: View {
    let score: Double
    var body: some View {
        Text(String(format: "%.1f", score))
            .font(Serif.display(20, weight: .semibold))
            .foregroundStyle(Palette.ink)
            .padding(.horizontal, 14)
            .padding(.vertical, 6)
            .background(Color.white)
            .clipShape(Capsule())
            .shadow(color: .black.opacity(0.08), radius: 6, y: 2)
    }
}

// MARK: - Pull quote (italic serif, tan-bordered)
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

// MARK: - Section row (used across screens)
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

struct Hairline: View {
    var body: some View { Rectangle().fill(Palette.hairline).frame(height: 0.5) }
}

// MARK: - Tan-bordered card
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
