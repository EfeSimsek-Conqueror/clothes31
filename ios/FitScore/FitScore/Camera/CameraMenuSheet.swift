import SwiftUI

/// Accordion sheet — one tile per Camera flow (Score / Try-on / A vs B / Roast
/// / Decode). Only one tile expanded at a time. Ports Android
/// `CameraMenuSheet` behavior + copy.
struct CameraMenuSheet: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject var session: SessionStore

    @State private var openId: String?

    @State private var showScore = false
    @State private var showTryOn = false
    @State private var showVersus = false
    @State private var showDecode = false
    @State private var showPaywall = false
    @State private var paywallContext: PaywallContext? = nil
    @State private var occasionFreeAvailable = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                HStack(alignment: .center) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("TONIGHT'S TOOLS")
                            .font(.system(size: 10, weight: .semibold))
                            .tracking(2)
                            .foregroundStyle(Palette.bronze)
                        Text("What's the look for?")
                            .font(Serif.display(26))
                            .foregroundStyle(Palette.ink)
                    }
                    Spacer()
                    Button(action: { dismiss() }) {
                        Image(systemName: "xmark")
                            .foregroundStyle(Palette.ink)
                            .frame(width: 36, height: 36)
                    }
                }
                Spacer(minLength: 20)

                Hairline()
                row("score", icon: "camera.fill",
                    title: "Score a look",
                    subtitle: "One photo, one honest number.",
                    cost: "\(Supa.scoreCost) credits",
                    description: "Snap or upload a fit and Hem calls it — score, per-piece notes, and one line of verdict.",
                    needs: "1 photo of the outfit.",
                    goLabel: "GO →",
                    proLocked: false,
                    onGo: {
                        dismiss()
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) { CameraMenuBus.shared.request(.score) }
                    })
                Hairline()
                row("tryon", icon: "sparkles",
                    title: "Try on",
                    subtitle: "Wear a Studio piece on your own photo.",
                    cost: "\(Supa.tryonCost) credits",
                    description: "Wear a Studio piece — or a garment you upload — on your own photo. Hem does the compositing.",
                    needs: "1 photo of yourself + 1 garment.",
                    goLabel: RcBilling.shared.isPro ? "GO →" : "Start 7-day trial →",
                    proLocked: !RcBilling.shared.isPro,
                    onGo: {
                        let ctx = FeatureGates.requireTryon()
                        dismiss()
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) {
                            if let ctx { CameraMenuBus.shared.request(.paywall, context: ctx) }
                            else { CameraMenuBus.shared.request(.tryon) }
                        }
                    })
                Hairline()
                row("versus", icon: "person.2.fill",
                    title: "A vs B",
                    subtitle: "Two photos, one winner.",
                    cost: "\(Supa.versusCost) credits",
                    description: "Two fits, one winner. Hem picks and explains, sharp and one-sentence.",
                    needs: "2 photos.",
                    goLabel: "GO →",
                    proLocked: false,
                    onGo: {
                        dismiss()
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) { CameraMenuBus.shared.request(.versus) }
                    })
                Hairline()
                row("chat", icon: "bubble.left.and.bubble.right.fill",
                    title: "Chat with Hem",
                    subtitle: "Ask anything — fit, occasion, swap ideas.",
                    cost: "free",
                    description: "Your stylist on tap. Send a photo or a question — Hem answers honestly, in one voice. Remembers your recent fits.",
                    needs: "Nothing. Just start typing.",
                    goLabel: "OPEN →",
                    proLocked: false,
                    onGo: {
                        dismiss()
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) { CameraMenuBus.shared.request(.chat) }
                    })
                Hairline()
                row("decode", icon: "paintpalette.fill",
                    title: "Decode style",
                    subtitle: "Any reference — get the recipe.",
                    cost: "\(Supa.decodeCost) credits",
                    description: "Any reference — celebrity, magazine, IG. Hem breaks down the pieces, palette, and one-line style signature.",
                    needs: "1 reference photo.",
                    goLabel: "GO →",
                    proLocked: false,
                    onGo: {
                        dismiss()
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) { CameraMenuBus.shared.request(.decode) }
                    })
                Hairline()
                row("occasion", icon: "sparkle.magnifyingglass",
                    title: "Occasion Coach",
                    subtitle: "Big day? Hem plans 3 fits.",
                    cost: occasionFreeAvailable ? "free this week" : "\(Supa.occasionCost) credits",
                    description: "Tell Hem the moment. He picks 3 combos from your closet and gaps for Studio to fill.",
                    needs: "1 sentence about the occasion.",
                    goLabel: "GO →",
                    proLocked: false,
                    onGo: {
                        dismiss()
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) { CameraMenuBus.shared.request(.occasion) }
                    })
                // Covers are shelved, so the whole accordion entry — including its
                // leading hairline — drops out to avoid a doubled divider here.
                if Supa.magazineCoversEnabled {
                    Hairline()
                    row("cover", icon: "text.book.closed.fill",
                        title: "Compose a cover",
                        subtitle: "Your fit, on the cover.",
                        cost: "\(Supa.magazineCoverCost) credits",
                        description: "Turn any fit into an editorial magazine cover — masthead, headline, pull-quote. Made to be shared.",
                        needs: "1 photo (or pick from Journal).",
                        goLabel: "GO →",
                        proLocked: false,
                        onGo: {
                            dismiss()
                            DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) { CameraMenuBus.shared.request(.cover) }
                        })
                }
                Hairline()
                row("invitation", icon: "envelope.open.fill",
                    title: "Decode the invite",
                    subtitle: "Dress code → 3 closet combos.",
                    cost: "\(Supa.invitationDecodeCost) credits",
                    description: "Snap the invitation. Hem reads the dress code and pulls 3 combinations from your Studio closet.",
                    needs: "1 photo of the invitation.",
                    goLabel: "GO →",
                    proLocked: false,
                    onGo: {
                        dismiss()
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) { CameraMenuBus.shared.request(.invitation) }
                    })
                Hairline()
                Spacer(minLength: 30)
            }
            .padding(.horizontal, 20)
            .padding(.top, 16)
        }
        .background(Palette.paper.ignoresSafeArea())
        .task {
            occasionFreeAvailable = await OccasionCoachService.weeklyFreeAvailable()
        }
    }

    @ViewBuilder
    private func row(_ id: String,
                     icon: String,
                     title: String,
                     subtitle: String,
                     cost: String,
                     description: String,
                     needs: String,
                     goLabel: String,
                     proLocked: Bool,
                     onGo: @escaping () -> Void) -> some View {
        let open = openId == id
        VStack(alignment: .leading, spacing: 0) {
            Button(action: {
                Haptic.chip()
                withAnimation(.spring(response: 0.35, dampingFraction: 0.85)) {
                    openId = open ? nil : id
                }
            }) {
                HStack(alignment: .center, spacing: 12) {
                    Image(systemName: icon)
                        .font(.system(size: 20))
                        .foregroundStyle(Palette.ink)
                        .frame(width: 24, height: 24)
                    VStack(alignment: .leading, spacing: 3) {
                        Text(title)
                            .font(Serif.display(20))
                            .foregroundStyle(Palette.ink)
                        Text(subtitle)
                            .font(Serif.body(13))
                            .foregroundStyle(Palette.muted)
                    }
                    Spacer()
                    VStack(alignment: .trailing, spacing: 4) {
                        Text(cost.uppercased())
                            .font(.system(size: 10, weight: .semibold))
                            .tracking(1.5)
                            .foregroundStyle(Palette.bronze)
                        if proLocked {
                            Text("PRO")
                                .font(.system(size: 10, weight: .semibold))
                                .tracking(1.5)
                                .foregroundStyle(.white)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(Palette.bronze)
                                .clipShape(Capsule())
                        }
                    }
                    Image(systemName: open ? "chevron.down" : "chevron.right")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(Palette.bronze)
                        .frame(width: 22, height: 22)
                }
                .padding(.vertical, 14)
                .contentShape(Rectangle())
            }
            if open {
                VStack(alignment: .leading, spacing: 12) {
                    Text(description)
                        .font(Serif.body(14))
                        .foregroundStyle(Palette.ink)
                    HStack(spacing: 8) {
                        Text("NEEDS")
                            .font(.system(size: 10, weight: .semibold))
                            .tracking(1.5)
                            .foregroundStyle(Palette.bronze)
                        Text(needs)
                            .font(Serif.body(13, weight: .medium))
                            .foregroundStyle(Palette.muted)
                    }
                    HStack {
                        Spacer()
                        PrimaryButton(title: goLabel, action: {
                            Haptic.tap()
                            onGo()
                        })
                        .frame(maxWidth: proLocked ? .infinity : 260)
                    }
                }
                .padding(.leading, 36)
                .padding(.trailing, 4)
                .padding(.bottom, 14)
                .transition(.opacity.combined(with: .move(edge: .top)))
            }
        }
    }
}
