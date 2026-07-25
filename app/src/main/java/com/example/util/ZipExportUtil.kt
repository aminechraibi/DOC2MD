package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ZipExportUtil {

    suspend fun createWorkspaceZip(context: Context, workspaceDirPath: String, fileNameWithoutExt: String): File? = withContext(Dispatchers.IO) {
        val workspaceDir = File(workspaceDirPath)
        if (!workspaceDir.exists() || !workspaceDir.isDirectory) return@withContext null

        val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val zipFile = File(exportsDir, "${fileNameWithoutExt}_Doc2MD_Workspace.zip")

        try {
            ZipOutputStream(FileOutputStream(zipFile)).use { zipOut ->
                workspaceDir.walkTopDown().forEach { file ->
                    if (file.isFile) {
                        val relativePath = workspaceDir.toPath().relativize(file.toPath()).toString()
                        zipOut.putNextEntry(ZipEntry(relativePath))
                        FileInputStream(file).use { input ->
                            input.copyTo(zipOut)
                        }
                        zipOut.closeEntry()
                    }
                }
            }
            zipFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun shareZipFile(context: Context, zipFile: File) {
        val authority = "${context.packageName}.fileprovider"
        val contentUri: Uri = FileProvider.getUriForFile(context, authority, zipFile)

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, "Doc2MD Workspace Export - ${zipFile.name}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Markdown Workspace ZIP"))
    }
}
