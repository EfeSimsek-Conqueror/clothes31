import SwiftUI
import Kingfisher

/// Journal — editorial "chapter book". Each month = one chapter with opener,
/// hero fit, supporting rail, and closing brief. Vertical scroll of chapters,
/// separated by hairline dividers. No masonry, no FlowLayout — only vetted
/// primitives.
struct JournalView: View {
    @State private var outfits: [Outfit] = []
    @State private var photoUrls: [String: String] = [:]
    @State private var loaded = false

    @State private var kindTab: KindTab = .all
    @State private var activeFilter: FilterKind = .all
    @State private var selectedOccasions: Set<String> = []
    @State private var searchQuery = ""

    @State private var showOccasionSheet = false
    @State private var showStyleDna = false
    @State private var openOutfitId: String? = nil

    private enum KindTab: String, CaseIterable { case all = "All", fits = "Fits", studio = "Studio" }
    private enum FilterKind: String, CaseIterable { case all, best }

    private static let FIT_KINDS: Set<String> = ["score", "user_scan"]
    private static let STUDIO_KINDS: Set<String> = ["studio_gen", "tryon", "roast", "decode", "occasion_plan"]
    private static let DEFAULT_OCCASIONS = ["Everyday", "Work", "Date", "Wedding", "Weekend", "Party", "Formal", "Travel"]

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                masthead
                if loaded && !outfits.isEmpty {
                    styleDnaCard
                }
                filterBar
                if !loaded {
                    skeleton.padding(.top, 24)
                } else if orderedMonths.isEmpty {
                    emptyState.padding(.top, 60)
                } else {
                    ForEach(Array(orderedMonths.enumerated()), id: \.element) { idx, key in
                        chapter(key: key, index: idx)
                        if idx < orderedMonths.count - 1 {
                            chapterDivider
                        }
                    }
                }
                Spacer(minLength: 60)
            }
            .padding(.vertical, 16)
        }
        .background(Palette.paper.ignoresSafeArea())
        .refreshable { await load() }
        .task { if !loaded { await load() } }
        .sheet(isPresented: $showOccasionSheet) { occasionPicker }
        .sheet(isPresented: $showStyleDna) {
            StyleDnaView(monthKey: currentMonthKey(), onClose: { showStyleDna = false })
        }
        .fullScreenCover(item: openBinding) { wrap in
            ScoreDetailView(outfitId: wrap.id, onClose: { openOutfitId = nil })
        }
    }

    // MARK: - Masthead

    private var masthead: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("JOURNAL")
                .font(.system(size: 10, weight: .semibold))
                .tracking(2.5)
                .foregroundStyle(Palette.bronze)
            Text("Everything you've worn.")
                .font(Serif.display(30))
                .foregroundStyle(Palette.ink)
            if let quote = weekQuote {
                Text("\u{201C}\(quote)\u{201D}")
                    .font(Serif.body(15).italic())
                    .foregroundStyle(Palette.muted)
                    .padding(.top, 4)
            }
        }
        .padding(.horizontal, 22)
        .padding(.top, 8)
        .padding(.bottom, 20)
    }

    private var weekQuote: String? {
        outfits.first(where: { ($0.hem_comment?.isEmpty ?? true) == false })?.hem_comment
    }

    // MARK: - Style DNA card

    private var styleDnaCard: some View {
        Button(action: {
            Haptic.tap()
            showStyleDna = true
        }) {
            HStack(alignment: .center, spacing: 14) {
                ZStack {
                    Circle().fill(Palette.bronze.opacity(0.14))
                    Text("\u{2726}").font(.system(size: 20)).foregroundStyle(Palette.bronze)
                }
                .frame(width: 44, height: 44)
                VStack(alignment: .leading, spacing: 3) {
                    Text("STYLE DNA · WRAPPED")
                        .font(.system(size: 10, weight: .semibold))
                        .tracking(2)
                        .foregroundStyle(Palette.bronze)
                    Text("This month, distilled.")
                        .font(Serif.display(17))
                        .foregroundStyle(Palette.ink)
                    Text("Share your card \u{2192}")
                        .font(Serif.body(12).italic())
                        .foregroundStyle(Palette.muted)
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Palette.muted)
            }
            .padding(14)
            .background(Palette.card)
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(Palette.bronze.opacity(0.35), lineWidth: 1))
            .clipShape(RoundedRectangle(cornerRadius: 14))
        }
        .buttonStyle(.plain)
        .padding(.horizontal, 22)
        .padding(.bottom, 4)
    }

    // MARK: - Filter bar

    private var filterBar: some View {
        VStack(spacing: 12) {
            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass")
                    .foregroundStyle(Palette.muted)
                    .font(.system(size: 14))
                TextField("Search…", text: $searchQuery)
                    .font(.system(size: 15))
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
            }
            .padding(.vertical, 8)
            .overlay(
                Rectangle().fill(Palette.hairline).frame(height: 1),
                alignment: .bottom
            )
            .padding(.horizontal, 22)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(KindTab.allCases, id: \.self) { t in
                        chip(t.rawValue, on: kindTab == t) {
                            Haptic.chip(); kindTab = t
                        }
                    }
                    Divider().frame(height: 20).padding(.horizontal, 2)
                    chip("Best", on: activeFilter == .best) {
                        Haptic.chip()
                        activeFilter = activeFilter == .best ? .all : .best
                    }
                    chip(
                        selectedOccasions.isEmpty ? "Occasion" : "Occasion · \(selectedOccasions.count)",
                        on: !selectedOccasions.isEmpty
                    ) {
                        Haptic.chip(); showOccasionSheet = true
                    }
                    if !selectedOccasions.isEmpty || activeFilter == .best {
                        Button(action: {
                            Haptic.chip()
                            selectedOccasions.removeAll()
                            activeFilter = .all
                        }) {
                            Text("Reset")
                                .font(.system(size: 12, weight: .medium))
                                .foregroundStyle(Palette.muted)
                                .underline()
                        }
                        .buttonStyle(.plain)
                        .padding(.leading, 4)
                    }
                }
                .padding(.horizontal, 22)
            }
        }
        .padding(.bottom, 12)
    }

    private func chip(_ label: String, on selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label)
                .font(.system(size: 13, weight: .medium))
                .foregroundStyle(selected ? .white : Palette.ink)
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .background(selected ? Palette.ink : Color.clear)
                .overlay(Capsule().stroke(Palette.ink.opacity(0.4), lineWidth: 1))
                .clipShape(Capsule())
        }
        .buttonStyle(.plain)
    }

    // MARK: - Chapter

    private func chapter(key: String, index: Int) -> some View {
        let items = grouped[key] ?? []
        let hero = heroFor(items)
        let rest = items.filter { $0.stableId != (hero?.stableId ?? "") }
        return VStack(alignment: .leading, spacing: 18) {
            chapterOpener(key: key, count: items.count)
            if let quote = chapterQuote(items) {
                Text(quote)
                    .font(Serif.display(20).italic())
                    .foregroundStyle(Palette.ink)
                    .padding(.horizontal, 22)
                    .padding(.vertical, 4)
            }
            if let hero {
                heroCard(hero)
                    .padding(.horizontal, 22)
            }
            if !rest.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 10) {
                        ForEach(rest, id: \.stableId) { o in
                            smallCard(o)
                        }
                    }
                    .padding(.horizontal, 22)
                }
            }
            monthBrief(items)
                .padding(.horizontal, 22)
                .padding(.top, 4)
        }
        .padding(.vertical, 20)
    }

    private func chapterOpener(key: String, count: Int) -> some View {
        HStack(alignment: .center) {
            Text(prettyMonth(key))
                .font(.system(size: 11, weight: .semibold))
                .tracking(2.5)
                .foregroundStyle(Palette.bronze)
            Rectangle().fill(Palette.hairline).frame(height: 1)
            Text(romanNumeral(from: key))
                .font(Serif.display(16))
                .foregroundStyle(Palette.muted)
        }
        .padding(.horizontal, 22)
    }

    private var chapterDivider: some View {
        HStack {
            Spacer()
            Text("\u{2726}")   // small ornament
                .font(.system(size: 14))
                .foregroundStyle(Palette.bronze.opacity(0.5))
            Spacer()
        }
        .padding(.vertical, 24)
    }

    // MARK: - Cards

    @ViewBuilder
    private func heroCard(_ o: Outfit) -> some View {
        if o.kind == "occasion_plan" {
            occasionPlanCard(o)
        } else {
            let url = o.id.flatMap { photoUrls[$0] }
            Button {
                guard let id = o.id else { return }
                Haptic.tap()
                openOutfitId = id
            } label: {
                ZStack(alignment: .topTrailing) {
                    Group {
                        if let s = url, let u = URL(string: s) {
                            KFImage(u)
                                .placeholder { Rectangle().fill(Palette.muted.opacity(0.15)) }
                                .resizable()
                                .scaledToFill()
                        } else {
                            Rectangle().fill(Palette.muted.opacity(0.15))
                        }
                    }
                    .frame(height: 460)
                    .frame(maxWidth: .infinity)
                    .clipped()
                    if let sc = o.score, sc > 0 {
                        scoreChip(sc).padding(12)
                    } else if let k = o.kind, Self.STUDIO_KINDS.contains(k) {
                        kindPill(k).padding(12)
                    }
                }
                .clipShape(RoundedRectangle(cornerRadius: 4))
                .shadow(color: Palette.ink.opacity(0.08), radius: 12, x: 0, y: 6)
            }
            .buttonStyle(.plain)
        }
    }

    private func occasionPlanCard(_ o: Outfit) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Text("OCCASION PLAN")
                    .font(.system(size: 10, weight: .semibold))
                    .tracking(2)
                    .foregroundStyle(Palette.bronze)
                Spacer()
                if let occ = o.occasion, !occ.isEmpty {
                    Text(occ.uppercased())
                        .font(.system(size: 10, weight: .semibold))
                        .tracking(1.5)
                        .foregroundStyle(.white)
                        .padding(.horizontal, 8).padding(.vertical, 4)
                        .background(Palette.ink)
                        .clipShape(Capsule())
                }
            }
            if let comment = o.hem_comment, !comment.isEmpty {
                Text("\u{201C}\(comment)\u{201D}")
                    .font(Serif.display(20).italic())
                    .foregroundStyle(Palette.ink)
                    .fixedSize(horizontal: false, vertical: true)
            } else {
                Text("Saved plan")
                    .font(Serif.display(20).italic())
                    .foregroundStyle(Palette.ink)
            }
            if let notes = o.notes, !notes.isEmpty {
                Text(notes)
                    .font(Serif.body(14))
                    .foregroundStyle(Palette.muted)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Palette.card)
        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Palette.bronze.opacity(0.35), lineWidth: 1))
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }

    private func smallCard(_ o: Outfit) -> some View {
        let url = o.id.flatMap { photoUrls[$0] }
        return Button {
            guard let id = o.id else { return }
            Haptic.tap()
            openOutfitId = id
        } label: {
            ZStack(alignment: .topTrailing) {
                Group {
                    if let s = url, let u = URL(string: s) {
                        KFImage(u)
                            .placeholder { Rectangle().fill(Palette.muted.opacity(0.15)) }
                            .resizable()
                            .scaledToFill()
                    } else {
                        Rectangle().fill(Palette.muted.opacity(0.15))
                    }
                }
                .frame(width: 128, height: 168)
                .clipped()
                if let sc = o.score, sc > 0 {
                    scoreChip(sc).padding(6).scaleEffect(0.9)
                } else if let k = o.kind, Self.STUDIO_KINDS.contains(k) {
                    kindPill(k).padding(6).scaleEffect(0.85)
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: 4))
        }
        .buttonStyle(.plain)
    }

    private func scoreChip(_ s: Double) -> some View {
        let tint: Color = s >= 8 ? .green.opacity(0.85) : (s >= 6 ? Palette.bronze : .red.opacity(0.8))
        return Text(String(format: "%.1f", s))
            .font(.system(size: 12, weight: .bold))
            .foregroundStyle(.white)
            .padding(.horizontal, 8).padding(.vertical, 4)
            .background(tint).clipShape(Capsule())
    }

    private func kindPill(_ k: String) -> some View {
        let label: String
        switch k {
        case "studio_gen": label = "STUDIO"
        case "tryon": label = "TRY-ON"
        case "roast": label = "ROAST"
        case "decode": label = "DECODED"
        default: label = k.uppercased()
        }
        return Text(label)
            .font(.system(size: 10, weight: .semibold))
            .tracking(1.2)
            .foregroundStyle(.white)
            .padding(.horizontal, 8).padding(.vertical, 4)
            .background(k == "roast" ? Color.red.opacity(0.85) : Palette.bronze)
            .clipShape(Capsule())
    }

    // MARK: - Month brief

    private func monthBrief(_ items: [Outfit]) -> some View {
        let scored = items.compactMap { $0.score }.filter { $0 > 0 }
        let best = scored.max()
        let avg = scored.isEmpty ? nil : scored.reduce(0, +) / Double(scored.count)
        let mostWorn = items
            .compactMap { $0.occasion?.lowercased() }
            .reduce(into: [:]) { $0[$1, default: 0] += 1 }
            .max { $0.value < $1.value }?.key
            .map { $0.prefix(1).uppercased() + $0.dropFirst() }
        return HStack(spacing: 20) {
            statBlock(label: "COUNT", value: "\(items.count)")
            if let best {
                statBlock(label: "BEST", value: String(format: "%.1f", best))
            }
            if let avg {
                statBlock(label: "AVG", value: String(format: "%.1f", avg))
            }
            if let mostWorn {
                statBlock(label: "MOSTLY", value: String(mostWorn))
            }
            Spacer()
        }
        .padding(.top, 12)
        .overlay(
            Rectangle().fill(Palette.hairline).frame(height: 1),
            alignment: .top
        )
    }

    private func statBlock(label: String, value: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label)
                .font(.system(size: 9, weight: .semibold))
                .tracking(1.5)
                .foregroundStyle(Palette.muted)
            Text(value)
                .font(Serif.display(15))
                .foregroundStyle(Palette.ink)
        }
    }

    // MARK: - Empty / loading

    private var skeleton: some View {
        VStack(spacing: 24) {
            ForEach(0..<2, id: \.self) { _ in
                VStack(alignment: .leading, spacing: 12) {
                    RoundedRectangle(cornerRadius: 3)
                        .fill(Palette.muted.opacity(0.15))
                        .frame(width: 80, height: 10)
                    RoundedRectangle(cornerRadius: 4)
                        .fill(Palette.muted.opacity(0.15))
                        .frame(height: 400)
                }
                .padding(.horizontal, 22)
            }
        }
    }

    private var emptyState: some View {
        VStack(spacing: 14) {
            Image(systemName: "book.closed")
                .font(.system(size: 44))
                .foregroundStyle(Palette.muted.opacity(0.55))
            Text("Your Journal writes itself.")
                .font(Serif.display(22))
                .foregroundStyle(Palette.ink)
            Text("Start with today. Score a fit and it lands here.")
                .font(Serif.body(14).italic())
                .foregroundStyle(Palette.muted)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.horizontal, 32)
    }

    // MARK: - Occasion picker

    private var occasionPicker: some View {
        NavigationStack {
            List {
                ForEach(availableOccasions, id: \.self) { occ in
                    Button {
                        Haptic.chip()
                        if selectedOccasions.contains(occ) { selectedOccasions.remove(occ) }
                        else { selectedOccasions.insert(occ) }
                    } label: {
                        HStack {
                            Text(occ).foregroundStyle(Palette.ink)
                            Spacer()
                            if selectedOccasions.contains(occ) {
                                Image(systemName: "checkmark").foregroundStyle(Palette.bronze)
                            }
                        }
                    }
                }
                if !selectedOccasions.isEmpty {
                    Button("Clear all", role: .destructive) {
                        selectedOccasions.removeAll()
                    }
                }
            }
            .listStyle(.plain)
            .navigationTitle("Occasion")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") { showOccasionSheet = false }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }

    // MARK: - Data

    private var openBinding: Binding<IDWrap?> {
        Binding(get: { openOutfitId.map(IDWrap.init) }, set: { openOutfitId = $0?.id })
    }
    private struct IDWrap: Identifiable { let id: String }

    private var availableOccasions: [String] {
        let fromData = outfits.compactMap { $0.occasion }
            .map { $0.prefix(1).uppercased() + $0.dropFirst() }
        var seen: Set<String> = []
        return (Self.DEFAULT_OCCASIONS + fromData).filter { seen.insert(String($0)).inserted }
    }

    private var filtered: [Outfit] {
        var out = outfits
        switch kindTab {
        case .all:
            break
        case .fits:
            out = out.filter { o in (o.score ?? 0) > 0 && Self.FIT_KINDS.contains(o.kind ?? "score") }
        case .studio:
            out = out.filter { Self.STUDIO_KINDS.contains($0.kind ?? "") }
        }
        if activeFilter == .best {
            let scored = out.compactMap { $0.score }.filter { $0 > 0 }
            if scored.isEmpty { return [] }
            let avg = scored.reduce(0, +) / Double(scored.count)
            let threshold = max(7.0, avg + 0.5)
            out = out.filter { ($0.score ?? 0) >= threshold }
        }
        if !selectedOccasions.isEmpty {
            let s = Set(selectedOccasions.map { $0.lowercased() })
            out = out.filter { s.contains(($0.occasion ?? "").lowercased()) }
        }
        let q = searchQuery.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        if !q.isEmpty {
            out = out.filter {
                ($0.hem_comment?.lowercased().contains(q) ?? false) ||
                ($0.occasion?.lowercased().contains(q) ?? false) ||
                ($0.notes?.lowercased().contains(q) ?? false)
            }
        }
        return out
    }

    private var grouped: [String: [Outfit]] {
        Dictionary(grouping: filtered) { monthKey(from: $0.created_at) }
    }

    private var orderedMonths: [String] {
        grouped.keys.sorted(by: >).filter { $0 != "0000-00" }
    }

    private func heroFor(_ items: [Outfit]) -> Outfit? {
        items.max(by: { ($0.score ?? -1) < ($1.score ?? -1) }) ?? items.first
    }

    private func chapterQuote(_ items: [Outfit]) -> String? {
        // Deterministic 1-liner per month based on average score + tone
        let scored = items.compactMap { $0.score }.filter { $0 > 0 }
        guard !scored.isEmpty else {
            return items.first(where: { ($0.hem_comment?.isEmpty ?? true) == false })?.hem_comment
        }
        let avg = scored.reduce(0, +) / Double(scored.count)
        let mood: String
        switch avg {
        case 8.5...: mood = "A month you nailed."
        case 7.5..<8.5: mood = "Sharp, consistent, quiet confidence."
        case 6.5..<7.5: mood = "Solid — and one or two you'd repeat."
        default: mood = "A softer month. Learning what fits."
        }
        return mood
    }

    private func currentMonthKey() -> String {
        let f = DateFormatter(); f.dateFormat = "yyyy-MM"; f.timeZone = .init(identifier: "UTC")
        return f.string(from: Date())
    }

    private func monthKey(from iso: String?) -> String {
        guard let iso, iso.count >= 7 else { return "0000-00" }
        return String(iso.prefix(7))
    }

    private func prettyMonth(_ key: String) -> String {
        let parts = key.split(separator: "-")
        guard parts.count == 2, let y = Int(parts[0]), let m = Int(parts[1]),
              (1...12).contains(m) else { return key }
        let names = ["JAN","FEB","MAR","APR","MAY","JUN","JUL","AUG","SEP","OCT","NOV","DEC"]
        return "\(names[m-1]) '\(String(format: "%02d", y % 100))"
    }

    private func romanNumeral(from key: String) -> String {
        let parts = key.split(separator: "-")
        guard parts.count == 2, let m = Int(parts[1]), (1...12).contains(m) else { return "" }
        let romans = ["Ⅰ","Ⅱ","Ⅲ","Ⅳ","Ⅴ","Ⅵ","Ⅶ","Ⅷ","Ⅸ","Ⅹ","Ⅺ","Ⅻ"]
        return romans[m-1]
    }

    private func load() async {
        do {
            let list = try await Repo.shared.outfits(limit: 200)
            var urls: [String: String] = [:]
            for o in list {
                if let id = o.id, let p = o.photo_path {
                    urls[id] = (try? await Repo.shared.signedOutfitUrl(p)) ?? nil
                }
            }
            self.outfits = list
            self.photoUrls = urls
        } catch {
            self.outfits = []
        }
        self.loaded = true
    }
}

private extension Outfit {
    var stableId: String { id ?? (photo_path ?? UUID().uuidString) }
}
