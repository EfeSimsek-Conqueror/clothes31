import SwiftUI

struct JournalView: View {
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                Text("Journal")
                    .font(Serif.display(40))
                    .foregroundStyle(Palette.ink)

                TanCard {
                    VStack(alignment: .leading, spacing: 10) {
                        Eyebrow(text: "THE SUNDAY LETTER")
                        Text(Seeds.letter.weekLabel)
                            .font(Serif.display(20)).foregroundStyle(Palette.ink)
                        Text(Seeds.letter.body)
                            .font(Serif.italic(16))
                            .foregroundStyle(Palette.ink)
                            .fixedSize(horizontal: false, vertical: true)
                        HStack {
                            Spacer()
                            Text("Read on →")
                                .font(.system(size: 11, weight: .semibold)).tracking(1.6)
                                .foregroundStyle(Palette.bronze)
                        }
                    }
                }

                VStack(spacing: 0) {
                    Hairline()
                    ForEach(Seeds.journal) { o in
                        HStack(spacing: 14) {
                            RoundedRectangle(cornerRadius: 2)
                                .fill(o.swatch)
                                .frame(width: 34, height: 42)
                            VStack(alignment: .leading, spacing: 3) {
                                Text(o.title).font(Serif.body(15, weight: .medium)).foregroundStyle(Palette.ink)
                                Text(o.weekday).font(.system(size: 10, weight: .semibold)).tracking(1.5)
                                    .foregroundStyle(Palette.muted)
                            }
                            Spacer()
                            Text(String(format: "%.1f", o.score))
                                .font(Serif.display(22)).foregroundStyle(Palette.bronze)
                        }
                        .padding(.vertical, 14)
                        Hairline()
                    }
                }
            }
            .padding(22)
        }
        .background(Palette.paper.ignoresSafeArea())
    }
}
