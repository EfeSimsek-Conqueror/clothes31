import SwiftUI

/// Edit-cover sheet — presented from MagazineCoverSheet when the user taps
/// "Edit" instead of "Compose". Lets them override the AI's decisions before
/// running the compose:
///   • Free-form user prompt (appended to nano-banana instructions)
///   • Custom masthead (no-reference mode only — reference mode preserves ref's masthead)
///   • Custom headline / pull-quote (no-reference mode)
///   • Cover lines (add / remove up to 4 items)
///
/// All fields optional. Empty fields fall back to the AI defaults.
struct EditCoverSheet: View {
    // Bound to parent state so the parent's compose flow reads back the edits.
    @Binding var userPrompt: String
    @Binding var customMasthead: String
    @Binding var customHeadline: String
    @Binding var customPullQuote: String
    @Binding var coverLines: [String]

    let referenceMode: Bool          // true = reference-based swap; hide masthead/text overrides
    let composeCostCredits: Int
    var onCompose: () -> Void
    var onClose: () -> Void

    @State private var newCoverLine: String = ""

    var body: some View {
        ZStack {
            Palette.paper.ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    header
                    Text(referenceMode
                         ? "The cover's masthead + typography come from your reference. Everything else is up to you."
                         : "Override what Hem would pick by default. Leave any field blank to let AI decide.")
                        .font(Serif.italic(14))
                        .foregroundStyle(Palette.muted)

                    promptField

                    if !referenceMode {
                        mastheadField
                        headlineField
                        pullQuoteField
                        coverLinesField
                    }

                    Spacer(minLength: 20)
                    PrimaryButton(
                        title: "Compose · \(composeCostCredits) credits",
                        enabled: true,
                        action: onCompose
                    )
                    Button(action: onClose) {
                        Text("Cancel")
                            .font(Serif.body(14))
                            .foregroundStyle(Palette.muted)
                            .frame(maxWidth: .infinity)
                            .padding(.top, 10)
                    }
                    Spacer(minLength: 24)
                }
                .padding(20)
            }
        }
    }

    private var header: some View {
        HStack {
            VStack(alignment: .leading, spacing: 6) {
                Eyebrow(text: "EDIT")
                Text("Fine-tune it.")
                    .font(Serif.display(30))
                    .foregroundStyle(Palette.ink)
            }
            Spacer()
            Button(action: onClose) {
                Image(systemName: "xmark").foregroundStyle(Palette.ink).frame(width: 36, height: 36)
            }
        }
    }

    // MARK: - Fields

    private var promptField: some View {
        fieldGroup(label: "YOUR DIRECTION (OPT)") {
            TextField(
                "e.g. make it feel like summer · add contrast · shift subject left",
                text: $userPrompt,
                axis: .vertical
            )
            .font(Serif.body(15))
            .lineLimit(3, reservesSpace: true)
            .autocorrectionDisabled()
        }
    }

    private var mastheadField: some View {
        fieldGroup(label: "MASTHEAD (OPT)") {
            TextField("FITRATER", text: $customMasthead)
                .font(Serif.display(18))
                .textInputAutocapitalization(.characters)
                .autocorrectionDisabled()
        }
    }

    private var headlineField: some View {
        fieldGroup(label: "HEADLINE (OPT)") {
            TextField("Auto — a study in bronze", text: $customHeadline, axis: .vertical)
                .font(Serif.display(18))
                .lineLimit(2, reservesSpace: true)
                .textInputAutocapitalization(.characters)
                .autocorrectionDisabled()
        }
    }

    private var pullQuoteField: some View {
        fieldGroup(label: "PULL QUOTE (OPT)") {
            TextField("Auto — an italic subline", text: $customPullQuote, axis: .vertical)
                .font(Serif.italic(15))
                .lineLimit(2, reservesSpace: true)
                .autocorrectionDisabled()
        }
    }

    private var coverLinesField: some View {
        VStack(alignment: .leading, spacing: 8) {
            Eyebrow(text: "COVER LINES (OPT)")
            if !coverLines.isEmpty {
                VStack(spacing: 6) {
                    ForEach(Array(coverLines.enumerated()), id: \.offset) { idx, line in
                        HStack {
                            Text(line)
                                .font(Serif.body(14, weight: .semibold))
                                .foregroundStyle(Palette.ink)
                            Spacer()
                            Button(action: { coverLines.remove(at: idx) }) {
                                Image(systemName: "xmark").font(.system(size: 11)).foregroundStyle(Palette.muted)
                            }.buttonStyle(.plain)
                        }
                        .padding(10)
                        .background(Palette.card)
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                        .overlay(RoundedRectangle(cornerRadius: 10).stroke(Palette.hairline, lineWidth: 1))
                    }
                }
            }
            if coverLines.count < 4 {
                HStack {
                    TextField("THE STYLE REPORT", text: $newCoverLine)
                        .font(Serif.body(15, weight: .semibold))
                        .textInputAutocapitalization(.characters)
                        .autocorrectionDisabled()
                        .padding(12)
                        .background(Palette.card)
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                        .overlay(RoundedRectangle(cornerRadius: 10).stroke(Palette.hairline, lineWidth: 1))
                    Button(action: {
                        let cleaned = newCoverLine.trimmingCharacters(in: .whitespaces).uppercased()
                        if !cleaned.isEmpty {
                            Haptic.tap()
                            coverLines.append(cleaned)
                            newCoverLine = ""
                        }
                    }) {
                        Image(systemName: "plus")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundStyle(.white)
                            .frame(width: 40, height: 40)
                            .background(Palette.ink)
                            .clipShape(Circle())
                    }
                    .disabled(newCoverLine.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
        }
    }

    private func fieldGroup<Content: View>(label: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Eyebrow(text: label)
            content()
                .padding(12)
                .background(Palette.card)
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(Palette.hairline, lineWidth: 1))
        }
    }
}
