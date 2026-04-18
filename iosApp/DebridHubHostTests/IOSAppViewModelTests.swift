import XCTest
import Shared

#if canImport(DebridHub)
@testable import DebridHub
#elseif canImport(DebridHubHost)
@testable import DebridHubHost
#endif

final class IOSAppViewModelTests: XCTestCase {
    func testStartAuthorizationSetsSessionState() async {
        let service = FakeIOSAppService(
            startSession: AuthorizationSessionState(
                userCode: "ABCD-1234",
                verificationURL: "https://real-debrid.com/device",
                directVerificationURL: "https://real-debrid.com/device/confirm",
                expiresAt: nil,
                pollIntervalSeconds: 10
            ),
            pollResults: [.pending]
        )
        let viewModel = await MainActor.run {
            IOSAppViewModel(
                service: service,
                notificationPermissionProvider: StubNotificationPermissionStateProvider(state: .unknown),
                settingsOpener: StubSettingsOpener(),
                autoBootstrap: false
            )
        }

        await MainActor.run {
            viewModel.startAuthorization()
        }
        await waitUntil {
            await MainActor.run {
                viewModel.userCode == "ABCD-1234" &&
                    viewModel.verificationURL == "https://real-debrid.com/device" &&
                    viewModel.directVerificationURL == "https://real-debrid.com/device/confirm" &&
                    viewModel.authorizationBrowserTarget?.url.absoluteString == "https://real-debrid.com/device/confirm"
            }
        }

        await MainActor.run {
            XCTAssertEqual("ABCD-1234", viewModel.userCode)
            XCTAssertEqual("https://real-debrid.com/device", viewModel.verificationURL)
            XCTAssertEqual("https://real-debrid.com/device/confirm", viewModel.directVerificationURL)
            XCTAssertEqual(10, viewModel.authorizationPollIntervalSeconds)
            viewModel.cancelAuthorization()
        }
    }

    func testPollAuthorizationDeniedClearsSessionAndSurfacesError() async {
        let service = FakeIOSAppService(
            startSession: AuthorizationSessionState(
                userCode: "ABCD-1234",
                verificationURL: "https://real-debrid.com/device",
                directVerificationURL: nil,
                expiresAt: nil,
                pollIntervalSeconds: 1
            ),
            pollResults: [.denied]
        )
        let viewModel = await MainActor.run {
            IOSAppViewModel(
                service: service,
                notificationPermissionProvider: StubNotificationPermissionStateProvider(state: .unknown),
                settingsOpener: StubSettingsOpener(),
                autoBootstrap: false
            )
        }

        await MainActor.run {
            viewModel.startAuthorization()
        }
        await waitUntil {
            await MainActor.run { viewModel.errorMessage != nil }
        }

        await MainActor.run {
            XCTAssertEqual("Real-Debrid denied the authorization request.", viewModel.errorMessage)
            XCTAssertNil(viewModel.userCode)
            XCTAssertNil(viewModel.verificationURL)
            XCTAssertNil(viewModel.directVerificationURL)
            XCTAssertNil(viewModel.authorizationBrowserTarget)
        }
    }

    func testPollAuthorizationExpiredClearsSessionAndSurfacesError() async {
        let service = FakeIOSAppService(
            startSession: AuthorizationSessionState(
                userCode: "ABCD-1234",
                verificationURL: "https://real-debrid.com/device",
                directVerificationURL: nil,
                expiresAt: nil,
                pollIntervalSeconds: 1
            ),
            pollResults: [.expired]
        )
        let viewModel = await MainActor.run {
            IOSAppViewModel(
                service: service,
                notificationPermissionProvider: StubNotificationPermissionStateProvider(state: .unknown),
                settingsOpener: StubSettingsOpener(),
                autoBootstrap: false
            )
        }

        await MainActor.run {
            viewModel.startAuthorization()
        }
        await waitUntil {
            await MainActor.run { viewModel.errorMessage != nil }
        }

        await MainActor.run {
            XCTAssertEqual("The device authorization session expired.", viewModel.errorMessage)
            XCTAssertNil(viewModel.userCode)
            XCTAssertNil(viewModel.authorizationBrowserTarget)
        }
    }

    func testPollAuthorizationFailureClearsSessionAndSurfacesError() async {
        let service = FakeIOSAppService(
            startSession: AuthorizationSessionState(
                userCode: "ABCD-1234",
                verificationURL: "https://real-debrid.com/device",
                directVerificationURL: nil,
                expiresAt: nil,
                pollIntervalSeconds: 1
            ),
            pollResults: [.failure("Temporary authorization outage")]
        )
        let viewModel = await MainActor.run {
            IOSAppViewModel(
                service: service,
                notificationPermissionProvider: StubNotificationPermissionStateProvider(state: .unknown),
                settingsOpener: StubSettingsOpener(),
                autoBootstrap: false
            )
        }

        await MainActor.run {
            viewModel.startAuthorization()
        }
        await waitUntil {
            await MainActor.run { viewModel.errorMessage != nil }
        }

        await MainActor.run {
            XCTAssertEqual("Temporary authorization outage", viewModel.errorMessage)
            XCTAssertNil(viewModel.userCode)
            XCTAssertNil(viewModel.authorizationBrowserTarget)
        }
    }

    private func waitUntil(
        timeoutNanoseconds: UInt64 = 1_000_000_000,
        condition: @escaping () async -> Bool
    ) async {
        let start = DispatchTime.now().uptimeNanoseconds
        while DispatchTime.now().uptimeNanoseconds - start < timeoutNanoseconds {
            if await condition() {
                return
            }
            try? await Task.sleep(nanoseconds: 10_000_000)
        }
        XCTFail("Timed out waiting for expected state transition")
    }
}

private final class FakeIOSAppService: IOSAppServiceProtocol {
    private let startSession: AuthorizationSessionState
    private var pollResults: [AuthorizationPollState]

    init(
        startSession: AuthorizationSessionState,
        pollResults: [AuthorizationPollState]
    ) {
        self.startSession = startSession
        self.pollResults = pollResults
    }

    func close() {}

    func getReminderConfigSnapshot() async throws -> ReminderConfigSnapshot {
        ReminderConfigSnapshot(
            enabled: true,
            sevenDayReminder: true,
            threeDayReminder: true,
            oneDayReminder: true,
            notifyOnExpiry: true,
            notifyAfterExpiry: false
        )
    }

    func isAuthenticated() async throws -> Bool { false }

    func startAuthorization() async throws -> AuthorizationSessionState {
        startSession
    }

    func pollAuthorization() async throws -> AuthorizationPollState {
        guard !pollResults.isEmpty else { return .pending }
        return pollResults.removeFirst()
    }

    func refreshAccountStatus() async throws -> AccountStatus {
        throw FakeServiceError.unexpectedCall
    }

    func syncReminders() async throws -> Int { 0 }

    func previewReminders() async throws -> [ScheduledReminder] { [] }

    func requestNotificationPermission() async throws -> Bool { false }

    func updateReminderConfigSnapshot(snapshot: ReminderConfigSnapshot) async throws {}

    func disconnect() async throws {}

    func exportDiagnostics() async throws -> ExportedFile {
        ExportedFile(displayName: "diagnostics.json", location: "/tmp/diagnostics.json")
    }

    func previewDiagnostics() async throws -> String { "{}" }
}

private enum FakeServiceError: Error {
    case unexpectedCall
}

private struct StubNotificationPermissionStateProvider: NotificationPermissionStateProviding {
    let state: NotificationPermissionState

    func currentState() async -> NotificationPermissionState {
        state
    }
}

private struct StubSettingsOpener: SettingsOpening {
    func openAppSettings() {}
}
