import SwiftUI

private let DEPS: [(String, String)] = [
    ("SwiftUI", "Apple Software License · Apple"),
    ("Supabase-swift", "MIT · Supabase Community"),
    ("GoogleSignIn-iOS", "Apache License 2.0 · Google"),
    ("RevenueCat", "MIT · RevenueCat Inc."),
    ("Kingfisher", "MIT · Wei Wang"),
    ("KeychainAccess", "MIT · kishikawakatsumi"),
    ("Swift Charts", "Apple Software License · Apple"),
    ("PDFKit", "Apple Software License · Apple"),
    ("AVFoundation", "Apple Software License · Apple"),
]

struct LicensesView: View {
    var body: some View {
        SubpageScaffold(eyebrow: "LEGAL", title: "Open source licenses") {
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    Text("Fitrater ships with the following open-source libraries — thank you to their authors.")
                        .font(Serif.body(14)).foregroundStyle(Palette.muted)
                        .padding(.bottom, 16)
                    ForEach(DEPS, id: \.0) { (name, license) in
                        VStack(alignment: .leading, spacing: 2) {
                            Text(name).font(Serif.body(15, weight: .semibold)).foregroundStyle(Palette.ink)
                            Text(license).font(Serif.body(13)).foregroundStyle(Palette.muted)
                        }
                        .padding(.vertical, 12)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        Hairline()
                    }
                    Spacer().frame(height: 40)
                }
                .padding(20)
            }
        }
    }
}
