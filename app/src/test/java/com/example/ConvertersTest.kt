package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.converter.DocxConverter
import com.example.converter.PDFConverter
import com.example.converter.PptxConverter
import com.example.converter.TextConverter
import com.example.converter.XlsxConverter
import com.example.data.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConvertersTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testConverterSupport() {
        val pdfConverter = PDFConverter()
        val docxConverter = DocxConverter()
        val pptxConverter = PptxConverter()
        val xlsxConverter = XlsxConverter()
        val textConverter = TextConverter()

        assertTrue(pdfConverter.supports("pdf", "application/pdf"))
        assertTrue(docxConverter.supports("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
        assertTrue(pptxConverter.supports("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"))
        assertTrue(xlsxConverter.supports("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        assertTrue(textConverter.supports("csv", "text/csv"))
        assertTrue(textConverter.supports("json", "application/json"))
        assertTrue(textConverter.supports("kt", "text/x-kotlin"))
    }

    @Test
    fun testTextConverterCsvAndJson() = runBlocking {
        val textConverter = TextConverter()

        // Test CSV conversion
        val csvFile = File(context.cacheDir, "sample.csv")
        csvFile.writeText("Name,Age,Role\nAlice,30,Developer\nBob,25,Designer")

        val csvUri = Uri.fromFile(csvFile)
        val workspaceDir = File(context.cacheDir, "workspace_test_csv").apply { mkdirs() }

        val result = textConverter.convert(
            context = context,
            uri = csvUri,
            fileName = "sample.csv",
            documentId = UUID.randomUUID().toString(),
            workspaceDir = workspaceDir
        )

        assertNotNull(result)
        assertEquals("CSV", result.fileType)
        assertTrue(File(result.fullMarkdownPath).exists())
        val mdContent = File(result.fullMarkdownPath).readText()
        assertTrue(mdContent.contains("| Name | Age | Role |"))
        assertTrue(mdContent.contains("| Alice | 30 | Developer |"))

        // Test JSON conversion
        val jsonFile = File(context.cacheDir, "sample.json")
        jsonFile.writeText("""{"title":"Doc2MD","version":1}""")
        val jsonUri = Uri.fromFile(jsonFile)
        val workspaceJsonDir = File(context.cacheDir, "workspace_test_json").apply { mkdirs() }

        val jsonResult = textConverter.convert(
            context = context,
            uri = jsonUri,
            fileName = "sample.json",
            documentId = UUID.randomUUID().toString(),
            workspaceDir = workspaceJsonDir
        )

        assertNotNull(jsonResult)
        assertEquals("JSON", jsonResult.fileType)
        val jsonMd = File(jsonResult.fullMarkdownPath).readText()
        assertTrue(jsonMd.contains("```json"))
        assertTrue(jsonMd.contains("Doc2MD"))
    }

    @Test
    fun testRoomDatabaseIntegration() = runBlocking {
        val db = AppDatabase.getDatabase(context)
        val dao = db.documentDao()

        val docEntity = com.example.data.DocumentEntity(
            id = "doc123",
            uriString = "file:///dummy/doc.pdf",
            fileName = "TestDocument.pdf",
            fileType = "PDF",
            fileSize = 1024L,
            pageCount = 2,
            fullMarkdownPath = "/dummy/path/doc.md",
            workspaceDirPath = "/dummy/path"
        )

        dao.insertDocument(docEntity)

        val fetched = dao.getDocumentById("doc123")
        assertNotNull(fetched)
        assertEquals("TestDocument.pdf", fetched?.fileName)
        assertEquals(2, fetched?.pageCount)

        dao.deleteDocumentById("doc123")
        val afterDelete = dao.getDocumentById("doc123")
        assertTrue(afterDelete == null)
    }
}
