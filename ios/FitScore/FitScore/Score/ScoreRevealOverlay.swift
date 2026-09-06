import SwiftUI

// Scoring v4 — the reveal.
//
// The order of the beats is the argument, and it starts with the BRIEF, not the
// number: the user reads back what they asked for before they are told how they
// did against it. A number that arrives first is a judgement; a number that
// arrives second is a measurement.
//
// The one hard rule here is the stamp. "ON BRIEF" and "NEARLY" are filled;
// "OFF BRIEF" is an outline, always. A filled MISSED burned over a photograph
// of someone at their friend's wedding is a delete-the-app moment, and no
// amount of correct arithmetic underneath earns it back.
struct ScoreRevealOverlay: View {
    let result: HemScored
    let outfitId: String?
    var onDone: () -> Void
    var onRescore: () -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// How many beats have landed. `beatCount` means "all of them".
    @State private var beat = 0

    private let beatCount = 8

    var body: some View {
        ZStack {
            Color.black.opacity(0.62).ignoresSafeArea()

            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: 0) {
                    briefBlock
                    headlineBlock
                    stampBlock
                    commentBlock
                    axesBlock
                    dressCodeBlock
                    leverBlock
                    actionBlock
                }
                .padding(24)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .background(Palette.paper)
            .clipShape(RoundedRectangle(cornerRadius: 6))
            .overlay(RoundedRectangle(cornerRadius: 6).stroke(Palette.bronze.opacity(0.35), lineWidth: 1))
            .shadow(color: .black.opacity(0.25), radius: 30, y: 12)
            .padding(.horizontal, 22)
            .padding(.vertical, 60)
        }
        .task { await runBeats() }
    }

    // MARK: - Beats

    private func runBeats() async {
        guard !reduceMotion else {
            beat = beatCount
            Haptic.soft()
            return
        }
        for step in 1...beatCount {
            try? await Task.sleep(nanoseconds: 130_000_000)
            withAnimation(.easeOut(duration: 0.34)) { beat = step }
            if step == 2 { Haptic.soft() }
        }
    }

    /// Reveal modifier — a beat that has not landed yet is invisible and sits a
    /// few points low, so the stagger reads as type settling onto paper.
    private func beatStyle<V: View>(_ index: Int, _ view: V) -> some View {
        view
            .opacity(beat >= index ? 1 : 0)
            .offset(y: beat >= index ? 0 : 8)
    }

    // MARK: - 1. The brief

    @ViewBuilder
    private var briefBlock: some View {
        if let line = briefLine {
            beatStyle(1, VStack(alignment: .leading, spacing: 8) {
                Eyebrow(text: "YOUR BRIEF")
                Text(line)
                    .font(Serif.italic(16))
                    .foregroundStyle(Palette.muted)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(.bottom, 18))
        }
    }

    private var briefLine: String? {
        if let l = result.rubric?.brief_line, !l.isEmpty { return l }
        if let l = result.rubric?.label, !l.isEmpty { return l }
        return nil
    }

    // MARK: - 2. The number

    private var headlineBlock: some View {
        beatStyle(2, HStack(alignment: .lastTextBaseline, spacing: 6) {
            Text(String(format: "%.1f", result.headlineScore))
                .font(Serif.display(72, weight: .semibold))
                .foregroundStyle(Palette.ink)
            Text("/ 10")
                .font(Serif.display(20))
                .foregroundStyle(Palette.muted)
            Spacer(minLength: 0)
        })
    }

    // MARK: - 3. The stamp

    @ViewBuilder
    private var stampBlock: some View {
        let stamp = stampStyle
        beatStyle(3, VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 10) {
                if let stamp {
                    Text(stamp.title)
                        .font(.system(size: 11, weight: .semibold))
                        .tracking(2.4)
                        .foregroundStyle(stamp.filled ? .white : stamp.color)
                        .padding(.horizontal, 12).padding(.vertical, 7)
                        .background(stamp.filled ? stamp.color : Color.clear)
                        .overlay(RoundedRectangle(cornerRadius: 3).stroke(stamp.color, lineWidth: 1))
                        .clipShape(RoundedRectangle(cornerRadius: 3))
                }
                if let band = result.headline?.band, !band.isEmpty, stamp == nil {
                    Text(band)
                        .font(Serif.italic(16))
                        .foregroundStyle(Palette.muted)
                }
            }
            if let label = result.rubric?.label, !label.isEmpty {
                Text(label)
                    .font(.system(size: 11, weight: .semibold))
                    .tracking(1.6)
                    .foregroundStyle(Palette.muted)
                    .fixedSize(horizontal: false, vertical: true)
            }
            if let copy = result.briefVerdict?.copy, !copy.isEmpty {
                Text(copy)
                    .font(Serif.body(14))
                    .foregroundStyle(Palette.ink)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, 2)
            }
        }
        .padding(.top, 12)
        .padding(.bottom, 18))
    }

    private struct Stamp: Equatable {
        let title: String
        let color: Color
        let filled: Bool
    }

    /// `off_brief` is never filled. See the note at the top of this file.
    private var stampStyle: Stamp? {
        switch result.briefVerdict?.state {
        case "on_brief":  return Stamp(title: "ON BRIEF", color: Palette.bronze, filled: true)
        case "nearly":    return Stamp(title: "NEARLY", color: Palette.ink, filled: true)
        case "off_brief": return Stamp(title: "OFF BRIEF", color: Palette.roastRed, filled: false)
        default:          return nil
        }
    }

    // MARK: - 4. Hem

    @ViewBuilder
    private var commentBlock: some View {
        let comment = result.hemComment.trimmingCharacters(in: .whitespacesAndNewlines)
        if !comment.isEmpty {
            beatStyle(4, VStack(alignment: .leading, spacing: 10) {
                Text(comment)
                    .font(Serif.italic(20))
                    .foregroundStyle(Palette.ink)
                    .fixedSize(horizontal: false, vertical: true)
                HStack(spacing: 8) {
                    HemMonogram(size: 18)
                    Text("Hem").font(Serif.italic(15)).foregroundStyle(Palette.muted)
                }
            }
            .padding(.bottom, 20))
        }
    }

    // MARK: - 5. The four axes that carried the weight

    @ViewBuilder
    private var axesBlock: some View {
        let top = Array(result.orderedAxes.prefix(4))
        if !top.isEmpty {
            let maxWeight = top.map(\.weight).max() ?? 0
            beatStyle(5, VStack(alignment: .leading, spacing: 14) {
                Eyebrow(text: "WHAT CARRIED IT")
                ForEach(top) { axis in
                    VStack(alignment: .leading, spacing: 6) {
                        HStack(alignment: .firstTextBaseline, spacing: 8) {
                            Text(axis.label)
                                .font(Serif.body(14, weight: .medium))
                                .foregroundStyle(axis.isUnjudgeable ? Palette.muted : Palette.ink)
                            Spacer(minLength: 6)
                            Text("·\(axis.weightPercent)%")
                                .font(.system(size: 11, weight: .semibold))
                                .tracking(1)
                                .foregroundStyle(Palette.bronze)
                            Text(axis.isUnjudgeable ? "—" : String(format: "%.1f", axis.score ?? 0))
                                .font(Serif.display(15, weight: .semibold))
                                .foregroundStyle(axis.isUnjudgeable ? Palette.muted : Palette.ink)
                                .frame(minWidth: 30, alignment: .trailing)
                        }
                        AxisScoreBar(
                            score: axis.isUnjudgeable ? nil : axis.score,
                            rawScore: axis.raw_score,
                            weight: axis.weight,
                            maxWeight: maxWeight,
                            tint: axis.isUnjudgeable ? Palette.muted.opacity(0.4) : Palette.ink
                        )
                    }
                }
            }
            .padding(.bottom, 20))
        }
    }

    // MARK: - 6. The gap

    @ViewBuilder
    private var dressCodeBlock: some View {
        if let dc = result.dressCode, dc.shouldRender, abs(dc.gap ?? 0) >= 1,
           let line = dc.line, !line.isEmpty {
            beatStyle(6, HStack(alignment: .top, spacing: 10) {
                Rectangle().fill(Palette.roastRed.opacity(0.6)).frame(width: 2)
                VStack(alignment: .leading, spacing: 4) {
                    Text(line)
                        .font(Serif.body(14, weight: .medium))
                        .foregroundStyle(Palette.ink)
                        .fixedSize(horizontal: false, vertical: true)
                    if let ev = dc.evidence, !ev.isEmpty {
                        Text(ev)
                            .font(Serif.body(13))
                            .foregroundStyle(Palette.muted)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
            }
            .fixedSize(horizontal: false, vertical: true)
            .padding(.bottom, 20))
        }
    }

    // MARK: - 7. The one thing

    @ViewBuilder
    private var leverBlock: some View {
        if let text = leverLine {
            beatStyle(7, TanCard {
                VStack(alignment: .leading, spacing: 6) {
                    Eyebrow(text: "THE ONE THING")
                    Text(text)
                        .font(Serif.italic(18))
                        .foregroundStyle(Palette.ink)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(.bottom, 22))
        }
    }

    /// "A dinner jacket and a bow tie and you're at 8.3." Projected, never promised.
    private var leverLine: String? {
        guard let lever = result.lever else { return nil }
        let action = (lever.action ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        if !action.isEmpty, let projected = lever.projected {
            return "\(action) and you're at \(String(format: "%.1f", projected))."
        }
        if let copy = lever.copy, !copy.isEmpty { return copy }
        return action.isEmpty ? nil : action
    }

    // MARK: - 8. Out

    private var actionBlock: some View {
        beatStyle(8, VStack(spacing: 12) {
            PrimaryButton(title: outfitId == nil ? "DONE" : "SEE THE FULL READ") {
                Haptic.tap()
                onDone()
            }
            Button {
                Haptic.chip()
                onRescore()
            } label: {
                HStack(spacing: 4) {
                    Text("Wrong occasion?").font(Serif.body(13))
                    Image(systemName: "chevron.right").font(.system(size: 10, weight: .semibold))
                }
                .foregroundStyle(Palette.muted)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        })
    }
}
