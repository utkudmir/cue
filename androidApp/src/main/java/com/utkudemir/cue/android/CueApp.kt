package com.utkudemir.cue.android

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.utkudemir.cue.shared.CueExternalLinks
import com.utkudemir.cue.shared.CueOpenSourceNotices
import com.utkudemir.cue.shared.domain.model.AccountStatus
import com.utkudemir.cue.shared.domain.model.ReminderConfig
import com.utkudemir.cue.shared.domain.model.ScheduledReminder
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CueApp(
    viewModel: CueViewModel,
    screenshotScene: CueScreenshotScene? = null,
    onOpenUrl: (String) -> Unit,
    onShareDiagnostics: (String, String) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenNotificationSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var isTrustCenterOpen by rememberSaveable { mutableStateOf(screenshotScene == CueScreenshotScene.DEMO_TRUST) }
    var hasAppliedScreenshotScene by rememberSaveable { mutableStateOf(false) }
    val isScreenshotCapture = screenshotScene != null

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is CueEvent.OpenUrl -> onOpenUrl(event.url)
                is CueEvent.ShareDiagnostics -> onShareDiagnostics(event.displayName, event.location)
                CueEvent.RequestNotificationPermission -> onRequestNotificationPermission()
            }
        }
    }

    LaunchedEffect(isTrustCenterOpen) {
        if (isTrustCenterOpen) {
            viewModel.loadDiagnosticsPreview()
        }
    }

    LaunchedEffect(screenshotScene, uiState.checkingSession, hasAppliedScreenshotScene) {
        if (!hasAppliedScreenshotScene && !uiState.checkingSession) {
            when (screenshotScene) {
                CueScreenshotScene.ONBOARDING -> viewModel.enterScreenshotOnboarding()
                CueScreenshotScene.DEMO_HOME -> viewModel.startDemo()
                CueScreenshotScene.DEMO_TRUST -> {
                    viewModel.startDemo()
                    isTrustCenterOpen = true
                }
                null -> Unit
            }
            hasAppliedScreenshotScene = true
        }
    }

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            if (isTrustCenterOpen) {
                                localizedText("common.trust_center")
                            } else {
                                localizedText("common.app_name")
                            }
                        )
                    },
                    navigationIcon = {
                        if (isTrustCenterOpen) {
                            TextButton(onClick = { isTrustCenterOpen = false }) {
                                Text(localizedText("common.back"))
                            }
                        }
                    },
                    actions = {
                        if (!isTrustCenterOpen) {
                            TextButton(onClick = { isTrustCenterOpen = true }) {
                                Text(localizedText("common.trust_center"))
                            }
                        }
                    }
                )
            }
        ) { padding ->
            when {
                isTrustCenterOpen -> {
                    TrustCenterScreen(
                        modifier = Modifier.padding(padding),
                        uiState = uiState,
                        isScreenshotCapture = isScreenshotCapture,
                        onOpenUrl = onOpenUrl,
                        onRefreshPreview = viewModel::loadDiagnosticsPreview,
                        onExportDiagnostics = viewModel::exportDiagnostics
                    )
                }
                uiState.checkingSession -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                uiState.isAuthenticated -> {
                    HomeScreen(
                        modifier = Modifier.padding(padding),
                        uiState = uiState,
                        isScreenshotCapture = isScreenshotCapture,
                        onRefresh = viewModel::refreshAccountStatus,
                        onDisconnect = viewModel::disconnect,
                        onRequestNotifications = viewModel::requestNotificationPermission,
                        onOpenNotificationSettings = onOpenNotificationSettings,
                        onReminderEnabledChanged = viewModel::setReminderEnabled,
                        onReminderDayToggled = viewModel::toggleReminderDay,
                        onNotifyOnExpiryChanged = viewModel::setNotifyOnExpiry,
                        onNotifyAfterExpiryChanged = viewModel::setNotifyAfterExpiry
                    )
                }
                else -> {
                    OnboardingScreen(
                        modifier = Modifier.padding(padding),
                        uiState = uiState,
                        isScreenshotCapture = isScreenshotCapture,
                        onConnect = viewModel::startAuthorization,
                        onStartDemo = viewModel::startDemo,
                        onCancel = viewModel::cancelAuthorization,
                        onOpenAuthorizationPage = onOpenUrl,
                        onRequestNotifications = viewModel::requestNotificationPermission,
                        onOpenNotificationSettings = onOpenNotificationSettings
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingScreen(
    modifier: Modifier,
    uiState: CueUiState,
    isScreenshotCapture: Boolean,
    onConnect: () -> Unit,
    onStartDemo: () -> Unit,
    onCancel: () -> Unit,
    onOpenAuthorizationPage: (String) -> Unit,
    onRequestNotifications: () -> Unit,
    onOpenNotificationSettings: () -> Unit
    ) {
        val clipboard = LocalClipboard.current
        val coroutineScope = rememberCoroutineScope()

        Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (!isScreenshotCapture) uiState.infoMessage?.let {
            MessageBanner(
                message = it,
                isError = false
            )
        }

        Text(
            localizedText("onboarding.hero_title"),
            style = MaterialTheme.typography.headlineSmall
        )
        Text(localizedText("onboarding.hero_body"))

        SectionCard(title = localizedText("onboarding.before_connect_title")) {
            Text(localizedText("onboarding.before_connect_step_1"))
            Text(localizedText("onboarding.before_connect_step_2"))
            Text(localizedText("onboarding.before_connect_step_3"))
            Text(localizedText("onboarding.before_connect_step_4"))
            Text(localizedText("onboarding.before_connect_step_5"))
        }

        if (!isScreenshotCapture) uiState.errorMessage?.let {
            MessageBanner(message = it, isError = true)
        }

        val session = uiState.onboarding.session
        if (session != null) {
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(localizedText("onboarding.finish_authorization_title"), fontWeight = FontWeight.SemiBold)
                    Text(localizedText("onboarding.enter_code"))
                    SelectionContainer {
                        Text(session.userCode, style = MaterialTheme.typography.headlineSmall)
                    }
                    Text(localizedText("onboarding.verification_url", session.verificationUrl))
                    session.directVerificationUrl?.let { directUrl ->
                        Text(localizedText("onboarding.direct_verification_url", directUrl))
                    }
                    Text(
                        localizedText(
                            "onboarding.polling_until",
                            formatIntegerForLocale(session.pollIntervalSeconds.toInt()),
                            formatInstantForLocale(session.expiresAt)
                        )
                    )
                    Text(localizedText("onboarding.waiting_for_approval"))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    clipboard.setClipEntry(
                                        ClipEntry(ClipData.newPlainText("Cue Auth Code", session.userCode))
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(localizedText("common.copy_code"))
                        }
                        OutlinedButton(
                            onClick = { onOpenAuthorizationPage(session.directVerificationUrl ?: session.verificationUrl) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(localizedText("common.open_authorization_page"))
                        }
                    }
                    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                        Text(localizedText("common.cancel_authorization"))
                    }
                }
            }
        }

        if (uiState.errorMessage != null && session == null) {
            OutlinedButton(
                onClick = onConnect,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(localizedText("common.start_again"))
            }
        }

        Button(
            onClick = onConnect,
            enabled = !uiState.onboarding.isStarting && !uiState.onboarding.isPolling,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (uiState.onboarding.isStarting) {
                    localizedText("common.starting")
                } else {
                    localizedText("common.connect_real_debrid")
                }
            )
        }

        OutlinedButton(onClick = onStartDemo, modifier = Modifier.fillMaxWidth()) {
            Text("Try Demo")
        }

        Text(
            text = "Cue is independent and not affiliated with Real-Debrid.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        when (uiState.notificationPermissionState) {
            NotificationPermissionUiState.Granted -> {
                Text(
                    localizedText("onboarding.notifications_already_enabled"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (uiState.isDemoMode) {
                    Text(
                        text = "Demo mode shows notification permission only; prompts stay disabled.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            NotificationPermissionUiState.Disabled,
            NotificationPermissionUiState.Unknown -> {
                OutlinedButton(
                    onClick = onRequestNotifications,
                    enabled = !uiState.isDemoMode,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (uiState.isDemoMode) "Demo Only" else localizedText("common.enable_notifications"))
                }
                if (uiState.notificationPermissionState == NotificationPermissionUiState.Disabled) {
                    OutlinedButton(
                        onClick = onOpenNotificationSettings,
                        enabled = !uiState.isDemoMode,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (uiState.isDemoMode) "Demo Only" else localizedText("common.open_notification_settings"))
                    }
                    Text(
                        localizedText("onboarding.notifications_disabled_help"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (uiState.isDemoMode) {
                        Text(
                            text = "Demo mode shows notification permission only; prompts stay disabled.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text(
                        localizedText("onboarding.notifications_later"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (uiState.isDemoMode) {
                        Text(
                            text = "Demo mode shows notification permission only; prompts stay disabled.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    modifier: Modifier,
    uiState: CueUiState,
    isScreenshotCapture: Boolean,
    onRefresh: () -> Unit,
    onDisconnect: () -> Unit,
    onRequestNotifications: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onReminderEnabledChanged: (Boolean) -> Unit,
    onReminderDayToggled: (Int) -> Unit,
    onNotifyOnExpiryChanged: (Boolean) -> Unit,
    onNotifyAfterExpiryChanged: (Boolean) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (uiState.isDemoMode && !isScreenshotCapture) {
            MessageBanner(
                message = "Demo mode is active. Notifications and exports stay disabled.",
                isError = false
            )
        }

        if (uiState.isDemoMode) {
            MessageBanner(
                message = "Notification schedule below is a preview in demo mode.",
                isError = false
            )
        }

        if (!isScreenshotCapture) uiState.infoMessage?.let {
            MessageBanner(message = it, isError = false)
        }

        if (!isScreenshotCapture) uiState.errorMessage?.let {
            MessageBanner(message = it, isError = true)
        }

        AccountCard(
            accountStatus = uiState.accountStatus,
            isRefreshing = uiState.isRefreshingAccount
        )

        ReminderSettingsCard(
            config = uiState.reminderConfig,
            onReminderEnabledChanged = onReminderEnabledChanged,
            onReminderDayToggled = onReminderDayToggled,
            onNotifyOnExpiryChanged = onNotifyOnExpiryChanged,
            onNotifyAfterExpiryChanged = onNotifyAfterExpiryChanged
        )

        ReminderScheduleCard(
            accountStatus = uiState.accountStatus,
            config = uiState.reminderConfig,
            reminders = uiState.scheduledReminders,
            notificationPermissionState = uiState.notificationPermissionState
        )

        SectionCard(title = localizedText("common.actions")) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onRefresh, modifier = Modifier.weight(1f)) {
                    Text(localizedText("common.refresh_status"))
                }
                OutlinedButton(
                    onClick = onRequestNotifications,
                    enabled = !uiState.isDemoMode,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (uiState.isDemoMode) "Demo Only" else localizedText("common.notifications"))
                }
            }

            if (uiState.notificationPermissionState == NotificationPermissionUiState.Disabled) {
                OutlinedButton(
                    onClick = onOpenNotificationSettings,
                    enabled = !uiState.isDemoMode,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (uiState.isDemoMode) "Demo Only" else localizedText("common.open_notification_settings"))
                }
            }

            OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) {
                Text(if (uiState.isDemoMode) "End Demo" else localizedText("common.disconnect"))
            }
        }
    }
}

@Composable
private fun TrustCenterScreen(
    modifier: Modifier,
    uiState: CueUiState,
    isScreenshotCapture: Boolean,
    onOpenUrl: (String) -> Unit,
    onRefreshPreview: () -> Unit,
    onExportDiagnostics: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (!isScreenshotCapture) uiState.infoMessage?.let {
            MessageBanner(message = it, isError = false)
        }

        if (!isScreenshotCapture) uiState.errorMessage?.let {
            MessageBanner(message = it, isError = true)
        }

        Text(
            localizedText("trust_center.hero_title"),
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            localizedText("trust_center.hero_body"),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SectionCard(title = localizedText("trust_center.privacy_title")) {
            Text(localizedText("trust_center.privacy_line_1"))
            Text(localizedText("trust_center.privacy_line_2"))
            Text(localizedText("trust_center.privacy_line_3"))
            Text(localizedText("trust_center.privacy_line_4"))
        }

        SectionCard(title = localizedText("trust_center.security_title")) {
            Text(localizedText("trust_center.security_line_1"))
            Text(localizedText("trust_center.security_line_2_android"))
            Text(localizedText("trust_center.security_line_2_ios"))
            Text(localizedText("trust_center.security_line_3"))
        }

        SectionCard(title = localizedText("trust_center.diagnostics_title")) {
            Text(localizedText("trust_center.diagnostics_line_1"))
            Text(localizedText("trust_center.diagnostics_line_2"))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onRefreshPreview,
                    enabled = !uiState.isLoadingDiagnosticsPreview,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        if (uiState.isLoadingDiagnosticsPreview) {
                            localizedText("common.loading")
                        } else {
                            localizedText("common.refresh_preview")
                        }
                    )
                }
                Button(
                    onClick = onExportDiagnostics,
                    enabled = !uiState.isExportingDiagnostics && !uiState.isDemoMode,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        if (uiState.isDemoMode) {
                            "Export Disabled"
                        } else if (uiState.isExportingDiagnostics) {
                            localizedText("common.exporting")
                        } else {
                            localizedText("common.export_json")
                        }
                    )
                }
            }

            if (uiState.isLoadingDiagnosticsPreview && uiState.diagnosticsPreview == null) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                SelectionContainer {
                    Text(
                        text = uiState.diagnosticsPreview ?: localizedText("trust_center.preview_unavailable"),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        SectionCard(title = localizedText("trust_center.about_title")) {
            Text(localizedText("trust_center.about_line_1"))
            Text(localizedText("trust_center.about_line_2"))
            Text(localizedText("trust_center.about_line_3"))
        }

        SectionCard(title = "Independence") {
            Text("Cue is an independent companion app for Real-Debrid renewal reminders.")
            Text("Cue is not affiliated with, endorsed by, or operated by Real-Debrid.")
        }

        SectionCard(title = "Links") {
            OutlinedButton(onClick = { onOpenUrl(CueExternalLinks.privacyPolicyUrl) }, modifier = Modifier.fillMaxWidth()) {
                Text("Open Privacy Policy")
            }
            OutlinedButton(onClick = { onOpenUrl(CueExternalLinks.supportUrl) }, modifier = Modifier.fillMaxWidth()) {
                Text("Open Support")
            }
            OutlinedButton(onClick = { onOpenUrl(CueExternalLinks.reviewGuideUrl) }, modifier = Modifier.fillMaxWidth()) {
                Text("Open Review Guide")
            }
            OutlinedButton(onClick = { onOpenUrl(CueExternalLinks.sourceRepositoryUrl) }, modifier = Modifier.fillMaxWidth()) {
                Text("View Source on GitHub")
            }
        }

        SectionCard(title = "Open Source Licenses") {
            Text(CueOpenSourceNotices.summary)
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun AccountCard(accountStatus: AccountStatus?, isRefreshing: Boolean) {
    SectionCard(title = localizedText("account.section_title")) {
        when {
            isRefreshing && accountStatus == null -> {
                CircularProgressIndicator()
            }
            accountStatus == null -> {
                Text(localizedText("account.no_data"))
            }
            else -> {
                Text(localizedText("account.username", accountStatus.username ?: localizedText("common.unknown")))
                Text(
                    localizedText(
                        "account.premium",
                        if (accountStatus.isPremium) localizedText("common.active") else localizedText("common.inactive")
                    )
                )
                Text(localizedText("account.state", localizedExpiryState(accountStatus.expiryState.name)))
                Text(
                    localizedText(
                        "account.days_remaining",
                        accountStatus.remainingDays?.let(::formatIntegerForLocale) ?: localizedText("common.unknown")
                    )
                )
                Text(
                    localizedText(
                        "account.expires",
                        accountStatus.expiration?.let(::formatInstantForLocale) ?: localizedText("common.unknown")
                    )
                )
                Text(localizedText("account.last_checked", formatInstantForLocale(accountStatus.lastCheckedAt)))
            }
        }
    }
}

@Composable
private fun ReminderSettingsCard(
    config: ReminderConfig,
    onReminderEnabledChanged: (Boolean) -> Unit,
    onReminderDayToggled: (Int) -> Unit,
    onNotifyOnExpiryChanged: (Boolean) -> Unit,
    onNotifyAfterExpiryChanged: (Boolean) -> Unit
) {
    SectionCard(title = localizedText("reminders.settings_title")) {
        ToggleRow(localizedText("reminders.enable"), config.enabled, onReminderEnabledChanged)
        Text(localizedText("reminders.days_before_expiry"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(7, 3, 1).forEach { day ->
                FilterChip(
                    onClick = { onReminderDayToggled(day) },
                    selected = config.daysBefore.contains(day),
                    label = { Text(localizedPlural("reminders.day_count", day, formatIntegerForLocale(day))) }
                )
            }
        }
        Text(localizedText("reminders.selected", formatSelectedReminderDays(config.daysBefore.sorted())))
        ToggleRow(localizedText("reminders.notify_on_expiry"), config.notifyOnExpiry, onNotifyOnExpiryChanged)
        ToggleRow(localizedText("reminders.notify_after_expiry"), config.notifyAfterExpiry, onNotifyAfterExpiryChanged)
    }
}

@Composable
private fun ReminderScheduleCard(
    accountStatus: AccountStatus?,
    config: ReminderConfig,
    reminders: List<ScheduledReminder>,
    notificationPermissionState: NotificationPermissionUiState
) {
    SectionCard(title = localizedText("reminders.schedule_title")) {
        when {
            accountStatus?.expiration == null -> {
                Text(localizedText("reminders.refresh_to_preview"))
            }
            !config.enabled -> {
                Text(localizedText("reminders.turned_off"))
            }
            notificationPermissionState == NotificationPermissionUiState.Disabled -> {
                Text(localizedText("reminders.notifications_disabled"))
                if (reminders.isNotEmpty()) {
                    Text(localizedText("reminders.planned_times"))
                    reminders.forEach { reminder ->
                        Text(localizedText("reminders.scheduled_item", formatInstantForLocale(reminder.fireAt), reminder.message))
                    }
                }
            }
            reminders.isEmpty() -> {
                Text(localizedText("reminders.no_future_planned"))
            }
            else -> {
                Text(localizedText("reminders.local_schedule_intro"))
                reminders.forEach { reminder ->
                    Text(localizedText("reminders.scheduled_item", formatInstantForLocale(reminder.fireAt), reminder.message))
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun MessageBanner(
    message: String,
    isError: Boolean
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = message,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun localizedExpiryState(rawState: String): String = when (rawState) {
    "ACTIVE" -> localizedText("account.expiry_state.active")
    "EXPIRING_SOON" -> localizedText("account.expiry_state.expiring_soon")
    "EXPIRED" -> localizedText("account.expiry_state.expired")
    else -> localizedText("account.expiry_state.unknown")
}

private fun formatSelectedReminderDays(days: List<Int>): String =
    if (days.isEmpty()) {
        localizedTextForCurrentLocale("common.none")
    } else {
        days.joinToString(separator = ", ") { formatIntegerForLocale(it) }
    }
