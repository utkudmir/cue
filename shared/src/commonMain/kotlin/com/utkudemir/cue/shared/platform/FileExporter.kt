package com.utkudemir.cue.shared.platform

interface FileExporter {
    suspend fun exportTextFile(fileName: String, content: String): ExportedFile
}
