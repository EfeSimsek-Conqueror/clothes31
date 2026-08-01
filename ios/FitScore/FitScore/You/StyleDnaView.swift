import SwiftUI
import Kingfisher

/// Full sheet showing the user's Style DNA — deterministic snapshot + a
/// shareable Wrapped card (1080x1920 PNG rendered on-demand).
struct StyleDnaView: View {
    let monthKey: String?     // nil = all-time
    let onClose: () -> Void

    @State private var dna: StyleDna?
    @State private var heroImage: UIImage?
    @State private var busy = false
    @State private var sharing: UIImage?

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 22) {
                    header
                    if let dna {
                        subline(dna)
                        preview(dna)
                        stats(dna)
                        palette(dna)
                        shareButton
                    } else {
                        ProgressView().padding(.top, 60).frame(maxWidth: .infinity)
                    }
                    Spacer(minLength: 40)
                }
                .padding(.horizontal, 22)
                .padding(.vertical, 16)
            }
            .background(Palette.paper.ignoresSafeArea())
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(action: onClose) {
                        Image(systemName: "xmark").foregroundStyle(Palette.ink)
                    }
                }
            }
        }
        .task { await load() }
        .sheet(item: shareItemBinding) { item in
            ActivityShare(image: item.image)
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("STYLE DNA")
                .font(.system(size: 11, weight: .semibold))
                .tracking(2.5)
                .foregroundStyle(Palette.bronze)
            Text(title)
                .font(Serif.display(32))
                .foregroundStyle(Palette.ink)
        }
    }

    private func subline(_ dna: StyleDna) -> some View {
        Text(dna.subline)
            .font(Serif.body(16).italic())
            .foregroundStyle(Palette.muted)
            .padding(.vertical, 4)
    }

    private func preview(_ dna: StyleDna) -> some View {
        // Small on-screen preview of the card
        RoundedRectangle(cornerRadius: 8)
            .fill(Palette.card)
            .frame(height: 380)
            .overlay(
                VStack(spacing: 8) {
                    Text("YOUR STYLE")
                        .font(.system(size: 10, weight: .semibold)).tracking(2)
                        .foregroundStyle(Palette.bronze)
                    Text(title).font(Serif.display(22)).foregroundStyle(Palette.ink)
                    if let img = heroImage {
                        Image(uiImage: img)
                            .resizable().scaledToFill()
                            .frame(width: 160, height: 200)
                            .clipShape(RoundedRectangle(cornerRadius: 4))
                    } else {
                        RoundedRectangle(cornerRadius: 4)
                            .fill(Palette.muted.opacity(0.15))
                            .frame(width: 160, height: 200)
                    }
                    HStack(spacing: 6) {
                        ForEach(dna.paletteHex, id: \.self) { hex in
                            Circle().fill(Color(hex: hex) ?? Palette.muted).frame(width: 16, height: 16)
                        }
                    }
                    Text("FITRATER")
                        .font(.system(size: 10, weight: .semibold)).tracking(2)
                        .foregroundStyle(Palette.ink)
                }
            )
            .overlay(RoundedRectangle(cornerRadius: 8).stroke(Palette.hairline, lineWidth: 1))
    }

    private func stats(_ dna: StyleDna) -> some View {
        HStack(spacing: 24) {
            stat("FITS", "\(dna.fitCount)")
            if let a = dna.avgScore { stat("AVG", String(format: "%.1f", a)) }
            if let b = dna.bestScore { stat("BEST", String(format: "%.1f", b)) }
            if let occ = dna.topOccasion { stat("MOSTLY", occ.uppercased()) }
            Spacer()
        }
        .padding(.top, 8)
        .overlay(Rectangle().fill(Palette.hairline).frame(height: 1), alignment: .top)
    }

    private func stat(_ label: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(label).font(.system(size: 9, weight: .semibold)).tracking(1.5).foregroundStyle(Palette.muted)
            Text(value).font(Serif.display(16)).foregroundStyle(Palette.ink)
        }
    }

    private func palette(_ dna: StyleDna) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("PALETTE").font(.system(size: 10, weight: .semibold)).tracking(2).foregroundStyle(Palette.bronze)
            HStack(spacing: 10) {
                ForEach(dna.paletteHex, id: \.self) { hex in
                    RoundedRectangle(cornerRadius: 6)
                        .fill(Color(hex: hex) ?? Palette.muted)
                        .frame(width: 44, height: 44)
                        .overlay(RoundedRectangle(cornerRadius: 6).stroke(Palette.ink.opacity(0.1), lineWidth: 1))
                }
            }
        }
    }

    private var shareButton: some View {
        Button(action: {
            Haptic.tap()
            Task { await share() }
        }) {
            HStack {
                Spacer()
                if busy {
                    ProgressView().tint(.white)
                } else {
                    Image(systemName: "square.and.arrow.up")
                    Text("Share Wrapped Card")
                        .font(Serif.body(15, weight: .semibold))
                }
                Spacer()
            }
            .padding(.vertical, 14)
            .background(Palette.ink)
            .foregroundStyle(.white)
            .clipShape(Capsule())
        }
        .buttonStyle(.plain)
        .disabled(busy)
        .padding(.top, 12)
    }

    // MARK: - Data

    private var title: String {
        guard let m = monthKey, !m.isEmpty else { return "All-Time" }
        let parts = m.split(separator: "-")
        guard parts.count == 2, let mm = Int(parts[1]), (1...12).contains(mm) else { return m }
        let names = ["January","February","March","April","May","June","July","August","September","October","November","December"]
        return "\(names[mm-1]) \(parts[0])"
    }

    private func load() async {
        do {
            async let outfits = Repo.shared.outfits(limit: 300)
            async let closet = Repo.shared.closetItems()
            async let profile = Repo.shared.currentProfile()
            let (os, cs, p) = try await (outfits, closet, profile)
            let d = StyleDnaBuilder.build(outfits: os, closet: cs, profile: p, monthKey: monthKey)
            self.dna = d
            if let path = d.bestFitPath, let url = try? await Repo.shared.signedOutfitUrl(path),
               let u = URL(string: url), let data = try? Data(contentsOf: u),
               let img = UIImage(data: data) {
                self.heroImage = img
            }
        } catch {
            self.dna = nil
        }
    }

    private func share() async {
        guard let dna else { return }
        busy = true
        defer { busy = false }
        if let img = await MainActor.run(body: { WrappedCardRenderer.render(dna: dna, heroImage: heroImage) }) {
            sharing = img
        }
    }

    private struct ShareItem: Identifiable { let id = UUID(); let image: UIImage }
    private var shareItemBinding: Binding<ShareItem?> {
        Binding(get: { sharing.map { ShareItem(image: $0) } }, set: { sharing = $0?.image })
    }
}

// MARK: - Share sheet wrapper (UIActivityViewController)

private struct ActivityShare: UIViewControllerRepresentable {
    let image: UIImage
    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: [image], applicationActivities: nil)
    }
    func updateUIViewController(_ vc: UIActivityViewController, context: Context) {}
}
