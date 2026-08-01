import SwiftUI
import Kingfisher

struct WeeklyLetterView: View {
    var isPro: Bool = false
    var onOpenPaywall: () -> Void = {}

    @State private var loaded = false
    @State private var letters: [SundayLetter] = []
    @State private var outfitsByLetter: [String: [Outfit]] = [:]
    @State private var thumbUrls: [String: String] = [:]
    @State private var expanded: Set<String> = []

    var body: some View {
        SubpageScaffold(eyebrow: "SUNDAY", title: "Weekly Letter") {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    if !isPro {
                        lockedContent
                    } else if !loaded {
                        ForEach(0..<2, id: \.self) { _ in
                            RoundedRectangle(cornerRadius: 14).fill(Palette.card).frame(height: 120)
                        }
                    } else if letters.isEmpty {
                        emptyContent
                    } else {
                        ForEach(letters, id: \.id) { letter in
                            LetterCard(
                                letter: letter,
                                expanded: expanded.contains(letter.id ?? ""),
                                outfits: outfitsByLetter[letter.id ?? ""] ?? [],
                                urlFor: { thumbUrls[$0] },
                                onToggleExpand: {
                                    guard let id = letter.id else { return }
                                    if expanded.contains(id) { expanded.remove(id) } else { expanded.insert(id) }
                                }
                            )
                        }
                    }
                    Spacer().frame(height: 24)
                }
                .padding(20)
            }
        }
        .task { if isPro { await load() } }
    }

    private var lockedContent: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Weekly Letter, on the house.").font(Serif.display(28)).foregroundStyle(Palette.ink)
            Text("Every Sunday, Hem writes you a short letter — what you wore, what worked, what to try. It arrives with Pro.")
                .font(Serif.body(15)).foregroundStyle(Palette.ink)
            PrimaryButton(title: "Start 7-day trial →", action: onOpenPaywall)
            Text("One free preview when you subscribe.")
                .font(Serif.body(13)).foregroundStyle(Palette.muted)
        }
    }

    private var emptyContent: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Your first Sunday letter arrives after a week of fits.")
                .font(Serif.display(20)).foregroundStyle(Palette.ink)
        }
        .padding(.vertical, 24)
    }

    private func load() async {
        let list = (try? await Repo.shared.sundayLetters(limit: 12)) ?? []
        letters = list
        var byLetter: [String: [Outfit]] = [:]
        var urls: [String: String] = [:]
        for letter in list {
            guard let id = letter.id, let start = letter.week_start else { continue }
            let end = letter.week_end ?? {
                // 7 days later
                let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd"; f.locale = Locale(identifier: "en_US_POSIX")
                if let d = f.date(from: start), let nd = Calendar.current.date(byAdding: .day, value: 6, to: d) {
                    return f.string(from: nd)
                }
                return start
            }()
            let startIso = "\(start)T00:00:00+00:00"
            let endIso = "\(end)T23:59:59+00:00"
            let out = (try? await Repo.shared.outfitsInWindow(startIso: startIso, endIso: endIso, limit: 3)) ?? []
            byLetter[id] = out
            for o in out {
                if let path = o.photo_path, urls[path] == nil {
                    urls[path] = (try? await Repo.shared.signedOutfitUrl(path)) ?? nil
                }
            }
        }
        outfitsByLetter = byLetter
        thumbUrls = urls
        loaded = true
    }
}

private struct LetterCard: View {
    let letter: SundayLetter
    let expanded: Bool
    let outfits: [Outfit]
    let urlFor: (String) -> String?
    let onToggleExpand: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(formatWeekRange(letter.week_start, letter.week_end))
                .font(.system(size: 10, weight: .semibold)).tracking(2).foregroundStyle(Palette.bronze)
            HStack(alignment: .top, spacing: 8) {
                Rectangle().fill(Palette.bronze).frame(width: 2)
                Text(letter.body ?? "")
                    .font(Serif.italic(18))
                    .foregroundStyle(Palette.ink)
                    .lineLimit(expanded ? nil : 3)
            }
            if (letter.body ?? "").count > 140 && !expanded {
                Button("Read more →", action: onToggleExpand)
                    .font(Serif.body(14)).foregroundStyle(Palette.bronze)
            }
            if !outfits.isEmpty {
                HStack(spacing: 6) {
                    ForEach(outfits.prefix(3), id: \.id) { o in
                        let url = o.photo_path.flatMap(urlFor)
                        ZStack {
                            Palette.paper
                            if let s = url, let u = URL(string: s) {
                                KFImage(u).resizable().scaledToFill()
                            }
                        }
                        .aspectRatio(0.75, contentMode: .fit)
                        .frame(maxWidth: .infinity)
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Palette.hairline, lineWidth: 1))
                    }
                    ForEach(0..<max(0, 3 - min(outfits.count, 3)), id: \.self) { _ in
                        Color.clear.frame(maxWidth: .infinity)
                    }
                }
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Palette.card)
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Palette.hairline, lineWidth: 1))
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }
}

private func formatWeekRange(_ start: String?, _ end: String?) -> String {
    guard let start else { return "WEEK OF —" }
    let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd"; f.locale = Locale(identifier: "en_US_POSIX")
    guard let s = f.date(from: start) else { return "WEEK OF —" }
    let e: Date = end.flatMap { f.date(from: $0) } ?? Calendar.current.date(byAdding: .day, value: 6, to: s)!
    let of = DateFormatter(); of.dateFormat = "MMM d"; of.locale = Locale(identifier: "en_US")
    return "WEEK OF \(of.string(from: s)) – \(of.string(from: e))".uppercased()
}

/// Shared shell for the "You" sub-pages.
struct SubpageScaffold<Content: View>: View {
    let eyebrow: String
    let title: String
    @ViewBuilder let content: () -> Content
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Button(action: { dismiss() }) {
                    Image(systemName: "xmark").font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(Palette.ink)
                        .frame(width: 28, height: 28)
                }
                Spacer()
            }
            .padding(.horizontal, 20).padding(.vertical, 12)
            VStack(alignment: .leading, spacing: 6) {
                Eyebrow(text: eyebrow)
                Text(title).font(Serif.display(30)).foregroundStyle(Palette.ink)
            }
            .padding(.horizontal, 20)
            content()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(Palette.paper.ignoresSafeArea())
        .navigationBarBackButtonHidden(true)
    }
}
