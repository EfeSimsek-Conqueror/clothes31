import SwiftUI
import PhotosUI

/// Decode a reference photo into pieces + palette + style signature. Auto-
/// inserts a journal entry (kind='decode'), and offers a "Save to closet"
/// action that writes each decoded piece as a `closet_items` row with
/// `source='decoded'`.
struct DecodeView: View {
    var onClose: () -> Void
    var onOpenPaywall: () -> Void = {}

    @State private var pickedBytes: Data?
    @State private var pickedImage: UIImage?
    @State private var busy = false
    @State private var error: String?
    @State private var result: DecodeResponse?
    @State private var saving = false
    @State private var savedOk = false
    @State private var pickerItem: PhotosPickerItem?

    var body: some View {
        ZStack {
            Palette.paper.ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    header
                    PhotosPicker(selection: $pickerItem, matching: .images) {
                        RoundedRectangle(cornerRadius: 18)
                            .fill(Palette.card)
                            .aspectRatio(16.0 / 9.0, contentMode: .fit)
                            .overlay(
                                Group {
                                    if let img = pickedImage {
                                        Image(uiImage: img).resizable().scaledToFill()
                                    } else {
                                        Text("Tap to pick a reference photo")
                                            .font(Serif.body(14))
                                            .foregroundStyle(Palette.muted)
                                    }
                                }
                                .clipShape(RoundedRectangle(cornerRadius: 18))
                            )
                            .overlay(RoundedRectangle(cornerRadius: 18).stroke(Palette.hairline, lineWidth: 1))
                    }

                    if let res = result {
                        decoded(res)
                    } else {
                        if let error {
                            Text(error).font(Serif.body(13)).foregroundStyle(Palette.bronze)
                        }
                        PrimaryButton(
                            title: busy ? "Decoding…" : "Decode · \(Supa.decodeCost) credits",
                            enabled: pickedBytes != nil && !busy,
                            action: run
                        )
                    }
                    Spacer(minLength: 30)
                }
                .padding(20)
            }
        }
        .onChange(of: pickerItem) { _, item in
            Task {
                if let item, let d = try? await item.loadTransferable(type: Data.self) {
                    let p = CameraModel.processJpeg(d) ?? d
                    pickedBytes = p
                    pickedImage = UIImage(data: p)
                    result = nil
                    savedOk = false
                }
            }
        }
    }

    private var header: some View {
        HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 6) {
                Text("READ THE ROOM").font(.system(size: 10, weight: .semibold)).tracking(2).foregroundStyle(Palette.bronze)
                Text("Decode style").font(Serif.display(28)).foregroundStyle(Palette.ink)
            }
            Spacer()
            Button(action: onClose) {
                Image(systemName: "xmark").foregroundStyle(Palette.ink).frame(width: 36, height: 36)
            }
        }
    }

    @ViewBuilder
    private func decoded(_ res: DecodeResponse) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("STYLE SIGNATURE").font(.system(size: 10, weight: .semibold)).tracking(2).foregroundStyle(Palette.bronze)
            Text(res.style_signature.isEmpty ? "—" : res.style_signature)
                .font(Serif.italic(22))
                .foregroundStyle(Palette.ink)

            Text("PIECES").font(.system(size: 10, weight: .semibold)).tracking(2).foregroundStyle(Palette.bronze)
            VStack(alignment: .leading, spacing: 0) {
                ForEach(Array(res.pieces.enumerated()), id: \.offset) { idx, p in
                    if idx > 0 { Hairline() }
                    VStack(alignment: .leading, spacing: 4) {
                        let title = "\(p.silhouette) \(p.type)".trimmingCharacters(in: .whitespaces).capitalized
                        Text(title.isEmpty ? p.type.capitalized : title)
                            .font(Serif.body(15, weight: .semibold))
                            .foregroundStyle(Palette.ink)
                        if !p.note.isEmpty {
                            Text(p.note).font(Serif.body(13)).foregroundStyle(Palette.muted)
                        }
                        let meta = [p.fabric, p.colors.joined(separator: ", ")].filter { !$0.isEmpty }.joined(separator: " · ")
                        if !meta.isEmpty {
                            Text(meta).font(Serif.body(12)).foregroundStyle(Palette.muted)
                        }
                    }
                    .padding(.vertical, 10)
                }
            }

            if !res.palette_hex.isEmpty {
                Text("PALETTE").font(.system(size: 10, weight: .semibold)).tracking(2).foregroundStyle(Palette.bronze)
                HStack(spacing: 6) {
                    ForEach(res.palette_hex, id: \.self) { hex in
                        RoundedRectangle(cornerRadius: 8)
                            .fill(Self.colorFromHex(hex) ?? Palette.muted)
                            .frame(height: 56)
                            .frame(maxWidth: .infinity)
                            .overlay(RoundedRectangle(cornerRadius: 8).stroke(Palette.hairline, lineWidth: 1))
                    }
                }
            }

            PrimaryButton(
                title: savedOk ? "Saved to closet" : (saving ? "Saving…" : "Save pieces to closet"),
                enabled: !saving && !savedOk && !res.pieces.isEmpty,
                action: { save(res) }
            )
        }
    }

    private func run() {
        guard !busy else { return }
        busy = true
        error = nil
        Task {
            let gate = await CreditsGate.check(Supa.decodeCost)
            if case .ok = gate {} else {
                busy = false
                if case .insufficientBalance = gate { _ = CreditsGate.explainAndBlock(gate); onOpenPaywall() }
                else { _ = CreditsGate.explainAndBlock(gate) }
                return
            }
            do {
                guard let bytes = pickedBytes else { throw RepoError.notFound }
                let path = try await Repo.shared.uploadClosetPhoto(bytes: bytes)
                guard let signed = try await Repo.shared.signedClosetUrl(path) else {
                    throw NSError(domain: "Decode", code: 1, userInfo: [NSLocalizedDescriptionKey: "Could not sign URL"])
                }
                let res = await HemService.decode(imageUrl: signed)
                if let err = res.error {
                    throw NSError(domain: "Decode", code: 2, userInfo: [NSLocalizedDescriptionKey: "Hem: \(err) — \(res.detail ?? "")"])
                }
                try? await Repo.shared.spendCredits(amount: Supa.decodeCost, kind: "decode")
                result = res
                // Auto-insert journal entry.
                if let uid = Repo.shared.userId {
                    let outPath = (try? await Repo.shared.uploadOutfitPhoto(bytes: bytes)) ?? path
                    _ = try? await Repo.shared.insertOutfit(OutfitInsert(
                        user_id: uid,
                        photo_path: outPath,
                        score: 0.0,
                        occasion: "Decoded",
                        hem_comment: res.style_signature.isEmpty ? "Decoded look" : res.style_signature,
                        weather_c: nil, verdict: nil, subscores: nil, swaps: nil, annotations: nil,
                        kind: "decode", linked_piece_id: nil
                    ))
                }
                busy = false
            } catch {
                self.error = error.localizedDescription
                ToastBus.shared.post("Decode failed: \(error.localizedDescription)")
                busy = false
            }
        }
    }

    private func save(_ res: DecodeResponse) {
        saving = true
        Task {
            guard let uid = Repo.shared.userId else {
                saving = false
                ToastBus.shared.post("Not signed in.")
                return
            }
            var ok = 0
            for p in res.pieces {
                let base = "\(p.silhouette) \(p.type)".trimmingCharacters(in: .whitespaces)
                let name = (base.isEmpty ? p.type : base).capitalized
                let category = categoryFor(p.type.lowercased())
                let hex = res.palette_hex.first.map { String($0.prefix(7)) }
                do {
                    _ = try await Repo.shared.insertClosetItem(ClosetItemInsert(
                        user_id: uid,
                        name: name,
                        category: category,
                        subcategory: p.type.lowercased().isEmpty ? nil : p.type.lowercased(),
                        image_path: nil,
                        color_hex: hex,
                        parent_id: nil,
                        source: "decoded"
                    ))
                    ok += 1
                } catch { /* skip */ }
            }
            saving = false
            if ok > 0 {
                savedOk = true
                ToastBus.shared.post("Added \(ok) piece\(ok > 1 ? "s" : "") to your closet.")
            } else {
                ToastBus.shared.post("Save failed — try again.")
            }
        }
    }

    private func categoryFor(_ type: String) -> String {
        switch type {
        case "trousers", "pants", "jeans", "shorts", "skirt": return "bottom"
        case "boots", "shoes", "sneakers", "sandals", "heels": return "footwear"
        case "bag", "belt", "hat", "cap", "scarf", "sunglasses", "watch", "necklace": return "accessory"
        case "blazer", "jacket", "coat", "cardigan": return "outerwear"
        default: return "top"
        }
    }

    private static func colorFromHex(_ hex: String) -> Color? {
        var s = hex.trimmingCharacters(in: .whitespaces).uppercased()
        if s.hasPrefix("#") { s.removeFirst() }
        guard s.count == 6, let v = UInt32(s, radix: 16) else { return nil }
        let r = Double((v >> 16) & 0xFF) / 255
        let g = Double((v >> 8) & 0xFF) / 255
        let b = Double(v & 0xFF) / 255
        return Color(red: r, green: g, blue: b)
    }
}
