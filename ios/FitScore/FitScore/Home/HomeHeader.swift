import SwiftUI

/// Top row on Home: date eyebrow + serif greeting on the left; weather circle
/// + avatar circle with credits label on the right. Ports Android `HomeHeader`.
struct HomeHeader: View {
    let name: String
    let tempC: Int?
    let initial: String
    let credits: Int?
    var onOpenCredits: () -> Void = {}

    var body: some View {
        HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 6) {
                Text(dateLabel())
                    .font(.system(size: 11, weight: .semibold))
                    .tracking(1.8)
                    .foregroundStyle(Palette.muted)
                Text(greetingText())
                    .font(Serif.display(30, weight: .regular))
                    .foregroundStyle(Palette.ink)
            }
            Spacer(minLength: 12)
            HStack(alignment: .top, spacing: 6) {
                CircleToken(text: tempC.map { "\($0)°" } ?? "--°")
                VStack(spacing: 4) {
                    Button(action: {
                        Haptic.chip()
                        onOpenCredits()
                    }) {
                        CircleToken(text: initial)
                    }
                    Button(action: {
                        Haptic.chip()
                        onOpenCredits()
                    }) {
                        Text(credits.map { "\($0) CR" } ?? "— CR")
                            .font(.system(size: 10, weight: .semibold))
                            .tracking(1.2)
                            .foregroundStyle(credits != nil ? Palette.bronze : Palette.muted)
                    }
                }
            }
        }
    }

    private func dateLabel() -> String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "EEEE · d MMMM"
        return f.string(from: Date()).uppercased()
    }

    private func greetingText() -> String {
        let h = Calendar.current.component(.hour, from: Date())
        let greeting: String
        switch h {
        case ..<5:    greeting = "Late night"
        case 5..<12:  greeting = "Morning"
        case 12..<17: greeting = "Afternoon"
        case 17..<21: greeting = "Evening"
        default:      greeting = "Night"
        }
        return name.isEmpty ? "\(greeting)." : "\(greeting), \(name)."
    }
}

/// Small 38-pt circle used for the temp chip and initials avatar.
struct CircleToken: View {
    let text: String
    var body: some View {
        ZStack {
            Circle().fill(Palette.card)
                .overlay(Circle().stroke(Palette.hairline, lineWidth: 1))
                .frame(width: 38, height: 38)
            Text(text)
                .font(.system(size: 13, weight: .semibold, design: .serif))
                .foregroundStyle(Palette.ink)
        }
    }
}
