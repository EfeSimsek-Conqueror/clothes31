import SwiftUI

/// Full-screen score detail.
///
/// The section order IS the argument, and it is deliberate: what I graded you
/// against → the read → where the gap is → the arithmetic → the proof → what to
/// do → what I couldn't see → the record. A number with no ledger under it is a
/// verdict; a number with one is a receipt.
///
/// Every v4 field is optional and every section is gated on its own data, so a
/// row written before v4 renders exactly as it did before: hero, pull quote,
/// subscores grid, flat swaps, details.
struct ScoreDetailView: View {
    let outfitId: String
    var onClose: () -> Void = {}
    /// Supplied by a presenter that owns its own brief editor (the score
    /// reveal does). When nil this screen falls back to its own re-brief sheet,
    /// so the "change this ›" affordance is never dead.
    var onRescore: (() -> Void)? = nil

    @State private var outfit: Outfit?
    @State private var photoUrl: String?
    @State private var average: Double?
    @State private var loaded = false
    // Sprint 2 X-Ray + Fit Map viewer state
    @State private var markupAnnotations: [MarkupAnnotation] = []
    @State private var fitMap: FitMap?
    @State private var showXRay = false
    // Aug 2026: swipeable side view for Studio outfits (outfit_studio +
    // outfit_studio_side sibling).
    @State private var sideUrl: String? = nil
    @State private var heroPagerIndex: Int = 0
    @State private var reportTarget: ReportTarget? = nil
    // v4 disclosures
    @State private var showAllRules = false
    @State private var showWeightTable = false
    @State private var showRebrief = false
    @State private var rescoring = false

    var body: some View {
        ZStack {
            // A tool's own output replaces this screen rather than being embedded
            // in it. Nesting it inside the scroll view below put one ScrollView
            // inside another: the tool's pinned header scrolled away with the
            // content and its hero bled to the top edge, so the same look opened
            // from the Journal did not match the screen shown right after the
            // render. Presented at this level it is the same screen either way.
            if loaded, let o = outfit, ToolResultView.handles(o.kind) {
                ToolResultView(outfit: o, onClose: onClose)
            } else {
            GeometryReader { geo in
                let heroHeight = geo.size.height * 0.65
                ScrollView {
                    VStack(alignment: .leading, spacing: 0) {
                        if !loaded {
                            skeleton(heroHeight: heroHeight)
                        } else if let o = outfit, let summary = o.versusSummary {
                            // A comparison is not a scored photo. Hand off so the
                            // row opens as the call it actually was.
                            VersusDetailView(outfit: o, summary: summary, onClose: onClose)
                        } else if let o = outfit {
                            heroBlock(o: o, height: heroHeight)
                            contentBlock(o: o)
                        } else {
                            notFoundBlock
                        }
                    }
                    .padding(.bottom, 30)
                }
            }
            }

            // The read is pushed *inside* this view rather than presented as its
            // own `fullScreenCover`. This screen is already the content of a
            // fullScreenCover (Home/Journal), and stacking a second one made UIKit
            // tear down the outer presentation — tapping "See the read" dropped the
            // user back on the dashboard. As an overlay it pushes in, and closing
            // it returns to the photo, which then closes back to the dashboard.
            if showXRay, let url = photoUrl {
                ScoreAnnotationView(
                    photoUrl: url,
                    annotations: markupAnnotations,
                    fitMap: fitMap,
                    hemComment: outfit?.hem_comment ?? outfit?.notes ?? "",
                    onClose: { withAnimation(.easeInOut(duration: 0.25)) { showXRay = false } }
                )
                .background(Palette.paper.ignoresSafeArea())
                .transition(.move(edge: .trailing))
                .zIndex(1)
            }
        }
        .background(Palette.paper.ignoresSafeArea())
        .task { await load() }
        .sheet(item: $reportTarget) { t in
            ReportContentSheet(target: t) { reportTarget = nil }
        }
        .sheet(isPresented: $showRebrief) {
            ScoreRebriefSheet(
                intake: outfit?.intake ?? ScoreIntake(occasion: .everyday),
                rescoresLeft: max(0, 3 - (outfit?.rescore_count ?? 0)),
                busy: rescoring,
                onCancel: { showRebrief = false },
                onSubmit: { intake in Task { await rescore(with: intake) } }
            )
            .presentationDetents([.large])
        }
    }

    // MARK: - ① Hero

    @ViewBuilder
    private func heroBlock(o: Outfit, height: CGFloat) -> some View {
        let hasSide = sideUrl != nil
        ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: 20)
                .fill(Palette.card)
                .frame(height: height)
                .overlay(
                    Group {
                        if hasSide {
                            // Studio outfit with a side view — swipeable pager.
                            TabView(selection: $heroPagerIndex) {
                                heroImage(url: photoUrl).tag(0)
                                heroImage(url: sideUrl).tag(1)
                            }
                            .tabViewStyle(.page(indexDisplayMode: .never))
                        } else if let s = photoUrl, let u = URL(string: s) {
                            AsyncImage(url: u) { $0.resizable().scaledToFill() } placeholder: { Color.clear }
                        }
                    }
                )
                // "Clean plate": the photograph carries the hero on its own. The
                // score, the brief it was graded against and the verdict ride a
                // scrim along the bottom edge. Editorial markup lives in the
                // full-screen "See the read" viewer rather than on top of the
                // photo — anchor coords are normalized to the *source* image,
                // which `scaledToFill` crops, so drawing them here landed the
                // leader lines on background instead of the outfit.
                .overlay(alignment: .bottom) { heroCaption(o: o) }
                .clipShape(RoundedRectangle(cornerRadius: 20))

            Button(action: onClose) {
                Image(systemName: "xmark")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(Palette.ink)
                    .frame(width: 32, height: 32)
                    .background(Palette.paper.opacity(0.9))
                    .clipShape(Circle())
            }
            .padding(12)
        }
        .overlay(alignment: .topTrailing) {
            if hasSide {
                HStack(spacing: 8) {
                    pagerDot(active: heroPagerIndex == 0, label: "FRONT")
                    pagerDot(active: heroPagerIndex == 1, label: "SIDE")
                }
                .padding(.horizontal, 12).padding(.vertical, 6)
                .background(Palette.paper.opacity(0.9))
                .clipShape(Capsule())
                .padding(12)
            }
        }
        .padding(.horizontal, 20)
        .padding(.top, 16)
    }

    /// Bottom scrim on the hero — small-caps eyebrow (delta + occasion) over the
    /// score numeral and the brief line.
    ///
    /// v4 rows put the rubric label here rather than the verdict paragraph: the
    /// verdict is printed in full under THE READ, and printing it twice on one
    /// screen makes the second one read as filler. Legacy rows, which have no
    /// rubric, keep the old behaviour and show whatever verdict they carry.
    @ViewBuilder
    private func heroCaption(o: Outfit) -> some View {
        let label = o.rubricLabel
        let verdict = (o.verdict ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let caption = label ?? (verdict.isEmpty ? nil : verdict)
        let eyebrow = heroEyebrow(o: o)
        let score = o.displayScore ?? 0
        if !eyebrow.isEmpty || caption != nil || score > 0 {
            VStack(alignment: .leading, spacing: 8) {
                if !eyebrow.isEmpty {
                    Text(eyebrow)
                        .font(.system(size: 11, weight: .semibold))
                        .tracking(2)
                        .foregroundStyle(.white.opacity(0.78))
                }
                HStack(alignment: .firstTextBaseline, spacing: 12) {
                    if score > 0 {
                        Text(String(format: "%.1f", score))
                            .font(Serif.display(40, weight: .semibold))
                            .foregroundStyle(.white)
                    }
                    if let caption {
                        Text(caption)
                            .font(Serif.italic(17))
                            .foregroundStyle(.white.opacity(0.92))
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    Spacer(minLength: 0)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 18)
            .padding(.top, 56)
            .padding(.bottom, 18)
            .background(
                LinearGradient(
                    colors: [.clear, .black.opacity(0.30), .black.opacity(0.74)],
                    startPoint: .top, endPoint: .bottom
                )
            )
        }
    }

    /// "0.2 ABOVE YOUR AVERAGE · EVERYDAY".
    ///
    /// `average` is computed over this row's own scoring family only (see
    /// `load()`), so the delta is suppressed rather than lied about when the
    /// user's history is on the other engine — a v4 headline is capped and a v3
    /// mean is not, and comparing them would report a perfectly good look as
    /// below average.
    private func heroEyebrow(o: Outfit) -> String {
        var parts: [String] = []
        if let a = average, let s = o.displayScore {
            let d = s - a
            if d > 0.05 { parts.append(String(format: "%.1f above your average", d)) }
            else if d < -0.05 { parts.append(String(format: "%.1f below your average", abs(d))) }
            else { parts.append("on your average") }
        }
        if let occ = o.occasion, !occ.isEmpty { parts.append(occ) }
        return parts.joined(separator: " · ").uppercased()
    }

    private func heroImage(url: String?) -> some View {
        Group {
            if let s = url, let u = URL(string: s) {
                AsyncImage(url: u) { $0.resizable().scaledToFill() } placeholder: { Color.clear }
            } else {
                Color.clear
            }
        }
    }

    private func pagerDot(active: Bool, label: String) -> some View {
        HStack(spacing: 5) {
            Circle()
                .fill(active ? Palette.ink : Palette.hairline)
                .frame(width: 6, height: 6)
            Text(label)
                .font(.system(size: 9, weight: .semibold))
                .tracking(1.5)
                .foregroundStyle(active ? Palette.ink : Palette.muted)
        }
    }

    // MARK: - Body

    @ViewBuilder
    private func contentBlock(o: Outfit) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            briefBlock(o: o)          // ②
            readBlock(o: o)           // ③
            xrayButton                // (kept)
            dressCodeBlock(o: o)      // ④
            presenceBlock(o: o)       // ⑤
            whyThisNumberBlock(o: o)  // ⑥
            breakdownBlock(o: o)      // ⑦
            leverBlock(o: o)          // ⑧
            swapsBlock(o: o)          // ⑨
            caveatsBlock(o: o)        // ⑩
            piecesBlock(o: o)         // ⑪
            detailsBlock(o: o)        // ⑫
            // This read was written by AI — let the wearer flag it here.
            Button(action: { Haptic.tap(); reportTarget = ReportTarget(ReportKind.outfit, outfitId) }) {
                HStack(spacing: 6) {
                    Image(systemName: "flag").font(.system(size: 11))
                    Text("Report this critique").font(Serif.body(13))
                }
                .foregroundStyle(Palette.muted)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .padding(.top, 12)
        }
        .padding(.horizontal, 20)
        .padding(.top, 20)
    }

    @ViewBuilder
    private var xrayButton: some View {
        if !markupAnnotations.isEmpty || fitMap != nil {
            Button(action: { Haptic.tap(); withAnimation(.easeInOut(duration: 0.25)) { showXRay = true } }) {
                HStack(spacing: 8) {
                    Image(systemName: "eye")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(Palette.bronze)
                    Text("See the read")
                        .font(Serif.body(14, weight: .medium))
                        .foregroundStyle(Palette.ink)
                    Spacer()
                    Image(systemName: "arrow.right")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(Palette.bronze)
                }
                .padding(.horizontal, 14).padding(.vertical, 12)
                .background(Palette.card)
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(Palette.hairline, lineWidth: 1))
                .clipShape(RoundedRectangle(cornerRadius: 12))
            }
            .buttonStyle(.plain)
        }
    }

    // MARK: - ② What I graded you against

    /// The intake, read back one answer at a time, each with what it actually
    /// did to the weights. This section exists so a user can never say the app
    /// ignored their answer.
    @ViewBuilder
    private func briefBlock(o: Outfit) -> some View {
        let answers = briefAnswers(o: o)
        if !answers.isEmpty {
            VStack(alignment: .leading, spacing: 0) {
                HStack {
                    Eyebrow(text: "WHAT I GRADED YOU AGAINST")
                    Spacer()
                    Button {
                        Haptic.chip()
                        if let onRescore { onRescore() } else { showRebrief = true }
                    } label: {
                        HStack(spacing: 3) {
                            Text("change this").font(Serif.body(12))
                            Image(systemName: "chevron.right").font(.system(size: 9, weight: .semibold))
                        }
                        .foregroundStyle(Palette.bronze)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
                .padding(.bottom, 6)

                ForEach(answers) { a in
                    VStack(alignment: .leading, spacing: 3) {
                        HStack(alignment: .firstTextBaseline) {
                            Text(a.label).font(Serif.body(14)).foregroundStyle(Palette.muted)
                            Spacer(minLength: 10)
                            Text(a.value)
                                .font(Serif.body(14, weight: .medium))
                                .foregroundStyle(Palette.ink)
                                .multilineTextAlignment(.trailing)
                        }
                        if !a.effect.isEmpty {
                            Text(a.effect)
                                .font(Serif.body(12))
                                .foregroundStyle(Palette.muted)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                    .padding(.vertical, 9)
                    Rectangle().fill(Palette.hairline).frame(height: 0.5)
                }
            }
            .padding(.top, 4)
        }
    }

    private struct BriefAnswer: Identifiable {
        let label: String
        let value: String
        let effect: String
        var id: String { "\(label)|\(value)" }
    }

    /// Derived from the stored `intake` and the stored `axes`.
    ///
    /// The server's `intake_echo` is not one of the columns an outfit row
    /// carries, so it cannot be read back here. Everything below is rebuilt from
    /// the same tables the server used — the weights come off the row's own
    /// `axes`, so the percentages quoted are the ones that were actually
    /// applied, not a re-derivation that could drift.
    private func briefAnswers(o: Outfit) -> [BriefAnswer] {
        guard let intake = o.intake, o.scoringFamily == "v4" else { return [] }
        let weights = weightMap(o: o, intake: intake)
        var rows: [BriefAnswer] = []

        let heaviest = weights.max { lhs, rhs in
            lhs.value == rhs.value ? lhs.key.order > rhs.key.order : lhs.value < rhs.value
        }
        let heaviestCopy = heaviest.map {
            "\($0.key.label) carried the most weight here, at \(pct($0.value))."
        } ?? ""
        rows.append(BriefAnswer(label: "Occasion", value: intake.occasion.title, effect: heaviestCopy))

        let target = Rubric.target(intake.occasion, intake.formality)
        rows.append(BriefAnswer(
            label: "Dressed for",
            value: intake.formalityCaption,
            effect: "Graded against \(Rubric.formalityReadLabel(target).lowercased()) on the absolute scale."
        ))

        let loudAxes: [AxisKey] = [.COL, .DTL, .POV]
        let loudShare = loudAxes.reduce(0.0) { $0 + (weights[$1] ?? 0) }
        rows.append(BriefAnswer(
            label: "Presence",
            value: intake.presenceCaption,
            effect: "Colour, detail and point of view carry \(pct(loudShare)) between them at that setting."
        ))

        if let role = intake.role {
            rows.append(BriefAnswer(label: "Your part in it", value: role.label,
                                    effect: deltaEffect(Rubric.roleDeltas[role] ?? [:])))
        }
        if let venue = intake.venue {
            rows.append(BriefAnswer(label: "Venue", value: venue.label,
                                    effect: deltaEffect(Rubric.venueDeltas[venue] ?? [:])))
        }
        if let room = intake.room {
            rows.append(BriefAnswer(label: "The room", value: room.label,
                                    effect: deltaEffect(Rubric.roomDeltas[room] ?? [:])))
        }
        if let feet = intake.on_feet {
            rows.append(BriefAnswer(label: "On your feet", value: feet.label,
                                    effect: deltaEffect(Rubric.onFeetDeltas[feet] ?? [:])))
        }
        if let weather = intake.weather {
            var d = Rubric.weatherDeltas[weather.band] ?? [:]
            if weather.precip {
                for (k, v) in Rubric.precipDelta { d[k, default: 0] += v }
            }
            rows.append(BriefAnswer(
                label: "Weather",
                value: weather.band.label + (weather.precip ? " · rain" : ""),
                effect: deltaEffect(d)
            ))
        } else {
            rows.append(BriefAnswer(label: "Weather", value: "Not given",
                                    effect: "The weather rules stayed disarmed — I don't guess at it."))
        }
        rows.append(BriefAnswer(label: "Time of day", value: intake.time_of_day.label,
                                effect: deltaEffect(Rubric.timeDeltas[intake.time_of_day] ?? [:])))

        if let intent = intake.intent ?? o.intent, !intent.isEmpty {
            let axis = (o.axes ?? []).first(where: { $0.isIntentAxis })?.axisKey ?? intake.intentChipAxis
            let effect = axis.map {
                "Bought \($0.label.lowercased()) an extra \(pct(Rubric.intentWeightBonus)) of the weight."
            } ?? "Read as an instruction and weighted for."
            rows.append(BriefAnswer(label: "You asked for", value: intent, effect: effect))
        }
        return rows
    }

    /// The weights actually applied, off the row's own axes. Falls back to the
    /// client's preview port only for a row whose axes never made it to storage.
    private func weightMap(o: Outfit, intake: ScoreIntake) -> [AxisKey: Double] {
        let axes = o.axes ?? []
        if !axes.isEmpty {
            var out: [AxisKey: Double] = [:]
            for a in axes { if let k = a.axisKey { out[k] = a.weight } }
            if !out.isEmpty { return out }
        }
        return RubricWeights.weights(for: intake)
    }

    /// "Raised footwear and fabric; eased detail and colour." Built off the same
    /// delta tables the server applies, so it can be checked line by line.
    private func deltaEffect(_ deltas: [AxisKey: Double]) -> String {
        let ups = deltas.filter { $0.value > 0 }.sorted { $0.value > $1.value }.map { $0.key.label.lowercased() }
        let downs = deltas.filter { $0.value < 0 }.sorted { $0.value < $1.value }.map { $0.key.label.lowercased() }
        if ups.isEmpty && downs.isEmpty { return "The default for this brief — nothing shifted." }
        var parts: [String] = []
        if !ups.isEmpty { parts.append("Raised \(list(ups))") }
        if !downs.isEmpty { parts.append("eased \(list(downs))") }
        return parts.joined(separator: "; ") + "."
    }

    private func list(_ items: [String]) -> String {
        switch items.count {
        case 0: return ""
        case 1: return items[0]
        case 2: return "\(items[0]) and \(items[1])"
        default: return items.dropLast().joined(separator: ", ") + " and " + (items.last ?? "")
        }
    }

    private func pct(_ v: Double) -> String { "\(Int((v * 100).rounded()))%" }

    // MARK: - ③ The read

    @ViewBuilder
    private func readBlock(o: Outfit) -> some View {
        let verdict = (o.verdict ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let comment = (o.hem_comment ?? o.notes ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        if !verdict.isEmpty || !comment.isEmpty {
            VStack(alignment: .leading, spacing: 12) {
                if !verdict.isEmpty || !comment.isEmpty {
                    Eyebrow(text: "THE READ").padding(.top, 8)
                }
                PullQuote(quote: verdict.isEmpty ? comment : verdict, attribution: nil)
                if !verdict.isEmpty && !comment.isEmpty {
                    HStack(alignment: .top, spacing: 8) {
                        HemMonogram(size: 18)
                        Text(comment)
                            .font(Serif.body(14))
                            .foregroundStyle(Palette.muted)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
            }
        }
    }

    // MARK: - ④ Dress code

    @ViewBuilder
    private func dressCodeBlock(o: Outfit) -> some View {
        if let dc = o.dress_code, dc.shouldRender {
            VStack(alignment: .leading, spacing: 12) {
                Eyebrow(text: "DRESS CODE").padding(.top, 8)

                VStack(alignment: .leading, spacing: 8) {
                    if let claimed = dc.claimed ?? dc.target {
                        codeRow(caption: "You said",
                                label: (o.intake?.formalityCaption).flatMap { $0.isEmpty ? nil : $0 }
                                    ?? Rubric.formalityReadLabel(claimed),
                                level: claimed,
                                tint: Palette.ink)
                    }
                    if let read = dc.read {
                        codeRow(caption: "The photo says",
                                label: Rubric.formalityReadLabel(read),
                                level: read,
                                tint: Palette.bronze)
                    }
                }

                if let line = dc.line, !line.isEmpty {
                    Text(line)
                        .font(Serif.body(15, weight: .medium))
                        .foregroundStyle(Palette.ink)
                        .fixedSize(horizontal: false, vertical: true)
                }
                if let severity = dc.severity, !severity.isEmpty {
                    Text(severityCopy(severity, direction: dc.direction, gap: dc.gap))
                        .font(Serif.body(13))
                        .foregroundStyle(Palette.muted)
                        .fixedSize(horizontal: false, vertical: true)
                }
                if let ev = dc.evidence, !ev.isEmpty {
                    Text(ev)
                        .font(Serif.body(13))
                        .foregroundStyle(Palette.muted)
                        .fixedSize(horizontal: false, vertical: true)
                }

                if let markers = dc.markers, !markers.isEmpty {
                    VStack(alignment: .leading, spacing: 8) {
                        ForEach(markers) { m in markerRow(m) }
                    }
                    .padding(.top, 2)
                }

                if let rule = codeRule(o: o) {
                    HStack(alignment: .top, spacing: 8) {
                        Rectangle().fill(Palette.bronze.opacity(0.5)).frame(width: 2)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(rule.name ?? rule.id)
                                .font(Serif.body(13, weight: .medium))
                                .foregroundStyle(Palette.ink)
                            if let effect = rule.effect ?? rule.trigger {
                                Text(effect)
                                    .font(Serif.body(12))
                                    .foregroundStyle(Palette.muted)
                                    .fixedSize(horizontal: false, vertical: true)
                            }
                        }
                    }
                    .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
    }

    /// "YOU SAID BLACK TIE ●●●●●" — the dial as five pips so the claim and the
    /// read can be compared without reading two numbers.
    private func codeRow(caption: String, label: String, level: Int, tint: Color) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 10) {
            Text(caption.uppercased())
                .font(.system(size: 10, weight: .semibold))
                .tracking(1.6)
                .foregroundStyle(Palette.muted)
                .frame(width: 100, alignment: .leading)
            Text(label.uppercased())
                .font(.system(size: 12, weight: .semibold))
                .tracking(1.2)
                .foregroundStyle(tint)
            Spacer(minLength: 6)
            HStack(spacing: 4) {
                ForEach(1...5, id: \.self) { i in
                    Circle()
                        .fill(i <= level ? tint : Color.clear)
                        .overlay(Circle().stroke(tint.opacity(0.5), lineWidth: 1))
                        .frame(width: 7, height: 7)
                }
            }
        }
    }

    private func markerRow(_ m: CodeMarker) -> some View {
        HStack(alignment: .top, spacing: 8) {
            Group {
                switch m.present {
                case .some(true):  Image(systemName: "checkmark").foregroundStyle(Palette.bronze)
                case .some(false): Image(systemName: "xmark").foregroundStyle(Palette.roastRed)
                case nil:          Text("·").foregroundStyle(Palette.muted)
                }
            }
            .font(.system(size: 11, weight: .semibold))
            .frame(width: 14, alignment: .leading)

            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    Text(m.name)
                        .font(Serif.body(14))
                        .foregroundStyle(m.present == nil ? Palette.muted : Palette.ink)
                        .fixedSize(horizontal: false, vertical: true)
                    if m.required == true {
                        Text("REQUIRED")
                            .font(.system(size: 8.5, weight: .semibold))
                            .tracking(1)
                            .foregroundStyle(Palette.bronze)
                    }
                }
                if m.present == nil {
                    Text(m.note?.isEmpty == false ? (m.note ?? "") : "not visible")
                        .font(Serif.body(12))
                        .foregroundStyle(Palette.muted)
                        .fixedSize(horizontal: false, vertical: true)
                } else if let note = m.note, !note.isEmpty {
                    Text(note)
                        .font(Serif.body(12))
                        .foregroundStyle(Palette.muted)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
    }

    private func severityCopy(_ severity: String, direction: String?, gap: Int?) -> String {
        let rungs = abs(gap ?? 0)
        let step = rungs == 1 ? "a rung" : "\(rungs) rungs"
        switch severity {
        case "met":         return "You met the code."
        case "interpreted": return "A reading of the code rather than a break with it."
        case "near":
            guard let direction, direction != "none" else { return "Within a rung of the code." }
            return "\(step.capitalized) \(direction) the code."
        case "miss":
            guard let direction, direction != "none" else { return "Off the code." }
            return "\(step.capitalized) \(direction) the code — that is what the number is carrying."
        default:            return severity
        }
    }

    /// The rule that fired on the dress-code axis, if any.
    private func codeRule(o: Outfit) -> RuleFired? {
        (o.score_breakdown?.rules_fired ?? []).first { $0.axis == AxisKey.CTX.rawValue }
    }

    // MARK: - ⑤ Presence

    @ViewBuilder
    private func presenceBlock(o: Outfit) -> some View {
        if let pc = o.presence_check, pc.shouldRender {
            VStack(alignment: .leading, spacing: 10) {
                Eyebrow(text: "PRESENCE").padding(.top, 8)
                if let claimed = pc.claimed {
                    codeRow(caption: "You wanted",
                            label: Rubric.presenceCaption(claimed),
                            level: claimed,
                            tint: Palette.ink)
                }
                if let read = pc.read {
                    codeRow(caption: "The photo reads",
                            label: Rubric.presenceCaption(read),
                            level: read,
                            tint: Palette.bronze)
                }
                if let line = pc.line, !line.isEmpty {
                    Text(line)
                        .font(Serif.body(15, weight: .medium))
                        .foregroundStyle(Palette.ink)
                        .fixedSize(horizontal: false, vertical: true)
                }
                if let ev = pc.evidence, !ev.isEmpty {
                    Text(ev)
                        .font(Serif.body(13))
                        .foregroundStyle(Palette.muted)
                        .fixedSize(horizontal: false, vertical: true)
                }
                if let mech = pc.mechanism, !mech.isEmpty {
                    HStack(alignment: .top, spacing: 8) {
                        Rectangle().fill(Palette.bronze.opacity(0.5)).frame(width: 2)
                        Text(mech)
                            .font(Serif.body(12))
                            .foregroundStyle(Palette.muted)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
    }

    // MARK: - ⑥ Why this number

    /// The ledger. Weighted mean at the top, every rule that fired with what it
    /// cost, the binding cap, the final. Nothing on this screen is model-written
    /// — it all comes off `score_breakdown`, and it adds up.
    @ViewBuilder
    private func whyThisNumberBlock(o: Outfit) -> some View {
        if let bd = o.score_breakdown, bd.raw_weighted != nil || !(bd.rules_fired ?? []).isEmpty {
            let fired = (bd.rules_fired ?? []).filter { ($0.headline_cost ?? 0) != 0 || $0.mode == "cap" }
            let shown = showAllRules ? fired : Array(fired.prefix(3))
            VStack(alignment: .leading, spacing: 0) {
                Eyebrow(text: "WHY THIS NUMBER").padding(.top, 8).padding(.bottom, 8)

                if let raw = bd.raw_weighted {
                    ledgerRow(title: "Weighted mean across nine axes",
                              value: String(format: "%.2f", raw),
                              emphasis: false)
                }

                ForEach(shown) { rule in
                    VStack(alignment: .leading, spacing: 3) {
                        HStack(alignment: .firstTextBaseline) {
                            Text(rule.name ?? rule.id)
                                .font(Serif.body(14))
                                .foregroundStyle(Palette.ink)
                                .fixedSize(horizontal: false, vertical: true)
                            Spacer(minLength: 10)
                            Text(signed(rule.headline_cost))
                                .font(Serif.body(14, weight: .medium))
                                .foregroundStyle((rule.headline_cost ?? 0) < 0 ? Palette.roastRed : Palette.bronze)
                        }
                        if let detail = rule.effect ?? rule.trigger, !detail.isEmpty {
                            Text(detail)
                                .font(Serif.body(12))
                                .foregroundStyle(Palette.muted)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                        if let axis = rule.axis, let before = rule.before, let after = rule.after {
                            Text("\(AxisKey(rawValue: axis)?.label ?? axis): \(String(format: "%.1f", before)) → \(String(format: "%.1f", after))")
                                .font(Serif.body(12))
                                .foregroundStyle(Palette.muted)
                        }
                    }
                    .padding(.vertical, 9)
                    Rectangle().fill(Palette.hairline).frame(height: 0.5)
                }

                if fired.count > shown.count || (showAllRules && fired.count > 3) {
                    Button {
                        Haptic.chip()
                        withAnimation(.easeInOut(duration: 0.2)) { showAllRules.toggle() }
                    } label: {
                        HStack(spacing: 6) {
                            Text(showAllRules ? "fewer" : "every rule that fired (\(fired.count))")
                                .font(Serif.body(13))
                            Image(systemName: showAllRules ? "chevron.up" : "chevron.down")
                                .font(.system(size: 10, weight: .semibold))
                        }
                        .foregroundStyle(Palette.bronze)
                        .padding(.vertical, 10)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }

                if let cap = bd.bindingCap, let to = cap.to {
                    ledgerRow(title: "Held at a ceiling of \(String(format: "%.1f", to))",
                              value: cap.id,
                              emphasis: false)
                }

                if let nulls = bd.null_axes, !nulls.isEmpty {
                    Text("\(list(nulls.map { AxisKey(rawValue: $0)?.label.lowercased() ?? $0.lowercased() })) couldn't be graded from the photo. The share went to the rest — nothing was guessed and nothing was held against you.".prefix(1).uppercased() + "\(list(nulls.map { AxisKey(rawValue: $0)?.label.lowercased() ?? $0.lowercased() })) couldn't be graded from the photo. The share went to the rest — nothing was guessed and nothing was held against you.".dropFirst())
                        .font(Serif.body(12))
                        .foregroundStyle(Palette.muted)
                        .fixedSize(horizontal: false, vertical: true)
                        .padding(.vertical, 8)
                }

                if let downgraded = bd.downgraded_to_caveat, !downgraded.isEmpty {
                    ForEach(downgraded) { d in
                        VStack(alignment: .leading, spacing: 2) {
                            HStack(alignment: .firstTextBaseline) {
                                Text(d.name ?? d.id).font(Serif.body(13)).foregroundStyle(Palette.muted)
                                Spacer(minLength: 10)
                                Text("no effect").font(Serif.body(12)).foregroundStyle(Palette.muted)
                            }
                            if let copy = d.copy, !copy.isEmpty {
                                Text(copy)
                                    .font(Serif.body(12))
                                    .foregroundStyle(Palette.muted)
                                    .fixedSize(horizontal: false, vertical: true)
                            }
                        }
                        .padding(.vertical, 7)
                    }
                }

                Rectangle().fill(Palette.ink.opacity(0.35)).frame(height: 1).padding(.top, 4)
                ledgerRow(title: "The number",
                          value: String(format: "%.1f", bd.final ?? o.displayScore ?? 0),
                          emphasis: true)

                if let history = bd.history, !history.isEmpty {
                    ForEach(Array(history.enumerated()), id: \.offset) { _, h in
                        Text("Re-scored from \(String(format: "%.1f", h.from ?? 0)) to \(String(format: "%.1f", h.to ?? 0))\(h.rubric_id.map { " · \($0)" } ?? "")")
                            .font(Serif.body(12))
                            .foregroundStyle(Palette.muted)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
            }
        }
    }

    private func ledgerRow(title: String, value: String, emphasis: Bool) -> some View {
        HStack(alignment: .firstTextBaseline) {
            Text(title)
                .font(Serif.body(emphasis ? 15 : 14, weight: emphasis ? .medium : .regular))
                .foregroundStyle(Palette.ink)
                .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 10)
            Text(value)
                .font(emphasis ? Serif.display(20, weight: .semibold) : Serif.body(14, weight: .medium))
                .foregroundStyle(emphasis ? Palette.ink : Palette.muted)
        }
        .padding(.vertical, 9)
    }

    private func signed(_ v: Double?) -> String {
        guard let v, v != 0 else { return "0.00" }
        return (v > 0 ? "+" : "−") + String(format: "%.2f", abs(v))
    }

    // MARK: - ⑦ The breakdown

    @ViewBuilder
    private func breakdownBlock(o: Outfit) -> some View {
        let axes = o.axes ?? []
        if !axes.isEmpty {
            VStack(alignment: .leading, spacing: 0) {
                Eyebrow(text: "THE BREAKDOWN").padding(.top, 8).padding(.bottom, 6)
                AxisBreakdownView(axes: axes, ruleNames: ruleNames(o: o))
                Button {
                    Haptic.chip()
                    withAnimation(.easeInOut(duration: 0.2)) { showWeightTable.toggle() }
                } label: {
                    HStack(spacing: 6) {
                        Text(showWeightTable ? "hide the maths" : "how is this calculated?")
                            .font(Serif.body(13))
                        Image(systemName: showWeightTable ? "chevron.up" : "chevron.right")
                            .font(.system(size: 10, weight: .semibold))
                    }
                    .foregroundStyle(Palette.bronze)
                    .padding(.vertical, 12)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                if showWeightTable {
                    AxisWeightTable(axes: axes, source: o.score_breakdown?.weights_source)
                        .padding(.bottom, 8)
                }
            }
        } else if let sub = o.subscores {
            // Legacy rows: the four-subscore grid, and the arithmetic spelled
            // out so the hero number and the breakdown can never disagree.
            VStack(alignment: .leading, spacing: 0) {
                Eyebrow(text: "BREAKDOWN").padding(.top, 8).padding(.bottom, 6)
                SubscoresView(subscores: sub)
                if let avg = sub.averaged {
                    HStack {
                        Text("Average")
                            .font(Serif.body(15, weight: .medium))
                            .foregroundStyle(Palette.ink)
                        Spacer()
                        Text(String(format: "%.1f / 10", avg))
                            .font(Serif.body(14, weight: .medium))
                            .foregroundStyle(Palette.bronze)
                    }
                    .padding(.vertical, 10)
                }
            }
        }
    }

    private func ruleNames(o: Outfit) -> [String: String] {
        var out: [String: String] = [:]
        for r in o.score_breakdown?.rules_fired ?? [] where !r.id.isEmpty {
            out[r.id] = r.name ?? r.id
        }
        for d in o.score_breakdown?.downgraded_to_caveat ?? [] where !d.id.isEmpty {
            out[d.id] = d.name ?? d.id
        }
        return out
    }

    // MARK: - ⑧ The one thing

    @ViewBuilder
    private func leverBlock(o: Outfit) -> some View {
        if let lever = o.lever, lever.action?.isEmpty == false || lever.copy?.isEmpty == false {
            VStack(alignment: .leading, spacing: 10) {
                Eyebrow(text: "THE ONE THING").padding(.top, 8)
                TanCard {
                    VStack(alignment: .leading, spacing: 10) {
                        if let action = lever.action, !action.isEmpty {
                            Text(action)
                                .font(Serif.display(20))
                                .foregroundStyle(Palette.ink)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                        if let label = lever.label ?? lever.axis.flatMap({ AxisKey(rawValue: $0)?.label }) {
                            HStack(spacing: 8) {
                                Text(label.uppercased())
                                    .font(.system(size: 10, weight: .semibold))
                                    .tracking(1.6)
                                    .foregroundStyle(Palette.bronze)
                                if let from = lever.from, let to = lever.to {
                                    Text("\(String(format: "%.1f", from)) → \(String(format: "%.1f", to))")
                                        .font(Serif.body(13, weight: .medium))
                                        .foregroundStyle(Palette.ink)
                                }
                            }
                        }
                        if let copy = lever.copy, !copy.isEmpty {
                            Text(copy)
                                .font(Serif.body(14))
                                .foregroundStyle(Palette.ink)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                        if let projected = lever.projected {
                            HStack(alignment: .firstTextBaseline, spacing: 8) {
                                Text(String(format: "%.1f", projected))
                                    .font(Serif.display(28, weight: .semibold))
                                    .foregroundStyle(Palette.ink)
                                if let delta = lever.delta, delta != 0 {
                                    Text(String(format: "%+.1f", delta))
                                        .font(Serif.body(14, weight: .medium))
                                        .foregroundStyle(Palette.bronze)
                                }
                                Spacer(minLength: 0)
                            }
                        }
                        Text("Projected on the same photograph, with everything else unchanged. Not a promise — the next shot gets read on its own.")
                            .font(Serif.body(12))
                            .foregroundStyle(Palette.muted)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
        }
    }

    // MARK: - ⑨ Swaps

    /// `swaps_v2` is not one of the columns an outfit row carries, so a stored
    /// row always renders the flat `swaps` strings the server also writes. The
    /// structured branch is here for the day the column lands.
    @ViewBuilder
    private func swapsBlock(o: Outfit) -> some View {
        if let swaps = o.swaps, !swaps.isEmpty {
            VStack(alignment: .leading, spacing: 8) {
                Eyebrow(text: "SWAPS").padding(.top, 8)
                ForEach(swaps, id: \.self) { s in
                    HStack(alignment: .top, spacing: 8) {
                        Text("•").font(Serif.body(15)).foregroundStyle(Palette.bronze)
                        Text(s)
                            .font(Serif.body(15))
                            .foregroundStyle(Palette.ink)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
            }
        }
    }

    // MARK: - ⑩ What I couldn't see

    @ViewBuilder
    private func caveatsBlock(o: Outfit) -> some View {
        let caveats = o.caveats ?? []
        if !caveats.isEmpty {
            VStack(alignment: .leading, spacing: 10) {
                Eyebrow(text: "WHAT I COULDN'T SEE").padding(.top, 8)
                ForEach(caveats) { c in
                    VStack(alignment: .leading, spacing: 3) {
                        Text(c.copy)
                            .font(Serif.body(14))
                            .foregroundStyle(Palette.ink)
                            .fixedSize(horizontal: false, vertical: true)
                        if let cost = c.cost, !cost.isEmpty {
                            Text(cost)
                                .font(Serif.body(12))
                                .foregroundStyle(Palette.muted)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                }
                if caveats.contains(where: { $0.kind == "no_back_view" }) {
                    OutlinePill(title: "Add a back view · \(Supa.scoreCost + Supa.frontBackExtraCost) credits") {
                        Haptic.tap()
                        // The back view has to be photographed, and the camera
                        // flow owns that. Close out of the detail first: two
                        // presentations in one runloop tick cancel each other.
                        onClose()
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) {
                            CameraMenuBus.shared.request(.score, context: nil)
                        }
                    }
                }
            }
        }
    }

    // MARK: - ⑪ The pieces

    @ViewBuilder
    private func piecesBlock(o: Outfit) -> some View {
        let pieces = o.pieces ?? []
        if !pieces.isEmpty {
            VStack(alignment: .leading, spacing: 0) {
                Eyebrow(text: "THE PIECES").padding(.top, 8).padding(.bottom, 6)
                ForEach(pieces) { p in
                    VStack(alignment: .leading, spacing: 4) {
                        HStack(alignment: .firstTextBaseline, spacing: 8) {
                            Text(p.label)
                                .font(Serif.body(15, weight: .medium))
                                .foregroundStyle(Palette.ink)
                            Text(pieceVerdict(p.verdict).text.uppercased())
                                .font(.system(size: 9, weight: .semibold))
                                .tracking(1.2)
                                .foregroundStyle(pieceVerdict(p.verdict).color)
                                .padding(.horizontal, 6).padding(.vertical, 3)
                                .overlay(RoundedRectangle(cornerRadius: 3)
                                    .stroke(pieceVerdict(p.verdict).color, lineWidth: 1))
                            Spacer(minLength: 6)
                            Text(String(format: "%.1f", p.score))
                                .font(Serif.display(16, weight: .semibold))
                                .foregroundStyle(Palette.ink)
                        }
                        if !p.note.isEmpty {
                            Text(p.note)
                                .font(Serif.body(13))
                                .foregroundStyle(Palette.muted)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                    .padding(.vertical, 10)
                    Rectangle().fill(Palette.hairline).frame(height: 0.5)
                }
            }
        }
    }

    private func pieceVerdict(_ raw: String) -> (text: String, color: Color) {
        switch raw {
        case "works":           return ("Works", Palette.bronze)
        case "works_elsewhere": return ("Works elsewhere", Palette.muted)
        case "drags":           return ("Drags", Palette.roastRed)
        default:                return (raw.replacingOccurrences(of: "_", with: " "), Palette.muted)
        }
    }

    // MARK: - ⑫ The details

    /// The wearable facts about a fit: when it was worn, what it was graded at,
    /// which views I had, and its palette.
    @ViewBuilder
    private func detailsBlock(o: Outfit) -> some View {
        let worn = formattedWornDate(o.created_at)
        let palette = Array((o.dominant_colors ?? []).prefix(6))
        let kind = (o.kind ?? "").isEmpty || o.kind == "score" ? nil : o.kind
        let gradedAt = o.intake.map { intake -> String in
            [intake.formalityCaption, intake.presenceCaption]
                .filter { !$0.isEmpty }
                .joined(separator: " · ")
        }
        let hasAny = worn != nil || !(o.occasion ?? "").isEmpty
            || o.weather_c != nil || !palette.isEmpty || kind != nil
            || gradedAt != nil || o.rubric_id != nil || o.fits_you != nil
        if hasAny {
            Eyebrow(text: "THE DETAILS").padding(.top, 8)
            VStack(alignment: .leading, spacing: 0) {
                if let w = worn { detailRow(label: "Worn", value: w) }
                if let occ = o.occasion, !occ.isEmpty {
                    detailRow(label: "Occasion", value: occ.capitalized)
                }
                if let g = gradedAt, !g.isEmpty { detailRow(label: "Graded at", value: g) }
                if let rid = o.rubric_id, !rid.isEmpty { detailRow(label: "Rubric", value: rid) }
                if o.scoringFamily == "v4" || o.back_photo_path != nil {
                    let both = o.back_photo_path != nil || o.intake?.has_back == true
                    detailRow(label: "Views", value: both ? "Front and back" : "Front only")
                }
                if let f = o.fits_you {
                    detailRow(label: "Fits you", value: String(format: "%.1f / 10", f))
                }
                if let c = o.weather_c { detailRow(label: "Weather", value: "\(c)°C") }
                if let k = kind {
                    detailRow(label: "Kind", value: k.replacingOccurrences(of: "_", with: " ").capitalized)
                }
                if !palette.isEmpty {
                    VStack(spacing: 0) {
                        HStack {
                            Text("Palette").font(Serif.body(15)).foregroundStyle(Palette.ink)
                            Spacer()
                            HStack(spacing: 6) {
                                ForEach(palette, id: \.self) { hex in
                                    Circle()
                                        .fill(Color(hex: hex) ?? Palette.muted.opacity(0.3))
                                        .frame(width: 16, height: 16)
                                        .overlay(Circle().stroke(Palette.hairline, lineWidth: 1))
                                }
                            }
                        }
                        .padding(.vertical, 10)
                        Rectangle().fill(Palette.hairline).frame(height: 0.5)
                    }
                }
            }
        }
    }

    private func detailRow(label: String, value: String) -> some View {
        VStack(spacing: 0) {
            HStack(alignment: .firstTextBaseline) {
                Text(label).font(Serif.body(15)).foregroundStyle(Palette.ink)
                Spacer(minLength: 10)
                Text(value)
                    .font(Serif.body(14))
                    .foregroundStyle(Palette.muted)
                    .multilineTextAlignment(.trailing)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(.vertical, 10)
            Rectangle().fill(Palette.hairline).frame(height: 0.5)
        }
    }

    /// Supabase hands back ISO-8601 with or without fractional seconds.
    private func formattedWornDate(_ raw: String?) -> String? {
        guard let raw, !raw.isEmpty else { return nil }
        let iso = ISO8601DateFormatter()
        iso.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        var date = iso.date(from: raw)
        if date == nil {
            iso.formatOptions = [.withInternetDateTime]
            date = iso.date(from: raw)
        }
        guard let date else { return nil }
        let out = DateFormatter()
        out.dateFormat = "d MMM yyyy · HH:mm"
        return out.string(from: date)
    }

    private var notFoundBlock: some View {
        VStack(alignment: .center, spacing: 8) {
            HStack {
                Button(action: onClose) {
                    Image(systemName: "xmark").frame(width: 28, height: 28).foregroundStyle(Palette.ink)
                }
                Spacer()
            }
            Spacer().frame(height: 40)
            Text("Outfit not found.").font(Serif.display(22)).foregroundStyle(Palette.ink)
            Text("It may have been deleted.").font(Serif.body(14)).foregroundStyle(Palette.muted)
        }
        .padding(20)
        .frame(maxWidth: .infinity)
    }

    private func skeleton(heroHeight: CGFloat) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            RoundedRectangle(cornerRadius: 20).fill(Palette.card).frame(height: heroHeight)
            ForEach(0..<4, id: \.self) { _ in
                SkeletonBar(height: 18)
            }
        }
        .padding(20)
    }

    // MARK: - Loading

    /// Two round trips deep, not five.
    ///
    /// The row, the recent list, the markup and the fit map are independent, so
    /// they go out together; only the two signed URLs have to wait, because
    /// neither the photo path nor the side view's id exists until the row lands.
    private func load() async {
        async let rowTask = fetchOutfit()
        async let recentTask = fetchRecent()
        async let annotationsTask = fetchAnnotations()
        async let fitMapTask = fetchFitMap()
        let (row, recent, annotations, map) = await (rowTask, recentTask, annotationsTask, fitMapTask)

        outfit = row
        markupAnnotations = annotations
        fitMap = map

        if let row {
            // The average is only meaningful WITHIN this row's scoring family:
            // a v4 headline is capped and a v3 mean is not, so mixing them would
            // report a good look as below average. No sibling in the same
            // family ⇒ no delta, and the eyebrow drops it.
            let family = row.scoringFamily
            let peers = recent
                .filter { $0.kind == nil || $0.kind == "score" || $0.kind == "user_scan" }
                .filter { $0.scoringFamily == family }
                .compactMap { $0.displayScore }
                .filter { $0 > 0 }
            average = peers.isEmpty ? nil : peers.reduce(0, +) / Double(peers.count)

            async let frontTask = signedUrl(row.photo_path)
            async let sideTask = fetchSideUrl(row)
            let (front, side) = await (frontTask, sideTask)
            photoUrl = front
            sideUrl = side
        }

        loaded = true
        if outfit?.displayScore != nil { Haptic.soft() }
    }

    private func fetchOutfit() async -> Outfit? {
        try? await Repo.shared.outfitById(outfitId)
    }

    private func fetchRecent() async -> [Outfit] {
        (try? await Repo.shared.outfits(limit: 50)) ?? []
    }

    private func fetchAnnotations() async -> [MarkupAnnotation] {
        (try? await Repo.shared.loadAnnotations(outfitId: outfitId)) ?? []
    }

    private func fetchFitMap() async -> FitMap? {
        try? await Repo.shared.loadFitMap(outfitId: outfitId)
    }

    private func signedUrl(_ path: String?) async -> String? {
        guard let path else { return nil }
        return try? await Repo.shared.signedOutfitUrl(path)
    }

    /// Studio outfits keep their side view in a sibling row.
    private func fetchSideUrl(_ row: Outfit) async -> String? {
        guard row.kind == "outfit_studio" else { return nil }
        guard let side = try? await Repo.shared.linkedOutfit(linkedTo: outfitId, kind: "outfit_studio_side"),
              let path = side.photo_path else { return nil }
        return try? await Repo.shared.signedOutfitUrl(path)
    }

    // MARK: - Re-brief

    /// `/rescore` replays the engine over the stored witness statement with a
    /// new brief. No model call, no credit — but at most three per look, which
    /// the server enforces and the sheet states.
    private func rescore(with intake: ScoreIntake) async {
        guard !rescoring else { return }
        rescoring = true
        defer { rescoring = false }
        do {
            _ = try await HemService.rescore(outfitId: outfitId, intake: intake)
            showRebrief = false
            loaded = false
            await load()
            ToastBus.shared.post("Re-scored against the new brief")
        } catch {
            ToastBus.shared.post(rescoreMessage(for: error))
        }
    }

    private func rescoreMessage(for error: Error) -> String {
        let text = error.localizedDescription
        if text.contains("rescore_limit") { return "That's all three re-scores for this look." }
        if text.contains("no_witness") { return "This look predates the new engine — score a fresh photo." }
        return "Couldn't re-score that. Try again in a moment."
    }
}

// MARK: - Re-brief sheet

/// The fallback brief editor, used when the presenter did not supply its own.
///
/// It edits an intake and hands it back; it never scores anything. The strip at
/// the bottom is the client's weight PREVIEW — the same port the brief sheet
/// uses — so the user can see what a dial does before spending a re-score.
private struct ScoreRebriefSheet: View {
    @State var intake: ScoreIntake
    let rescoresLeft: Int
    let busy: Bool
    var onCancel: () -> Void
    var onSubmit: (ScoreIntake) -> Void

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 22) {
                    Text("Re-scoring replays the same read against a different brief. It costs nothing and never calls the model again.")
                        .font(Serif.body(14))
                        .foregroundStyle(Palette.muted)
                        .fixedSize(horizontal: false, vertical: true)

                    occasionPicker
                    dial(title: "DRESSED FOR",
                         caption: intake.formalityCaption,
                         value: intake.formality) { intake.formality = $0 }
                    dial(title: "PRESENCE",
                         caption: intake.presenceCaption,
                         value: intake.presence) { intake.presence = $0 }
                    bandFour
                    timePicker
                    primaryAxesStrip
                }
                .padding(20)
            }
            .background(Palette.paper.ignoresSafeArea())
            .safeAreaInset(edge: .bottom) {
                StickyFooter {
                    VStack(spacing: 8) {
                        PrimaryButton(
                            title: busy ? "RE-SCORING…" : "RE-SCORE · FREE",
                            enabled: !busy && rescoresLeft > 0
                        ) {
                            onSubmit(intake)
                        }
                        Text(rescoresLeft > 0
                             ? "\(rescoresLeft) re-score\(rescoresLeft == 1 ? "" : "s") left on this look"
                             : "No re-scores left on this look")
                            .font(Serif.body(12))
                            .foregroundStyle(Palette.muted)
                    }
                }
            }
            .navigationTitle("Change the brief")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close", action: onCancel)
                        .foregroundStyle(Palette.ink)
                }
            }
        }
    }

    private var occasionPicker: some View {
        VStack(alignment: .leading, spacing: 10) {
            Eyebrow(text: "OCCASION")
            FlowLayout(spacing: 8) {
                ForEach(ScoreOccasion.allCases, id: \.self) { occ in
                    let active = intake.occasion == occ
                    Button {
                        Haptic.chip()
                        // Tapping a chip snaps both dials to that occasion's
                        // seeds and drops the answers it has no use for — the
                        // same thing the server does with a foreign field.
                        intake.snapToOccasion(occ)
                    } label: {
                        Text(occ.title)
                            .font(Serif.body(13, weight: .medium))
                            .foregroundStyle(active ? .white : Palette.ink)
                            .padding(.horizontal, 14).padding(.vertical, 8)
                            .background(active ? Palette.ink : Palette.card)
                            .overlay(Capsule().stroke(Palette.hairline, lineWidth: 1))
                            .clipShape(Capsule())
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    private func dial(title: String, caption: String, value: Int, set: @escaping (Int) -> Void) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Eyebrow(text: title)
            HStack(spacing: 8) {
                ForEach(1...5, id: \.self) { i in
                    Button {
                        Haptic.chip()
                        set(i)
                    } label: {
                        Text("\(i)")
                            .font(Serif.body(14, weight: .medium))
                            .foregroundStyle(i == value ? .white : Palette.ink)
                            .frame(width: 44, height: 40)
                            .background(i == value ? Palette.ink : Palette.card)
                            .overlay(RoundedRectangle(cornerRadius: 4).stroke(Palette.hairline, lineWidth: 1))
                            .clipShape(RoundedRectangle(cornerRadius: 4))
                    }
                    .buttonStyle(.plain)
                }
            }
            Text(caption).font(Serif.italic(15)).foregroundStyle(Palette.muted)
        }
    }

    /// The one band-4 question this occasion owns. Anything else is dropped by
    /// `ScoreIntake` on the way out, so there is nothing to show.
    @ViewBuilder
    private var bandFour: some View {
        switch intake.occasion {
        case .wedding:
            picker(title: "YOUR PART IN IT", options: WeddingRole.allCases,
                   selection: intake.role ?? .guest, label: { $0.label }) { intake.role = $0 }
            picker(title: "VENUE", options: WeddingVenue.allCases,
                   selection: intake.venue ?? .ballroom, label: { $0.label }) { intake.venue = $0 }
        case .work:
            picker(title: "THE ROOM", options: WorkRoom.allCases,
                   selection: intake.room ?? .business_casual, label: { $0.label }) { intake.room = $0 }
        case .everyday:
            picker(title: "ON YOUR FEET", options: OnFeet.allCases,
                   selection: intake.on_feet ?? .some, label: { $0.label }) { intake.on_feet = $0 }
        case .date, .casual:
            EmptyView()
        }
    }

    private var timePicker: some View {
        picker(title: "TIME OF DAY", options: TimeOfDay.allCases,
               selection: intake.time_of_day, label: { $0.label }) { intake.time_of_day = $0 }
    }

    private func picker<T: Hashable>(
        title: String,
        options: [T],
        selection: T,
        label: @escaping (T) -> String,
        set: @escaping (T) -> Void
    ) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Eyebrow(text: title)
            FlowLayout(spacing: 8) {
                ForEach(options, id: \.self) { opt in
                    let active = opt == selection
                    Button {
                        Haptic.chip()
                        set(opt)
                    } label: {
                        Text(label(opt))
                            .font(Serif.body(13))
                            .foregroundStyle(active ? .white : Palette.ink)
                            .padding(.horizontal, 12).padding(.vertical, 7)
                            .background(active ? Palette.ink : Palette.card)
                            .overlay(Capsule().stroke(Palette.hairline, lineWidth: 1))
                            .clipShape(Capsule())
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    private var primaryAxesStrip: some View {
        let axes = RubricWeights.primaryAxes(for: intake, intentAxis: intake.intentChipAxis)
        let weights = RubricWeights.weights(for: intake, intentAxis: intake.intentChipAxis)
        return VStack(alignment: .leading, spacing: 8) {
            Eyebrow(text: "THIS BRIEF GRADES HARDEST ON")
            ForEach(axes, id: \.self) { axis in
                HStack {
                    Text(axis.label).font(Serif.body(14)).foregroundStyle(Palette.ink)
                    Spacer()
                    Text("\(Int(((weights[axis] ?? 0) * 100).rounded()))%")
                        .font(Serif.body(13, weight: .medium))
                        .foregroundStyle(Palette.bronze)
                }
                .padding(.vertical, 5)
            }
        }
    }
}
