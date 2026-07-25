package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.util.CacheManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CacheManagerTest {

    private lateinit var context: Context
    private lateinit var cacheManager: CacheManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        cacheManager = CacheManager(context)
    }

    @Test
    fun testStorageUsageAndClearPreviews() = runBlocking {
        val workspaceDir = cacheManager.getWorkspaceDir("test_doc_123")
        val previewsDir = File(workspaceDir, "previews").apply { mkdirs() }

        val samplePreview = File(previewsDir, "page_1.webp")
        samplePreview.writeText("sample preview image data")

        val convertedMd = File(workspaceDir, "document.md")
        convertedMd.writeText("# Test Markdown Document Content")

        var stats = cacheManager.getStorageUsage()
        assertTrue(stats.totalWorkspaceSizeBytes > 0)
        assertTrue(stats.previewsSizeBytes > 0)
        assertTrue(stats.convertedFilesSizeBytes > 0)

        // Clear previews
        cacheManager.clearPreviews()

        stats = cacheManager.getStorageUsage()
        assertEquals(0L, stats.previewsSizeBytes)
        assertTrue(convertedMd.exists())

        // Clear converted files
        cacheManager.clearConvertedFiles()
        stats = cacheManager.getStorageUsage()
        assertEquals(0L, stats.convertedFilesSizeBytes)
    }
}
