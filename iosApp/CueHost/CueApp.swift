import SwiftUI

@main
struct CueApp: App {
    @StateObject private var viewModel = IOSAppViewModel()
    private let screenshotScene = CueScreenshotScene.current

    var body: some Scene {
        WindowGroup {
            ContentView(viewModel: viewModel, screenshotScene: screenshotScene)
        }
    }
}
