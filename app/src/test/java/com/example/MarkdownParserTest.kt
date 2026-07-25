package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.ui.components.MarkdownNode
import com.example.ui.components.parseMarkdownNodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MarkdownParserTest {

    @Test
    fun testHeadingsAndParagraphs() {
        val markdown = """
            # Heading 1
            ## Heading 2
            ### Heading 3
            
            This is a paragraph of text in markdown.
        """.trimIndent()

        val nodes = parseMarkdownNodes(markdown)

        assertEquals(4, nodes.size)
        assertTrue(nodes[0] is MarkdownNode.Heading && (nodes[0] as MarkdownNode.Heading).level == 1)
        assertTrue(nodes[1] is MarkdownNode.Heading && (nodes[1] as MarkdownNode.Heading).level == 2)
        assertTrue(nodes[2] is MarkdownNode.Heading && (nodes[2] as MarkdownNode.Heading).level == 3)
        assertTrue(nodes[3] is MarkdownNode.Paragraph && (nodes[3] as MarkdownNode.Paragraph).text.contains("paragraph"))
    }

    @Test
    fun testCodeBlockAndTables() {
        val markdown = """
            ```kotlin
            val x = 10
            println(x)
            ```

            | Header 1 | Header 2 |
            | --- | --- |
            | Cell 1 | Cell 2 |
        """.trimIndent()

        val nodes = parseMarkdownNodes(markdown)

        val codeNode = nodes.filterIsInstance<MarkdownNode.CodeBlock>().firstOrNull()
        assertTrue(codeNode != null)
        assertEquals("kotlin", codeNode?.language)
        assertTrue(codeNode?.code?.contains("val x = 10") == true)

        val tableNode = nodes.filterIsInstance<MarkdownNode.Table>().firstOrNull()
        assertTrue(tableNode != null)
        assertEquals(2, tableNode?.rows?.size)
        assertEquals("Header 1", tableNode?.rows?.first()?.first())
    }

    @Test
    fun testBlockquotesBulletsAndImages() {
        val markdown = """
            > Important quote line
            
            - Bullet item 1
            * Bullet item 2
            
            ![Alt Text](previews/page_1.webp)
            
            ---
        """.trimIndent()

        val nodes = parseMarkdownNodes(markdown)

        val quoteNode = nodes.filterIsInstance<MarkdownNode.Blockquote>().firstOrNull()
        assertTrue(quoteNode != null)
        assertEquals("Important quote line", quoteNode?.text)

        val bulletNodes = nodes.filterIsInstance<MarkdownNode.Bullet>()
        assertEquals(2, bulletNodes.size)

        val imageNode = nodes.filterIsInstance<MarkdownNode.Image>().firstOrNull()
        assertTrue(imageNode != null)
        assertEquals("Alt Text", imageNode?.altText)
        assertEquals("previews/page_1.webp", imageNode?.relativePath)

        val dividerNode = nodes.filterIsInstance<MarkdownNode.Divider>().firstOrNull()
        assertTrue(dividerNode != null)
    }
}
