import SwiftUI

struct OnboardingGenderView: View {
    @EnvironmentObject var session: SessionStore
    @State private var pick: Gender? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: 24) {
            Spacer(minLength: 24)
            Eyebrow(text: "STEP 1 OF 2")
            Text("Who's dressing?")
                .font(Serif.display(40))
                .foregroundStyle(Palette.ink)
            Text("We tune fits and palettes to what you actually wear.")
                .font(Serif.body(15)).foregroundStyle(Palette.muted)

            VStack(spacing: 14) {
                ForEach(Gender.allCases) { g in
                    Button {
                        pick = g
                    } label: {
                        HStack {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(g.rawValue)
                                    .font(Serif.display(22))
                                    .foregroundStyle(Palette.ink)
                                Text(g.caption)
                                    .font(Serif.body(13))
                                    .foregroundStyle(Palette.muted)
                            }
                            Spacer()
                            Circle()
                                .strokeBorder(pick == g ? Palette.bronze : Palette.hairline, lineWidth: 1.5)
                                .background(Circle().fill(pick == g ? Palette.bronze : .clear))
                                .frame(width: 18, height: 18)
                        }
                        .padding(20)
                        .frame(maxWidth: .infinity)
                        .frame(minHeight: 96)
                        .overlay(
                            RoundedRectangle(cornerRadius: 4)
                                .stroke(pick == g ? Palette.ink : Palette.hairline, lineWidth: pick == g ? 1.5 : 1)
                        )
                    }
                }
            }
            .padding(.top, 8)

            Spacer()

            PrimaryButton(title: "Continue", enabled: pick != nil) {
                session.gender = pick
                session.advance(to: .onboarding2)
            }
            .padding(.bottom, 24)
        }
        .padding(.horizontal, 24)
    }
}

struct OnboardingVibeView: View {
    @EnvironmentObject var session: SessionStore
    @State private var picked: Set<String> = []

    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            Spacer(minLength: 24)
            Eyebrow(text: "STEP 2 OF 2")
            Text("Your vibe.")
                .font(Serif.display(40))
                .foregroundStyle(Palette.ink)
            Text("Pick anything that feels like you. You can change these later.")
                .font(Serif.body(15)).foregroundStyle(Palette.muted)

            FlowLayout(spacing: 10) {
                ForEach(Seeds.styleChips) { chip in
                    let on = picked.contains(chip.label)
                    Button {
                        if on { picked.remove(chip.label) } else { picked.insert(chip.label) }
                    } label: {
                        Text(chip.label)
                            .font(Serif.body(14))
                            .foregroundStyle(on ? .white : Palette.ink)
                            .padding(.horizontal, 16).padding(.vertical, 10)
                            .background(on ? Palette.ink : Color.clear)
                            .overlay(Capsule().stroke(on ? Palette.ink : Palette.hairline, lineWidth: 1))
                            .clipShape(Capsule())
                    }
                }
            }
            .padding(.top, 8)

            Spacer()

            PrimaryButton(title: "Continue — \(picked.count) picked", enabled: !picked.isEmpty) {
                session.pickedChips = picked
                session.advance(to: .home)
            }
            .padding(.bottom, 24)
        }
        .padding(.horizontal, 24)
    }
}

// Simple flow layout for chips
struct FlowLayout: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let maxWidth = proposal.width ?? 0
        var x: CGFloat = 0, y: CGFloat = 0, rowH: CGFloat = 0
        for s in subviews {
            let sz = s.sizeThatFits(.unspecified)
            if x + sz.width > maxWidth {
                x = 0; y += rowH + spacing; rowH = 0
            }
            x += sz.width + spacing
            rowH = max(rowH, sz.height)
        }
        return CGSize(width: maxWidth, height: y + rowH)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x: CGFloat = bounds.minX, y: CGFloat = bounds.minY, rowH: CGFloat = 0
        for s in subviews {
            let sz = s.sizeThatFits(.unspecified)
            if x + sz.width > bounds.maxX {
                x = bounds.minX; y += rowH + spacing; rowH = 0
            }
            s.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(sz))
            x += sz.width + spacing
            rowH = max(rowH, sz.height)
        }
    }
}
