package com.example.converter

import android.content.Context
import android.net.Uri
import com.example.data.PageEntity
import java.io.File

data class ConversionResult(
    val documentId: String,
    val fileName: String,
    val fileType: String,
    val fileSize: Long,
    val pageCount: Int,
    val fullMarkdownPath: String,
    val workspaceDirPath: String,
    val pages: List<PageEntity>,
    val metadataJson: String = "{}"
)

interface DocumentConverter {
    fun supports(fileExtension: String, mimeType: String?): Boolean

    suspend fun convert(
        context: Context,
        uri: Uri,
        fileName: String,
        documentId: String,
        workspaceDir: File
    ): ConversionResult
}
