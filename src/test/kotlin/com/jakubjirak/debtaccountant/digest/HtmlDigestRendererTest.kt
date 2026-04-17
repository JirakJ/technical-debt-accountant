package com.jakubjirak.debtaccountant.digest

import com.jakubjirak.debtaccountant.digest.DigestTestFactory.item
import com.jakubjirak.debtaccountant.model.DebtPriority
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HtmlDigestRendererTest {

    private val now = 1_713_355_200_000L

    private fun build(): DigestData = DigestDataCollector(
        valuator = { it.estimatedFixMinutes.toDouble() },
        monthlyInterestValuator = { it.totalWorkaroundMinutes.toDouble() },
        slaProvider = SlaSummaryProvider { "OK" }
    ).collect(
        listOf(
            item("<xss>", filePath = "src/A&B.kt", description = "bad <script> desc",
                priority = DebtPriority.HIGH, estimatedFixMinutes = 60)
        ),
        null, DigestPeriod.Last7, "Proj & Co", now
    )

    @Test
    fun `output is self contained html document`() {
        val html = HtmlDigestRenderer.render(build())
        assertTrue(html.startsWith("<!DOCTYPE html>"))
        assertTrue(html.contains("<style>"))
        assertTrue(html.contains("</html>"))
        assertFalse(html.contains("http://"), "No external references")
        assertFalse(html.contains("<script>bad"), "User scripts escaped")
    }

    @Test
    fun `required sections present`() {
        val html = HtmlDigestRenderer.render(build())
        for (h in listOf("Summary", "Priority Breakdown", "SLA Compliance", "Top Hotspots", "Sprint Recommendations")) {
            assertTrue(html.contains(">$h</h2>"), "Missing section: $h")
        }
    }

    @Test
    fun `entity escaping applied to descriptions and project name`() {
        val html = HtmlDigestRenderer.render(build())
        assertTrue(html.contains("Proj &amp; Co"))
        assertTrue(html.contains("src/A&amp;B.kt"))
        assertTrue(html.contains("&lt;xss&gt;"))
        assertTrue(html.contains("bad &lt;script&gt; desc"))
    }

    @Test
    fun `dark mode block included`() {
        val html = HtmlDigestRenderer.render(build())
        assertTrue(html.contains("prefers-color-scheme: dark"))
    }
}
