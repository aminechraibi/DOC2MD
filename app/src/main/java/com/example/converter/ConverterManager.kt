package com.example.converter

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class ConverterManager(private val context: Context) {

    private val converters: List<DocumentConverter> = listOf(
        PDFConverter(),
        DocxConverter(),
        PptxConverter(),
        XlsxConverter(),
        TextConverter()
    )

    suspend fun convertDocument(uri: Uri): ConversionResult = withContext(Dispatchers.IO) {
        val fileName = getFileName(context, uri)
        val mimeType = context.contentResolver.getType(uri)
        val fileExt = fileName.substringAfterLast(".", "").lowercase()

        val converter = converters.firstOrNull { it.supports(fileExt, mimeType) }
            ?: TextConverter() // Fallback to TextConverter if unknown

        val documentId = UUID.randomUUID().toString()
        val workspaceDir = File(context.cacheDir, "workspaces/$documentId").apply { mkdirs() }

        val result = converter.convert(context, uri, fileName, documentId, workspaceDir)

        // Persist in Room Database
        val db = AppDatabase.getDatabase(context)
        val docDao = db.documentDao()

        val docEntity = com.example.data.DocumentEntity(
            id = result.documentId,
            uriString = uri.toString(),
            fileName = result.fileName,
            fileType = result.fileType,
            fileSize = result.fileSize,
            convertedTimestamp = System.currentTimeMillis(),
            pageCount = result.pageCount,
            fullMarkdownPath = result.fullMarkdownPath,
            workspaceDirPath = result.workspaceDirPath,
            lastReadPage = 0,
            lastReadPosition = 0,
            metadataJson = result.metadataJson
        )

        docDao.insertDocument(docEntity)
        docDao.insertPages(result.pages)

        result
    }

    private fun getFileName(context: Context, uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        result = cursor.getString(index)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != null && cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result ?: "document_${System.currentTimeMillis()}"
    }
}
