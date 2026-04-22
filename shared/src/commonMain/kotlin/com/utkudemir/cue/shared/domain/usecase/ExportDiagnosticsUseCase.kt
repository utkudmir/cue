package com.utkudemir.cue.shared.domain.usecase

import com.utkudemir.cue.shared.domain.repository.DiagnosticsRepository
import com.utkudemir.cue.shared.platform.ExportedFile
import com.utkudemir.cue.shared.platform.FileExporter
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
