package com.utkudemir.cue.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.utkudemir.cue.shared.CueDemoContent
import com.utkudemir.cue.shared.core.RealDebridErrorMessages
import com.utkudemir.cue.shared.domain.model.AccountStatus
import com.utkudemir.cue.shared.domain.model.AuthPollResult
import com.utkudemir.cue.shared.domain.model.DeviceAuthSession
import com.utkudemir.cue.shared.domain.model.ReminderConfig
import com.utkudemir.cue.shared.domain.model.ScheduledReminder
import com.utkudemir.cue.shared.domain.repository.AccountRepository
import com.utkudemir.cue.shared.domain.repository.AuthRepository
import com.utkudemir.cue.shared.domain.repository.ReminderRepository
import com.utkudemir.cue.shared.domain.usecase.ExportDiagnosticsUseCase
import com.utkudemir.cue.shared.domain.usecase.PreviewDiagnosticsUseCase
import com.utkudemir.cue.shared.localization.AppLocalization
import com.utkudemir.cue.shared.platform.NotificationScheduler
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val isStarting: Boolean = false,
    val isPolling: Boolean = false,
    val session: DeviceAuthSession? = null
)

enum class NotificationPermissionUiState {
    Unknown,
    Granted,
    Disabled
}

data class CueUiState(
    val checkingSession: Boolean = true,
    val isAuthenticated: Boolean = false,
    val isDemoMode: Boolean = false,
    val isRefreshingAccount: Boolean = false,
    val isExportingDiagnostics: Boolean = false,
    val isLoadingDiagnosticsPreview: Boolean = false,
    val diagnosticsPreview: String? = null,
    val onboarding: OnboardingUiState = OnboardingUiState(),
    val accountStatus: AccountStatus? = null,
    val scheduledReminders: List<ScheduledReminder> = emptyList(),
    val reminderConfig: ReminderConfig = ReminderConfig(),
    val notificationPermissionState: NotificationPermissionUiState = NotificationPermissionUiState.Unknown,
    val infoMessage: String? = null,
    val errorMessage: String? = null
)

sealed interface CueEvent {
    data class OpenUrl(val url: String) : CueEvent
    data class ShareDiagnostics(val displayName: String, val location: String) : CueEvent
    data object RequestNotificationPermission : CueEvent
}

class CueViewModel(
    private val authRepository: AuthRepository,
    private val accountRepository: AccountRepository,
    private val reminderRepository: ReminderRepository,
    private val notificationScheduler: NotificationScheduler,
    private val exportDiagnosticsUseCase: ExportDiagnosticsUseCase,
    private val previewDiagnosticsUseCase: PreviewDiagnosticsUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(CueUiState())
    val uiState = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<CueEvent>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    private var authPollingJob: Job? = null

    init {
        viewModelScope.launch {
            val reminderConfig = reminderRepository.getConfig()
            val notificationsEnabled = notificationScheduler.areNotificationsEnabled()
            _uiState.update {
                it.copy(
                    reminderConfig = reminderConfig,
                    notificationPermissionState = notificationsEnabled.toPermissionState()
                )
            }
            val authenticated = authRepository.isAuthenticated()
            _uiState.update {
                it.copy(
                    checkingSession = false,
                    isAuthenticated = authenticated
                )
            }
            if (authenticated) {
                refreshAccountStatus()
            }
        }
    }

    fun refreshNotificationPermissionState() {
        viewModelScope.launch {
            val notificationsEnabled = notificationScheduler.areNotificationsEnabled()
            _uiState.update {
                it.copy(notificationPermissionState = notificationsEnabled.toPermissionState())
            }
        }
    }

    fun startAuthorization() {
        if (_uiState.value.isDemoMode) {
            leaveDemo(showMessage = false)
        }
        if (_uiState.value.onboarding.isStarting || _uiState.value.onboarding.isPolling) return
        viewModelScope.launch {
            _uiState.update { it.copy(errorMessage = null, onboarding = OnboardingUiState(isStarting = true)) }
            runCatching { authRepository.startAuthorization() }
                .onSuccess { session ->
                    _uiState.update {
                        it.copy(
                            onboarding = OnboardingUiState(
                                isStarting = false,
                                isPolling = true,
                                session = session
                            )
                        )
                    }
                    _events.tryEmit(
                        CueEvent.OpenUrl(
                            session.directVerificationUrl ?: session.verificationUrl
                        )
                    )
                    beginPolling(session)
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            onboarding = OnboardingUiState(),
                            errorMessage = throwable.presentableMessage("messages.unable_start_authorization"),
                            infoMessage = null
                        )
                    }
                }
        }
    }

    fun cancelAuthorization() {
        authPollingJob?.cancel()
        _uiState.update { it.copy(onboarding = OnboardingUiState()) }
    }

    fun refreshAccountStatus() {
        if (_uiState.value.isDemoMode) {
            viewModelScope.launch {
                val notificationsEnabled = notificationScheduler.areNotificationsEnabled()
                applyDemoState(
                    notificationPermissionState = notificationsEnabled.toPermissionState(),
                    infoMessage = "Demo refreshed."
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshingAccount = true, errorMessage = null) }
            accountRepository.refreshAccountStatus()
                .onSuccess { status ->
                    _uiState.update {
                        it.copy(
                            isAuthenticated = true,
                            isRefreshingAccount = false,
                            accountStatus = status
                        )
                    }
                    syncReminders(status)
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isRefreshingAccount = false,
                            errorMessage = throwable.presentableMessage("messages.unable_refresh_account"),
                            infoMessage = null
                        )
                    }
                }
        }
    }

    fun onNotificationPermissionResult(granted: Boolean) {
        viewModelScope.launch {
            val notificationsEnabled = notificationScheduler.areNotificationsEnabled()
            _uiState.update { it.copy(notificationPermissionState = notificationsEnabled.toPermissionState()) }
            if (!granted) {
                showInfoKey("messages.notifications_still_disabled")
                _uiState.value.accountStatus?.let { refreshReminderPreview(it) }
                return@launch
            }
            showInfoKey("messages.notifications_enabled")
            _uiState.value.accountStatus?.let { syncReminders(it) }
        }
    }

    fun requestNotificationPermission() {
        if (_uiState.value.isDemoMode) {
            showInfo("Notification prompts are disabled in demo mode.")
            return
        }
        viewModelScope.launch {
            val notificationsEnabled = notificationScheduler.areNotificationsEnabled()
            _uiState.update { it.copy(notificationPermissionState = notificationsEnabled.toPermissionState()) }
            if (notificationsEnabled) {
                showInfoKey("onboarding.notifications_already_enabled")
                _uiState.value.accountStatus?.let { syncReminders(it) }
            } else {
                _events.emit(CueEvent.RequestNotificationPermission)
            }
        }
    }

    fun setReminderEnabled(enabled: Boolean) {
        mutateReminderConfig { copy(enabled = enabled) }
    }

    fun toggleReminderDay(day: Int) {
        if (day !in setOf(1, 3, 7)) return
        mutateReminderConfig {
            val nextDays = if (daysBefore.contains(day)) daysBefore - day else daysBefore + day
            copy(daysBefore = nextDays)
        }
    }

    fun setNotifyOnExpiry(enabled: Boolean) {
        mutateReminderConfig { copy(notifyOnExpiry = enabled) }
    }

    fun setNotifyAfterExpiry(enabled: Boolean) {
        mutateReminderConfig { copy(notifyAfterExpiry = enabled) }
    }

    fun exportDiagnostics() {
        if (_uiState.value.isDemoMode) {
            showInfo("Diagnostics export is disabled in demo mode.")
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isExportingDiagnostics = true) }
            exportDiagnosticsUseCase()
                .onSuccess { exportedFile ->
                    showInfo(AppLocalization.text("messages.diagnostics_exported", exportedFile.location))
                    _events.emit(
                        CueEvent.ShareDiagnostics(
                            displayName = exportedFile.displayName,
                            location = exportedFile.location
                        )
                    )
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            errorMessage = throwable.presentableMessage("messages.unable_export_diagnostics"),
                            infoMessage = null
                        )
                    }
                }
            _uiState.update { it.copy(isExportingDiagnostics = false) }
        }
    }

    fun loadDiagnosticsPreview() {
        if (_uiState.value.isLoadingDiagnosticsPreview) return
        if (_uiState.value.isDemoMode) {
            _uiState.update {
                it.copy(
                    diagnosticsPreview = CueDemoContent.diagnosticsPreview(),
                    errorMessage = null
                )
            }
            return
        }
        _uiState.update { it.copy(isLoadingDiagnosticsPreview = true) }
        viewModelScope.launch {
            previewDiagnosticsUseCase()
                .onSuccess { preview ->
                    _uiState.update {
                        it.copy(
                            isLoadingDiagnosticsPreview = false,
                            diagnosticsPreview = preview
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoadingDiagnosticsPreview = false,
                            errorMessage = throwable.presentableMessage("messages.unable_load_diagnostics_preview"),
                            infoMessage = null
                        )
                    }
                }
        }
    }

    fun startDemo() {
        authPollingJob?.cancel()
        viewModelScope.launch {
            val notificationsEnabled = notificationScheduler.areNotificationsEnabled()
            applyDemoState(
                notificationPermissionState = notificationsEnabled.toPermissionState(),
                infoMessage = "Demo mode is active. No live account changes will be made."
            )
        }
    }

    fun enterScreenshotOnboarding() {
        authPollingJob?.cancel()
        viewModelScope.launch {
            val notificationsEnabled = notificationScheduler.areNotificationsEnabled()
            _uiState.value = CueUiState(
                checkingSession = false,
                reminderConfig = ReminderConfig(),
                notificationPermissionState = notificationsEnabled.toPermissionState()
            )
        }
    }

    fun disconnect() {
        if (_uiState.value.isDemoMode) {
            leaveDemo(showMessage = true)
            return
        }
        authPollingJob?.cancel()
        viewModelScope.launch {
            authRepository.disconnect()
            reminderRepository.cancelReminders()
            _uiState.value = CueUiState(
                checkingSession = false,
                reminderConfig = reminderRepository.getConfig(),
                infoMessage = AppLocalization.text("messages.disconnected")
            )
        }
    }

    private fun beginPolling(session: DeviceAuthSession) {
        authPollingJob?.cancel()
        authPollingJob = viewModelScope.launch {
            while (true) {
                when (val result = authRepository.pollAuthorization()) {
                    AuthPollResult.Pending -> delay(session.pollIntervalSeconds * 1000)
                    is AuthPollResult.Authorized -> {
                        _uiState.update {
                            it.copy(
                                isAuthenticated = true,
                                onboarding = OnboardingUiState(),
                                infoMessage = AppLocalization.text("messages.authorization_completed"),
                                errorMessage = null
                            )
                        }
                        refreshAccountStatus()
                        return@launch
                    }
                    AuthPollResult.Expired -> {
                        _uiState.update {
                            it.copy(
                                onboarding = OnboardingUiState(),
                                errorMessage = AppLocalization.text("messages.authorization_expired"),
                                infoMessage = null
                            )
                        }
                        return@launch
                    }
                    AuthPollResult.Denied -> {
                        _uiState.update {
                            it.copy(
                                onboarding = OnboardingUiState(),
                                errorMessage = AppLocalization.text("messages.authorization_denied"),
                                infoMessage = null
                            )
                        }
                        return@launch
                    }
                    is AuthPollResult.Failure -> {
                        _uiState.update {
                            it.copy(
                                onboarding = OnboardingUiState(),
                                errorMessage = result.message,
                                infoMessage = null
                            )
                        }
                        return@launch
                    }
                }
            }
        }
    }

    private fun mutateReminderConfig(transform: ReminderConfig.() -> ReminderConfig) {
        if (_uiState.value.isDemoMode) {
            val updated = _uiState.value.reminderConfig.transform()
            _uiState.update {
                it.copy(
                    reminderConfig = updated,
                    scheduledReminders = CueDemoContent.scheduledReminders(updated),
                    errorMessage = null,
                    infoMessage = "Demo reminder preview updated."
                )
            }
            return
        }
        viewModelScope.launch {
            val updated = _uiState.value.reminderConfig.transform()
            reminderRepository.updateConfig(updated)
            _uiState.update { it.copy(reminderConfig = updated, errorMessage = null) }
            _uiState.value.accountStatus?.let { syncReminders(it) }
        }
    }

    private suspend fun syncReminders(status: AccountStatus) {
        if (_uiState.value.isDemoMode) {
            val notificationsEnabled = notificationScheduler.areNotificationsEnabled()
            _uiState.update {
                it.copy(
                    reminderConfig = CueDemoContent.reminderConfig(),
                    scheduledReminders = CueDemoContent.scheduledReminders(),
                    notificationPermissionState = notificationsEnabled.toPermissionState()
                )
            }
            return
        }
        val config = reminderRepository.getConfig()
        val preview = reminderRepository.previewReminders(status)
        val notificationsEnabled = notificationScheduler.areNotificationsEnabled()
        _uiState.update {
            it.copy(
                reminderConfig = config,
                scheduledReminders = preview,
                notificationPermissionState = notificationsEnabled.toPermissionState()
            )
        }
        if (!config.enabled) {
            reminderRepository.cancelReminders()
            return
        }
        if (!notificationsEnabled) {
            reminderRepository.cancelReminders()
            return
        }
        val reminders = reminderRepository.scheduleReminders(status)
        _uiState.update { it.copy(scheduledReminders = reminders) }
    }

    private suspend fun refreshReminderPreview(status: AccountStatus) {
        val config = reminderRepository.getConfig()
        val preview = reminderRepository.previewReminders(status)
        val notificationsEnabled = notificationScheduler.areNotificationsEnabled()
        _uiState.update {
            it.copy(
                reminderConfig = config,
                scheduledReminders = preview,
                notificationPermissionState = notificationsEnabled.toPermissionState()
            )
        }
    }

    private fun Throwable.presentableMessage(fallback: String): String {
        val details = generateSequence(this) { it.cause }
            .mapNotNull { it.message?.trim()?.takeIf(String::isNotEmpty) }
            .distinct()
            .joinToString(separator = " | ")
        return RealDebridErrorMessages.presentableMessage(details, AppLocalization.text(fallback))
    }

    private fun showInfo(message: String) {
        _uiState.update {
            it.copy(
                infoMessage = message,
                errorMessage = null
            )
        }
    }

    private fun showInfoKey(key: String) {
        showInfo(AppLocalization.text(key))
    }

    private fun applyDemoState(
        notificationPermissionState: NotificationPermissionUiState,
        infoMessage: String? = null
    ) {
        _uiState.value = CueUiState(
            checkingSession = false,
            isAuthenticated = true,
            isDemoMode = true,
            accountStatus = CueDemoContent.accountStatus(),
            reminderConfig = CueDemoContent.reminderConfig(),
            scheduledReminders = CueDemoContent.scheduledReminders(),
            notificationPermissionState = notificationPermissionState,
            diagnosticsPreview = CueDemoContent.diagnosticsPreview(),
            infoMessage = infoMessage
        )
    }

    private fun leaveDemo(showMessage: Boolean) {
        authPollingJob?.cancel()
        _uiState.value = CueUiState(
            checkingSession = false,
            reminderConfig = ReminderConfig(),
            infoMessage = if (showMessage) "Exited demo mode." else null
        )
    }

    private fun Boolean.toPermissionState(): NotificationPermissionUiState =
        if (this) NotificationPermissionUiState.Granted else NotificationPermissionUiState.Disabled
}

class CueViewModelFactory(
    private val graph: AndroidAppGraph
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CueViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CueViewModel(
                authRepository = graph.authRepository,
                accountRepository = graph.accountRepository,
                reminderRepository = graph.reminderRepository,
                notificationScheduler = graph.notificationScheduler,
                exportDiagnosticsUseCase = graph.exportDiagnosticsUseCase,
                previewDiagnosticsUseCase = graph.previewDiagnosticsUseCase
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
