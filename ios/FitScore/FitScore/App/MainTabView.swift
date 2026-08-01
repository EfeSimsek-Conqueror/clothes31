import SwiftUI

/// Bottom tab bar shell with a floating center camera FAB. The camera slot is
/// NOT a nav destination — tapping it opens the `CameraMenuSheet`.
struct MainTabView: View {
    enum Tab: Int, CaseIterable {
        case today, studio, journal, you
        var label: String {
            switch self {
            case .today:   return "TODAY"
            case .studio:  return "STUDIO"
            case .journal: return "JOURNAL"
            case .you:     return "YOU"
            }
        }
    }

    @State private var tab: Tab = .today
    @State private var showCameraMenu = false
    @State private var showYouSheet = false
    @State private var showCreditsSheet = false
    @State private var showPaywall = false
    @ObservedObject private var cameraBus = CameraMenuBus.shared

    var body: some View {
        ZStack(alignment: .bottom) {
            Group {
                switch tab {
                case .today:   HomeView()
                case .studio:  StudioView()
                case .journal: JournalView()
                case .you:     YouSheet()
                }
            }
            .padding(.bottom, 88)

            TabBar(
                tab: $tab,
                onCamera: { showCameraMenu = true }
            )
        }
        .background(Palette.paper.ignoresSafeArea())
        .sheet(isPresented: $showCameraMenu) {
            CameraMenuSheet()
                .presentationDetents([.medium, .large])
                .presentationDragIndicator(.visible)
        }
        .sheet(isPresented: $showCreditsSheet) {
            CreditsSheet()
                .presentationDetents([.medium, .large])
        }
        .fullScreenCover(item: $cameraBus.pending) { flow in
            switch flow {
            case .score:
                ScoreSheetView(onClose: { cameraBus.pending = nil },
                               onScored: { _ in cameraBus.pending = nil },
                               onOpenPaywall: { cameraBus.request(.paywall, context: nil) })
            case .tryon:
                TryOnView(onClose: { cameraBus.pending = nil },
                          onOpenPaywall: { cameraBus.request(.paywall, context: .tryon) })
            case .versus:
                VersusView(onClose: { cameraBus.pending = nil },
                           onOpenPaywall: { cameraBus.request(.paywall, context: nil) })
            case .roast:
                RoastView(onClose: { cameraBus.pending = nil },
                          onOpenPaywall: { cameraBus.request(.paywall, context: .brutal) })
            case .decode:
                DecodeView(onClose: { cameraBus.pending = nil },
                           onOpenPaywall: { cameraBus.request(.paywall, context: nil) })
            case .occasion:
                OccasionCoachView(onClose: { cameraBus.pending = nil },
                                  onOpenPaywall: { cameraBus.request(.paywall, context: nil) })
            case .cover:
                MagazineCoverSheet(outfitId: nil,
                                   onClose: { cameraBus.pending = nil })
            case .invitation:
                InvitationDecoderView(onClose: { cameraBus.pending = nil },
                                      onOpenPaywall: { cameraBus.request(.paywall, context: nil) })
            case .paywall:
                CreditsSheet(onClose: { cameraBus.pending = nil },
                             paywallContext: cameraBus.paywallContext?.rawValue)
            }
        }
    }
}

private struct TabBar: View {
    @Binding var tab: MainTabView.Tab
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
    private func tabItem(_ t: MainTabView.Tab) -> some View {
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
