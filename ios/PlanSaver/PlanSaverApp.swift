import SwiftUI
import UIKit

@main
struct PlanSaverApp: App {
    /// Re-read whenever the user changes the text size in iOS Settings while the app is running.
    @State private var systemCategory = UIApplication.shared.preferredContentSizeCategory

    /// On iPhone: at least extra-extra-large (the app is meant to be easy to read), but never smaller than the size the user
    /// chose in iOS Settings. On Mac Catalyst the floor would make the window text about 20% oversized, so the preferred size is used.
    private var textSize: ContentSizeCategory {
        #if targetEnvironment(macCatalyst)
        return ContentSizeCategory(systemCategory) ?? .large
        #else
        if systemCategory > .extraExtraLarge, let bigger = ContentSizeCategory(systemCategory) { return bigger }
        return .extraExtraLarge
        #endif
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(\.sizeCategory, textSize)
                .onReceive(NotificationCenter.default.publisher(for: UIContentSizeCategory.didChangeNotification)) { _ in
                    systemCategory = UIApplication.shared.preferredContentSizeCategory
                }
        }
    }
}
