import Foundation
import Supabase

// MARK: - Models

struct OccasionPiece: Codable, Hashable, Sendable, Identifiable {
    var id = UUID()
    var name: String
    var category: String
    var color: String
    var matched_closet_id: String?
    var generate_prompt: String?

    enum CodingKeys: String, CodingKey {
        case name, category, color, matched_closet_id, generate_prompt
    }
}

struct OccasionCombo: Codable, Hashable, Sendable, Identifiable {
    var id = UUID()
    var title: String
    var rationale: String
    var pieces: [OccasionPiece]

    enum CodingKeys: String, CodingKey {
        case title, rationale, pieces
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
