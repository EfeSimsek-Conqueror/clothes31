import Foundation

/// Minimal Supabase client (REST + Auth + Storage) so the app builds without the
/// `supabase-swift` SPM dependency. Swap for `supabase-swift` when adding SPM.
struct SupabaseClient {
    let url: URL
    let anonKey: String

    static let shared = SupabaseClient(
        url: URL(string: "https://ilrzqifdmjvooeyqvexd.supabase.co")!,
        anonKey: "sb_publishable_bz5nOR8qZ3jTaXJMbR4MHA_w2jaMyDS"
    )

    private func request(_ path: String, method: String = "GET", token: String? = nil, body: Data? = nil) -> URLRequest {
        var req = URLRequest(url: url.appendingPathComponent(path))
        req.httpMethod = method
        req.addValue(anonKey, forHTTPHeaderField: "apikey")
        req.addValue("Bearer \(token ?? anonKey)", forHTTPHeaderField: "Authorization")
        req.addValue("application/json", forHTTPHeaderField: "Content-Type")
        req.httpBody = body
        return req
    }

    // MARK: - Simple REST reads (RLS applies)
    func fetchOutfits(token: String) async throws -> Data {
        let req = request("rest/v1/outfits?select=id,name,score,image_url,created_at&order=created_at.desc&limit=50", token: token)
        let (data, _) = try await URLSession.shared.data(for: req)
        return data
    }

    func fetchCloset(token: String) async throws -> Data {
        let req = request("rest/v1/closet_items?select=id,name,category,color,image_url,created_at&order=created_at.desc&limit=100", token: token)
        let (data, _) = try await URLSession.shared.data(for: req)
        return data
    }

    func creditsBalance(token: String) async throws -> Int {
        let req = request("rest/v1/credit_transactions?select=amount", token: token)
        let (data, _) = try await URLSession.shared.data(for: req)
        struct Row: Decodable { let amount: Int }
        let rows = (try? JSONDecoder().decode([Row].self, from: data)) ?? []
        return rows.reduce(0) { $0 + $1.amount }
    }

    // MARK: - Auth (email OTP send)
    func sendOTP(email: String) async throws {
        let body = try JSONSerialization.data(withJSONObject: ["email": email])
        var req = request("auth/v1/otp", method: "POST", body: body)
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        _ = try await URLSession.shared.data(for: req)
    }

    // MARK: - Storage upload (outfits bucket)
    func uploadOutfitPhoto(token: String, name: String, jpeg: Data) async throws -> URL {
        var req = URLRequest(url: url.appendingPathComponent("storage/v1/object/outfits/\(name)"))
        req.httpMethod = "POST"
        req.addValue(anonKey, forHTTPHeaderField: "apikey")
        req.addValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        req.addValue("image/jpeg", forHTTPHeaderField: "Content-Type")
        req.httpBody = jpeg
        _ = try await URLSession.shared.data(for: req)
        return url.appendingPathComponent("storage/v1/object/public/outfits/\(name)")
    }
}
