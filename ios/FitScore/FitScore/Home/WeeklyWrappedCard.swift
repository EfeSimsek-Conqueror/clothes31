import SwiftUI

/// Mondays-only wrapped teaser. Given last week's outfits (Mon–Sun of the week
/// just past), pull out top score + count + one-line signature. Tapping OPEN
/// launches the Spotify-Wrapped style story view (`WeeklyStoryView`).
struct HomeWeeklyWrappedCard: View {
    let lastWeekOutfits: [Outfit]
    var onOpenJournal: () -> Void = {}

    @State private var showStory = false

    var body: some View {
        // Only render on Mondays.
        let weekday = Calendar.current.component(.weekday, from: Date())
        // Sunday = 1, Monday = 2 in Foundation.
        if weekday != 2 || lastWeekOutfits.isEmpty {
            EmptyView()
        } else {
            let top = lastWeekOutfits.compactMap { $0.score }.max() ?? 0
            let count = lastWeekOutfits.count
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Eyebrow(text: "WEEKLY WRAPPED")
                    Spacer()
                    Button(action: {
                        Haptic.chip()
                        showStory = true
                    }) {
                        Text("OPEN →")
                            .font(.system(size: 11, weight: .semibold))
                            .tracking(1.8)
                            .foregroundStyle(Palette.bronze)
                    }
                }
                Button(action: {
                    Haptic.chip()
                    showStory = true
                }) {
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Last week, in a line.")
                            .font(Serif.display(20))
                            .foregroundStyle(Palette.ink)
                        HStack(spacing: 24) {
                            stat("TOP", String(format: "%.1f", top))
                            stat("FITS", "\(count)")
                        }
                        Text("Tap to open the wrap.")
                            .font(Serif.body(13))
                            .foregroundStyle(Palette.muted)
                    }
                    .padding(16)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Palette.card)
                    .clipShape(RoundedRectangle(cornerRadius: 14))
                    .overlay(RoundedRectangle(cornerRadius: 14).stroke(Palette.hairline, lineWidth: 1))
                }
                .buttonStyle(.plain)
            }
            .fullScreenCover(isPresented: $showStory) {
                WeeklyStoryView(outfits: lastWeekOutfits, onClose: { showStory = false })
            }
        }
    }

    private func stat(_ label: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label)
                .font(.system(size: 10, weight: .semibold))
                .tracking(1.8)
                .foregroundStyle(Palette.muted)
            Text(value)
                .font(Serif.display(22, weight: .semibold))
                .foregroundStyle(Palette.ink)
        }
    }
}

// MARK: - Weekly Story View (Aug 2026)
// Kept in this file to avoid Xcode pbxproj edits. Split into its own file
// later if the story grows past a couple hundred lines.

/// One "story slide" — the base surface for each moment in the wrap.
private struct StorySlide<Content: View>: View {
    let content: Content
    init(@ViewBuilder _ content: () -> Content) { self.content = content() }
    var body: some View {
        VStack { content }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

/// A shareable card at the end of the wrap — 9:16 look, watermark, big number.
private struct WrapShareCard: View {
    let avg: Double
    let count: Int
    let topFitName: String
    var body: some View {
        VStack(spacing: 14) {
            Text("MY WEEK IN FITS")
                .font(.system(size: 11, weight: .semibold))
                .tracking(3)
                .foregroundStyle(Palette.bronze)
            Text(String(format: "%.1f", avg))
                .font(Serif.display(90, weight: .semibold))
                .foregroundStyle(Palette.ink)
            Text("avg score · \(count) fits")
                .font(Serif.body(15))
                .foregroundStyle(Palette.muted)
            Rectangle().fill(Palette.hairline).frame(width: 40, height: 1)
            Text(topFitName)
                .font(Serif.italic(15))
                .foregroundStyle(Palette.ink)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 24)
            Spacer()
            Text("fitrater.ai")
                .font(.system(size: 10, weight: .semibold))
                .tracking(2)
                .foregroundStyle(Palette.muted)
        }
        .padding(.top, 60)
        .padding(.bottom, 40)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Palette.paper)
    }
}

struct WeeklyStoryView: View {
    let outfits: [Outfit]
    var onClose: () -> Void

    @State private var index: Int = 0
    @State private var isPaused: Bool = false
    @State private var progress: CGFloat = 0
    @State private var timer: Timer? = nil

    private let slideDuration: Double = 5.0

    // Derived stats
    private var avgScore: Double {
        let scores = outfits.compactMap { $0.score }
        guard !scores.isEmpty else { return 0 }
        return scores.reduce(0, +) / Double(scores.count)
    }
    private var topScore: Double { outfits.compactMap { $0.score }.max() ?? 0 }
    private var topOutfit: Outfit? {
        outfits.max(by: { ($0.score ?? 0) < ($1.score ?? 0) })
    }
    private var count: Int { outfits.count }

    // 6 slides: 0-hero, 1-count, 2-top score, 3-avg score, 4-vibe, 5-share
    private let slideCount = 6

    var body: some View {
        ZStack {
            Palette.paper.ignoresSafeArea()
            VStack(spacing: 0) {
                progressBar
                Spacer(minLength: 8)
                Group {
                    switch index {
                    case 0: heroSlide
                    case 1: countSlide
                    case 2: topScoreSlide
                    case 3: avgScoreSlide
                    case 4: vibeSlide
                    case 5: shareSlide
                    default: EmptyView()
                    }
                }
                .transition(.opacity)
                Spacer(minLength: 12)
            }
            .padding(.top, 12)
            // Tap zones: left = prev, right = next; long press = pause
            HStack(spacing: 0) {
                Color.clear.contentShape(Rectangle())
                    .onTapGesture { prev() }
                Color.clear.contentShape(Rectangle())
                    .onTapGesture { next() }
            }
            .simultaneousGesture(
                LongPressGesture(minimumDuration: 0.15)
                    .onChanged { _ in isPaused = true }
                    .onEnded { _ in isPaused = false }
            )

            // Close button on top-right
            VStack {
                HStack {
                    Spacer()
                    Button(action: onClose) {
                        Image(systemName: "xmark")
                            .foregroundStyle(Palette.ink)
                            .frame(width: 36, height: 36)
                            .background(Palette.card.opacity(0.9))
                            .clipShape(Circle())
                    }
                    .padding(.trailing, 14)
                    .padding(.top, 14)
                }
                Spacer()
            }
        }
        .onAppear { startTimer() }
        .onDisappear { stopTimer() }
    }

    // MARK: - Progress bar

    private var progressBar: some View {
        HStack(spacing: 4) {
            ForEach(0..<slideCount, id: \.self) { i in
                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        Capsule().fill(Palette.hairline)
                        Capsule().fill(Palette.ink)
                            .frame(width: fillWidth(for: i, total: geo.size.width))
                    }
                }
                .frame(height: 3)
            }
        }
        .padding(.horizontal, 14)
    }

    private func fillWidth(for i: Int, total: CGFloat) -> CGFloat {
        if i < index { return total }
        if i > index { return 0 }
        return total * progress
    }

    // MARK: - Slides

    private var heroSlide: some View {
        StorySlide {
            VStack(spacing: 16) {
                Spacer()
                Text("YOUR WEEK")
                    .font(.system(size: 11, weight: .semibold))
                    .tracking(3)
                    .foregroundStyle(Palette.bronze)
                Text("A quick wrap.")
                    .font(Serif.display(44))
                    .foregroundStyle(Palette.ink)
                Text("6 slides · tap to skip")
                    .font(Serif.italic(13))
                    .foregroundStyle(Palette.muted)
                Spacer()
            }
        }
    }

    private var countSlide: some View {
        StorySlide {
            VStack(spacing: 14) {
                Spacer()
                bigNumber("\(count)")
                Text(count == 1 ? "fit shared" : "fits shared")
                    .font(Serif.display(22))
                    .foregroundStyle(Palette.ink)
                Text("Consistency compounds. Keep going.")
                    .font(Serif.italic(14))
                    .foregroundStyle(Palette.muted)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 40)
                Spacer()
            }
        }
    }

    private var topScoreSlide: some View {
        StorySlide {
            VStack(spacing: 14) {
                Spacer()
                Text("TOP SCORE")
                    .font(.system(size: 11, weight: .semibold))
                    .tracking(3)
                    .foregroundStyle(Palette.bronze)
                bigNumber(String(format: "%.1f", topScore))
                Text("Your peak this week.")
                    .font(Serif.italic(14))
                    .foregroundStyle(Palette.muted)
                Spacer()
            }
        }
    }

    private var avgScoreSlide: some View {
        StorySlide {
            VStack(spacing: 14) {
                Spacer()
                Text("AVERAGE")
                    .font(.system(size: 11, weight: .semibold))
                    .tracking(3)
                    .foregroundStyle(Palette.bronze)
                bigNumber(String(format: "%.1f", avgScore))
                Text(avgScore >= 7 ? "Comfortably above average." : "Plenty of room to climb.")
                    .font(Serif.italic(14))
                    .foregroundStyle(Palette.muted)
                Spacer()
            }
        }
    }

    private var vibeSlide: some View {
        StorySlide {
            VStack(spacing: 14) {
                Spacer()
                Text("THIS WEEK'S PALETTE")
                    .font(.system(size: 11, weight: .semibold))
                    .tracking(3)
                    .foregroundStyle(Palette.bronze)
                HStack(spacing: 12) {
                    swatch(color: Color(red: 0.86, green: 0.80, blue: 0.72))
                    swatch(color: Color(red: 0.36, green: 0.32, blue: 0.28))
                    swatch(color: Color(red: 0.96, green: 0.93, blue: 0.86))
                }
                Text("Neutral, quiet, considered.")
                    .font(Serif.italic(14))
                    .foregroundStyle(Palette.muted)
                Spacer()
            }
        }
    }

    private var shareSlide: some View {
        StorySlide {
            VStack(spacing: 16) {
                WrapShareCard(
                    avg: avgScore,
                    count: count,
                    topFitName: topOutfit?.name ?? topOutfit?.verdict ?? "your best fit"
                )
                .clipShape(RoundedRectangle(cornerRadius: 20))
                .overlay(RoundedRectangle(cornerRadius: 20).stroke(Palette.hairline, lineWidth: 1))
                .frame(maxWidth: 320)
                .padding(.top, 8)
                Button(action: {
                    Haptic.tap()
                    // Share sheet stub — real implementation uses UIActivityViewController
                    // once we render this card to a UIImage. Ships in the next update.
                }) {
                    HStack(spacing: 8) {
                        Image(systemName: "square.and.arrow.up")
                        Text("Share").font(Serif.body(15, weight: .medium))
                    }
                    .foregroundStyle(.white)
                    .padding(.horizontal, 26)
                    .padding(.vertical, 12)
                    .background(Palette.ink)
                    .clipShape(Capsule())
                }
                Button(action: onClose) {
                    Text("Done")
                        .font(Serif.body(14))
                        .foregroundStyle(Palette.muted)
                }
                Spacer()
            }
        }
    }

    private func bigNumber(_ s: String) -> some View {
        Text(s)
            .font(Serif.display(96, weight: .semibold))
            .foregroundStyle(Palette.ink)
    }

    private func swatch(color: Color) -> some View {
        Circle()
            .fill(color)
            .frame(width: 54, height: 54)
            .overlay(Circle().stroke(Palette.hairline, lineWidth: 1))
    }

    // MARK: - Timer

    private func startTimer() {
        stopTimer()
        progress = 0
        timer = Timer.scheduledTimer(withTimeInterval: 0.05, repeats: true) { _ in
            Task { @MainActor in
                if isPaused { return }
                progress += 0.05 / slideDuration
                if progress >= 1.0 {
                    next()
                }
            }
        }
    }

    private func stopTimer() {
        timer?.invalidate()
        timer = nil
    }

    private func next() {
        if index < slideCount - 1 {
            withAnimation(.easeInOut(duration: 0.2)) {
                index += 1
                progress = 0
            }
        } else {
            onClose()
        }
    }

    private func prev() {
        if index > 0 {
            withAnimation(.easeInOut(duration: 0.2)) {
                index -= 1
                progress = 0
            }
        } else {
            progress = 0
        }
    }
}
