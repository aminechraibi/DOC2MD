package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FormatPaint
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DocumentEntity
import com.example.data.PageEntity
import com.example.ui.components.MarkdownViewer
import com.example.util.ZipExportUtil
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceViewerScreen(
    documentId: String,
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var document by remember { mutableStateOf<DocumentEntity?>(null) }
    val pagesState by viewModel.getPagesForDocument(documentId).collectAsState(initial = emptyList())

    var isRawMarkdownMode by remember { mutableStateOf(false) }
    var selectedPageIndex by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchVisible by remember { mutableStateOf(false) }

    val lazyListState = rememberLazyListState()

    LaunchedEffect(documentId) {
        document = viewModel.getDocumentById(documentId)
        document?.let {
            selectedPageIndex = it.lastReadPage
        }
    }

    // Save reading position on page switch
    fun onPageSelected(pageIndex: Int) {
        selectedPageIndex = pageIndex
        viewModel.updateReadingPosition(documentId, pageIndex, 0)
    }

    val fullMarkdownText = remember(pagesState) {
        if (pagesState.isNotEmpty()) {
            pagesState.joinToString("\n\n---\n\n") { it.markdownContent }
        } else {
            document?.fullMarkdownPath?.let { path ->
                val file = File(path)
                if (file.exists()) file.readText() else ""
            } ?: ""
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = document?.fileName ?: "Document Workspace",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        document?.let { doc ->
                            Text(
                                text = "${doc.fileType} • ${doc.pageCount} ${if (doc.fileType == "PPTX") "slides" else "pages"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { isSearchVisible = !isSearchVisible }) {
                        Icon(Icons.Default.Search, contentDescription = "Search inside doc")
                    }
                    IconButton(
                        onClick = { isRawMarkdownMode = !isRawMarkdownMode },
                        modifier = Modifier.testTag("toggle_markdown_mode")
                    ) {
                        Icon(
                            imageVector = if (isRawMarkdownMode) Icons.Default.FormatPaint else Icons.Default.Code,
                            contentDescription = "Toggle Rendered/Code"
                        )
                    }
                    IconButton(onClick = {
                        document?.let { doc ->
                            coroutineScope.launch {
                                val zip = ZipExportUtil.createWorkspaceZip(context, doc.workspaceDirPath, doc.fileName)
                                zip?.let { ZipExportUtil.shareZipFile(context, it) }
                            }
                        }
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Export ZIP")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search Bar Toggle
            AnimatedVisibility(visible = isSearchVisible) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search text in document...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("workspace_search_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // Page / Slide Tabs Navigation
            if (pagesState.size > 1) {
                ScrollableTabRow(
                    selectedTabIndex = selectedPageIndex,
                    edgePadding = 16.dp,
                    divider = {}
                ) {
                    pagesState.forEachIndexed { index, page ->
                        Tab(
                            selected = selectedPageIndex == index,
                            onClick = { onPageSelected(index) },
                            text = {
                                Text(
                                    text = page.pageTitle,
                                    fontWeight = if (selectedPageIndex == index) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }
            }

            // Workspace Content View
            if (isRawMarkdownMode) {
                // Raw Markdown Text Viewer
                val activeContent = if (pagesState.size > 1) {
                    pagesState.getOrNull(selectedPageIndex)?.markdownContent ?: fullMarkdownText
                } else fullMarkdownText

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "RAW MARKDOWN WORKSPACE",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                IconButton(onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Markdown", activeContent))
                                    Toast.makeText(context, "Markdown copied to clipboard", Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy Markdown")
                                }
                            }
                            Text(
                                text = activeContent,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                            )
                        }
                    }
                }
            } else {
                // Rendered Markdown Preview
                if (pagesState.isNotEmpty()) {
                    val currentPage = pagesState.getOrNull(selectedPageIndex) ?: pagesState.first()

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        MarkdownViewer(
                            markdownText = currentPage.markdownContent,
                            workspaceDirPath = document?.workspaceDirPath,
                            searchQuery = searchQuery
                        )

                        // Speaker Notes section if PPTX
                        currentPage.speakerNotes?.let { notes ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = "💡 Speaker Notes",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = notes,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                            }
                        }
                    }
                } else {
                    MarkdownViewer(
                        markdownText = fullMarkdownText,
                        workspaceDirPath = document?.workspaceDirPath,
                        searchQuery = searchQuery,
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    )
                }
            }
        }
    }
}
