import Foundation
import Supabase

/// Decoded shape of the `score-outfit` edge function.
/// Sprint 2 (v3) adds `markup_annotations`, `fit_map`, and `fits_you`. All new
/// fields are optional so v2 responses (and older clients) still decode.
struct ScoreOutfitResponse: Decodable {
    var score: Double?
    var subscores: Subscores?
    var hem_comment: String?
    var swaps: [String]?
    var annotations: [Annotation]?
    // Sprint 2 additions:
    var markup_annotations: [MarkupAnnotation]?
    var fit_map: FitMap?
    var fits_you: Double?
    var error: String?
    var detail: String?
    var raw: String?
}

struct HemScored {
    let score: Double
    let hemComment: String
    let subscores: Subscores
    let swaps: [String]
    let annotations: [Annotation]
    // Sprint 2 additions — all optional, safe to ignore in existing UI.
    let markupAnnotations: [MarkupAnnotation]
    let fitMap: FitMap?
    let fitsYou: Double?
}

/// Compare + Decode edge function responses.
struct CompareResponse: Decodable, Hashable {
    var winner: String?
    var score_a: Double?
    var score_b: Double?
    var comment: String?
    var reason_a: String?
    var reason_b: String?
    var error: String?
    var detail: String?
}

struct DecodePiece: Decodable, Hashable {
    var type: String = ""
    var silhouette: String = ""
    var colors: [String] = []
    var fabric: String = ""
    var note: String = ""
}

struct DecodeResponse: Decodable, Hashable {
    var pieces: [DecodePiece] = []
    var palette_hex: [String] = []
    var style_signature: String = ""
    var error: String?
    var detail: String?
}

/// Edge-function wrappers. Mirror Android `HemService` + `CompareService`.
enum HemService {
    static func score(imageUrl: String,
                      occasion: String,
                      honesty: String,
                      intent: String? = nil,
                      backUrl: String? = nil,
                      bodyProfile: BodyProfile? = nil) async throws -> HemScored {
        struct Payload: Encodable {
            let image_url: String
            let occasion: String
            let honesty: String
            let intent: String?
            let back_url: String?
            let body_profile: BodyProfile?
        }
        let payload = Payload(
            image_url: imageUrl,
            occasion: occasion,
            honesty: honesty,
            intent: intent?.isEmpty == false ? intent : nil,
            back_url: backUrl,
            body_profile: bodyProfile
        )
        let parsed: ScoreOutfitResponse = try await invoke("score-outfit", body: payload)
        if let err = parsed.error {
            let detail = parsed.detail ?? parsed.raw ?? err
            throw NSError(domain: "Hem", code: 1, userInfo: [NSLocalizedDescriptionKey: "Hem: \(err) — \(String(detail.prefix(140)))"])
        }
        guard let s = parsed.score else {
            throw NSError(domain: "Hem", code: 2, userInfo: [NSLocalizedDescriptionKey: "Hem returned no score."])
        }
        let rounded = (s * 10).rounded() / 10
        return HemScored(
            score: rounded,
            hemComment: parsed.hem_comment ?? "",
            subscores: parsed.subscores ?? Subscores(),
            swaps: parsed.swaps ?? [],
            annotations: parsed.annotations ?? [],
            markupAnnotations: parsed.markup_annotations ?? [],
            fitMap: parsed.fit_map,
            fitsYou: parsed.fits_you
        )
    }

    static func compare(aUrl: String, bUrl: String, occasion: String) async -> CompareResponse {
        struct Payload: Encodable {
            let image_a_url: String
            let image_b_url: String
            let occasion: String
        }
        let p = Payload(image_a_url: aUrl, image_b_url: bUrl, occasion: occasion)
        do {
            return try await invoke("compare-outfits", body: p)
        } catch {
            return CompareResponse(error: "decode_failed", detail: String(describing: error).prefix(200).description)
        }
    }

    static func decode(imageUrl: String) async -> DecodeResponse {
        struct Payload: Encodable { let image_url: String }
        do {
            return try await invoke("decode-outfit", body: Payload(image_url: imageUrl))
        } catch {
            return DecodeResponse(error: "decode_failed", detail: String(describing: error).prefix(200).description)
        }
    }

    private static func invoke<T: Decodable, B: Encodable>(_ name: String, body: B) async throws -> T {
        let decoder = JSONDecoder()
        return try await Supa.client.functions.invoke(
            name,
            options: FunctionInvokeOptions(body: body),
            decoder: decoder
        )
    }
}
