import SwiftUI

struct OnboardVibeView: View {
    @EnvironmentObject var session: SessionStore
    @EnvironmentObject var toasts: ToastBus
    @State private var picked: Set<String> = []
    @State private var saving = false

    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            Spacer(minLength: 24)
            Eyebrow(text: "STEP 2 OF 2")
            Text("Your vibe.")
                .font(Serif.display(40))
                .foregroundStyle(Palette.ink)
            Text("Pick anything that feels like you. You can change these later.")
                .font(Serif.body(15)).foregroundStyle(Palette.muted)

            FlowLayout(spacing: 10) {
                ForEach(Seeds.styleChips) { chip in
                    let on = picked.contains(chip.label)
                    Button {
                        if on { picked.remove(chip.label) } else { picked.insert(chip.label) }
                    } label: {
                        Text(chip.label)
                            .font(Serif.body(14))
                            .foregroundStyle(on ? .white : Palette.ink)
                            .padding(.horizontal, 16).padding(.vertical, 10)
                            .background(on ? Palette.ink : Color.clear)
                            .overlay(Capsule().stroke(on ? Palette.ink : Palette.hairline, lineWidth: 1))
                            .clipShape(Capsule())
                    }
                }
            }
            .padding(.top, 8)

            Spacer()

            PrimaryButton(
                title: saving ? "Saving…" : "Continue — \(picked.count) picked",
                enabled: !picked.isEmpty && !saving
            ) {
                finish()
            }
            .padding(.bottom, 24)
        }
        .padding(.horizontal, 24)
    }

    private func finish() {
        session.pickedChips = picked
        saving = true
        Task {
            defer { saving = false }
            guard let uid = Repo.shared.userId else {
                session.advance(to: .calibration)
                return
            }
            do {
                try await Repo.shared.upsertProfile(ProfileUpsert(
                    id: uid,
                    email: Repo.shared.userEmail,
                    gender: session.gender?.rawValue,
                    style_tags: Array(picked),
                    onboarded: true
                ))
                session.profile = try? await Repo.shared.currentProfile()
            } catch {
                toasts.post("Couldn't save vibe — carrying on.")
            }
            // New users flow into body calibration before Home; onboarded
            // users won't hit this path.
            session.advance(to: .calibration)
        }
    }
}
