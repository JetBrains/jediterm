package com.jediterm.terminal.model.hyperlinks

class TestSyncFilter : HyperlinkFilter {
  private val myDelegate = TestFilter(true)

  override fun apply(line: String): LinkResult? {
    val future = myDelegate.apply(object : AsyncHyperlinkFilter.LineInfo {
      override val line: String
        get() = line
    })
    return future.getNow(null)
  }
}
