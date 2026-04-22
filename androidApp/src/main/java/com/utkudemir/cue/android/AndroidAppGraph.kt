package com.utkudemir.cue.android

import android.content.Context
import android.os.Build
import com.utkudemir.cue.shared.data.remote.RealDebridApi
import com.utkudemir.cue.shared.data.repository.AccountRepositoryImpl
import com.utkudemir.cue.shared.data.repository.AuthRepositoryImpl
import com.utkudemir.cue.shared.data.repository.DiagnosticsRepositoryImpl
import com.utkudemir.cue.shared.data.repository.ReminderRepositoryImpl
import com.utkudemir.cue.shared.domain.repository.AccountRepository
import com.utkudemir.cue.shared.domain.repository.AuthRepository
import com.utkudemir.cue.shared.domain.repository.DiagnosticsRepository
import com.utkudemir.cue.shared.domain.repository.ReminderRepository
import com.utkudemir.cue.shared.domain.usecase.ExportDiagnosticsUseCase
import com.utkudemir.cue.shared.domain.usecase.PreviewDiagnosticsUseCase
import com.utkudemir.cue.shared.platform.FileExporter
import com.utkudemir.cue.shared.platform.FileExporterImpl
import com.utkudemir.cue.shared.platform.NotificationScheduler
import com.utkudemir.cue.shared.platform.NotificationSchedulerImpl
import com.utkudemir.cue.shared.platform.ReminderConfigStore
import com.utkudemir.cue.shared.platform.ReminderConfigStoreImpl
import com.utkudemir.cue.shared.platform.SecureTokenStore
import com.utkudemir.cue.shared.platform.SecureTokenStoreImpl
import com.utkudemir.cue.shared.reminders.ReminderPlanner
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.net.Proxy

data class AndroidAppGraph(
    val authRepository: AuthRepository,
    val accountRepository: AccountRepository,
    val reminderRepository: ReminderRepository,
    val notificationScheduler: NotificationScheduler,
    val exportDiagnosticsUseCase: ExportDiagnosticsUseCase,
    val previewDiagnosticsUseCase: PreviewDiagnosticsUseCase
)

fun buildAndroidAppGraph(context: Context): AndroidAppGraph {
    val appContext = context.applicationContext
    val httpClient = HttpClient(OkHttp) {
        engine {
            config {
                proxy(Proxy.NO_PROXY)
            }
        }
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(Logging) {
            level = LogLevel.NONE
        }
    }

    val api = RealDebridApi(httpClient)
    val tokenStore: SecureTokenStore = SecureTokenStoreImpl(appContext)
    val reminderConfigStore: ReminderConfigStore = ReminderConfigStoreImpl(appContext)
    val notificationScheduler: NotificationScheduler = NotificationSchedulerImpl(appContext)
    val authRepository: AuthRepository = AuthRepositoryImpl(api, tokenStore)
    val accountRepository: AccountRepository = AccountRepositoryImpl(api, authRepository)
    val reminderRepository: ReminderRepository = ReminderRepositoryImpl(
        configStore = reminderConfigStore,
        planner = ReminderPlanner(),
        notificationScheduler = notificationScheduler
    )
    val diagnosticsRepository: DiagnosticsRepository = DiagnosticsRepositoryImpl(
        appVersionProvider = { BuildConfig.VERSION_NAME },
        osProvider = { "Android ${Build.VERSION.RELEASE}" },
        accountRepository = accountRepository,
        additionalInfoProvider = {
            mapOf(
                "notificationsEnabled" to notificationScheduler.areNotificationsEnabled().toString()
            )
        }
    )
    val fileExporter: FileExporter = FileExporterImpl(appContext)

    return AndroidAppGraph(
        authRepository = authRepository,
        accountRepository = accountRepository,
        reminderRepository = reminderRepository,
        notificationScheduler = notificationScheduler,
        exportDiagnosticsUseCase = ExportDiagnosticsUseCase(diagnosticsRepository, fileExporter),
        previewDiagnosticsUseCase = PreviewDiagnosticsUseCase(diagnosticsRepository)
    )
}
