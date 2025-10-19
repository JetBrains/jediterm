import com.jediterm.app.UrlFilter
import com.jediterm.terminal.model.hyperlinks.LinkResult
import com.jediterm.terminal.model.hyperlinks.LinkResultItem
import org.junit.Assert
import org.junit.Test

class UrlFilterTest {

  private val filter: UrlFilter = UrlFilter()

  @Test
  fun testSingleFileHyperlink() {
    assertHyperlink(" at file:///home/file.txt", 4, 25)
    assertHyperlink("file:///home/file.txt", 0, 21)
    assertHyperlink("text before file:///home/file.txt:3 some test after", 12, 35)
    assertHyperlink("text before file:///home/file.txt:3:30 some test after", 12, 38)
    assertHyperlink("Click file:///C:/Users/user/file.js:12:40", 6, 41)
    assertHyperlink(
      "See file:////wsl$/Ubuntu-20.04/projects/report.txt:4",
      4, 52
    )
  }

  @Test
  fun testMinimalFileProtocol() {
    assertHyperlink("Click file:/path/to/file.diff:2:4 to see the difference", 6, 33)
    assertHyperlink("file:/path/to/file.txt", 0, 22)
  }

  @Test
  fun testNoFileHyperlink() {
    assertHyperlink("file://path/to/file.txt", 0, 23)
  }

  @Test
  fun testSingleBrowserHyperlink() {
    assertHyperlink("http://test.com", 0, 15)
    assertHyperlink(" at http://test.com", 4, 19)
    assertHyperlink("http://test.com?q=text&", 0, 23)
  }

  @Test
  fun testMultipleHyperlinks() {
    assertHyperlinks(
      "file:///home/file1.txt -> file:///home/file2.txt", listOf(
        LinkInfo(0, 22),
        LinkInfo(26, 48)
      )
    )
  }

  @Test
  fun testUrlEncodedFileLinks() {
    assertHyperlink(
      "e file:///home/path%20with%20space/file.kt:3 Expecting an expression", 2, 44,
    )
    assertHyperlink(
      "w file:///home/wrongly%EncodedPath/file.kt:3:10 Variable 'q' is never used", 2, 47,
    )
    assertHyperlink(
      "Click file:////wsl$/Ubuntu-20.04/path-test-gradle%206/src/main/kotlin/base/Starter.kt:4:10",
      6, 90
    )
  }

  @Test
  fun testUrlAndFileInOneString() {
    assertHyperlinks(
      "w: file:///Users/kmp-app-march/shared/build.gradle.kts:9:13: 'kotlinOptions(KotlinJvmOptions.() -> Unit): Unit' is deprecated. Please migrate to the compilerOptions DSL. More details are here: https://kotl.in/u1r8ln\n\n",
      listOf(
        LinkInfo(3, 59),
        LinkInfo(193, 215)
      )
    )
  }

  private fun assertHyperlink(line: String, highlightStartOffset: Int, highlightEndOffset: Int) {
    assertHyperlinks(line, listOf(LinkInfo(highlightStartOffset, highlightEndOffset)))
  }

  private fun assertHyperlinks(line: String, expectedLinks: List<LinkInfo>) {
    assertHyperlinks(filter.apply(line), expectedLinks)
  }

  private fun assertHyperlinks(result: LinkResult?, infos: List<LinkInfo>) {
    val items = result?.items ?: emptyList()
    Assert.assertEquals(infos.size, items.size)
    for (i in infos.indices) {
      assertLink(infos[i], items[i])
    }
  }

  private fun assertLink(expected: LinkInfo, actual: LinkResultItem) {
    Assert.assertEquals(expected.highlightStartOffset, actual.startOffset)
    Assert.assertEquals(expected.highlightEndOffset, actual.endOffset)
  }

  private open class LinkInfo(val highlightStartOffset: Int, val highlightEndOffset: Int)

}
