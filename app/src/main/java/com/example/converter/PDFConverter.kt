package com.example.converter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.example.data.PageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class PDFConverter : DocumentConverter {

    override fun supports(fileExtension: String, mimeType: String?): Boolean {
        return fileExtension.lowercase() == "pdf" || mimeType?.lowercase() == "application/pdf"
    }

    override suspend fun convert(
        context: Context,
        uri: Uri,
        fileName: String,
        documentId: String,
        workspaceDir: File
    ): ConversionResult = withContext(Dispatchers.IO) {
        val previewsDir = File(workspaceDir, "previews").apply { mkdirs() }
        val tempPdf = File(context.cacheDir, "temp_$documentId.pdf")

        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempPdf).use { output ->
                    input.copyTo(output)
                }
            }

            val pfd = ParcelFileDescriptor.open(tempPdf, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val pageCount = renderer.pageCount

            val pages = mutableListOf<PageEntity>()
            val fullMarkdownBuilder = StringBuilder()

            fullMarkdownBuilder.append("# $fileName\n\n")
            fullMarkdownBuilder.append("- **Format:** PDF Document\n")
            fullMarkdownBuilder.append("- **Total Pages:** $pageCount\n\n")
            fullMarkdownBuilder.append("---\n\n")

            for (i in 0 until pageCount) {
                val page = renderer.openPage(i)
                
                // Render page image (adaptive width, up to 1080px for crisp quality while compressed)
                val width = (page.width * 1.5).toInt().coerceAtLeast(600)
                val height = (page.height * 1.5).toInt().coerceAtLeast(800)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                // Save compressed preview
                val previewFile = File(previewsDir, "page_${i + 1}.webp")
                FileOutputStream(previewFile).use { out ->
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 80, out)
                    } else {
                        @Suppress("DEPRECATION")
                        bitmap.compress(Bitmap.CompressFormat.WEBP, 80, out)
                    }
                }
                bitmap.recycle()

                val pageTitle = "Page ${i + 1}"
                val pageMarkdown = buildString {
                    append("## $pageTitle\n\n")
                    append("![Page ${i + 1} Preview](previews/page_${i + 1}.webp)\n\n")
                    append("> *PDF Visual Page ${i + 1} of $pageCount preserved in workspace.*\n\n")
                }

                fullMarkdownBuilder.append(pageMarkdown)
                fullMarkdownBuilder.append("---\n\n")

                pages.add(
                    PageEntity(
                        documentId = documentId,
                        pageIndex = i,
                        pageTitle = pageTitle,
                        markdownContent = pageMarkdown,
                        previewImagePath = previewFile.absolutePath,
                        speakerNotes = null,
                        tableDataJson = null
                    )
                )
            }

            renderer.close()
            pfd.close()

            val mainMdFile = File(workspaceDir, "document.md")
            mainMdFile.writeText(fullMarkdownBuilder.toString())

            ConversionResult(
                documentId = documentId,
                fileName = fileName,
                fileType = "PDF",
                fileSize = tempPdf.length(),
                pageCount = pageCount,
                fullMarkdownPath = mainMdFile.absolutePath,
                workspaceDirPath = workspaceDir.absolutePath,
                pages = pages,
                metadataJson = """{"type":"PDF","pages":$pageCount}"""
            )
        } finally {
            if (tempPdf.exists()) {
                tempPdf.delete()
            }
        }
    }
}
