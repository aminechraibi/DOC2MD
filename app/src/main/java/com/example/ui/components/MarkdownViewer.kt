package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File

sealed class MarkdownNode {
    data class Heading(val level: Int, val text: String) : MarkdownNode()
    data class Paragraph(val text: String) : MarkdownNode()
    data class CodeBlock(val code: String, val language: String) : MarkdownNode()
    data class Table(val rows: List<List<String>>) : MarkdownNode()
    data class Blockquote(val text: String) : MarkdownNode()
    data class Bullet(val text: String) : MarkdownNode()
    data class Image(val altText: String, val relativePath: String) : MarkdownNode()
    object Divider : MarkdownNode()
}

fun parseMarkdownNodes(markdownText: String): List<MarkdownNode> {
    val nodes = mutableListOf<MarkdownNode>()
    val lines = markdownText.lines()

    var inCodeBlock = false
    var codeLang = ""
    val codeBuffer = StringBuilder()

    var inTable = false
    val tableRows = mutableListOf<List<String>>()

    fun flushTable() {
        if (tableRows.isNotEmpty()) {
            nodes.add(MarkdownNode.Table(ArrayList(tableRows)))
            tableRows.clear()
        }
        inTable = false
    }

    for (line in lines) {
        val trimmed = line.trim()

        if (trimmed.startsWith("```")) {
            if (inTable) flushTable()
            if (inCodeBlock) {
                nodes.add(MarkdownNode.CodeBlock(codeBuffer.toString().trimEnd(), codeLang))
                codeBuffer.clear()
                inCodeBlock = false
            } else {
                inCodeBlock = true
                codeLang = trimmed.removePrefix("```").trim()
            }
            continue
        }

        if (inCodeBlock) {
            codeBuffer.append(line).append("\n")
            continue
        }

        if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
            if (!inTable) inTable = true
            val cells = trimmed.split("|").drop(1).dropLast(1).map { it.trim() }
            if (cells.none { cell -> cell.all { ch -> ch == '-' || ch == ':' } }) {
                tableRows.add(cells)
            }
            continue
        } else if (inTable) {
            flushTable()
        }

        when {
            trimmed.startsWith("# ") -> nodes.add(MarkdownNode.Heading(1, trimmed.substring(2)))
            trimmed.startsWith("## ") -> nodes.add(MarkdownNode.Heading(2, trimmed.substring(3)))
            trimmed.startsWith("### ") -> nodes.add(MarkdownNode.Heading(3, trimmed.substring(4)))
            trimmed.startsWith("---") || trimmed.startsWith("***") -> nodes.add(MarkdownNode.Divider)
            trimmed.startsWith("> ") -> nodes.add(MarkdownNode.Blockquote(trimmed.substring(2)))
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> nodes.add(MarkdownNode.Bullet(trimmed.substring(2)))
            trimmed.startsWith("![") && trimmed.contains("](") && trimmed.endsWith(")") -> {
                val alt = trimmed.substringAfter("![").substringBefore("]")
                val path = trimmed.substringAfter("](").substringBeforeLast(")")
                nodes.add(MarkdownNode.Image(alt, path))
            }
            trimmed.isNotEmpty() -> nodes.add(MarkdownNode.Paragraph(trimmed))
        }
    }

    if (inTable) flushTable()
    if (inCodeBlock && codeBuffer.isNotEmpty()) {
        nodes.add(MarkdownNode.CodeBlock(codeBuffer.toString().trimEnd(), codeLang))
    }

    return nodes
}

@Composable
fun MarkdownViewer(
    markdownText: String,
    workspaceDirPath: String?,
    searchQuery: String = "",
    modifier: Modifier = Modifier
) {
    val nodes = remember(markdownText) { parseMarkdownNodes(markdownText) }
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        for (node in nodes) {
            when (node) {
                is MarkdownNode.Heading -> {
                    when (node.level) {
                        1 -> Text(
                            text = highlightText(node.text, searchQuery),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp).testTag("md_h1")
                        )
                        2 -> Text(
                            text = highlightText(node.text, searchQuery),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp).testTag("md_h2")
                        )
                        else -> Text(
                            text = highlightText(node.text, searchQuery),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp).testTag("md_h3")
                        )
                    }
                }
                is MarkdownNode.Paragraph -> {
                    Text(
                        text = highlightText(node.text, searchQuery),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 22.sp
                    )
                }
                is MarkdownNode.CodeBlock -> {
                    MarkdownCodeBlock(
                        code = node.code,
                        language = node.language,
                        searchQuery = searchQuery,
                        context = context
                    )
                }
                is MarkdownNode.Table -> {
                    MarkdownTable(rows = node.rows, searchQuery = searchQuery)
                }
                is MarkdownNode.Blockquote -> {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 3.dp,
                                color = MaterialTheme.colorScheme.secondary,
                                shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)
                            )
                    ) {
                        Text(
                            text = highlightText(node.text, searchQuery),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
                is MarkdownNode.Bullet -> {
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(
                            text = "• ",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = highlightText(node.text, searchQuery),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                is MarkdownNode.Image -> {
                    val imageFile = if (workspaceDirPath != null) {
                        File(workspaceDirPath, node.relativePath)
                    } else null

                    if (imageFile != null && imageFile.exists()) {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(imageFile)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = node.altText,
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                            )
                        }
                    } else {
                        Text(
                            text = "🖼️ [Image: ${node.altText}]",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
                is MarkdownNode.Divider -> {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }
        }
    }
}

@Composable
fun MarkdownTable(rows: List<List<String>>, searchQuery: String) {
    if (rows.isEmpty()) return

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            val header = rows.first()
            Row(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(4.dp))
                    .padding(vertical = 8.dp, horizontal = 4.dp)
            ) {
                header.forEach { cell ->
                    Text(
                        text = highlightText(cell, searchQuery),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .width(130.dp)
                            .padding(horizontal = 6.dp)
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            for (r in 1 until rows.size) {
                val row = rows[r]
                val bg = if (r % 2 == 0) MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f) else Color.Transparent
                Row(
                    modifier = Modifier
                        .background(bg, RoundedCornerShape(4.dp))
                        .padding(vertical = 6.dp, horizontal = 4.dp)
                ) {
                    row.forEach { cell ->
                        Text(
                            text = highlightText(cell, searchQuery),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .width(130.dp)
                                .padding(horizontal = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MarkdownCodeBlock(code: String, language: String, searchQuery: String, context: Context) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language.ifBlank { "code" },
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Code", code))
                        Toast.makeText(context, "Code copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy code",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = highlightText(code, searchQuery),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .padding(12.dp)
                    .horizontalScroll(rememberScrollState())
            )
        }
    }
}

@Composable
fun highlightText(text: String, query: String): AnnotatedString {
    if (query.isBlank()) return AnnotatedString(text)

    return buildAnnotatedString {
        var start = 0
        val lowerText = text.lowercase()
        val lowerQuery = query.lowercase()

        while (true) {
            val index = lowerText.indexOf(lowerQuery, start)
            if (index == -1) {
                append(text.substring(start))
                break
            }

            append(text.substring(start, index))
            withStyle(
                style = SpanStyle(
                    background = Color(0xFFFFE082),
                    color = Color.Black,
                    fontWeight = FontWeight.Bold
                )
            ) {
                append(text.substring(index, index + query.length))
            }
            start = index + query.length
        }
    }
}
