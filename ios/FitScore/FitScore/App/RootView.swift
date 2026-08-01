import SwiftUI

/// Top-level navigator. Delegates to a stage view + overlays the global toast.
struct RootView: View {
    @EnvironmentObject var session: SessionStore
    @EnvironmentObject var toasts: ToastBus

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
