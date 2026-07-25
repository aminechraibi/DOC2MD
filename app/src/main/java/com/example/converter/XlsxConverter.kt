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
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class XlsxConverter : DocumentConverter {

    override fun supports(fileExtension: String, mimeType: String?): Boolean {
        val ext = fileExtension.lowercase()
        return ext == "xlsx" || ext == "xls" || mimeType?.lowercase()?.contains("excel") == true || mimeType?.lowercase()?.contains("spreadsheet") == true
    }

    override suspend fun convert(
        context: Context,
        uri: Uri,
        fileName: String,
        documentId: String,
        workspaceDir: File
    ): ConversionResult = withContext(Dispatchers.IO) {
        val mediaDir = File(workspaceDir, "media").apply { mkdirs() }
        val tempZip = File(context.cacheDir, "temp_xlsx_$documentId.zip")
        val sharedStrings = mutableListOf<String>()
        val sheetMap = mutableMapOf<Int, String>()
        val extractedImages = mutableListOf<String>()

        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempZip).use { output ->
                    input.copyTo(output)
                }
            }

            // Extract shared strings, worksheets, media images
            ZipInputStream(tempZip.inputStream()).use { zip ->
                var entry: ZipEntry? = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (name.startsWith("xl/media/") && !entry.isDirectory) {
                        val imgName = name.substringAfterLast("/")
                        val imgFile = File(mediaDir, imgName)
                        FileOutputStream(imgFile).use { out -> zip.copyTo(out) }
                        extractedImages.add("media/$imgName")
                    } else if (name == "xl/sharedStrings.xml") {
                        val xmlText = zip.bufferedReader().use { it.readText() }
                        sharedStrings.addAll(parseSharedStrings(xmlText))
                    } else if (name.matches(Regex("xl/worksheets/sheet\\d+\\.xml"))) {
                        val sheetNum = name.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 1
                        val xmlText = zip.bufferedReader().use { it.readText() }
                        sheetMap[sheetNum] = xmlText
                    }
                    entry = zip.nextEntry
                }
            }

            val sortedSheets = sheetMap.keys.sorted()
            val totalSheets = sortedSheets.size.coerceAtLeast(1)

            val pages = mutableListOf<PageEntity>()
            val fullMarkdownBuilder = StringBuilder()

            fullMarkdownBuilder.append("# $fileName\n\n")
            fullMarkdownBuilder.append("- **Format:** Excel Spreadsheet (XLSX)\n")
            fullMarkdownBuilder.append("- **Sheets:** $totalSheets\n\n")
            fullMarkdownBuilder.append("---\n\n")

            sortedSheets.forEachIndexed { index, sheetNum ->
                val sheetXml = sheetMap[sheetNum] ?: ""
                val sheetTitle = "Sheet ${index + 1}"
                val tableRows = parseSheetXml(sheetXml, sharedStrings)

                val sheetMarkdown = buildString {
                    append("## $sheetTitle\n\n")
                    if (tableRows.isNotEmpty()) {
                        val maxCols = tableRows.maxOfOrNull { it.size } ?: 0
                        if (maxCols > 0) {
                            val header = tableRows.first().padTo(maxCols)
                            append("| ").append(header.joinToString(" | ")).append(" |\n")
                            append("| ").append(List(maxCols) { "---" }.joinToString(" | ")).append(" |\n")

                            for (r in 1 until tableRows.size) {
                                val rowData = tableRows[r].padTo(maxCols)
                                append("| ").append(rowData.joinToString(" | ")).append(" |\n")
                            }
                            append("\n")
                        }
                    } else {
                        append("*(Empty sheet)*\n\n")
                    }

                    if (extractedImages.isNotEmpty() && index == 0) {
                        append("### Exported Charts & Visual Assets\n\n")
                        extractedImages.forEach { imgPath ->
                            append("![$imgPath]($imgPath)\n\n")
                        }
                    }
                }

                fullMarkdownBuilder.append(sheetMarkdown)
                fullMarkdownBuilder.append("---\n\n")

                pages.add(
                    PageEntity(
                        documentId = documentId,
                        pageIndex = index,
                        pageTitle = sheetTitle,
                        markdownContent = sheetMarkdown,
                        previewImagePath = null,
                        speakerNotes = null,
                        tableDataJson = null
                    )
                )
            }

            val mainMdFile = File(workspaceDir, "document.md")
            mainMdFile.writeText(fullMarkdownBuilder.toString())

            ConversionResult(
                documentId = documentId,
                fileName = fileName,
                fileType = "XLSX",
                fileSize = tempZip.length(),
                pageCount = totalSheets,
                fullMarkdownPath = mainMdFile.absolutePath,
                workspaceDirPath = workspaceDir.absolutePath,
                pages = pages,
                metadataJson = """{"type":"XLSX","sheets":$totalSheets}"""
            )
        } finally {
            if (tempZip.exists()) tempZip.delete()
        }
    }

    private fun parseSharedStrings(xmlText: String): List<String> {
        val strings = mutableListOf<String>()
        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(xmlText.reader())

            var eventType = parser.eventType
            var currentText = StringBuilder()

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (parser.name == "t") {
                            currentText.clear()
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (parser.text != null) {
                            currentText.append(parser.text)
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "t") {
                            strings.add(currentText.toString())
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return strings
    }

    private fun parseSheetXml(xmlText: String, sharedStrings: List<String>): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var currentRow = mutableListOf<String>()

        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(xmlText.reader())

            var eventType = parser.eventType
            var isSharedString = false
            var currentValue = StringBuilder()

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "row" -> {
                                currentRow = mutableListOf()
                            }
                            "c" -> {
                                val tAttr = parser.getAttributeValue(null, "t")
                                isSharedString = (tAttr == "s")
                                currentValue.clear()
                            }
                            "v" -> {
                                currentValue.clear()
                            }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (parser.text != null) {
                            currentValue.append(parser.text)
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            "c" -> {
                                val valStr = currentValue.toString().trim()
                                val cellVal = if (isSharedString) {
                                    val idx = valStr.toIntOrNull()
                                    if (idx != null && idx in sharedStrings.indices) {
                                        sharedStrings[idx]
                                    } else {
                                        valStr
                                    }
                                } else {
                                    valStr
                                }
                                currentRow.add(cellVal.replace("|", "\\|").replace("\n", " "))
                            }
                            "row" -> {
                                if (currentRow.isNotEmpty()) {
                                    rows.add(ArrayList(currentRow))
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return rows
    }

    private fun List<String>.padTo(size: Int): List<String> {
        val list = this.toMutableList()
        while (list.size < size) {
            list.add("")
        }
        return list
    }
}
