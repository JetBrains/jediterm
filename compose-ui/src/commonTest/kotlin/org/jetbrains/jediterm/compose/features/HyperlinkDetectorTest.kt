package org.jetbrains.jediterm.compose.features

import kotlin.test.*

class HyperlinkDetectorTest {

    private val detector = HyperlinkDetector()

    @Test
    fun testCanContainUrl() {
        assertTrue(detector.canContainUrl("Visit https://example.com"))
        assertTrue(detector.canContainUrl("Check www.example.com"))
        assertTrue(detector.canContainUrl("Email: mailto:test@example.com"))
        assertTrue(detector.canContainUrl("File: file:///path/to/file"))
        assertFalse(detector.canContainUrl("No URL here"))
    }

    @Test
    fun testDetectHttpUrl() {
        val line = "Visit https://example.com for more info"
        val hyperlinks = detector.detectHyperlinks(line)

        assertEquals(1, hyperlinks.size)
        val link = hyperlinks[0]
        assertEquals(6, link.startColumn)
        assertEquals(25, link.endColumn)
        assertEquals("https://example.com", link.url)
        assertEquals(HyperlinkDetector.HyperlinkType.URL_PATTERN, link.type)
    }

    @Test
    fun testDetectWwwUrl() {
        val line = "Go to www.example.com"
        val hyperlinks = detector.detectHyperlinks(line)

        assertEquals(1, hyperlinks.size)
        val link = hyperlinks[0]
        assertEquals("http://www.example.com", link.url)
        assertEquals("www.example.com", link.displayText)
    }

    @Test
    fun testDetectMailtoUrl() {
        val line = "Contact: mailto:admin@example.com"
        val hyperlinks = detector.detectHyperlinks(line)

        assertEquals(1, hyperlinks.size)
        val link = hyperlinks[0]
        assertEquals("mailto:admin@example.com", link.url)
    }

    @Test
    fun testDetectFileUrl() {
        val line = "Open file:///home/user/document.txt"
        val hyperlinks = detector.detectHyperlinks(line)

        assertEquals(1, hyperlinks.size)
        val link = hyperlinks[0]
        assertEquals("file:///home/user/document.txt", link.url)
    }

    @Test
    fun testDetectMultipleUrls() {
        val line = "Visit https://example.com or https://another.com"
        val hyperlinks = detector.detectHyperlinks(line)

        assertEquals(2, hyperlinks.size)
        assertEquals("https://example.com", hyperlinks[0].url)
        assertEquals("https://another.com", hyperlinks[1].url)
    }

    @Test
    fun testNoUrlDetection() {
        val line = "This line has no URLs at all"
        val hyperlinks = detector.detectHyperlinks(line)

        assertEquals(0, hyperlinks.size)
    }

    @Test
    fun testFindHyperlinkAt() {
        val line = "Visit https://example.com for info"
        val hyperlinks = detector.detectHyperlinks(line)

        assertNotNull(detector.findHyperlinkAt(hyperlinks, 10))
        assertNotNull(detector.findHyperlinkAt(hyperlinks, 6))
        assertNotNull(detector.findHyperlinkAt(hyperlinks, 24))
        assertNull(detector.findHyperlinkAt(hyperlinks, 5))
        assertNull(detector.findHyperlinkAt(hyperlinks, 25))
    }

    @Test
    fun testOSC8Hyperlink() {
        val line = "Click here for info"
        val osc8Links = mapOf(
            6 to HyperlinkDetector.OSC8Info("https://example.com"),
            7 to HyperlinkDetector.OSC8Info("https://example.com"),
            8 to HyperlinkDetector.OSC8Info("https://example.com"),
            9 to HyperlinkDetector.OSC8Info("https://example.com")
        )

        val hyperlinks = detector.detectHyperlinks(line, osc8Links)

        assertTrue(hyperlinks.isNotEmpty())
        val link = hyperlinks.find { it.type == HyperlinkDetector.HyperlinkType.OSC8_SEQUENCE }
        assertNotNull(link)
        assertEquals("https://example.com", link.url)
        assertEquals(6, link.startColumn)
    }

    @Test
    fun testParseOSC8Sequence() {
        // Valid OSC 8 with URL
        val info1 = parseOSC8Sequence(";https://example.com")
        assertNotNull(info1)
        assertEquals("https://example.com", info1.url)

        // OSC 8 with ID parameter
        val info2 = parseOSC8Sequence("id=123;https://example.com")
        assertNotNull(info2)
        assertEquals("https://example.com", info2.url)
        assertEquals("123", info2.id)

        // OSC 8 with empty URL (end of hyperlink)
        val info3 = parseOSC8Sequence(";")
        assertNull(info3)
    }

    @Test
    fun testUrlWithSpecialCharacters() {
        val line = "API: https://api.example.com/v1/users?id=123&active=true"
        val hyperlinks = detector.detectHyperlinks(line)

        assertEquals(1, hyperlinks.size)
        assertEquals("https://api.example.com/v1/users?id=123&active=true", hyperlinks[0].url)
    }

    @Test
    fun testFtpUrl() {
        val line = "Download from ftp://ftp.example.com/file.zip"
        val hyperlinks = detector.detectHyperlinks(line)

        assertEquals(1, hyperlinks.size)
        assertEquals("ftp://ftp.example.com/file.zip", hyperlinks[0].url)
    }

    @Test
    fun testUrlAtLineStart() {
        val line = "https://example.com"
        val hyperlinks = detector.detectHyperlinks(line)

        assertEquals(1, hyperlinks.size)
        assertEquals(0, hyperlinks[0].startColumn)
    }

    @Test
    fun testUrlAtLineEnd() {
        val line = "Visit https://example.com"
        val hyperlinks = detector.detectHyperlinks(line)

        assertEquals(1, hyperlinks.size)
        assertEquals(25, hyperlinks[0].endColumn)
    }
}
