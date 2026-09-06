import Foundation
import Supabase

// MARK: - Models

struct OccasionPiece: Codable, Hashable, Sendable, Identifiable {
    var id = UUID()
    var name: String
    var category: String
    var color: String
    /// Cloth and the one construction detail worth naming. Both optional so a
    /// build talking to an older server still decodes.
    var fabric: String?
    var detail: String?
    var matched_closet_id: String?
    var generate_prompt: String?

    /// The right-hand line on a piece row: cloth and detail, whichever exist.
    var meta: String {
        [fabric, detail]
            .compactMap { $0?.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
            .joined(separator: " · ")
    }

    var isOwned: Bool { matched_closet_id != nil }

    enum CodingKeys: String, CodingKey {
        case name, category, color, fabric, detail, matched_closet_id, generate_prompt
    }
}

struct OccasionCombo: Codable, Hashable, Sendable, Identifiable {
    var id = UUID()
    var title: String
    var rationale: String
    var pieces: [OccasionPiece]
    var palette_hex: [String]?

    /// Pieces the wearer does not already own — the only ones a render has to
    /// pay for, and what `Supa.occasionOutfitCost` is computed from.
    var missingPieces: [OccasionPiece] { pieces.filter { !$0.isOwned } }

    var renderCost: Int { Supa.occasionOutfitCost(missingPieces: missingPieces.count) }

    enum CodingKeys: String, CodingKey {
        case title, rationale, pieces, palette_hex
    }
}

struct OccasionResponse: Decodable {
    var combos: [OccasionCombo]?
    var error: String?
    var detail: String?
}

// MARK: - Service

@MainActor
enum OccasionCoachService {
    /// Slim payload shape mirroring `closet_items` — the edge function only
    /// needs the light metadata for name/color matching.
    private struct ClosetSlim: Encodable {
        let id: String
        let name: String?
        let category: String?
        let subcategory: String?
        let color: String?
        let color_hex: String?
    }

    private struct StyleSlim: Encodable {
        let gender: String?
        let style_tags: [String]?
    }

    private struct Payload: Encodable {
        let prompt: String
        let closet_items: [ClosetSlim]
        let style_profile: StyleSlim
    }

    static func plan(prompt: String) async throws -> [OccasionCombo] {
        // 1) Assemble input.
        let closet = (try? await Repo.shared.closetItems(limit: 60)) ?? []
        let profile = try? await Repo.shared.currentProfile()
        let payload = Payload(
            prompt: prompt,
            closet_items: closet.compactMap { c in
                guard let id = c.id else { return nil }
                return ClosetSlim(
                    id: id,
                    name: c.name,
                    category: c.category,
                    subcategory: c.subcategory,
                    color: c.color,
                    color_hex: c.color_hex
                )
            },
            style_profile: StyleSlim(
                gender: profile?.gender,
                style_tags: profile?.style_tags
            )
        )

        // 2) Invoke edge function.
        let decoder = JSONDecoder()
        let resp: OccasionResponse
        do {
            resp = try await Supa.client.functions.invoke(
                "occasion-coach",
                options: FunctionInvokeOptions(body: payload),
                decoder: decoder
            )
        } catch {
            if case FunctionsError.httpError(_, let data) = error,
               let text = String(data: data, encoding: .utf8) {
                throw NSError(domain: "OccasionCoach", code: 2,
                              userInfo: [NSLocalizedDescriptionKey: String(text.prefix(200))])
            }
            throw error
        }

        if let err = resp.error {
            throw NSError(domain: "OccasionCoach", code: 1,
                          userInfo: [NSLocalizedDescriptionKey: "Hem: \(err) — \(resp.detail ?? "")"])
        }
        return resp.combos ?? []
    }

    // MARK: - Weekly free tracking

    /// True if the user has NOT yet claimed their weekly free Occasion Coach
    /// plan this ISO week (Monday-based). We tally by looking for a
    /// `credit_transactions` row with `kind = 'occasion_coach_free'` created
    /// in the current week window (device-local tz).
    static func weeklyFreeAvailable() async -> Bool {
        guard let uid = Repo.shared.userId else { return false }
        let startIso = isoStartOfWeek()
        struct Row: Decodable { let id: String? }
        let rows: [Row] = (try? await Supa.client
            .from("credit_transactions")
            .select("id,created_at,kind")
            .eq("user_id", value: uid)
            .eq("kind", value: "occasion_coach_free")
            .gte("created_at", value: startIso)
            .execute()
            .value) ?? []
        return rows.isEmpty
    }

    /// Log a zero-cost credit_transactions row so weeklyFreeAvailable() flips
    /// to false until next ISO week.
    static func recordWeeklyFreeUse() async {
        guard let uid = Repo.shared.userId else { return }
        let current = (try? await Repo.shared.credits()) ?? 0
        _ = try? await Supa.client
            .from("credit_transactions")
            .insert(CreditTxInsert(
                user_id: uid,
                amount: 0,
                kind: "occasion_coach_free",
                balance_after: current
            ))
            .execute()
    }

    // MARK: - Helpers

    private static func isoStartOfWeek() -> String {
        var cal = Calendar(identifier: .iso8601)
        cal.firstWeekday = 2 // Monday
        let now = Date()
        let comps = cal.dateComponents([.yearForWeekOfYear, .weekOfYear], from: now)
        let start = cal.date(from: comps) ?? cal.startOfDay(for: now)
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime]
        return f.string(from: start)
    }
}

// MARK: - Rendering a combo

/// What a rendered combo produced: the outfit image to show, plus the rows it
/// wrote so the caller can point elsewhere in the app at them.
struct OccasionRender: Sendable {
    var imageUrl: String
    var outfitId: String?
    var generatedPieceCount: Int
}

extension OccasionCoachService {
    /// Turn a written combo into a real outfit image.
    ///
    /// Pieces the wearer already owns are used as-is — their closet photo is
    /// the reference. Only the missing ones are generated, and each generated
    /// piece is filed into the closet on the way past, so a combo the wearer
    /// liked leaves their wardrobe genuinely larger rather than producing a
    /// throwaway picture.
    ///
    /// The finished look is written twice on purpose: an `outfits` row so it
    /// appears in Journal, and a `closet_items` row under category "outfit" so
    /// Try On can actually wear it. Try On reads the closet, not `outfits`.
    static func generateOutfit(combo: OccasionCombo, occasion: String) async throws -> OccasionRender {
        guard let uid = Repo.shared.userId else { throw RepoError.notSignedIn }

        // 1. Reference image per piece, in the combo's own order so the combine
        //    prompt's numbering lines up with the pieces it names.
        var refs = [String?](repeating: nil, count: combo.pieces.count)
        var generatedCount = 0

        try await withThrowingTaskGroup(of: (Int, String?, Bool).self) { group in
            for (idx, piece) in combo.pieces.enumerated() {
                group.addTask {
                    if let cid = piece.matched_closet_id {
                        guard let item = try? await Repo.shared.closetItemById(cid) else { return (idx, nil, false) }
                        if let direct = item.image_url, !direct.isEmpty { return (idx, direct, false) }
                        guard let path = item.image_path else { return (idx, nil, false) }
                        return (idx, try? await Repo.shared.signedClosetUrl(path), false)
                    }
                    let prompt = piece.generate_prompt ?? Self.fallbackPrompt(for: piece)
                    let out = try await Repo.shared.generatePiece(prompt: prompt)
                    guard let url = out.image_url, !url.isEmpty else { return (idx, nil, false) }
                    await Self.fileInCloset(url: url, piece: piece, uid: uid)
                    return (idx, url, true)
                }
            }
            for try await (idx, url, wasGenerated) in group {
                refs[idx] = url
                if wasGenerated { generatedCount += 1 }
            }
        }

        let usable = refs.compactMap { $0 }
        guard usable.count >= 2 else { throw RepoError.notFound }

        // 2. Stitch the pieces into one look.
        let named = combo.pieces.enumerated().compactMap { i, p -> String? in
            refs[i] == nil ? nil : "\(p.name.lowercased())"
        }.joined(separator: ", ")
        let combined = try await Repo.shared.generatePiece(
            prompt: Self.combinePrompt(pieceList: named, count: usable.count),
            imageUrls: usable
        )
        guard let outUrl = combined.image_url, !outUrl.isEmpty else { throw RepoError.notFound }

        // 3. File the finished look in both places.
        var outfitId: String? = nil
        if let bytes = try? await Repo.shared.downloadBytes(outUrl) {
            if let path = try? await Repo.shared.uploadOutfitPhoto(bytes: bytes, ext: "png") {
                outfitId = try? await Repo.shared.insertOutfit(OutfitInsert(
                    user_id: uid,
                    photo_path: path,
                    score: 0.0,
                    occasion: occasion,
                    hem_comment: combo.rationale.isEmpty ? combo.title : combo.rationale,
                    verdict: combo.title,
                    kind: "outfit_studio"
                )).id
            }
            if let closetPath = try? await Repo.shared.uploadClosetPhoto(bytes: bytes, ext: "png") {
                _ = try? await Repo.shared.insertClosetItem(ClosetItemInsert(
                    user_id: uid,
                    name: combo.title,
                    category: "outfit",
                    subcategory: nil,
                    image_path: closetPath,
                    color_hex: combo.palette_hex?.first,
                    parent_id: nil,
                    source: "occasion_coach"
                ))
            }
        }

        return OccasionRender(imageUrl: outUrl, outfitId: outfitId, generatedPieceCount: generatedCount)
    }

    nonisolated private static func fallbackPrompt(for piece: OccasionPiece) -> String {
        let bits = [piece.color, piece.fabric ?? "", piece.name, piece.detail ?? ""]
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
            .joined(separator: ", ")
        return "Studio product photograph of \(bits). Isolated on a clean cream backdrop — "
            + "NO person, NO mannequin, NO other garments in frame. Soft diffused studio lighting, "
            + "sharp fabric and stitching detail, editorial catalog aesthetic."
    }

    /// Mirrors the Studio wizard's combine: a headless mannequin wearing exactly
    /// what it was shown. The hard rules are there because the model will
    /// otherwise invent a tee under a jacket or drop a bag entirely.
    nonisolated private static func combinePrompt(pieceList: String, count: Int) -> String {
        """
        Studio product photograph. Dress a SINGLE headless matte-white androgynous MANNEQUIN \
        with EXACTLY these \(count) piece(s) and nothing else: \(pieceList).

        HARD RULES:
        - Use ONLY the garments shown in the reference images. Do NOT add, invent, or substitute anything.
        - Every reference garment must appear, worn in its natural position.
        - Full body from shoulders to feet, standing upright, facing the camera.
        - Clean cream backdrop, soft diffused studio lighting, sharp fabric detail.
        - No head, no face, no text, no props.
        """
    }

    /// A generated piece becomes a real closet item, so the next combo can
    /// match it instead of paying to render it again.
    private static func fileInCloset(url: String, piece: OccasionPiece, uid: String) async {
        guard let bytes = try? await Repo.shared.downloadBytes(url),
              let path = try? await Repo.shared.uploadClosetPhoto(bytes: bytes, ext: "png")
        else { return }
        _ = try? await Repo.shared.insertClosetItem(ClosetItemInsert(
            user_id: uid,
            name: piece.name,
            category: piece.category.lowercased(),
            subcategory: nil,
            image_path: path,
            color_hex: nil,
            parent_id: nil,
            source: "occasion_coach"
        ))
    }
}
