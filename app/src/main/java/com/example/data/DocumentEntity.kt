package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey val id: String,
    val uriString: String,
    val fileName: String,
    val fileType: String, // PDF, DOCX, PPTX, XLSX, CSV, TXT, JSON, HTML, MARKDOWN, CODE
    val fileSize: Long,
    val convertedTimestamp: Long = System.currentTimeMillis(),
    val pageCount: Int,
    val fullMarkdownPath: String,
    val workspaceDirPath: String,
    val lastReadPage: Int = 0,
    val lastReadPosition: Int = 0,
    val metadataJson: String = "{}"
)
