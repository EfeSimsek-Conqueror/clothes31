import SwiftUI

struct RootView: View {
    @EnvironmentObject var session: SessionStore

    var body: some View {
        ZStack {
            Palette.paper.ignoresSafeArea()
            switch session.stage {
            case .splash:       SplashView()
            case .signIn:       SignInView()
            case .onboarding1:  OnboardingGenderView()
            case .onboarding2:  OnboardingVibeView()
            case .home:         MainTabView()
            }
        }
    }
}
