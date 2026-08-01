import SwiftUI

/// Bronze soft-nudge banner rendered above the Hem card when the user's
/// balance dips below 30 and they haven't dismissed today. Dismisses stay in
/// memory only (force-quit re-shows — intentional soft nudge).
struct LowCreditsBanner: View {
    @ObservedObject private var credits = CreditsBus.shared
    @ObservedObject private var nudge = NudgeBus.shared
    var onOpenCredits: () -> Void = {}

    var body: some View {
        let bal = credits.balance ?? 999
        if bal < 30 && !nudge.isDismissedToday {
            HStack(alignment: .center, spacing: 12) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("LOW CREDITS")
                        .font(.system(size: 10, weight: .semibold))
                        .tracking(1.8)
                        .foregroundStyle(Palette.bronze)
                    Text("\(bal) left — top up before your next look.")
                        .font(Serif.body(14, weight: .medium))
                        .foregroundStyle(Palette.ink)
                }
                Spacer()
                Button(action: {
                    Haptic.tap()
                    onOpenCredits()
                }) {
                    Text("TOP UP")
                        .font(.system(size: 11, weight: .semibold))
                        .tracking(1.6)
                        .foregroundStyle(.white)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .background(Palette.bronze)
                        .clipShape(Capsule())
                }
                Button(action: { nudge.dismissForToday() }) {
                    Image(systemName: "xmark")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(Palette.muted)
                        .frame(width: 28, height: 28)
                }
            }
            .padding(14)
            .background(Palette.card)
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Palette.bronze.opacity(0.3), lineWidth: 1))
        } else {
            EmptyView()
        }
    }
}
