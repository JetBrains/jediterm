package com.jediterm.terminal.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Compares the bundled, dependency-free [WidecharwidthProvider] (the candidate production backend)
 * against [Icu4jProvider] (an authoritative, maintained reference) across the whole Unicode range.
 *
 * Two goals:
 *  - assert both agree on the cases that actually matter for terminal rendering, and
 *  - freeze the exact set of code points where they differ, so a future Unicode bump or library
 *    upgrade surfaces as a reviewable change to the pinned baselines below rather than silently
 *    shifting column widths.
 *
 * The Private Use Area is excluded: Unicode assigns it no width, so ICU reporting it "Ambiguous"
 * vs widecharwidth reporting it single-width is not a meaningful disagreement (and it would
 * otherwise drown out the ~400 differences that do matter under a 137k-entry pile).
 */
class DoubleWidthProviderComparisonTest {
  private val widecharwidth = WidecharwidthProvider()
  private val icu = Icu4jProvider()
  private val providers = listOf(widecharwidth, icu)

  private data class Mismatch(val cp: Int, val ambiguous: Boolean, val icu: Boolean, val wcw: Boolean)

  private fun isPrivateUse(cp: Int): Boolean =
    cp in 0xE000..0xF8FF || cp in 0xF0000..0xFFFFD || cp in 0x100000..0x10FFFD

  private fun collectMismatches(): List<Mismatch> {
    val result = ArrayList<Mismatch>()
    for (cp in 0..0x10FFFF) {
      if (cp in 0xD800..0xDFFF) continue // surrogates are never standalone scalar values
      if (isPrivateUse(cp)) continue
      for (ambiguous in booleanArrayOf(false, true)) {
        val byIcu = icu.isDoubleWidth(cp, ambiguous)
        val byWcw = widecharwidth.isDoubleWidth(cp, ambiguous)
        if (byIcu != byWcw) result.add(Mismatch(cp, ambiguous, byIcu, byWcw))
      }
    }
    return result
  }

  @Test
  fun emojiAndCjkAreDoubleWidthInBothProviders() {
    for (p in providers) {
      assertTrue("${p.name}: ✅ U+2705", p.isDoubleWidth(0x2705, false))
      assertTrue("${p.name}: ❌ U+274C", p.isDoubleWidth(0x274C, false))
      assertTrue("${p.name}: 生 U+751F", p.isDoubleWidth(0x751F, false))
      assertTrue("${p.name}: fullwidth A U+FF21", p.isDoubleWidth(0xFF21, false))
      assertFalse("${p.name}: ASCII 'a'", p.isDoubleWidth('a'.code, false))
      assertFalse("${p.name}: space", p.isDoubleWidth(' '.code, false))
    }
  }

  @Test
  fun legacyProviderReproducesPreFixEmojiBehavior() {
    val legacy = CharUtils.LegacyDoubleWidthProvider()
    assertFalse("legacy: ✅ U+2705", legacy.isDoubleWidth(0x2705, false))
    assertFalse("legacy: ❌ U+274C", legacy.isDoubleWidth(0x274C, false))
    assertTrue("widecharwidth: ✅ U+2705", widecharwidth.isDoubleWidth(0x2705, false))
    assertTrue("widecharwidth: ❌ U+274C", widecharwidth.isDoubleWidth(0x274C, false))
    // ...while still agreeing with the default provider on classic wide characters.
    assertTrue("legacy: 生 U+751F", legacy.isDoubleWidth(0x751F, false))
    assertTrue("legacy: fullwidth A U+FF21", legacy.isDoubleWidth(0xFF21, false))
  }

  @Test
  fun ambiguousWidthToggleIsHonoredByBothProviders() {
    // Box-drawing '│' (U+2502) and inverted '¡' (U+00A1) are East-Asian "ambiguous width".
    for (p in providers) {
      for (cp in intArrayOf(0x2502, 0x00A1)) {
        assertFalse("${p.name}: U+%04X default".format(cp), p.isDoubleWidth(cp, false))
        assertTrue("${p.name}: U+%04X ambiguous".format(cp), p.isDoubleWidth(cp, true))
      }
    }
  }

  @Test
  fun inDefaultModeProvidersOnlyDifferOnCombiningMarks() {
    // With ambiguous=false the two backends agree on every assigned, non-PUA scalar value EXCEPT
    // a handful of combining marks: widecharwidth correctly treats them as zero-width, while the
    // ICU provider only consults East_Asian_Width and so misses the Combining property and reports
    // them "wide". This is a limitation of the EAW-only ICU approach, not of ICU itself.
    val defaultModeMismatches = collectMismatches().filter { !it.ambiguous }
    for (m in defaultModeMismatches) {
      assertEquals(
        "U+%04X should differ only because widecharwidth classifies it COMBINING".format(m.cp),
        WcWidth.Type.COMBINING, WcWidth.Type.of(m.cp))
      assertTrue("U+%04X: expected ICU=true, wcw=false".format(m.cp), m.icu && !m.wcw)
    }
    assertEquals("combining marks ICU widens in default mode", 11, defaultModeMismatches.size)
  }

  @Test
  fun mismatchProfileMatchesPinnedBaseline() {
    val mismatches = collectMismatches()
    val byCategory = mismatches.groupingBy {
      "type=${WcWidth.Type.of(it.cp)} ambiguous=${it.ambiguous} icu=${it.icu} wcw=${it.wcw}"
    }.eachCount().toSortedMap()

    println("=== widecharwidth vs ICU4J (PUA excluded): ${mismatches.size} mismatching (codePoint, ambiguous) pairs ===")
    for ((category, count) in byCategory) {
      println("  $count\t$category")
    }

    // Baseline frozen against ICU4J 78.3 and widecharwidth's Unicode 17.0.0 table.
    // Every difference is a case where widecharwidth is the better answer for a terminal:
    //  - COMBINING: zero-width combiners that ICU's EAW-only view reports as wide/ambiguous.
    //  - NON_PRINT: a non-printing control ICU reports as ambiguous.
    val expected = sortedMapOf(
      "type=COMBINING ambiguous=false icu=true wcw=false" to 11,
      "type=COMBINING ambiguous=true icu=true wcw=false" to 379,
      "type=NON_PRINT ambiguous=true icu=true wcw=false" to 1,
    )
    assertEquals(expected, byCategory)
  }
}
