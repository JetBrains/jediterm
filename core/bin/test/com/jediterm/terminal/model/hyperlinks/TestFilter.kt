/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jediterm.terminal.model.hyperlinks

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.regex.Pattern

class TestFilter(private val completeImmediately: Boolean) : AsyncHyperlinkFilter {
  private val futures: MutableList<FutureWithResult> = CopyOnWriteArrayList<FutureWithResult>()

  override fun apply(lineInfo: AsyncHyperlinkFilter.LineInfo): CompletableFuture<LinkResult?> {
    val line = lineInfo.line ?: return CompletableFuture.completedFuture(null)
    val matcher = PATTERN.matcher(line)
    if (matcher.find()) {
      val startInd = matcher.start(0)
      val endInd = matcher.end(0)
      val linkResult = LinkResult(LinkResultItem(startInd, endInd, LinkInfo {}))
      val futureWithResult = FutureWithResult(linkResult)
      futures.add(futureWithResult)
      if (completeImmediately) {
        futureWithResult.complete()
      }
      return futureWithResult.future
    }
    return CompletableFuture.completedFuture(null)
  }

  fun completeAll() {
    futures.forEach {
      it.complete()
      futures.remove(it)
    }
  }

  private class FutureWithResult(private val linkResult: LinkResult) {

    val future: CompletableFuture<LinkResult?> = CompletableFuture()

    fun complete() {
      future.complete(linkResult)
    }
  }

  companion object {
    private const val PREFIX = "my_link:"
    private val PATTERN : Pattern = Pattern.compile("my_link:\\p{Alnum}*") 

    @JvmStatic
    fun formatLink(text: String): String {
      return PREFIX + text
    }
  }
}
