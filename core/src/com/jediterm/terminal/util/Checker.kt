package com.jediterm.terminal.util

class Checker {

  companion object {
    @JvmStatic
    fun main(args: Array<String>) {
      val comparison = Comparison(
        listOf(
          JediTermDoubleWidthProvider(),
          //WidecharwidthProvider(),
          Icu4jProvider(),
          //NativeDoubleWidthProvider()
        )
      )
      var mismatches = 0
      for (ch in 0..0x10FFFF) {
        if (!comparison.check(ch)) {
          mismatches++
        }
      }
      println("\nMismatches: $mismatches")
    }
  }
}

class Comparison(val providers: List<DoubleWidthProvider>) {
  fun check(ch: Int): Boolean {
    val result = providers.map {
      it to it.isDoubleWidth(ch, false)
    }
    val distinctResults = result.map { it.second }.distinct()
    check(distinctResults.isNotEmpty())
    if (distinctResults.size > 1) {
      println("Different results for character $ch ${ch.toChar()}:")
      for (pair in result) {
        println("    ${pair.first.name}: ${pair.second}")
      }
    }
    return distinctResults.size == 1
  }
}
