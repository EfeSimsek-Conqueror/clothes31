import SwiftUI

/// Top-level navigator. Delegates to a stage view + overlays the global toast.
struct RootView: View {
    @EnvironmentObject var session: SessionStore
    @EnvironmentObject var toasts: ToastBus
    @State private var needsGender = false

    var body: some View {
        ZStack {
            Palette.paper.ignoresSafeArea()
            Group {
                switch session.stage {
                case .splash:       SplashView()
                case .signIn:       SignInView()
                case .onboarding1:  OnboardGenderView()
                case .onboarding2:  OnboardVibeView()
                case .calibration:
                    if let bp = session.bodyProfile {
                        BodyProfileRevealView(
                            profile: bp,
                            onDone: { session.advance(to: .home) }
                        )
                    } else {
                        BodyCalibrationView(onFinished: { _ in
                            if session.bodyProfile != nil {
                                // Reveal is now driven by the same stage — the
                                // if-branch above renders it next tick.
                            } else {
                                session.advance(to: .home)
                            }
                        })
                    }
                case .home:         MainTabView()
                }
            }
            .transition(.opacity)
            // Accounts that finished onboarding before the question existed —
            // or whose answer was written in a spelling nothing could read —
            // reach Home with no usable answer, and then every garment the app
            // makes for them is cut for nobody. Ask once, here, rather than
            // guessing downstream.
            .sheet(isPresented: $needsGender) {
                GenderCatchUpSheet { picked in
                    Task {
                        try? await Repo.shared.updateStyleProfile(
                            gender: picked.rawValue,
                            styleTags: nil, preferredFabrics: nil,
                            preferredColors: nil, sensitivities: nil
                        )
                        session.gender = picked
                        needsGender = false
                    }
                }
                .interactiveDismissDisabled()
            }
            .task(id: session.stage) {
                guard session.stage == .home else { return }
                let stored = try? await Repo.shared.currentProfile()?.gender
                if let g = Gender(stored: stored) {
                    session.gender = g
                } else {
                    needsGender = true
                }
            }

            // Global toast — bronze pill floating at the top.
            if let msg = toasts.message {
                VStack {
                    ToastPill(text: msg)
                        .padding(.top, 8)
                    Spacer()
                }
                .transition(.move(edge: .top).combined(with: .opacity))
                .animation(.easeInOut(duration: 0.2), value: msg)
                .allowsHitTesting(false)
            }
        }
    }
}

private struct ToastPill: View {
    let text: String
    var body: some View {
        Text(text)
            .font(Serif.body(14, weight: .medium))
            .foregroundStyle(.white)
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .background(Palette.ink)
            .clipShape(Capsule())
            .shadow(color: .black.opacity(0.12), radius: 8, y: 3)
            .padding(.horizontal, 24)
    }
}

/// The onboarding question, asked again for an account that reached Home
/// without a readable answer. Deliberately not dismissable: it is one tap, and
/// "Other" is a real answer rather than a way of refusing to give one.
private struct GenderCatchUpSheet: View {
    var onPick: (Gender) -> Void
    @State private var pick: Gender?

    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            Eyebrow(text: "ONE QUESTION")
            Text("Who's dressing?")
                .font(Serif.display(32))
                .foregroundStyle(Palette.ink)
            Text("It decides how everything Fitrater makes for you is cut.")
                .font(Serif.body(15))
                .foregroundStyle(Palette.muted)

            VStack(spacing: 12) {
                ForEach(Gender.allCases) { g in
                    Button { Haptic.chip(); pick = g } label: {
                        HStack {
                            VStack(alignment: .leading, spacing: 3) {
                                Text(g.rawValue).font(Serif.display(20)).foregroundStyle(Palette.ink)
                                Text(g.caption).font(Serif.body(12)).foregroundStyle(Palette.muted)
                            }
                            Spacer()
                            Circle()
                                .strokeBorder(pick == g ? Palette.bronze : Palette.hairline, lineWidth: 1.5)
                                .background(Circle().fill(pick == g ? Palette.bronze : .clear))
                                .frame(width: 16, height: 16)
                        }
                        .padding(16)
                        .frame(maxWidth: .infinity)
                        .overlay(RoundedRectangle(cornerRadius: 4)
                            .stroke(pick == g ? Palette.ink : Palette.hairline,
                                    lineWidth: pick == g ? 1.5 : 1))
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.top, 4)

            Spacer()
            PrimaryButton(title: "Save", enabled: pick != nil) {
                if let pick { onPick(pick) }
            }
        }
        .padding(24)
        .background(Palette.paper.ignoresSafeArea())
        .presentationDetents([.medium, .large])
    }
}
