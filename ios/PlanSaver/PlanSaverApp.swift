import SwiftUI

@main
struct PlanSaverApp: App {
    /// At least extra-extra-large, but never smaller than the size the user chose in iOS Settings.
    private var textSize: ContentSizeCategory {
        let mine = UIApplication.shared.preferredContentSizeCategory
        if mine > .extraExtraLarge, let bigger = ContentSizeCategory(mine) { return bigger }
        return .extraExtraLarge
    }

    var body: some Scene {
        WindowGroup { RootView().environment(\.sizeCategory, textSize) }
    }
}
