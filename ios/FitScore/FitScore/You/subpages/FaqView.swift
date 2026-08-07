import SwiftUI

private let FAQS: [(String, String)] = [
    ("What are credits?",
     "Credits fuel every scan, generation, try-on, and roast. New accounts start with \(Supa.signupCredits). Buy packs anytime or unlock unlimited with Pro."),
    ("What happens after my trial?",
     "Your Pro trial lasts 7 days on the annual plan. Until it ends you get \(Supa.trialDailyCap) credits/day. After that you're billed and unlock the full monthly cap."),
    ("Can I cancel?",
     "Yes — from Settings → Apple ID → Subscriptions. Access continues until the current period ends and nothing rolls over. No penalty, no dark patterns."),
    ("How is my data used?",
     "Photos and prompts are processed by our AI providers to score and generate your looks. Nothing is used to train third-party models. Export or delete anytime under Help & privacy."),
    ("How does Hem talk to me?",
     "One voice: honest and constructive. Real strengths, real weaknesses, always with a fix — never brutal, never sugar-coated."),
    ("How does Try-on work?",
     "Upload a photo of yourself plus a garment (or paste a link) and Hem renders it on you. Costs \(Supa.tryonCost) credits per try."),
]

struct FaqView: View {
    var body: some View {
        SubpageScaffold(eyebrow: "SUPPORT", title: "FAQ") {
            ScrollView {
                VStack(spacing: 12) {
                    ForEach(FAQS, id: \.0) { (q, a) in
                        FaqRow(question: q, answer: a)
                    }
                    Spacer().frame(height: 40)
                }
                .padding(20)
            }
        }
    }
}

private struct FaqRow: View {
    let question: String
    let answer: String
    @State private var open = false
    var body: some View {
        Button { open.toggle() } label: {
            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    Text(question).font(Serif.body(15, weight: .semibold)).foregroundStyle(Palette.ink)
                    Spacer()
                    Text(open ? "–" : "+").font(Serif.display(22)).foregroundStyle(Palette.ink)
                }
                if open {
                    Text(answer).font(Serif.body(14)).foregroundStyle(Palette.ink).lineSpacing(4)
                }
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Palette.card)
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Palette.hairline, lineWidth: 1))
            .clipShape(RoundedRectangle(cornerRadius: 12))
        }
        .buttonStyle(.plain)
    }
}
