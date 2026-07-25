package com.example.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class StorageUsageStats(
    val previewsSizeBytes: Long,
    val convertedFilesSizeBytes: Long,
    val tempCacheSizeBytes: Long,
    val totalWorkspaceSizeBytes: Long
)

class CacheManager(private val context: Context) {

    val workspacesDir: File
        get() = File(context.cacheDir, "workspaces")

    fun getWorkspaceDir(documentId: String): File {
        val dir = File(workspacesDir, documentId)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    suspend fun getStorageUsage(): StorageUsageStats = withContext(Dispatchers.IO) {
        var previewsSize = 0L
        var convertedFilesSize = 0L
        var tempCacheSize = 0L
        var totalWorkspaceSize = 0L

        if (workspacesDir.exists()) {
            workspacesDir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    val size = file.length()
                    totalWorkspaceSize += size
                    when {
                        file.parentFile?.name == "previews" || file.name.endsWith(".webp") || file.name.endsWith(".png") -> {
                            previewsSize += size
                        }
                        file.name == "document.md" || file.name.endsWith(".md") -> {
                            convertedFilesSize += size
                        }
                    }
                }
            }
        }

        if (context.cacheDir.exists()) {
            context.cacheDir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.startsWith("temp_")) {
                    tempCacheSize += file.length()
                }
            }
        }

        StorageUsageStats(
            previewsSizeBytes = previewsSize,
            convertedFilesSizeBytes = convertedFilesSize,
            tempCacheSizeBytes = tempCacheSize,
            totalWorkspaceSizeBytes = totalWorkspaceSize
        )
    }

    suspend fun clearPreviews(): Long = withContext(Dispatchers.IO) {
        var freedBytes = 0L
        if (workspacesDir.exists()) {
            workspacesDir.walkTopDown().forEach { file ->
                if (file.isFile && (file.parentFile?.name == "previews" || file.name.endsWith(".webp"))) {
                    freedBytes += file.length()
                    file.delete()
                }
            }
        }
        freedBytes
    }

    suspend fun clearConvertedFiles(): Long = withContext(Dispatchers.IO) {
        var freedBytes = 0L
        if (workspacesDir.exists()) {
            freedBytes = getFolderSize(workspacesDir)
            workspacesDir.deleteRecursively()
            workspacesDir.mkdirs()
        }
        freedBytes
    }

    suspend fun clearTempFiles(): Long = withContext(Dispatchers.IO) {
        var freedBytes = 0L
        if (context.cacheDir.exists()) {
            context.cacheDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("temp_")) {
                    freedBytes += file.length()
                    file.deleteRecursively()
                }
            }
        }
        freedBytes
    }

    private fun getFolderSize(folder: File): Long {
        var size = 0L
        if (folder.exists()) {
            folder.walkTopDown().forEach { file ->
                if (file.isFile) size += file.length()
            }
        }
        return size
    }
}
