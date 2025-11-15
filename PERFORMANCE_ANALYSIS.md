# Terminal Rendering Performance Analysis

**Date**: 2025-11-14
**Status**: 🔴 CRITICAL BOTTLENECKS IDENTIFIED

## Executive Summary

The terminal rendering pipeline has **critical performance bottlenecks** that result in excessive draw calls and object allocations. For a standard 80x24 terminal (1920 characters), the current implementation makes **~1920 individual draw calls per frame**, severely limiting rendering performance.

## Bottlenecks Identified

### 1. ⚠️ CRITICAL: Character-by-Character Rendering
**Location**: `ProperTerminal.kt:324-501`
**Impact**: VERY HIGH
**Severity**: Critical

**Problem**:
- Each character is rendered individually with separate `drawText()` calls
- No batching of consecutive characters with the same style
- For 80x24 terminal: ~1920 draw calls per frame
- For 160x48 terminal: ~7680 draw calls per frame

**Code**:
```kotlin
// Lines 444, 455, 465, 473 - separate drawText() for each character
while (col < width) {
    val char = line.charAt(col)
    val style = line.getStyleAt(col)
    // ... process single character ...
    drawText(
        textMeasurer = textMeasurer,
        text = char.toString(),  // Single character!
        topLeft = Offset(x, y),
        style = textStyle
    )
    col++
}
```

**Impact**:
- GPU/CPU overhead from thousands of draw calls
- Poor cache utilization
- Frame rate drops with large terminals

### 2. ⚠️ HIGH: TextStyle Object Creation in Hot Path
**Location**: `ProperTerminal.kt:417-424`
**Impact**: HIGH
**Severity**: High

**Problem**:
- New `TextStyle` object created for EVERY character
- No caching or reuse of common style combinations
- Heavy GC pressure from ~1920 allocations per frame

**Code**:
```kotlin
// Line 417-424 - creates new TextStyle for each character
val textStyle = TextStyle(
    color = fgColor,
    fontFamily = measurementStyle.fontFamily,
    fontSize = 14.sp,
    fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
    fontStyle = if (isItalic) androidx.compose.ui.text.font.FontStyle.Italic
               else androidx.compose.ui.text.font.FontStyle.Normal
)
```

**Impact**:
- Memory allocation overhead
- GC pressure affecting frame stability
- CPU time wasted on object creation

### 3. ⚠️ MEDIUM: Individual Background Rectangles
**Location**: `ProperTerminal.kt:401-405`
**Impact**: MEDIUM
**Severity**: Medium

**Problem**:
- Each character's background drawn as separate rectangle
- No merging of consecutive cells with same color
- ~1920 `drawRect()` calls per frame

**Code**:
```kotlin
// Line 401-405 - separate rect for each character
drawRect(
    color = bgColor,
    topLeft = Offset(x, y),
    size = Size(bgWidth, cellHeight)
)
```

**Impact**:
- Additional draw calls
- Overdraw issues

### 4. ⚠️ MEDIUM: Redundant Text Measurement
**Location**: `ProperTerminal.kt:431`
**Impact**: MEDIUM
**Severity**: Medium

**Problem**:
- Every double-width character measured individually
- No caching of glyph metrics
- Expensive measurement operation in hot loop

**Code**:
```kotlin
// Line 431 - measure each DWC individually
val measurement = textMeasurer.measure(char.toString(), textStyle)
val glyphWidth = measurement.size.width.toFloat()
```

**Impact**:
- CPU overhead for text measurement
- Poor performance with many CJK/emoji characters

## Proposed Optimizations

### Optimization 1: Text Batching (Priority: CRITICAL)
**Expected Improvement**: 10-20x reduction in draw calls

**Implementation**:
- Batch consecutive characters with identical style into single `drawText()` call
- Track style changes and flush batch when style changes
- Expected: ~1920 calls → ~100-200 calls (80-95% reduction)

**Pseudo-code**:
```kotlin
var batchStart = 0
var batchText = StringBuilder()
var batchStyle = currentStyle

while (col < width) {
    if (styleChanged || isSpecialChar) {
        // Flush accumulated batch
        drawText(
            textMeasurer = textMeasurer,
            text = batchText.toString(),
            topLeft = Offset(batchStart * cellWidth, y),
            style = batchStyle
        )
        batchText.clear()
        batchStart = col
        batchStyle = newStyle
    }
    batchText.append(char)
    col++
}
```

### Optimization 2: TextStyle Caching (Priority: HIGH)
**Expected Improvement**: Eliminate 1920 allocations/frame

**Implementation**:
- Pre-create TextStyle objects for common combinations
- Use cache key: (color, isBold, isItalic)
- Reuse cached styles instead of creating new ones

**Structure**:
```kotlin
data class StyleKey(val color: Color, val isBold: Boolean, val isItalic: Boolean)
val styleCache = mutableMapOf<StyleKey, TextStyle>()

fun getCachedStyle(color: Color, isBold: Boolean, isItalic: Boolean): TextStyle {
    val key = StyleKey(color, isBold, isItalic)
    return styleCache.getOrPut(key) {
        TextStyle(
            color = color,
            fontFamily = measurementStyle.fontFamily,
            fontSize = 14.sp,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (isItalic) FontStyle.Italic else FontStyle.Normal
        )
    }
}
```

### Optimization 3: Background Rectangle Batching (Priority: MEDIUM)
**Expected Improvement**: 5-10x reduction in rectangle draw calls

**Implementation**:
- Merge consecutive cells with same background color
- Draw single rectangle spanning multiple cells
- Expected: ~1920 calls → ~200-400 calls

**Approach**:
```kotlin
var bgStart = 0
var bgColor = currentBgColor
var bgWidth = cellWidth

while (col < width) {
    if (backgroundColorChanged) {
        // Flush accumulated background
        drawRect(
            color = bgColor,
            topLeft = Offset(bgStart * cellWidth, y),
            size = Size(bgWidth, cellHeight)
        )
        bgStart = col
        bgWidth = cellWidth
        bgColor = newBgColor
    } else {
        bgWidth += cellWidth
    }
    col++
}
```

### Optimization 4: Glyph Metrics Caching (Priority: LOW)
**Expected Improvement**: Eliminate redundant measurements

**Implementation**:
- Cache measured glyph widths for double-width characters
- Key: (char code, textStyle)
- Reduces CPU overhead for CJK/emoji rendering

## Implementation Plan

### Phase 1: Text Batching (Week 1)
- [ ] Implement batch accumulation logic
- [ ] Handle style change detection
- [ ] Handle special characters (double-width, emoji)
- [ ] Test with various terminal outputs
- [ ] Benchmark improvement

### Phase 2: Style Caching (Week 1)
- [ ] Design style cache structure
- [ ] Implement cache lookup/storage
- [ ] Integrate with rendering loop
- [ ] Test color correctness
- [ ] Benchmark improvement

### Phase 3: Background Batching (Week 2)
- [ ] Implement background span detection
- [ ] Handle double-width character backgrounds
- [ ] Test visual correctness
- [ ] Benchmark improvement

### Phase 4: Glyph Caching (Week 2)
- [ ] Design glyph metrics cache
- [ ] Implement measurement caching
- [ ] Integrate with double-width rendering
- [ ] Benchmark improvement

## Expected Results

| Metric | Before | After | Improvement |
|--------|--------|-------|-------------|
| Draw calls/frame (80x24) | ~1920 | ~100-200 | 90-95% ↓ |
| TextStyle allocations/frame | ~1920 | ~10-20 | 99% ↓ |
| Background rects/frame | ~1920 | ~200-400 | 80-90% ↓ |
| Frame time (estimated) | ~16ms | ~2-4ms | 75-87% ↓ |
| Max terminal size | 80x24 | 200x60+ | 2.5x+ ↑ |

## Testing Strategy

1. **Functional Testing**:
   - Verify text renders correctly
   - Test CJK/emoji rendering
   - Validate color accuracy
   - Check underline/bold/italic rendering

2. **Performance Testing**:
   - Benchmark frame time before/after
   - Profile draw call count
   - Monitor GC pressure
   - Test with large terminals (160x48, 200x60)

3. **Regression Testing**:
   - Run existing test suite
   - Visual comparison screenshots
   - Unicode rendering tests

## References

- Current implementation: `compose-ui/src/desktopMain/kotlin/org/jetbrains/jediterm/compose/demo/ProperTerminal.kt:316-604`
- TerminalRenderer interface: `compose-ui/src/commonMain/kotlin/org/jetbrains/jediterm/compose/TerminalRenderer.kt:72-77`
- Related: Font loading success (FONT_LOADING_SUCCESS.md)
