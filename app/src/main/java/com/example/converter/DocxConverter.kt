package com.example.converter

import android.content.Context
import android.net.Uri
import android.util.Xml
import com.example.data.PageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class DocxConverter : DocumentConverter {

    override fun supports(fileExtension: String, mimeType: String?): Boolean {
        val ext = fileExtension.lowercase()
        return ext == "docx" || ext == "doc" || mimeType?.lowercase()?.contains("word") == true
    }

    override suspend fun convert(
        context: Context,
        uri: Uri,
        fileName: String,
        documentId: String,
        workspaceDir: File
    ): ConversionResult = withContext(Dispatchers.IO) {
        val mediaDir = File(workspaceDir, "media").apply { mkdirs() }
        val extractedImages = mutableListOf<String>()
        var documentXmlStream: InputStream? = null
        val tempZip = File(context.cacheDir, "temp_docx_$documentId.zip")

        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempZip).use { output ->
                    input.copyTo(output)
                }
            }

            // Unzip media images and find word/document.xml
            ZipInputStream(tempZip.inputStream()).use { zip ->
                var entry: ZipEntry? = zip.nextEntry
                while (entry != null) {
                    val entryName = entry.name
                    if (entryName.startsWith("word/media/") && !entry.isDirectory) {
                        val imageName = entryName.substringAfterLast("/")
                        val imageFile = File(mediaDir, imageName)
                        FileOutputStream(imageFile).use { out ->
                            zip.copyTo(out)
                        }
                        extractedImages.add("media/$imageName")
                    }
                    entry = zip.nextEntry
                }
            }

            // Extract document.xml text content
            val docXmlContent = extractFileFromZip(tempZip, "word/document.xml")
            val markdownContent = parseDocxXml(docXmlContent, extractedImages)

            val pageMarkdown = if (markdownContent.isBlank()) {
                "# $fileName\n\n*(No text extracted from DOCX document)*"
            } else {
                markdownContent
            }

            val mainMdFile = File(workspaceDir, "document.md")
            mainMdFile.writeText(pageMarkdown)

            val pages = listOf(
                PageEntity(
                    documentId = documentId,
                    pageIndex = 0,
                    pageTitle = fileName.removeSuffix(".docx").removeSuffix(".doc"),
                    markdownContent = pageMarkdown,
                    previewImagePath = extractedImages.firstOrNull()?.let { File(workspaceDir, it).absolutePath },
                    speakerNotes = null,
                    tableDataJson = null
                )
            )

            ConversionResult(
                documentId = documentId,
                fileName = fileName,
                fileType = "DOCX",
                fileSize = tempZip.length(),
                pageCount = 1,
                fullMarkdownPath = mainMdFile.absolutePath,
                workspaceDirPath = workspaceDir.absolutePath,
                pages = pages,
                metadataJson = """{"type":"DOCX","extractedImages":${extractedImages.size}}"""
            )
        } finally {
            if (tempZip.exists()) tempZip.delete()
        }
    }

    private fun extractFileFromZip(zipFile: File, pathInZip: String): String {
        ZipInputStream(zipFile.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == pathInZip) {
                    return zip.bufferedReader().use { it.readText() }
                }
                entry = zip.nextEntry
            }
        }
        return ""
    }

    private fun parseDocxXml(xmlString: String, extractedImages: List<String>): String {
        if (xmlString.isBlank()) return ""

        val result = StringBuilder()
        var imageIndex = 0

        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(xmlString.reader())

            var eventType = parser.eventType
            var inParagraph = false
            var inTable = false
            var inTableRow = false
            var inTableCell = false
            var headingLevel = 0
            var currentText = StringBuilder()
            var currentStyleVal = ""
            var currentTableRow = mutableListOf<String>()
            var currentTable = mutableListOf<List<String>>()

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val name = parser.name
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (name) {
                            "w:p" -> {
                                inParagraph = true
                                headingLevel = 0
                                currentStyleVal = ""
                                currentText.clear()
                            }
                            "w:pStyle" -> {
                                val valAttr = parser.getAttributeValue(null, "w:val") ?: ""
                                currentStyleVal = valAttr
                                if (valAttr.contains("Heading1", ignoreCase = true) || valAttr == "1") headingLevel = 1
                                else if (valAttr.contains("Heading2", ignoreCase = true) || valAttr == "2") headingLevel = 2
                                else if (valAttr.contains("Heading3", ignoreCase = true) || valAttr == "3") headingLevel = 3
                                else if (valAttr.contains("Title", ignoreCase = true)) headingLevel = 1
                                else if (valAttr.contains("Subtitle", ignoreCase = true)) headingLevel = 2
                            }
                            "w:tbl" -> {
                                inTable = true
                                currentTable.clear()
                            }
                            "w:tr" -> {
                                inTableRow = true
                                currentTableRow.clear()
                            }
                            "w:tc" -> {
                                inTableCell = true
                                currentText.clear()
                            }
                            "w:drawing", "a:blip" -> {
                                if (imageIndex < extractedImages.size) {
                                    val imgPath = extractedImages[imageIndex++]
                                    currentText.append("\n\n![Extracted Image]($imgPath)\n\n")
                                }
                            }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (parser.text != null) {
                            currentText.append(parser.text)
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (name) {
                            "w:p" -> {
                                val paragraphText = currentText.toString().trim()
                                if (paragraphText.isNotEmpty()) {
                                    if (!inTableCell) {
                                        when (headingLevel) {
                                            1 -> result.append("# $paragraphText\n\n")
                                            2 -> result.append("## $paragraphText\n\n")
                                            3 -> result.append("### $paragraphText\n\n")
                                            else -> {
                                                if (currentStyleVal.contains("List", ignoreCase = true)) {
                                                    result.append("- $paragraphText\n")
                                                } else {
                                                    result.append("$paragraphText\n\n")
                                                }
                                            }
                                        }
                                    }
                                }
                                inParagraph = false
                            }
                            "w:tc" -> {
                                if (inTableRow) {
                                    currentTableRow.add(currentText.toString().trim().replace("\n", " "))
                                }
                                inTableCell = false
                            }
                            "w:tr" -> {
                                if (inTable && currentTableRow.isNotEmpty()) {
                                    currentTable.add(ArrayList(currentTableRow))
                                }
                                inTableRow = false
                            }
                            "w:tbl" -> {
                                if (currentTable.isNotEmpty()) {
                                    result.append("\n")
                                    val maxCols = currentTable.maxOfOrNull { it.size } ?: 0
                                    if (maxCols > 0) {
                                        // Header row
                                        val header = currentTable.first()
                                        result.append("| ").append(header.padTo(maxCols).joinToString(" | ")).append(" |\n")
                                        result.append("| ").append(List(maxCols) { "---" }.joinToString(" | ")).append(" |\n")
                                        // Data rows
                                        for (i in 1 until currentTable.size) {
                                            val row = currentTable[i]
                                            result.append("| ").append(row.padTo(maxCols).joinToString(" | ")).append(" |\n")
                                        }
                                        result.append("\n")
                                    }
                                }
                                inTable = false
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback plain text clean up if XML parsing partially completes
        }

        return result.toString()
    }

    private fun List<String>.padTo(size: Int): List<String> {
        val list = this.toMutableList()
        while (list.size < size) {
            list.add("")
        }
        return list
    }
}
