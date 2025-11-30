# TerminalTextBuffer Migration Plan
## Snapshot-Based Access Strategy

**Created**: November 29, 2025
**Status**: Planning Phase
**Goal**: Eliminate all direct lock/unlock usage, migrate to BufferSnapshot-based access

---

## 📊 Usage Analysis Summary

### Direct Lock/Unlock Locations Found

| File | Function | Lines | Lock Duration | Frequency | Priority |
|------|----------|-------|---------------|-----------|----------|
| DebugDataCollector.kt | captureTerminalState() | 80-138 | ~5-10ms | Every 100ms | **HIGH** |
| BuiltinActions.kt | extractSelectedText() | 221-249 | ~1-2ms | On copy | **MEDIUM** |
| ProperTerminal.kt | selectWordAt() | 1886-1898 | ~0.5-1ms | On double-click | **LOW** |
| ProperTerminal.kt | selectLineAt() | 1912-1940 | ~1-3ms | On triple-click | **LOW** |

### Additional Unlocked Access Patterns

| File | Usage Pattern | Lines | Issue | Priority |
|------|---------------|-------|-------|----------|
| ProperTerminal.kt | Direct width/height access | 183-184, 499-500, 518-519 | Race conditions possible | **LOW** |
| ProperTerminal.kt | ScrollBar lambda access | 465-467 | Called frequently | **MEDIUM** |

---

## 🔴 Priority 1: DebugDataCollector.kt

### Current Code (Lines 80-138)
```kotlin
textBuffer.lock()
try {
    val height = textBuffer.height
    for (row in 0 until height) {
        val line = textBuffer.getLine(row)
        // Process line...
    }

    for (row in -100 until 0) {
        val line = textBuffer.getLine(row)
        // Process history...
    }
} finally {
    textBuffer.unlock()
}
```

### Migration Strategy
**Replace with**:
```kotlin
val snapshot = textBuffer.createSnapshot()

val screenLines = buildString {
    for (row in 0 until snapshot.height) {
        val line = snapshot.getLine(row)
        appendLine(line.text)
    }
}

val historyLines = buildString {
    for (row in -snapshot.historyLinesCount until 0) {
        val line = snapshot.getLine(row)
        appendLine(line.text)
    }
}
```

**Benefits**:
- ✅ Lock hold: 5-10ms → <1ms (83-90% reduction)
- ✅ No writer blocking during debug snapshot capture
- ✅ Cleaner code (no try/finally)

**Effort**: 30 minutes
**Risk**: Low (straightforward replacement)

---

## 🟡 Priority 2: BuiltinActions.kt - extractSelectedText()

### Current Code (Lines 221-249)
```kotlin
textBuffer.lock()
return try {
    val result = StringBuilder()
    for (row in minRow..maxRow) {
        val line = textBuffer.getLine(row) ?: continue
        // Extract text...
    }
    result.toString()
} finally {
    textBuffer.unlock()
}
```

### Migration Strategy
**Replace with**:
```kotlin
val snapshot = textBuffer.createSnapshot()
val result = StringBuilder()

for (row in minRow..maxRow) {
    val line = snapshot.getLine(row)
    val colStart = if (row == minRow) minCol else 0
    val colEnd = if (row == maxRow) maxCol else (snapshot.width - 1)

    for (col in colStart..colEnd) {
        if (col < snapshot.width) {
            val char = line.charAt(col)
            if (char != CharUtils.DWC) {
                result.append(char)
            }
        }
    }

    if (row < maxRow) {
        result.append('\n')
    }
}

return result.toString()
```

**Benefits**:
- ✅ Lock hold: 1-2ms → <1ms
- ✅ Copy operation no longer blocks writers
- ✅ No try/finally needed

**Effort**: 20 minutes
**Risk**: Low (return value stays same)

---

## 🟢 Priority 3: ProperTerminal.kt - Selection Functions

### A. selectWordAt() (Lines 1886-1898)

**Current Code**:
```kotlin
textBuffer.lock()
try {
    val clickPoint = Point(col, row)
    val startPoint = getPreviousSeparator(clickPoint, textBuffer)
    val endPoint = getNextSeparator(clickPoint, textBuffer)
    return Pair(Pair(startPoint.x, startPoint.y), Pair(endPoint.x, endPoint.y))
} finally {
    textBuffer.unlock()
}
```

**Challenge**: `getPreviousSeparator()` and `getNextSeparator()` expect `TerminalTextBuffer`

**Migration Strategy**:
1. Check if `SelectionUtil` functions can accept `BufferSnapshot`
2. If not, create snapshot-compatible versions:
   ```kotlin
   private fun getPreviousSeparatorFromSnapshot(
       point: Point,
       snapshot: BufferSnapshot
   ): Point { ... }
   ```

**OR** simpler approach:
```kotlin
val snapshot = textBuffer.createSnapshot()
val clickPoint = Point(col, row)
val startPoint = getPreviousSeparatorFromSnapshot(clickPoint, snapshot)
val endPoint = getNextSeparatorFromSnapshot(clickPoint, snapshot)
return Pair(Pair(startPoint.x, startPoint.y), Pair(endPoint.x, endPoint.y))
```

**Effort**: 1-2 hours (need to refactor SelectionUtil helpers)
**Risk**: Medium (depends on SelectionUtil implementation)

### B. selectLineAt() (Lines 1912-1940)

**Current Code**:
```kotlin
textBuffer.lock()
try {
    var startLine = row
    var endLine = row

    // Walk backwards through wrapped lines
    while (startLine > -textBuffer.historyLinesCount) {
        val prevLine = textBuffer.getLine(startLine - 1)
        if (prevLine.isWrapped) {
            startLine--
        } else {
            break
        }
    }

    // Walk forwards
    while (endLine < textBuffer.height - 1) {
        val currentLine = textBuffer.getLine(endLine)
        if (currentLine.isWrapped) {
            endLine++
        } else {
            break
        }
    }

    return Pair(Pair(0, startLine), Pair(textBuffer.width - 1, endLine))
} finally {
    textBuffer.unlock()
}
```

**Migration Strategy** (Straightforward):
```kotlin
val snapshot = textBuffer.createSnapshot()
var startLine = row
var endLine = row

// Walk backwards through wrapped lines
while (startLine > -snapshot.historyLinesCount) {
    val prevLine = snapshot.getLine(startLine - 1)
    if (prevLine.isWrapped) {
        startLine--
    } else {
        break
    }
}

// Walk forwards
while (endLine < snapshot.height - 1) {
    val currentLine = snapshot.getLine(endLine)
    if (currentLine.isWrapped) {
        endLine++
    } else {
        break
    }
}

return Pair(Pair(0, startLine), Pair(snapshot.width - 1, endLine))
```

**Benefits**:
- ✅ Lock hold: 1-3ms → <1ms
- ✅ Triple-click no longer blocks writers
- ✅ Clean, readable code

**Effort**: 15 minutes
**Risk**: Low (simple replacement)

---

## 🟢 Priority 4: Unlocked Direct Access

### Locations with Potential Race Conditions

#### ProperTerminal.kt Direct Property Access
```kotlin
// Line 183-184
val screenHeight = textBuffer.height
val historySize = textBuffer.historyLinesCount

// Line 499-500
val col = (position.x / cellWidth).toInt().coerceIn(0, textBuffer.width - 1)
val row = (position.y / cellHeight).toInt().coerceIn(0, textBuffer.height - 1)

// Line 518-519
val currentCols = textBuffer.width
val currentRows = textBuffer.height
```

**Issue**: These reads happen without locks - could read mid-update values

**Migration Strategy**:
- For UI-critical reads: Use snapshot
- For non-critical bounds checks: Accept minor race (width/height rarely change)

**Recommended**:
```kotlin
// For scroll calculations
val snapshot = remember(display.redrawTrigger.value) {
    textBuffer.createSnapshot()
}
val screenHeight = snapshot.height
val historySize = snapshot.historyLinesCount
```

**Effort**: 30 minutes
**Risk**: Low (mostly non-critical code paths)

---

## 📋 Implementation Checklist

### Phase 1: Critical Path (HIGH Priority)
- [ ] Migrate DebugDataCollector.kt to use snapshots
- [ ] Test debug panel functionality
- [ ] Verify 90% lock reduction with metrics

### Phase 2: User Operations (MEDIUM Priority)
- [ ] Migrate extractSelectedText() in BuiltinActions.kt
- [ ] Test copy/paste operations
- [ ] Verify text extraction correctness

### Phase 3: Selection Logic (LOW Priority)
- [ ] Migrate selectLineAt() (easier, do first)
- [ ] Test triple-click selection
- [ ] Migrate selectWordAt() (requires SelectionUtil refactor)
- [ ] Test double-click selection

### Phase 4: Hardening (LOW Priority)
- [ ] Audit all unlocked textBuffer property access
- [ ] Add snapshot usage where appropriate
- [ ] Document remaining unlocked access (with justification)

---

## 🎯 Expected Impact After Full Migration

| Metric | Before | After | Improvement |
|--------|--------|-------|-------------|
| Debug snapshot lock hold | 5-10ms | <1ms | 83-90% |
| Copy operation lock hold | 1-2ms | <1ms | ~50% |
| Double-click lock hold | 0.5-1ms | <1ms | ~50% |
| Triple-click lock hold | 1-3ms | <1ms | 67-75% |
| **Total writer blocking** | ~15-20ms/sec | ~5ms/sec | **75% reduction** |

Combined with scrollArea() fix:
- **Overall optimization**: 94% lock contention reduction
- **User experience**: Zero freezing during streaming output
- **Code quality**: Cleaner, no try/finally blocks

---

## 🛠 Development Guidelines

### When to Use Snapshots
✅ **USE SNAPSHOTS**:
- Reading multiple lines sequentially
- Processing buffer content (search, copy, debug)
- Any operation taking >0.5ms
- Any operation called frequently

❌ **DIRECT ACCESS OK**:
- Single property read (width, height) for bounds checking
- Non-critical UI calculations
- Performance-critical hot paths (profile first!)

### Snapshot Pattern
```kotlin
// 1. Create snapshot (with lock, <1ms)
val snapshot = textBuffer.createSnapshot()

// 2. Process without lock (snapshot is immutable)
for (row in 0 until snapshot.height) {
    val line = snapshot.getLine(row)
    // Process line...
}
```

### Testing Checklist
After each migration:
- [ ] Build succeeds
- [ ] Feature functionality unchanged
- [ ] No visual regressions
- [ ] No performance degradation
- [ ] Streaming output remains smooth

---

## 📅 Timeline Estimate

| Phase | Tasks | Effort | Completion |
|-------|-------|--------|------------|
| Phase 1 | DebugDataCollector | 30 min | Day 1 |
| Phase 2 | extractSelectedText | 20 min | Day 1 |
| Phase 3 | Selection functions | 2 hours | Day 2 |
| Phase 4 | Hardening | 30 min | Day 2 |
| **Total** | **Full Migration** | **3-4 hours** | **2 days** |

---

## 🚀 Next Steps

1. **Immediate**: Implement Phase 1 (DebugDataCollector) - highest impact
2. **This Week**: Complete Phases 1-2 (critical + user operations)
3. **Next Week**: Phases 3-4 (selection + hardening)
4. **Validation**: Test with Claude streaming, large file cats, debug panel

---

*This migration plan completes the snapshot-based rendering architecture, eliminating all remaining direct lock/unlock patterns and achieving full lock-free read access to terminal buffer.*
