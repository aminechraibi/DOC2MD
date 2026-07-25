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

class PptxConverter : DocumentConverter {

    override fun supports(fileExtension: String, mimeType: String?): Boolean {
        val ext = fileExtension.lowercase()
        return ext == "pptx" || ext == "ppt" || mimeType?.lowercase()?.contains("presentation") == true || mimeType?.lowercase()?.contains("powerpoint") == true
    }

    override suspend fun convert(
        context: Context,
        uri: Uri,
        fileName: String,
        documentId: String,
        workspaceDir: File
    ): ConversionResult = withContext(Dispatchers.IO) {
        val mediaDir = File(workspaceDir, "media").apply { mkdirs() }
        val tempZip = File(context.cacheDir, "temp_pptx_$documentId.zip")
        val slideContents = mutableMapOf<Int, String>()
        val speakerNotesMap = mutableMapOf<Int, String>()
        val extractedImages = mutableListOf<String>()

        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempZip).use { output ->
                    input.copyTo(output)
                }
            }

            // Read slides, speaker notes, and extract media from zip
            ZipInputStream(tempZip.inputStream()).use { zip ->
                var entry: ZipEntry? = zip.nextEntry
                while (entry != null) {
                    val entryName = entry.name
                    if (entryName.startsWith("ppt/media/") && !entry.isDirectory) {
                        val imgName = entryName.substringAfterLast("/")
                        val imgFile = File(mediaDir, imgName)
                        FileOutputStream(imgFile).use { out -> zip.copyTo(out) }
                        extractedImages.add("media/$imgName")
                    } else if (entryName.matches(Regex("ppt/slides/slide\\d+\\.xml"))) {
                        val slideNumber = entryName.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 1
                        val xmlText = zip.bufferedReader().use { it.readText() }
                        slideContents[slideNumber] = parseSlideXml(xmlText, slideNumber)
                    } else if (entryName.matches(Regex("ppt/notesSlides/notesSlide\\d+\\.xml"))) {
                        val noteNumber = entryName.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 1
                        val xmlText = zip.bufferedReader().use { it.readText() }
                        speakerNotesMap[noteNumber] = parseNotesXml(xmlText)
                    }
                    entry = zip.nextEntry
                }
            }

            val sortedSlideIndices = slideContents.keys.sorted()
            val totalSlides = sortedSlideIndices.size.coerceAtLeast(1)

            val pages = mutableListOf<PageEntity>()
            val fullMarkdownBuilder = StringBuilder()

            fullMarkdownBuilder.append("# $fileName\n\n")
            fullMarkdownBuilder.append("- **Format:** PowerPoint Presentation (PPTX)\n")
            fullMarkdownBuilder.append("- **Total Slides:** $totalSlides\n\n")
            fullMarkdownBuilder.append("---\n\n")

            if (sortedSlideIndices.isEmpty()) {
                val fallbackText = "## Slide 1\n\n*(No slide content extracted)*"
                fullMarkdownBuilder.append(fallbackText)
                pages.add(
                    PageEntity(
                        documentId = documentId,
                        pageIndex = 0,
                        pageTitle = "Slide 1",
                        markdownContent = fallbackText,
                        previewImagePath = null,
                        speakerNotes = null,
                        tableDataJson = null
                    )
                )
            } else {
                sortedSlideIndices.forEachIndexed { index, slideNum ->
                    val slideText = slideContents[slideNum] ?: ""
                    val notesText = speakerNotesMap[slideNum] ?: ""

                    val slideTitle = "Slide ${index + 1}"
                    val slideMarkdown = buildString {
                        append("## $slideTitle\n\n")
                        if (slideText.isNotBlank()) {
                            append(slideText)
                            append("\n\n")
                        } else {
                            append("*(Empty slide)*\n\n")
                        }

                        if (notesText.isNotBlank()) {
                            append("> 💡 **Speaker Notes:**\n")
                            append("> ")
                            append(notesText.replace("\n", "\n> "))
                            append("\n\n")
                        }
                    }

                    fullMarkdownBuilder.append(slideMarkdown)
                    fullMarkdownBuilder.append("---\n\n")

                    pages.add(
                        PageEntity(
                            documentId = documentId,
                            pageIndex = index,
                            pageTitle = slideTitle,
                            markdownContent = slideMarkdown,
                            previewImagePath = null,
                            speakerNotes = notesText.ifBlank { null },
                            tableDataJson = null
                        )
                    )
                }
            }

            val mainMdFile = File(workspaceDir, "document.md")
            mainMdFile.writeText(fullMarkdownBuilder.toString())

            ConversionResult(
                documentId = documentId,
                fileName = fileName,
                fileType = "PPTX",
                fileSize = tempZip.length(),
                pageCount = totalSlides,
                fullMarkdownPath = mainMdFile.absolutePath,
                workspaceDirPath = workspaceDir.absolutePath,
                pages = pages,
                metadataJson = """{"type":"PPTX","slides":$totalSlides,"speakerNotes":${speakerNotesMap.isNotEmpty()}}"""
            )
        } finally {
            if (tempZip.exists()) tempZip.delete()
        }
    }

    private fun parseSlideXml(xmlText: String, slideIndex: Int): String {
        val result = StringBuilder()
        val textLines = mutableListOf<String>()

        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(xmlText.reader())

            var eventType = parser.eventType
            var currentText = StringBuilder()

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (parser.name == "a:t") {
                            currentText.clear()
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (parser.text != null) {
                            currentText.append(parser.text)
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "a:t") {
                            val line = currentText.toString().trim()
                            if (line.isNotEmpty()) {
                                textLines.add(line)
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (textLines.isNotEmpty()) {
            // First non-empty line as header if possible
            result.append("### ").append(textLines.first()).append("\n\n")
            for (i in 1 until textLines.size) {
                result.append("- ").append(textLines[i]).append("\n")
            }
        }

        return result.toString()
    }

    private fun parseNotesXml(xmlText: String): String {
        val textLines = mutableListOf<String>()

        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(xmlText.reader())

            var eventType = parser.eventType
            var currentText = StringBuilder()

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (parser.name == "a:t") {
                            currentText.clear()
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (parser.text != null) {
                            currentText.append(parser.text)
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "a:t") {
                            val line = currentText.toString().trim()
                            if (line.isNotEmpty() && !line.startsWith("Slide ")) {
                                textLines.add(line)
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return textLines.joinToString("\n")
    }
}
