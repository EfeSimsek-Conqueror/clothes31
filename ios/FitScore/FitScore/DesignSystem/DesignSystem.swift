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
    /// Ornamental type — capped at accessibility1 so a tracked small-caps label
    /// never wraps into three lines and shoves the heading off screen.
    var size: CGFloat = 11
    var body: some View {
        Text(text.uppercased())
            .font(.system(size: size, weight: .semibold))
            .tracking(2)
            .foregroundStyle(color)
            .dynamicTypeSize(...DynamicTypeSize.accessibility1)
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
    var height: CGFloat = 56
    var corner: CGFloat = 4
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            HStack(spacing: 10) {
                if let icon { Text(icon) }
                Text(title.uppercased()).tracking(2)
                    .font(.system(size: 13, weight: .semibold))
                    .lineLimit(2)
                    .minimumScaleFactor(0.8)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 12)
            }
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity)
            .frame(height: height)
            .background(Palette.ink.opacity(enabled ? 1 : 0.4))
            .clipShape(RoundedRectangle(cornerRadius: corner))
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

// MARK: - Pro badge

/// The Pro marker used in eyebrow rows (`.filled`) and inline beside a
/// Pro-gated row label (`.text`). Ornamental — capped at accessibility1.
struct ProBadge: View {
    enum Style { case filled, text }
    var style: Style = .filled

    var body: some View {
        Group {
            switch style {
            case .filled:
                Text("PRO")
                    .font(.system(size: 9, weight: .semibold))
                    .tracking(1.2)
                    .foregroundStyle(.white)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 3)
                    .background(Palette.bronze)
                    .clipShape(RoundedRectangle(cornerRadius: 3))
            case .text:
                Text("PRO")
                    .font(.system(size: 9.5, weight: .semibold))
                    .tracking(1.5)
                    .foregroundStyle(Palette.bronze)
            }
        }
        .dynamicTypeSize(...DynamicTypeSize.accessibility1)
    }
}

// MARK: - Circular close chip

/// Soft tan circle with an ink ✕. Reads at 34pt but keeps a 44pt tap target.
struct CircleCloseButton: View {
    var label: String = "Close"
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Circle()
                .fill(Palette.card)
                .frame(width: 34, height: 34)
                .overlay(Circle().stroke(Palette.hairline, lineWidth: 1))
                .overlay(
                    Image(systemName: "xmark")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(Palette.ink)
                )
                .frame(width: 44, height: 44)
                .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }
}

// MARK: - Dashed drop zone

/// Tan rounded rect with a dashed hairline border — the editorial "empty slot".
/// Pass `dashed: false` once it is filled so the border reads as a frame.
struct DashedDropZone<Content: View>: View {
    var corner: CGFloat = 14
    var aspect: CGFloat? = nil
    var dashed: Bool = true
    var maxHeight: CGFloat? = nil
    @ViewBuilder var content: Content

    var body: some View {
        Group {
            if let aspect {
                RoundedRectangle(cornerRadius: corner)
                    .fill(Palette.card)
                    .aspectRatio(aspect, contentMode: .fit)
            } else {
                RoundedRectangle(cornerRadius: corner)
                    .fill(Palette.card)
            }
        }
        .frame(maxHeight: maxHeight)
        .overlay(content.clipShape(RoundedRectangle(cornerRadius: corner)))
        .overlay(
            RoundedRectangle(cornerRadius: corner)
                .strokeBorder(style: StrokeStyle(lineWidth: 1, dash: dashed ? [5, 4] : []))
                .foregroundStyle(Palette.hairline)
        )
    }
}

// MARK: - Segmented pill

/// Two-or-more segment switch on a tan capsule track. Active segment is a
/// filled ink capsule; the fill slides between segments unless Reduce Motion.
struct SegmentedPill: View {
    let options: [(key: String, label: String)]
    @Binding var selection: String

    @Namespace private var ns
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        HStack(spacing: 0) {
            ForEach(options.indices, id: \.self) { i in
                let opt = options[i]
                let active = selection == opt.key
                Button {
                    guard selection != opt.key else { return }
                    Haptic.chip()
                    if reduceMotion {
                        selection = opt.key
                    } else {
                        withAnimation(.easeInOut(duration: 0.2)) { selection = opt.key }
                    }
                } label: {
                    Text(opt.label)
                        .font(Serif.body(12, weight: .semibold))
                        .foregroundStyle(active ? .white : Palette.ink)
                        .lineLimit(1)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 7)
                        .background {
                            if active {
                                Capsule()
                                    .fill(Palette.ink)
                                    .matchedGeometryEffect(id: "seg", in: ns)
                            }
                        }
                        .contentShape(Capsule())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(active ? [.isSelected] : [])
            }
        }
        .background(Palette.card)
        .clipShape(Capsule())
        .overlay(Capsule().stroke(Palette.hairline, lineWidth: 1))
    }
}

// MARK: - Burned-in caption

extension View {
    /// Burns an editorial caption into the bottom of an image card: a soft dark
    /// scrim, a tracked small-caps label on the left and an optional serif
    /// italic word on the right. Purely decorative — hidden from VoiceOver, so
    /// give the card itself an `.accessibilityLabel`.
    func burnedCaption(leading: String, trailingItalic: String? = nil, corner: CGFloat = 14) -> some View {
        self.overlay(
            GeometryReader { geo in
                ZStack(alignment: .bottom) {
                    LinearGradient(
                        colors: [.clear, .black.opacity(0.10), .black.opacity(0.55)],
                        startPoint: .top, endPoint: .bottom
                    )
                    .frame(height: max(geo.size.height * 0.36, 34))
                    .frame(maxHeight: .infinity, alignment: .bottom)

                    HStack(alignment: .firstTextBaseline, spacing: 8) {
                        Text(leading.uppercased())
                            .font(.system(size: 10, weight: .semibold))
                            .tracking(1.8)
                            .foregroundStyle(.white)
                            .lineLimit(1)
                            .minimumScaleFactor(0.7)
                        Spacer(minLength: 0)
                        if let trailingItalic {
                            Text(trailingItalic)
                                .font(Serif.italic(15))
                                .foregroundStyle(.white.opacity(0.92))
                                .lineLimit(1)
                        }
                    }
                    .padding(14)
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: corner))
            .allowsHitTesting(false)
            .accessibilityHidden(true)
        )
    }
}

// MARK: - Sticky footer

/// Hairline + paper-backed tray pinned above the safe area. Use inside
/// `.safeAreaInset(edge: .bottom)` so the scroll content clears it.
struct StickyFooter<Content: View>: View {
    @ViewBuilder var content: Content
    var body: some View {
        VStack(spacing: 0) {
            Hairline()
            content
                .padding(.horizontal, 20)
                .padding(.top, 14)
                .padding(.bottom, 10)
        }
        .background(Palette.paper)
    }
}

// MARK: - Wrapping chip row

/// Single-select chips that wrap onto as many lines as the labels need.
///
/// `ChipRow` divides the width evenly across a fixed HStack, which is right for
/// five short occasion words and wrong for everything else: "Registry or place
/// of worship" next to three siblings gets a fifth of the screen. This one flows.
struct FlowChipRow<Option: Hashable>: View {
    var eyebrow: String?
    let options: [Option]
    @Binding var selection: Option
    let label: (Option) -> String

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    init(eyebrow: String? = nil,
         options: [Option],
         selection: Binding<Option>,
         label: @escaping (Option) -> String) {
        self.eyebrow = eyebrow
        self.options = options
        self._selection = selection
        self.label = label
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            if let eyebrow { Eyebrow(text: eyebrow) }
            FlowLayout(spacing: 8) {
                ForEach(options, id: \.self) { opt in
                    chip(opt)
                }
            }
        }
    }

    @ViewBuilder
    private func chip(_ opt: Option) -> some View {
        let active = opt == selection
        Button {
            guard opt != selection else { return }
            Haptic.chip()
            if reduceMotion {
                selection = opt
            } else {
                withAnimation(.easeOut(duration: 0.18)) { selection = opt }
            }
        } label: {
            Text(label(opt))
                .font(Serif.body(13))
                .foregroundStyle(active ? .white : Palette.ink)
                .lineLimit(1)
                .padding(.horizontal, 14)
                .frame(minHeight: 38)
                .background(active ? Palette.ink : Color.clear)
                .overlay(Capsule().stroke(active ? Palette.ink : Palette.ink.opacity(0.5), lineWidth: 1))
                .clipShape(Capsule())
                .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(active ? [.isSelected] : [])
    }
}

extension FlowChipRow where Option == String {
    init(eyebrow: String? = nil, options: [String], selection: Binding<String>) {
        self.init(eyebrow: eyebrow, options: options, selection: selection, label: { $0 })
    }
}

// MARK: - Five-detent dial

/// A five-position dial on a labelled rail, with the caption for the current
/// detent underneath it.
///
/// Tap detents rather than a drag gesture: the positions are five discrete
/// rungs of a rubric, not a continuum, and a slider that snaps invites the user
/// to think the value between rungs means something.
struct DialControl: View {
    let eyebrow: String
    let leftLabel: String
    let rightLabel: String
    /// One caption per detent; index 0 is position 1.
    let captions: [String]
    @Binding var selection: Int

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private let detents = 5

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Eyebrow(text: eyebrow)
            HStack(spacing: 8) {
                railLabel(leftLabel)
                Rectangle().fill(Palette.hairline).frame(height: 1)
                railLabel(rightLabel)
            }
            rail
            caption
        }
        .accessibilityElement(children: .contain)
    }

    private func railLabel(_ text: String) -> some View {
        Text(text.uppercased())
            .font(.system(size: 9.5, weight: .semibold))
            .tracking(1.6)
            .foregroundStyle(Palette.muted)
            .dynamicTypeSize(...DynamicTypeSize.accessibility1)
            .fixedSize()
    }

    private var rail: some View {
        ZStack {
            Rectangle()
                .fill(Palette.hairline)
                .frame(height: 1)
                .padding(.horizontal, 22)
            HStack(spacing: 0) {
                ForEach(1...detents, id: \.self) { i in
                    detent(i)
                }
            }
        }
    }

    private func detent(_ i: Int) -> some View {
        let active = i == selection
        return Button {
            guard i != selection else { return }
            Haptic.chip()
            if reduceMotion {
                selection = i
            } else {
                withAnimation(.easeOut(duration: 0.2)) { selection = i }
            }
        } label: {
            ZStack {
                // Paper disc so the rail does not draw through the detent.
                Circle().fill(Palette.paper).frame(width: 22, height: 22)
                Circle()
                    .fill(i <= selection ? Palette.ink : Palette.card)
                    .frame(width: active ? 16 : 9, height: active ? 16 : 9)
                Circle()
                    .stroke(Palette.ink.opacity(0.4), lineWidth: 1)
                    .frame(width: 16, height: 16)
                    .opacity(active ? 0 : 1)
            }
            .frame(maxWidth: .infinity, minHeight: 44)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(captionFor(i))
        .accessibilityValue("\(i) of \(detents)")
        .accessibilityAddTraits(active ? [.isSelected] : [])
    }

    /// The caption sits under its detent in three zones rather than at the
    /// thumb's exact centre: a centred phrase at position 1 or 5 would be
    /// clipped by the sheet's margin, and a clipped caption is worse than an
    /// approximate one.
    private var caption: some View {
        Text(captionFor(selection))
            .font(Serif.italic(15))
            .foregroundStyle(Palette.ink)
            .lineLimit(1)
            .minimumScaleFactor(0.75)
            .frame(maxWidth: .infinity, alignment: captionAlignment)
            .animation(reduceMotion ? nil : .easeOut(duration: 0.2), value: selection)
    }

    private var captionAlignment: Alignment {
        switch selection {
        case 1, 2: return .leading
        case 3:    return .center
        default:   return .trailing
        }
    }

    private func captionFor(_ i: Int) -> String {
        (1...captions.count).contains(i) ? captions[i - 1] : ""
    }
}

// MARK: - Brief line

/// The one-sentence restatement of the brief, under the CTA. Rebuilt on every
/// touch — it is the sheet's receipt that the dials were heard.
struct BriefLine: View {
    let text: String
    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Rectangle()
                .fill(Palette.bronze)
                .frame(width: 2)
            Text(text)
                .font(Serif.italic(16))
                .foregroundStyle(Palette.ink)
                .fixedSize(horizontal: false, vertical: true)
        }
        .fixedSize(horizontal: false, vertical: true)
    }
}

// MARK: - Rubric preview strip

/// "GRADING HARDEST ON · DRESS CODE · FIT · DETAIL · SILHOUETTE".
///
/// Fed from the client's copy of the weight pipeline, so it rearranges as the
/// brief changes and costs nothing. It is the only place the user can watch the
/// rubric move before spending a credit.
struct RubricPreviewStrip: View {
    let labels: [String]
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Eyebrow(text: "GRADING HARDEST ON")
            Text(labels.map { $0.uppercased() }.joined(separator: " · "))
                .font(.system(size: 11, weight: .semibold))
                .tracking(1.6)
                .foregroundStyle(Palette.ink)
                .fixedSize(horizontal: false, vertical: true)
                .animation(.easeOut(duration: 0.2), value: labels)
        }
        .accessibilityElement(children: .combine)
    }
}

// MARK: - Wardrobe entrance

/// Staggered arrival for a rail of garments: each item settles a beat after the
/// one before it, hung from its top edge so the motion reads as a hanger
/// swinging into place rather than a card sliding on a table.
///
/// The delay is capped — a large closet must not leave the last piece arriving
/// seconds after the first — and the whole effect is skipped under Reduce Motion.
struct WardrobeEntrance: ViewModifier {
    let index: Int
    var perItem: Double = 0.07
    var maxDelay: Double = 0.7

    @State private var appeared = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func body(content: Content) -> some View {
        content
            .rotationEffect(.degrees(appeared ? 0 : 4), anchor: .top)
            .offset(y: appeared ? 0 : -14)
            .opacity(appeared ? 1 : 0)
            .onAppear {
                guard !appeared else { return }
                guard !reduceMotion else { appeared = true; return }
                withAnimation(
                    .spring(response: 0.45, dampingFraction: 0.72)
                        .delay(min(Double(index) * perItem, maxDelay))
                ) { appeared = true }
            }
    }
}

extension View {
    func wardrobeEntrance(index: Int) -> some View {
        modifier(WardrobeEntrance(index: index))
    }
}
