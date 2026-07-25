package com.example.converter

import android.content.Context
import android.net.Uri
import com.example.data.PageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class TextConverter : DocumentConverter {

    override fun supports(fileExtension: String, mimeType: String?): Boolean {
        val ext = fileExtension.lowercase()
        val textExtensions = listOf(
            "txt", "md", "markdown", "csv", "json", "html", "htm",
            "kt", "java", "py", "js", "ts", "c", "cpp", "cs", "go",
            "rs", "sql", "sh", "xml", "yml", "yaml", "css", "swift", "log"
        )
        return ext in textExtensions || mimeType?.startsWith("text/") == true || mimeType == "application/json"
    }

    override suspend fun convert(
        context: Context,
        uri: Uri,
        fileName: String,
        documentId: String,
        workspaceDir: File
    ): ConversionResult = withContext(Dispatchers.IO) {
        val fileExt = fileName.substringAfterLast(".", "").lowercase()
        var rawContent = ""

        context.contentResolver.openInputStream(uri)?.use { input ->
            rawContent = input.bufferedReader().use { it.readText() }
        }

        val convertedMarkdown = when (fileExt) {
            "csv" -> convertCsvToMarkdown(rawContent, fileName)
            "json" -> convertJsonToMarkdown(rawContent, fileName)
            "html", "htm" -> convertHtmlToMarkdown(rawContent, fileName)
            "md", "markdown" -> "# $fileName\n\n$rawContent"
            else -> {
                if (fileExt in listOf("kt", "java", "py", "js", "ts", "c", "cpp", "cs", "go", "rs", "sql", "sh", "xml", "yml", "yaml", "css", "swift")) {
                    convertCodeToMarkdown(rawContent, fileExt, fileName)
                } else {
                    "# $fileName\n\n$rawContent"
                }
            }
        }

        val mainMdFile = File(workspaceDir, "document.md")
        mainMdFile.writeText(convertedMarkdown)

        val pages = listOf(
            PageEntity(
                documentId = documentId,
                pageIndex = 0,
                pageTitle = fileName,
                markdownContent = convertedMarkdown,
                previewImagePath = null,
                speakerNotes = null,
                tableDataJson = null
            )
        )

        val fileSize = rawContent.toByteArray().size.toLong()

        ConversionResult(
            documentId = documentId,
            fileName = fileName,
            fileType = fileExt.uppercase().ifBlank { "TEXT" },
            fileSize = fileSize,
            pageCount = 1,
            fullMarkdownPath = mainMdFile.absolutePath,
            workspaceDirPath = workspaceDir.absolutePath,
            pages = pages,
            metadataJson = """{"type":"${fileExt.uppercase()}","length":${rawContent.length}}"""
        )
    }

    private fun convertCsvToMarkdown(csvContent: String, fileName: String): String {
        val lines = csvContent.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return "# $fileName\n\n*(Empty CSV file)*"

        val sb = StringBuilder("# $fileName\n\n")
        val parsedRows = lines.map { parseCsvLine(it) }
        val maxCols = parsedRows.maxOfOrNull { it.size } ?: 0

        if (maxCols > 0) {
            val header = parsedRows.first().padTo(maxCols)
            sb.append("| ").append(header.joinToString(" | ")).append(" |\n")
            sb.append("| ").append(List(maxCols) { "---" }.joinToString(" | ")).append(" |\n")

            for (i in 1 until parsedRows.size) {
                val row = parsedRows[i].padTo(maxCols)
                sb.append("| ").append(row.joinToString(" | ")).append(" |\n")
            }
        }
        return sb.toString()
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false

        for (ch in line) {
            when (ch) {
                '"' -> inQuotes = !inQuotes
                ',' -> {
                    if (inQuotes) {
                        current.append(ch)
                    } else {
                        result.add(current.toString().trim())
                        current.clear()
                    }
                }
                else -> current.append(ch)
            }
        }
        result.add(current.toString().trim())
        return result.map { it.replace("|", "\\|") }
    }

    private fun convertJsonToMarkdown(jsonContent: String, fileName: String): String {
        val sb = StringBuilder("# $fileName\n\n")
        try {
            val trimmed = jsonContent.trim()
            if (trimmed.startsWith("{")) {
                val obj = JSONObject(trimmed)
                sb.append("### Pretty JSON Structure\n\n")
                sb.append("```json\n")
                sb.append(obj.toString(2))
                sb.append("\n```\n")
            } else if (trimmed.startsWith("[")) {
                val arr = JSONArray(trimmed)
                sb.append("### JSON Array (${arr.length()} items)\n\n")
                sb.append("```json\n")
                sb.append(arr.toString(2))
                sb.append("\n```\n")
            } else {
                sb.append("```json\n$jsonContent\n```\n")
            }
        } catch (e: Exception) {
            sb.append("```json\n$jsonContent\n```\n")
        }
        return sb.toString()
    }

    private fun convertHtmlToMarkdown(htmlContent: String, fileName: String): String {
        var clean = htmlContent
            .replace(Regex("(?i)<script.*?>.*?</script>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("(?i)<style.*?>.*?</style>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("(?i)<h1.*?>(.*?)</h1>"), "\n# $1\n")
            .replace(Regex("(?i)<h2.*?>(.*?)</h2>"), "\n## $1\n")
            .replace(Regex("(?i)<h3.*?>(.*?)</h3>"), "\n### $1\n")
            .replace(Regex("(?i)<p.*?>(.*?)</p>"), "\n$1\n")
            .replace(Regex("(?i)<li.*?>(.*?)</li>"), "\n- $1")
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("<.*?>"), "") // Strip remaining tags
            .replace(Regex("\n{3,}"), "\n\n")

        return "# $fileName\n\n${clean.trim()}"
    }

    private fun convertCodeToMarkdown(codeContent: String, language: String, fileName: String): String {
        return "# $fileName\n\n```$language\n$codeContent\n```\n"
    }

    private fun List<String>.padTo(size: Int): List<String> {
        val list = this.toMutableList()
        while (list.size < size) list.add("")
        return list
    }
}
