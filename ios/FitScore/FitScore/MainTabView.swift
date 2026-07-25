import SwiftUI

enum Tab: Int, CaseIterable {
    case today, studio, camera, journal, you
    var label: String {
        switch self {
        case .today: return "TODAY"
        case .studio: return "STUDIO"
        case .camera: return ""
        case .journal: return "JOURNAL"
        case .you: return "YOU"
        }
    }
}

struct MainTabView: View {
    @State private var tab: Tab = .today
    @State private var showScoreSheet = false
    @State private var showPaywall = false

    var body: some View {
        ZStack(alignment: .bottom) {
            Group {
                switch tab {
                case .today:   TodayView(showPaywall: $showPaywall, showScoreSheet: $showScoreSheet)
                case .studio:  StudioView(showPaywall: $showPaywall)
                case .camera:  TodayView(showPaywall: $showPaywall, showScoreSheet: $showScoreSheet)
                case .journal: JournalView()
                case .you:     YouView(showPaywall: $showPaywall)
                }
            }
            .padding(.bottom, 88)

            TabBar(tab: $tab, onCamera: { showScoreSheet = true })
        }
        .background(Palette.paper.ignoresSafeArea())
        .sheet(isPresented: $showScoreSheet) { ScoreLookSheet() }
        .sheet(isPresented: $showPaywall) { PaywallView() }
    }
}

struct TabBar: View {
    @Binding var tab: Tab
    var onCamera: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            Hairline()
            HStack {
                tabItem(.today)
                tabItem(.studio)
                cameraButton
                tabItem(.journal)
                tabItem(.you)
            }
            .padding(.horizontal, 20)
            .padding(.top, 10)
            .padding(.bottom, 22)
            .background(Palette.paper)
        }
    }

    @ViewBuilder
    private func tabItem(_ t: Tab) -> some View {
        Button { tab = t } label: {
            VStack(spacing: 6) {
                Text(t.label)
                    .font(.system(size: 10, weight: .semibold))
                    .tracking(1.8)
                    .foregroundStyle(tab == t ? Palette.ink : Palette.muted)
                Rectangle()
                    .fill(tab == t ? Palette.bronze : .clear)
                    .frame(height: 1.5)
                    .frame(width: 22)
            }
            .frame(maxWidth: .infinity)
        }
    }

    private var cameraButton: some View {
        Button(action: onCamera) {
            ZStack {
                Circle().fill(Palette.ink).frame(width: 58, height: 58)
                Image(systemName: "camera.fill")
                    .foregroundStyle(.white)
                    .font(.system(size: 20, weight: .semibold))
            }
        }
        .frame(maxWidth: .infinity)
        .offset(y: -8)
    }
}
