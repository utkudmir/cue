import Foundation
import Shared
import UIKit
import UserNotifications

struct AuthorizationBrowserTarget: Identifiable, Equatable {
    let url: URL
    var id: String { url.absoluteString }
}

enum NotificationPermissionState {
    case unknown
    case notDetermined
    case granted
    case denied
}

@MainActor
final class IOSAppViewModel: ObservableObject {
    @Published var isCheckingSession = true
    @Published var isAuthenticated = false
    @Published var isRefreshing = false
    @Published var isExporting = false
    @Published var isLoadingDiagnosticsPreview = false
    @Published var isRequestingNotifications = false
    @Published var errorMessage: String?
    @Published var infoMessage: String?
    @Published var diagnosticsPreview: String?
    @Published var userCode: String?
    @Published var verificationURL: String?
    @Published var directVerificationURL: String?
    @Published var authorizationExpiresAt: Kotlinx_datetimeInstant?
    @Published var authorizationPollIntervalSeconds: Int64?
    @Published var authorizationBrowserTarget: AuthorizationBrowserTarget?
    @Published var accountStatus: AccountStatus?
    @Published var scheduledReminders: [ScheduledReminder] = []
    @Published var notificationPermissionState: NotificationPermissionState = .unknown
    @Published var reminderConfig = ReminderConfigSnapshot(
        enabled: true,
        sevenDayReminder: true,
        threeDayReminder: true,
        oneDayReminder: true,
        notifyOnExpiry: true,
        notifyAfterExpiry: false
    )
    private let graph = IosAppGraph(appVersion: "1.0.0")
    private var pollingTask: Task<Void, Never>?

    deinit {
        graph.close()
    }

    init() {
        Task {
            await bootstrap()
        }
    }

    func bootstrap() async {
        await refreshNotificationPermissionState()
        do {
            reminderConfig = try await graph.controller.getReminderConfigSnapshot()
            let authState = try await graph.controller.isAuthenticated()
            isAuthenticated = authState.boolValue
            isCheckingSession = false
            if isAuthenticated {
                await refreshAccount()
            }
        } catch {
            isCheckingSession = false
            showError(error)
        }
    }

    func startAuthorization() {
        pollingTask?.cancel()
        clearMessages()
        clearAuthorizationSession()
        Task {
            do {
                let session = try await graph.controller.startAuthorization()
                userCode = session.userCode
                verificationURL = session.verificationUrl
                directVerificationURL = session.directVerificationUrl
                authorizationExpiresAt = session.expiresAt
                authorizationPollIntervalSeconds = session.pollIntervalSeconds
                if let browserURL = URL(string: session.directVerificationUrl ?? session.verificationUrl) {
                    authorizationBrowserTarget = AuthorizationBrowserTarget(url: browserURL)
                }
                pollAuthorization(interval: session.pollIntervalSeconds)
            } catch {
                if error is CancellationError { return }
                clearAuthorizationSession()
                showError(error)
            }
        }
    }

    func cancelAuthorization() {
        pollingTask?.cancel()
        clearAuthorizationSession()
    }

    func refreshAccount() async {
        isRefreshing = true
        defer { isRefreshing = false }
        errorMessage = nil
        do {
            await refreshNotificationPermissionState()
            reminderConfig = try await graph.controller.getReminderConfigSnapshot()
            accountStatus = try await graph.controller.refreshAccountStatus()
            _ = try await graph.controller.syncReminders()
            scheduledReminders = try await graph.controller.previewReminders()
        } catch {
            showError(error)
        }
    }

    func requestNotifications() {
        Task {
            isRequestingNotifications = true
            defer { isRequestingNotifications = false }
            clearMessages()
            do {
                let permissionResult = try await graph.controller.requestNotificationPermission()
                let granted = permissionResult.boolValue
                await refreshNotificationPermissionState()
                if isAuthenticated {
                    _ = try await graph.controller.syncReminders()
                    scheduledReminders = try await graph.controller.previewReminders()
                }
                if granted {
                    infoMessage = "Notifications enabled."
                } else {
                    infoMessage = "Notifications remain disabled. Open system settings if you want reminder alerts."
                }
            } catch {
                await refreshNotificationPermissionState()
                showError(error)
            }
        }
    }

    func openAppSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(url)
    }

    func exportDiagnostics() {
        Task {
            isExporting = true
            defer { isExporting = false }
            clearMessages()
            do {
                let file = try await graph.controller.exportDiagnostics()
                infoMessage = "Diagnostics exported to \(file.location)"
            } catch {
                showError(error)
            }
        }
    }

    func loadDiagnosticsPreview() {
        guard !isLoadingDiagnosticsPreview else { return }
        Task {
            isLoadingDiagnosticsPreview = true
            defer { isLoadingDiagnosticsPreview = false }
            errorMessage = nil
            do {
                diagnosticsPreview = try await graph.controller.previewDiagnostics()
            } catch {
                showError(error)
            }
        }
    }

    func disconnect() {
        pollingTask?.cancel()
        clearMessages()
        clearAuthorizationSession()
        Task {
            do {
                try await graph.controller.disconnect()
                isAuthenticated = false
                accountStatus = nil
                diagnosticsPreview = nil
                scheduledReminders = []
                reminderConfig = try await graph.controller.getReminderConfigSnapshot()
                clearAuthorizationSession()
                infoMessage = "Disconnected from Real-Debrid."
            } catch {
                showError(error)
            }
        }
    }

    func setReminderEnabled(_ enabled: Bool) {
        updateReminderConfig(reminderConfig.doCopy(
            enabled: enabled,
            sevenDayReminder: reminderConfig.sevenDayReminder,
            threeDayReminder: reminderConfig.threeDayReminder,
            oneDayReminder: reminderConfig.oneDayReminder,
            notifyOnExpiry: reminderConfig.notifyOnExpiry,
            notifyAfterExpiry: reminderConfig.notifyAfterExpiry
        ))
    }

    func toggleReminderDay(_ day: Int) {
        let updated: ReminderConfigSnapshot
        switch day {
        case 7:
            updated = reminderConfig.doCopy(
                enabled: reminderConfig.enabled,
                sevenDayReminder: !reminderConfig.sevenDayReminder,
                threeDayReminder: reminderConfig.threeDayReminder,
                oneDayReminder: reminderConfig.oneDayReminder,
                notifyOnExpiry: reminderConfig.notifyOnExpiry,
                notifyAfterExpiry: reminderConfig.notifyAfterExpiry
            )
        case 3:
            updated = reminderConfig.doCopy(
                enabled: reminderConfig.enabled,
                sevenDayReminder: reminderConfig.sevenDayReminder,
                threeDayReminder: !reminderConfig.threeDayReminder,
                oneDayReminder: reminderConfig.oneDayReminder,
                notifyOnExpiry: reminderConfig.notifyOnExpiry,
                notifyAfterExpiry: reminderConfig.notifyAfterExpiry
            )
        case 1:
            updated = reminderConfig.doCopy(
                enabled: reminderConfig.enabled,
                sevenDayReminder: reminderConfig.sevenDayReminder,
                threeDayReminder: reminderConfig.threeDayReminder,
                oneDayReminder: !reminderConfig.oneDayReminder,
                notifyOnExpiry: reminderConfig.notifyOnExpiry,
                notifyAfterExpiry: reminderConfig.notifyAfterExpiry
            )
        default:
            return
        }
        updateReminderConfig(updated)
    }

    func setNotifyOnExpiry(_ enabled: Bool) {
        updateReminderConfig(reminderConfig.doCopy(
            enabled: reminderConfig.enabled,
            sevenDayReminder: reminderConfig.sevenDayReminder,
            threeDayReminder: reminderConfig.threeDayReminder,
            oneDayReminder: reminderConfig.oneDayReminder,
            notifyOnExpiry: enabled,
            notifyAfterExpiry: reminderConfig.notifyAfterExpiry
        ))
    }

    func setNotifyAfterExpiry(_ enabled: Bool) {
        updateReminderConfig(reminderConfig.doCopy(
            enabled: reminderConfig.enabled,
            sevenDayReminder: reminderConfig.sevenDayReminder,
            threeDayReminder: reminderConfig.threeDayReminder,
            oneDayReminder: reminderConfig.oneDayReminder,
            notifyOnExpiry: reminderConfig.notifyOnExpiry,
            notifyAfterExpiry: enabled
        ))
    }

    private func pollAuthorization(interval: Int64) {
        pollingTask = Task {
            while !Task.isCancelled {
                do {
                    let result = try await graph.controller.pollAuthorization()
                    switch result {
                    case is AuthPollResultPending:
                        try? await Task.sleep(for: .seconds(Double(interval)))
                    case is AuthPollResultAuthorized:
                        isAuthenticated = true
                        clearAuthorizationSession()
                        infoMessage = "Authorization completed."
                        await refreshAccount()
                        return
                    case is AuthPollResultExpired:
                        clearAuthorizationSession()
                        showErrorMessage("The device authorization session expired.")
                        return
                    case is AuthPollResultDenied:
                        clearAuthorizationSession()
                        showErrorMessage("Real-Debrid denied the authorization request.")
                        return
                    case let failure as AuthPollResultFailure:
                        clearAuthorizationSession()
                        showErrorMessage(failure.message)
                        return
                    default:
                        clearAuthorizationSession()
                        showErrorMessage("Unexpected authorization state.")
                        return
                    }
                } catch {
                    if error is CancellationError || Task.isCancelled {
                        return
                    }
                    clearAuthorizationSession()
                    showError(error)
                    return
                }
            }
        }
    }

    private func refreshNotificationPermissionState() async {
        let settings = await UNUserNotificationCenter.current().notificationSettings()
        switch settings.authorizationStatus {
        case .authorized, .provisional, .ephemeral:
            notificationPermissionState = .granted
        case .denied:
            notificationPermissionState = .denied
        case .notDetermined:
            notificationPermissionState = .notDetermined
        @unknown default:
            notificationPermissionState = .unknown
        }
    }

    private func updateReminderConfig(_ updated: ReminderConfigSnapshot) {
        reminderConfig = updated
        Task {
            do {
                errorMessage = nil
                try await graph.controller.updateReminderConfigSnapshot(snapshot: updated)
                if isAuthenticated {
                    await refreshNotificationPermissionState()
                    _ = try await graph.controller.syncReminders()
                    scheduledReminders = try await graph.controller.previewReminders()
                }
            } catch {
                showError(error)
            }
        }
    }

    private func clearAuthorizationSession() {
        userCode = nil
        verificationURL = nil
        directVerificationURL = nil
        authorizationExpiresAt = nil
        authorizationPollIntervalSeconds = nil
        authorizationBrowserTarget = nil
    }

    private func clearMessages() {
        errorMessage = nil
        infoMessage = nil
    }

    private func showError(_ error: Error) {
        infoMessage = nil
        errorMessage = presentableMessage(for: error)
    }

    private func showErrorMessage(_ message: String) {
        infoMessage = nil
        errorMessage = message
    }

    private func presentableMessage(for error: Error) -> String {
        let nsError = error as NSError
        if nsError.domain == NSURLErrorDomain && nsError.code == NSURLErrorSecureConnectionFailed {
            return "Secure connection to Real-Debrid failed. Your network appears to be intercepting or downgrading HTTPS traffic to api.real-debrid.com. Disable captive portals, VPNs, secure web gateways, or TLS inspection, or try a different network."
        }

        let message = nsError.localizedDescription
        if message.localizedCaseInsensitiveContains("tls") ||
            message.localizedCaseInsensitiveContains("ssl") ||
            message.localizedCaseInsensitiveContains("proxy") ||
            message.localizedCaseInsensitiveContains("plaintext connection") ||
            message.localizedCaseInsensitiveContains("wrong version number") ||
            message.localizedCaseInsensitiveContains("protocol version") ||
            message.localizedCaseInsensitiveContains("handshake") ||
            message.localizedCaseInsensitiveContains("middlebox") {
            return "Secure connection to Real-Debrid failed. Your network appears to be intercepting or downgrading HTTPS traffic to api.real-debrid.com. Disable captive portals, VPNs, secure web gateways, or TLS inspection, or try a different network."
        }

        if message.localizedCaseInsensitiveContains("unable to resolve host") ||
            message.localizedCaseInsensitiveContains("failed to connect") ||
            message.localizedCaseInsensitiveContains("network is unreachable") ||
            message.localizedCaseInsensitiveContains("timed out") {
            return "Couldn't reach Real-Debrid. Check your internet connection or try a different network."
        }

        return message
    }
}
