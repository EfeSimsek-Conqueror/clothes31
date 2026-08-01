import SwiftUI
import SafariServices

struct HelpPrivacySheet: View {
    let onClose: () -> Void
    @EnvironmentObject var toasts: ToastBus

    @State private var exporting = false
    @State private var showDeleteConfirm = false
    @State private var typedConfirm = ""
    @State private var deleting = false
    @State private var goFaq = false
    @State private var goLicenses = false
    @State private var exportedURL: URL?
    @State private var safariURL: URL?

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    HStack(alignment: .top) {
                        VStack(alignment: .leading, spacing: 4) {
                            Eyebrow(text: "HELP & PRIVACY")
                            Text("Your data, on demand.").font(Serif.display(28)).foregroundStyle(Palette.ink)
                        }
                        Spacer()
                        Button(action: onClose) {
                            Text("×").font(Serif.display(28)).foregroundStyle(Palette.ink)
                        }
                    }

                    Group {
                        Eyebrow(text: "GET IN TOUCH")
                        VStack(spacing: 0) {
                            Hairline()
                            ActionRow(title: "FAQ", subtitle: "Answers to the most common questions.") { goFaq = true }
                            Hairline()
                            ActionRow(title: "Contact support", subtitle: "efe@cloudgeng.com — usually within a day.") {
                                if let url = URL(string: "mailto:efe@cloudgeng.com?subject=Fitrater%20support") {
                                    UIApplication.shared.open(url)
                                }
                            }
                            Hairline()
                            ActionRow(title: "Rate on App Store", subtitle: "One tap. It helps a lot.") {
                                if let url = URL(string: "https://apps.apple.com/app/id0000000000?action=write-review") {
                                    UIApplication.shared.open(url)
                                }
                            }
                            Hairline()
                        }
                    }

                    Group {
                        Eyebrow(text: "LEGAL")
                        VStack(spacing: 0) {
                            Hairline()
                            ActionRow(title: "Privacy policy", subtitle: "fitrater.ai/privacy") {
                                safariURL = URL(string: "https://fitrater.ai/privacy")
                            }
                            Hairline()
                            ActionRow(title: "Terms of service", subtitle: "fitrater.ai/terms") {
                                safariURL = URL(string: "https://fitrater.ai/terms")
                            }
                            Hairline()
                            ActionRow(title: "Open-source licenses", subtitle: "The libraries that power Fitrater.") {
                                goLicenses = true
                            }
                            Hairline()
                        }
                    }

                    Group {
                        Eyebrow(text: "YOUR DATA")
                        VStack(spacing: 0) {
                            Hairline()
                            ActionRow(
                                title: "Export my data",
                                subtitle: exporting ? "Preparing your archive…" : "Everything Hem has, as JSON.",
                                enabled: !exporting
                            ) { exportData() }
                            Hairline()
                            ActionRow(title: "Delete my account", subtitle: "This can't be undone.", danger: true) {
                                showDeleteConfirm = true
                            }
                            Hairline()
                        }
                    }

                    Spacer().frame(height: 24)
                    Text("Questions? support@fitrater.com")
                        .font(Serif.body(13)).foregroundStyle(Palette.muted)
                    Spacer().frame(height: 24)
                }
                .padding(20)
            }
            .background(Palette.paper.ignoresSafeArea())
            .navigationDestination(isPresented: $goFaq) { FaqView() }
            .navigationDestination(isPresented: $goLicenses) { LicensesView() }
            .sheet(item: Binding(get: { safariURL.map { IdentifiableURL(url: $0) } }, set: { _ in safariURL = nil })) { holder in
                SafariView(url: holder.url)
            }
            .sheet(item: Binding(get: { exportedURL.map { IdentifiableURL(url: $0) } }, set: { _ in exportedURL = nil })) { holder in
                ActivityShareView(items: [holder.url])
            }
            .alert("Delete account?", isPresented: $showDeleteConfirm) {
                TextField("Type DELETE", text: $typedConfirm).autocapitalization(.allCharacters)
                Button("Cancel", role: .cancel) {
                    typedConfirm = ""
                }
                Button(deleting ? "Deleting…" : "DELETE", role: .destructive) {
                    guard typedConfirm.trimmingCharacters(in: .whitespaces) == "DELETE" else { return }
                    deleting = true
                    Task {
                        try? await Repo.shared.deleteAllUserData()
                        toasts.post("Account deleted")
                        deleting = false
                        onClose()
                    }
                }
                .disabled(deleting || typedConfirm.trimmingCharacters(in: .whitespaces) != "DELETE")
            } message: {
                Text("This wipes every look, piece, and note. Type DELETE below to confirm.")
            }
        }
    }

    private func exportData() {
        exporting = true
        Task {
            defer { exporting = false }
            do {
                let payload = try await Repo.shared.exportUserData()
                let dir = FileManager.default.temporaryDirectory
                let stamp = ISO8601DateFormatter().string(from: Date()).replacingOccurrences(of: ":", with: "-")
                let file = dir.appendingPathComponent("fitrater-data-\(stamp).json")
                try payload.write(to: file)
                exportedURL = file
            } catch {
                toasts.post("Export failed: \(error.localizedDescription)")
            }
        }
    }
}

private struct IdentifiableURL: Identifiable { let id = UUID(); let url: URL }

private struct ActionRow: View {
    let title: String
    let subtitle: String
    var enabled: Bool = true
    var danger: Bool = false
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(Serif.body(15, weight: .semibold))
                        .foregroundStyle(danger ? Palette.roastRed : Palette.ink)
                    Text(subtitle).font(Serif.body(13)).foregroundStyle(Palette.muted)
                }
                Spacer()
                Image(systemName: "chevron.right").font(.system(size: 12)).foregroundStyle(Palette.muted)
            }
            .padding(.vertical, 14)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }
}

struct SafariView: UIViewControllerRepresentable {
    let url: URL
    func makeUIViewController(context: Context) -> SFSafariViewController { SFSafariViewController(url: url) }
    func updateUIViewController(_ uiViewController: SFSafariViewController, context: Context) {}
}

struct ActivityShareView: UIViewControllerRepresentable {
    let items: [Any]
    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }
    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
