import SwiftUI
import PhotosUI
import Supabase

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
    /// Outfit to open after a score completes — see `onScored` below.
    @State private var scoredOutfitId: String? = nil
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
                               onScored: { id in
                                   // "See breakdown" used to drop the id on the floor and
                                   // just dismiss, landing the user back on the dashboard.
                                   // Open the detail for the fit that was scored — after the
                                   // scoring cover has finished dismissing, since two
                                   // presentations in the same tick cancel each other out.
                                   cameraBus.pending = nil
                                   DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) {
                                       scoredOutfitId = id
                                   }
                               },
                               onOpenPaywall: { cameraBus.request(.paywall, context: nil) })
            case .tryon:
                TryOnView(onClose: { cameraBus.pending = nil },
                          onOpenPaywall: { cameraBus.request(.paywall, context: .tryon) },
                          onOpenStudioCreate: {
                              // Two presentations in one runloop tick cancel each
                              // other out — same 0.35s workaround used above.
                              cameraBus.pending = nil
                              DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) { tab = .studio }
                          })
            case .versus:
                VersusView(onClose: { cameraBus.pending = nil },
                           onOpenPaywall: { cameraBus.request(.paywall, context: nil) })
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
            case .chat:
                StylistChatView(onClose: { cameraBus.pending = nil })
            case .paywall:
                CreditsSheet(onClose: { cameraBus.pending = nil },
                             paywallContext: cameraBus.paywallContext?.rawValue)
            }
        }
        .fullScreenCover(item: scoredOutfitBinding) { ref in
            ScoreDetailView(outfitId: ref.id, onClose: { scoredOutfitId = nil })
        }
    }

    /// Bridges the optional id into SwiftUI's `item:` presentation API.
    private var scoredOutfitBinding: Binding<ScoredOutfitRef?> {
        Binding(
            get: { scoredOutfitId.map { ScoredOutfitRef(id: $0) } },
            set: { if $0 == nil { scoredOutfitId = nil } }
        )
    }
}

private struct ScoredOutfitRef: Identifiable, Hashable { let id: String }

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

// MARK: - Stylist Chat (Aug 2026)
// Kept in this file to avoid Xcode pbxproj edits. Split into own files later.

/// A single line in the chat transcript.
struct StylistMessage: Identifiable, Equatable {
    enum Role: String { case user, assistant, system }
    let id = UUID()
    let role: Role
    var text: String
    /// Local image previews (base64 not persisted). For user messages.
    var image: UIImage? = nil
    var isStreaming: Bool = false
    var chips: [ChatChip] = []
}

enum ChatChip: String, Identifiable, CaseIterable {
    case tryon = "🪞 Try it on"
    case alternates = "🔁 3 alternatives"
    var id: String { rawValue }
}

@MainActor
final class StylistChatViewModel: ObservableObject {
    @Published var messages: [StylistMessage] = []
    @Published var input: String = ""
    @Published var pickedImage: UIImage? = nil
    @Published var pickedImageData: Data? = nil
    @Published var busy: Bool = false
    @Published var error: String? = nil

    init() {
        // Warm opener — moderate tone, sets expectations.
        messages.append(StylistMessage(
            role: .assistant,
            text: "Hey — I'm Hem. Send a fit photo or ask about tonight's look. I'll be honest and constructive."
        ))
    }

    /// The last request that failed, kept so the error banner's Retry can
    /// re-send it without the user retyping anything.
    private var failedRequest: (history: [StylistApi.Msg], imageData: Data?)? = nil

    func send() async {
        let trimmed = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty || pickedImage != nil else { return }
        let userMsg = StylistMessage(role: .user, text: trimmed, image: pickedImage)
        messages.append(userMsg)
        let imageData = pickedImageData
        input = ""
        pickedImage = nil
        pickedImageData = nil

        let history: [StylistApi.Msg] = messages
            .suffix(20)
            .map { StylistApi.Msg(role: $0.role.rawValue, content: $0.text) }
        await deliver(history: history, imageData: imageData)
    }

    /// Re-sends the last request that failed. No-op when nothing is pending.
    func retry() async {
        guard let request = failedRequest else { return }
        failedRequest = nil
        await deliver(history: request.history, imageData: request.imageData)
    }

    /// Sends one turn to the backend. On failure the streaming placeholder is
    /// removed — the transcript never shows a reply Hem didn't actually give —
    /// and `error` drives the banner with Retry.
    private func deliver(history: [StylistApi.Msg], imageData: Data?) async {
        busy = true
        error = nil

        // Placeholder assistant message we'll fill in.
        let placeholder = StylistMessage(role: .assistant, text: "", isStreaming: true)
        messages.append(placeholder)
        let placeholderId = placeholder.id

        do {
            let reply = try await StylistApi.send(history: history, imageData: imageData)
            if let idx = messages.firstIndex(where: { $0.id == placeholderId }) {
                messages[idx].text = reply
                messages[idx].isStreaming = false
                // Auto-add helpful chips for outfit-related answers.
                messages[idx].chips = deriveChips(for: reply)
            }
        } catch {
            messages.removeAll { $0.id == placeholderId }
            self.error = "Couldn't reach Hem right now. \(error.localizedDescription)"
            failedRequest = (history, imageData)
        }
        busy = false
    }

    /// Injects a follow-up prompt as if the user had typed it. Used by the
    /// suggestion chips under an assistant reply.
    func ask(_ prompt: String) async {
        guard !busy else { return }
        input = prompt
        pickedImage = nil
        pickedImageData = nil
        await send()
    }

    private func deriveChips(for reply: String) -> [ChatChip] {
        let lower = reply.lowercased()
        var chips: [ChatChip] = []
        if lower.contains("try") || lower.contains("wear") || lower.contains("pair") || lower.contains("swap") {
            chips.append(.tryon)
            chips.append(.alternates)
        }
        return chips
    }
}

/// Thin service around the `stylist-chat` edge function. In DEBUG only it falls
/// back to a canned reply so the UI is testable without the backend deployed;
/// Release builds always surface the real failure — a shipped build must never
/// present invented advice as a genuine answer.
enum StylistApi {
    struct Msg: Codable { let role: String; let content: String }
    struct Payload: Encodable {
        let messages: [Msg]
        let image_base64: String?
    }
    struct Reply: Decodable { let text: String? ; let error: String? }

    static func send(history: [Msg], imageData: Data?) async throws -> String {
        let payload = Payload(
            messages: history,
            image_base64: imageData?.base64EncodedString()
        )
        do {
            let r: Reply = try await Supa.client.functions.invoke(
                "stylist-chat",
                options: FunctionInvokeOptions(body: payload)
            )
            if let text = r.text, !text.isEmpty { return text }
            if let err = r.error { throw NSError(domain: "stylist-chat", code: 1, userInfo: [NSLocalizedDescriptionKey: err]) }
            throw NSError(domain: "stylist-chat", code: 2, userInfo: [NSLocalizedDescriptionKey: "empty reply"])
        } catch {
            #if DEBUG
            // Dev-only fallback so the UI works before the edge function is
            // deployed. Never compiled into Release.
            let last = history.last?.content ?? ""
            return canned(for: last)
            #else
            throw error
            #endif
        }
    }

    #if DEBUG
    private static func canned(for prompt: String) -> String {
        let p = prompt.lowercased()
        if p.contains("wedding") || p.contains("düğün") {
            return "For a wedding: linen suit if it's daytime, wool if evening. Keep shoes brown-leather, tie optional. Send a mirror shot when you've got the base on and I'll refine."
        }
        if p.isEmpty {
            return "Nice — I'll take a look. Anything specific: fit, colour, or occasion?"
        }
        return "Got you. Send a photo of what you're considering and I'll call the proportions and colour honestly."
    }
    #endif
}

/// Chat screen — presented as fullScreenCover from CameraMenuBus.request(.chat).
struct StylistChatView: View {
    var onClose: () -> Void

    @StateObject private var vm = StylistChatViewModel()
    @State private var pickerItem: PhotosPickerItem? = nil
    @State private var reportTarget: ReportTarget? = nil
    @FocusState private var inputFocused: Bool

    var body: some View {
        ZStack {
            Palette.paper.ignoresSafeArea()
            VStack(spacing: 0) {
                header
                Hairline()
                transcript
                errorBanner
                Hairline()
                composer
            }
        }
        .onChange(of: pickerItem) { _, item in
            Task {
                if let item, let data = try? await item.loadTransferable(type: Data.self) {
                    let processed = CameraModel.processJpeg(data) ?? data
                    vm.pickedImageData = processed
                    vm.pickedImage = UIImage(data: processed)
                }
            }
        }
        .sheet(item: $reportTarget) { t in
            ReportContentSheet(target: t) { reportTarget = nil }
        }
    }

    // MARK: header

    private var header: some View {
        HStack(alignment: .center) {
            VStack(alignment: .leading, spacing: 4) {
                Text("STYLIST")
                    .font(.system(size: 10, weight: .semibold))
                    .tracking(2)
                    .foregroundStyle(Palette.bronze)
                Text("Chat with Hem")
                    .font(Serif.display(22))
                    .foregroundStyle(Palette.ink)
            }
            Spacer()
            Button(action: onClose) {
                Image(systemName: "xmark")
                    .foregroundStyle(Palette.ink)
                    .frame(width: 36, height: 36)
            }
        }
        .padding(.horizontal, 20)
        .padding(.top, 12)
        .padding(.bottom, 10)
    }

    // MARK: transcript

    private var transcript: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 12) {
                    ForEach(vm.messages) { msg in
                        Group {
                            if msg.role == .assistant, !msg.text.isEmpty {
                                // Long-press to report an AI reply, on the artifact itself.
                                MessageBubble(msg: msg, onChip: handleChip)
                                    .contextMenu {
                                        Button("Report this reply", systemImage: "flag") {
                                            reportTarget = ReportTarget(ReportKind.chatMessage, msg.id.uuidString)
                                        }
                                    }
                            } else {
                                MessageBubble(msg: msg, onChip: handleChip)
                            }
                        }
                        .id(msg.id)
                    }
                    if vm.busy {
                        HStack(spacing: 6) {
                            ForEach(0..<3, id: \.self) { i in
                                Circle().fill(Palette.muted).frame(width: 5, height: 5)
                                    .opacity(0.6)
                            }
                        }
                        .padding(.leading, 20)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 16)
            }
            .onChange(of: vm.messages.count) { _, _ in
                if let last = vm.messages.last {
                    withAnimation(.easeOut(duration: 0.2)) {
                        proxy.scrollTo(last.id, anchor: .bottom)
                    }
                }
            }
        }
    }

    // MARK: error

    /// Shown when a turn failed. The transcript keeps no invented reply, so this
    /// banner and its Retry are the only recovery path.
    @ViewBuilder
    private var errorBanner: some View {
        if let err = vm.error {
            HStack(alignment: .top, spacing: 10) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Palette.roastRed)
                Text(err)
                    .font(Serif.body(13))
                    .foregroundStyle(Palette.ink)
                    .fixedSize(horizontal: false, vertical: true)
                Spacer(minLength: 8)
                Button(action: {
                    Haptic.tap()
                    Task { await vm.retry() }
                }) {
                    Text("RETRY")
                        .font(.system(size: 11, weight: .semibold))
                        .tracking(1.2)
                        .foregroundStyle(Palette.ink)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .overlay(Capsule().stroke(Palette.ink, lineWidth: 1))
                        .clipShape(Capsule())
                }
                .buttonStyle(.plain)
                .disabled(vm.busy)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .background(Palette.card)
        }
    }

    // MARK: chips

    /// Suggestion chips under an assistant reply. Every chip does real work —
    /// nothing here is decorative.
    private func handleChip(_ chip: ChatChip) {
        Haptic.chip()
        switch chip {
        case .tryon:
            let ctx = FeatureGates.requireTryon()
            onClose()
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) {
                if let ctx { CameraMenuBus.shared.request(.paywall, context: ctx) }
                else { CameraMenuBus.shared.request(.tryon) }
            }
        case .alternates:
            Task { await vm.ask("Give me 3 alternatives to that — numbered, one line each.") }
        }
    }

    // MARK: composer

    private var composer: some View {
        VStack(spacing: 8) {
            if let img = vm.pickedImage {
                HStack {
                    ZStack(alignment: .topTrailing) {
                        Image(uiImage: img)
                            .resizable()
                            .scaledToFill()
                            .frame(width: 64, height: 64)
                            .clipShape(RoundedRectangle(cornerRadius: 10))
                        Button(action: {
                            vm.pickedImage = nil
                            vm.pickedImageData = nil
                        }) {
                            Image(systemName: "xmark.circle.fill")
                                .foregroundStyle(.white, .black.opacity(0.6))
                                .font(.system(size: 18))
                                .padding(2)
                        }
                    }
                    Spacer()
                }
                .padding(.horizontal, 16)
            }
            HStack(spacing: 10) {
                PhotosPicker(selection: $pickerItem, matching: .images) {
                    Image(systemName: "photo.on.rectangle.angled")
                        .font(.system(size: 20))
                        .foregroundStyle(Palette.ink)
                        .frame(width: 36, height: 36)
                }
                TextField("Ask Hem — a fit, an occasion, a swap…", text: $vm.input, axis: .vertical)
                    .lineLimit(1...4)
                    .focused($inputFocused)
                    .font(Serif.body(15))
                    .foregroundStyle(Palette.ink)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 9)
                    .background(Palette.card)
                    .clipShape(RoundedRectangle(cornerRadius: 20))
                    .overlay(RoundedRectangle(cornerRadius: 20).stroke(Palette.hairline, lineWidth: 1))
                Button(action: {
                    Haptic.tap()
                    Task { await vm.send() }
                }) {
                    Image(systemName: "arrow.up.circle.fill")
                        .font(.system(size: 30))
                        .foregroundStyle(canSend ? Palette.ink : Palette.muted)
                }
                .disabled(!canSend || vm.busy)
            }
            .padding(.horizontal, 16)
            .padding(.top, 4)
            .padding(.bottom, 12)
        }
        .background(Palette.paper)
    }

    private var canSend: Bool {
        !vm.input.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || vm.pickedImage != nil
    }
}

private struct MessageBubble: View {
    let msg: StylistMessage
    var onChip: (ChatChip) -> Void

    var body: some View {
        HStack(alignment: .top) {
            if msg.role == .user { Spacer(minLength: 40) }
            VStack(alignment: msg.role == .user ? .trailing : .leading, spacing: 6) {
                if let img = msg.image {
                    Image(uiImage: img)
                        .resizable()
                        .scaledToFill()
                        .frame(maxWidth: 200, maxHeight: 240)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                }
                if !msg.text.isEmpty || msg.isStreaming {
                    Text(msg.isStreaming && msg.text.isEmpty ? "…" : msg.text)
                        .font(Serif.body(15))
                        .foregroundStyle(msg.role == .user ? .white : Palette.ink)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .background(msg.role == .user ? Palette.ink : Palette.card)
                        .clipShape(RoundedRectangle(cornerRadius: 14))
                }
                if msg.role == .assistant, !msg.chips.isEmpty {
                    HStack(spacing: 6) {
                        ForEach(msg.chips) { chip in
                            Button(action: { onChip(chip) }) {
                                Text(chip.rawValue)
                                    .font(.system(size: 11, weight: .semibold))
                                    .tracking(0.5)
                                    .foregroundStyle(Palette.ink)
                                    .padding(.horizontal, 10)
                                    .padding(.vertical, 6)
                                    .background(Palette.card)
                                    .overlay(Capsule().stroke(Palette.hairline, lineWidth: 1))
                                    .clipShape(Capsule())
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
            if msg.role == .assistant { Spacer(minLength: 40) }
        }
    }
}

/// Compact banner shown at the top of Home to invite chat entry.
struct StylistHomeInviteBar: View {
    var onTap: () -> Void
    var body: some View {
        Button(action: {
            Haptic.chip()
            onTap()
        }) {
            HStack(spacing: 10) {
                Image(systemName: "bubble.left.and.bubble.right.fill")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(Palette.bronze)
                Text("Ask your stylist…")
                    .font(Serif.body(14))
                    .foregroundStyle(Palette.muted)
                Spacer()
                Image(systemName: "arrow.right")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(Palette.bronze)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .background(Palette.card)
            .clipShape(RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(Palette.hairline, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}
