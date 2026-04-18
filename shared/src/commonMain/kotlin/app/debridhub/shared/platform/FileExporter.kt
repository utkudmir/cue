package app.debridhub.shared.platform

interface FileExporter {
    suspend fun exportTextFile(fileName: String, content: String): ExportedFile
}
