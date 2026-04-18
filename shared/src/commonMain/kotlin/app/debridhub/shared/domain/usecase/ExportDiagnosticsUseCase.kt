package app.debridhub.shared.domain.usecase

import app.debridhub.shared.domain.repository.DiagnosticsRepository
import app.debridhub.shared.platform.ExportedFile
import app.debridhub.shared.platform.FileExporter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ExportDiagnosticsUseCase(
    private val diagnosticsRepository: DiagnosticsRepository,
    private val fileExporter: FileExporter,
    private val json: Json = Json { prettyPrint = true }
) {
    suspend operator fun invoke(): Result<ExportedFile> = runCatching {
        val payload = json.encodeToString(diagnosticsRepository.collectDiagnostics())
        fileExporter.exportTextFile("diagnostics.json", payload)
    }
}
