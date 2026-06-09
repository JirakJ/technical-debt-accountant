package com.jakubjirak.debtaccountant.scanner

import com.jakubjirak.debtaccountant.model.DebtCategory
import com.jakubjirak.debtaccountant.model.DebtPriority
import com.jakubjirak.debtaccountant.model.TechDebtItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class DebtCommentScannerTest {

    // --- scanText: marker detection -----------------------------------------

    @Test
    fun `finds TODO in line comment with colon`() {
        val hits = DebtCommentScanner.scanText("val x = 1 // TODO: extract constant", "A.kt")
        assertEquals(1, hits.size)
        assertEquals(DebtMarker.TODO, hits[0].marker)
        assertEquals("extract constant", hits[0].text)
        assertEquals(1, hits[0].line)
        assertEquals("A.kt", hits[0].filePath)
    }

    @Test
    fun `finds FIXME without colon`() {
        val hits = DebtCommentScanner.scanText("// FIXME handle nulls", "A.kt")
        assertEquals(1, hits.size)
        assertEquals(DebtMarker.FIXME, hits[0].marker)
        assertEquals("handle nulls", hits[0].text)
    }

    @Test
    fun `finds HACK XXX and OPTIMIZE markers`() {
        val text = """
            // HACK working around SDK bug
            # XXX remove before release
            -- OPTIMIZE slow query
        """.trimIndent()
        val hits = DebtCommentScanner.scanText(text, "f")
        assertEquals(listOf(DebtMarker.HACK, DebtMarker.XXX, DebtMarker.OPTIMIZE), hits.map { it.marker })
        assertEquals(listOf(1, 2, 3), hits.map { it.line })
    }

    @Test
    fun `finds marker in hash python comment`() {
        val hits = DebtCommentScanner.scanText("x = 1  # TODO: vectorize", "a.py")
        assertEquals(1, hits.size)
        assertEquals("vectorize", hits[0].text)
    }

    @Test
    fun `finds marker in block comment and strips closing token`() {
        val hits = DebtCommentScanner.scanText("/* FIXME: leaking handle */", "a.c")
        assertEquals(1, hits.size)
        assertEquals("leaking handle", hits[0].text)
    }

    @Test
    fun `finds marker in html comment and strips closing token`() {
        val hits = DebtCommentScanner.scanText("<!-- TODO: add aria labels -->", "a.html")
        assertEquals(1, hits.size)
        assertEquals("add aria labels", hits[0].text)
    }

    @Test
    fun `finds marker in javadoc continuation line`() {
        val hits = DebtCommentScanner.scanText(" * TODO: document thread-safety", "A.java")
        assertEquals(1, hits.size)
        assertEquals("document thread-safety", hits[0].text)
    }

    @Test
    fun `strips author parenthetical`() {
        val hits = DebtCommentScanner.scanText("// TODO(jakub): rewrite in coroutines", "A.kt")
        assertEquals(1, hits.size)
        assertEquals("rewrite in coroutines", hits[0].text)
    }

    @Test
    fun `marker with no text yields empty text`() {
        val hits = DebtCommentScanner.scanText("// FIXME", "A.kt")
        assertEquals(1, hits.size)
        assertEquals("", hits[0].text)
    }

    @Test
    fun `reports correct line numbers across file`() {
        val text = "fun a() {}\n// TODO: one\nfun b() {}\n\n// FIXME: two\n"
        val hits = DebtCommentScanner.scanText(text, "f")
        assertEquals(listOf(2, 5), hits.map { it.line })
    }

    // --- scanText: things that must NOT match -------------------------------

    @Test
    fun `ignores lowercase todo`() {
        val hits = DebtCommentScanner.scanText("// todo: not a marker", "A.kt")
        assertTrue(hits.isEmpty())
    }

    @Test
    fun `ignores marker not preceded by comment token`() {
        val hits = DebtCommentScanner.scanText("""val s = "TODO list app"""", "A.kt")
        assertTrue(hits.isEmpty())
    }

    @Test
    fun `ignores marker as part of longer word`() {
        val hits = DebtCommentScanner.scanText("// TODOS for next sprint", "A.kt")
        assertTrue(hits.isEmpty())
    }

    @Test
    fun `truncates very long comment text`() {
        val longText = "x".repeat(500)
        val hits = DebtCommentScanner.scanText("// TODO: $longText", "A.kt")
        assertEquals(200, hits[0].text.length)
    }

    // --- toDebtItem / describe ----------------------------------------------

    @Test
    fun `toDebtItem maps marker to category priority and tags`() {
        val comment = ScannedComment("src/A.kt", 12, DebtMarker.HACK, "bypass validation")
        val item = DebtCommentScanner.toDebtItem(comment)
        assertEquals("src/A.kt", item.filePath)
        assertEquals(12, item.lineStart)
        assertEquals(12, item.lineEnd)
        assertEquals(DebtCategory.ARCHITECTURE_VIOLATION, item.category)
        assertEquals(DebtPriority.HIGH, item.priority)
        assertEquals("HACK: bypass validation", item.description)
        assertEquals(120, item.estimatedFixMinutes)
        assertTrue(DebtCommentScanner.SCAN_TAG in item.tags)
        assertTrue("hack" in item.tags)
    }

    @Test
    fun `describe falls back to marker name when text empty`() {
        val comment = ScannedComment("A.kt", 1, DebtMarker.FIXME, "")
        assertEquals("FIXME", DebtCommentScanner.describe(comment))
    }

    @Test
    fun `marker mapping table is as documented`() {
        assertEquals(DebtCategory.CODE_SMELL, DebtMarker.TODO.category)
        assertEquals(DebtPriority.MEDIUM, DebtMarker.TODO.priority)
        assertEquals(DebtCategory.CODE_SMELL, DebtMarker.FIXME.category)
        assertEquals(DebtPriority.HIGH, DebtMarker.FIXME.priority)
        assertEquals(DebtCategory.PERFORMANCE_ISSUE, DebtMarker.OPTIMIZE.category)
        assertEquals(DebtCategory.CODE_SMELL, DebtMarker.XXX.category)
        assertEquals(DebtPriority.HIGH, DebtMarker.XXX.priority)
    }

    // --- dedupe ---------------------------------------------------------------

    @Test
    fun `dedupe drops comment matching scanned item by file and line`() {
        val existing = listOf(
            TechDebtItem(
                filePath = "A.kt", lineStart = 5, lineEnd = 5,
                category = DebtCategory.CODE_SMELL, description = "TODO: anything",
                tags = mutableSetOf(DebtCommentScanner.SCAN_TAG)
            )
        )
        val scanned = listOf(ScannedComment("A.kt", 5, DebtMarker.TODO, "different text now"))
        assertTrue(DebtCommentScanner.dedupe(scanned, existing).isEmpty())
    }

    @Test
    fun `dedupe drops comment matching any item by file and description`() {
        val existing = listOf(
            TechDebtItem(
                filePath = "A.kt", lineStart = 99, lineEnd = 99,
                category = DebtCategory.CODE_SMELL, description = "TODO: extract constant"
            )
        )
        val scanned = listOf(ScannedComment("A.kt", 5, DebtMarker.TODO, "extract constant"))
        assertTrue(DebtCommentScanner.dedupe(scanned, existing).isEmpty())
    }

    @Test
    fun `dedupe keeps new comments`() {
        val existing = listOf(
            TechDebtItem(
                filePath = "A.kt", lineStart = 5, lineEnd = 5,
                category = DebtCategory.CODE_SMELL, description = "TODO: old",
                tags = mutableSetOf(DebtCommentScanner.SCAN_TAG)
            )
        )
        val scanned = listOf(
            ScannedComment("A.kt", 5, DebtMarker.TODO, "old"), // same line -> dropped
            ScannedComment("B.kt", 3, DebtMarker.FIXME, "fresh"),
        )
        val kept = DebtCommentScanner.dedupe(scanned, existing)
        assertEquals(1, kept.size)
        assertEquals("B.kt", kept[0].filePath)
    }

    @Test
    fun `dedupe with empty inventory keeps everything`() {
        val scanned = listOf(ScannedComment("A.kt", 1, DebtMarker.TODO, "x"))
        assertEquals(scanned, DebtCommentScanner.dedupe(scanned, emptyList()))
    }

    // --- scanDirectory --------------------------------------------------------

    @Test
    fun `scanDirectory finds markers with relative paths`(@TempDir dir: Path) {
        val sub = Files.createDirectories(dir.resolve("src/main"))
        Files.writeString(sub.resolve("App.kt"), "// TODO: wire DI\nfun main() {}\n")
        Files.writeString(dir.resolve("README.md"), "<!-- FIXME: outdated badge -->\n")

        val hits = DebtCommentScanner.scanDirectory(dir)
        assertEquals(2, hits.size)
        assertTrue(hits.any { it.filePath == "src/main/App.kt" && it.marker == DebtMarker.TODO })
        assertTrue(hits.any { it.filePath == "README.md" && it.marker == DebtMarker.FIXME })
    }

    @Test
    fun `scanDirectory skips build git and hidden directories`(@TempDir dir: Path) {
        for (skipped in listOf(".git", "build", "node_modules", ".tech-debt")) {
            val d = Files.createDirectories(dir.resolve(skipped))
            Files.writeString(d.resolve("x.kt"), "// TODO: should be ignored\n")
        }
        Files.writeString(dir.resolve("Visible.kt"), "// TODO: visible\n")

        val hits = DebtCommentScanner.scanDirectory(dir)
        assertEquals(1, hits.size)
        assertEquals("Visible.kt", hits[0].filePath)
    }

    @Test
    fun `scanDirectory skips binary extensions and binary content`(@TempDir dir: Path) {
        Files.write(dir.resolve("image.png"), byteArrayOf(1, 2, 3))
        Files.write(dir.resolve("data.bin"), byteArrayOf(0x2F, 0x2F, 0x20, 0, 0x54))
        Files.writeString(dir.resolve("ok.txt"), "// TODO: keep me\n")

        val hits = DebtCommentScanner.scanDirectory(dir)
        assertEquals(1, hits.size)
        assertEquals("ok.txt", hits[0].filePath)
    }

    @Test
    fun `scanDirectory on missing directory returns empty`(@TempDir dir: Path) {
        assertTrue(DebtCommentScanner.scanDirectory(dir.resolve("nope")).isEmpty())
    }
}
