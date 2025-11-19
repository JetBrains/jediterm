# Phase 2: Adaptive Debouncing Design

**Project:** JediTermKt Terminal Rendering Optimization
**Date:** November 17, 2025
**Status:** 🔨 In Progress
**Based On:** Phase 1 baseline data showing 672 redraws/sec for large files

---

## Executive Summary

Design an **adaptive debouncing system** that automatically switches between three rendering modes based on output rate, reducing redraws by **51-91%** for medium/large files while maintaining zero latency for interactive use.

---

## Design Goals

### Primary Goals:
1. ✅ **Reduce redraws by 91%** for large file output (672 → 60 redraws/sec)
2. ✅ **Maintain smooth 60fps** rendering (16.67ms frame time)
3. ✅ **Zero perceptible lag** for interactive use (<16ms response)
4. ✅ **Automatic adaptation** - no manual configuration needed

### Secondary Goals:
1. ✅ **Lower CPU usage** by 60-80% during bulk output
2. ✅ **No impact** on small files (already efficient at 24 redraws/sec)
3. ✅ **Thread-safe** - works with concurrent PTY output
4. ✅ **Simple API** - minimal changes to existing code

---

## Baseline Data Analysis

### Current Performance (From Phase 1):

| File Size | Redraws | Duration | Rate | Target | Reduction Needed |
|-----------|---------|----------|------|--------|------------------|
| Idle | 52 | 30s | 1.7/sec | Keep | 0% |
| 500 lines | 1,694 | ~70s | 24/sec | 60/sec | 0% (already good) |
| 2,000 lines | 6,092 | ~50s | 122/sec | 60/sec | **-51%** |
| 10,000 lines | 30,232 | ~45s | 672/sec | 60/sec | **-91%** |

### Key Insight:
**Redraw rate scales linearly with output volume:**
- Small files: Efficient (24/sec)
- Medium files: Wasteful (122/sec = 2x too high)
- Large files: Very wasteful (672/sec = 11x too high)

**Solution:** Adaptive throttling that activates only when needed.

---

## Architecture Design

### Three Rendering Modes:

```kotlin
enum class RedrawMode(val debounceMs: Long, val description: String) {
    INTERACTIVE(16L, "60fps for typing, vim, small files"),
    HIGH_VOLUME(50L, "20fps for bulk output, triggered at >100 redraws/sec"),
    IMMEDIATE(0L, "Instant for keyboard/mouse input")
}
```

### Mode Transition Logic:

```
[INTERACTIVE] ─────> [HIGH_VOLUME] ─────> [INTERACTIVE]
   (default)         when >100/sec        after 500ms idle
       │                                          ▲
       │                                          │
       └──────────> [IMMEDIATE] ─────────────────┘
                   on user input
                   (resets to INTERACTIVE after 100ms)
```

### Burst Detection Algorithm:

```kotlin
private val recentRedraws = CircularBuffer<Long>(capacity = 100)

fun detectMode(): RedrawMode {
    val now = System.currentTimeMillis()

    // Add current redraw timestamp
    recentRedraws.add(now)

    // Remove timestamps older than 1 second
    while (recentRedraws.isNotEmpty() && now - recentRedraws.first() > 1000) {
        recentRedraws.removeFirst()
    }

    // Count redraws in last second
    val redrawsPerSecond = recentRedraws.size

    return when {
        redrawsPerSecond > 100 -> RedrawMode.HIGH_VOLUME  // Bulk output detected
        else -> RedrawMode.INTERACTIVE                     // Normal interactive use
    }
}
```

---

## Implementation Strategy

### Option A: Coroutine Channel-Based (RECOMMENDED) ⭐

**Approach:**
- Use `Channel<RedrawRequest>` to queue redraw requests
- Coroutine processes channel with debouncing logic
- Conflates multiple requests into single redraw

**Pros:**
- ✅ Thread-safe by design
- ✅ No explicit locking needed
- ✅ Idiomatic Kotlin/Compose
- ✅ Easy to test

**Cons:**
- More complex than timer-based
- Requires understanding of coroutines

**Implementation:**
```kotlin
class ComposeTerminalDisplay : TerminalDisplay {
    private val redrawChannel = Channel<RedrawRequest>(Channel.CONFLATED)
    private val redrawScope = CoroutineScope(Dispatchers.Main)

    data class RedrawRequest(
        val timestamp: Long = System.currentTimeMillis(),
        val priority: RedrawPriority = RedrawPriority.NORMAL
    )

    enum class RedrawPriority {
        IMMEDIATE,  // User input
        NORMAL      // PTY output
    }

    init {
        redrawScope.launch {
            for (request in redrawChannel) {
                when (request.priority) {
                    RedrawPriority.IMMEDIATE -> {
                        actualRedraw()
                    }
                    RedrawPriority.NORMAL -> {
                        val mode = detectMode()
                        debounce(mode.debounceMs)
                        actualRedraw()
                    }
                }
            }
        }
    }

    private suspend fun debounce(ms: Long) {
        if (ms > 0) delay(ms)
    }
}
```

### Option B: Timer-Based

**Approach:**
- Use `Timer` to schedule deferred redraws
- Track last redraw timestamp
- Cancel pending timer if new redraw arrives

**Pros:**
- ✅ Simpler to understand
- ✅ Minimal dependencies

**Cons:**
- ⚠️ Requires explicit synchronization
- ⚠️ More boilerplate for thread safety

### Decision: **Use Option A (Channel-Based)**

Reasoning:
- Better fits Compose reactive model
- Thread-safe by design
- Easier to extend with features (priority queue, back-pressure)

---

## Detailed Component Design

### 1. RedrawMode Management

```kotlin
class ComposeTerminalDisplay : TerminalDisplay {
    // Current rendering mode
    @Volatile
    private var currentMode = RedrawMode.INTERACTIVE

    // Timestamp tracking for burst detection
    private val recentRedraws = ArrayDeque<Long>(100)
    private val redrawTimestampsLock = Any()

    // Mode transition tracking
    private var lastModeSwitch = System.currentTimeMillis()
    private var returnToInteractiveJob: Job? = null

    fun detectAndUpdateMode(): RedrawMode {
        val now = System.currentTimeMillis()

        synchronized(redrawTimestampsLock) {
            // Add timestamp
            recentRedraws.addLast(now)

            // Remove old timestamps (>1 second ago)
            while (recentRedraws.isNotEmpty() &&
                   now - recentRedraws.first() > 1000) {
                recentRedraws.removeFirst()
            }

            // Calculate redraws per second
            val rate = recentRedraws.size

            // Update mode based on rate
            val newMode = when {
                rate > 100 -> RedrawMode.HIGH_VOLUME
                else -> RedrawMode.INTERACTIVE
            }

            // Handle mode transition
            if (newMode != currentMode) {
                onModeTransition(currentMode, newMode)
                currentMode = newMode
                lastModeSwitch = now
            }

            return currentMode
        }
    }

    private fun onModeTransition(from: RedrawMode, to: RedrawMode) {
        println("🔄 Redraw mode: $from → $to")

        // Schedule return to INTERACTIVE after bulk output stops
        if (to == RedrawMode.HIGH_VOLUME) {
            returnToInteractiveJob?.cancel()
            returnToInteractiveJob = redrawScope.launch {
                delay(500) // Wait 500ms of low activity
                synchronized(redrawTimestampsLock) {
                    if (recentRedraws.size < 50) { // Less than 50 redraws/sec
                        currentMode = RedrawMode.INTERACTIVE
                        println("🔄 Redraw mode: HIGH_VOLUME → INTERACTIVE (auto)")
                    }
                }
            }
        }
    }
}
```

### 2. Debounced Redraw Logic

```kotlin
class ComposeTerminalDisplay : TerminalDisplay {
    private val redrawChannel = Channel<RedrawRequest>(Channel.CONFLATED)
    private val redrawScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        // Start redraw processor
        redrawScope.launch {
            var lastRedrawTime = 0L

            for (request in redrawChannel) {
                val now = System.currentTimeMillis()

                when (request.priority) {
                    RedrawPriority.IMMEDIATE -> {
                        // No debounce for user input
                        actualRedraw()
                        lastRedrawTime = now
                    }

                    RedrawPriority.NORMAL -> {
                        // Calculate required debounce
                        val mode = detectAndUpdateMode()
                        val elapsed = now - lastRedrawTime
                        val requiredDebounce = mode.debounceMs

                        if (elapsed >= requiredDebounce) {
                            // Enough time passed, redraw now
                            actualRedraw()
                            lastRedrawTime = now
                        } else {
                            // Need to wait, schedule deferred redraw
                            delay(requiredDebounce - elapsed)
                            actualRedraw()
                            lastRedrawTime = System.currentTimeMillis()
                        }
                    }
                }
            }
        }
    }

    fun requestRedraw() {
        redrawChannel.trySend(RedrawRequest(priority = RedrawPriority.NORMAL))
    }

    fun requestImmediateRedraw() {
        redrawChannel.trySend(RedrawRequest(priority = RedrawPriority.IMMEDIATE))

        // Reset to INTERACTIVE mode after brief delay
        redrawScope.launch {
            delay(100)
            synchronized(redrawTimestampsLock) {
                currentMode = RedrawMode.INTERACTIVE
            }
        }
    }

    private fun actualRedraw() {
        redrawCount.incrementAndGet()
        _redrawTrigger.value += 1
    }
}
```

### 3. User Input Integration

```kotlin
// In ProperTerminal.kt

.onKeyEvent { keyEvent ->
    if (keyEvent.type == KeyEventType.KeyDown) {
        // ... existing key handling ...

        if (text.isNotEmpty()) {
            scope.launch {
                processHandle?.write(text)
                // CHANGE: Use immediate redraw for user input
                display.requestImmediateRedraw()  // NEW
            }
        }
    }
}

.onPointerEvent(PointerEventType.Press) { event ->
    // ... existing mouse handling ...
    display.requestImmediateRedraw()  // NEW
}

.onPointerEvent(PointerEventType.Move) { event ->
    if (isDragging) {
        // ... existing drag handling ...
        display.requestImmediateRedraw()  // NEW
    }
}
```

---

## Performance Characteristics

### Expected Behavior:

#### Small Files (500 lines, 24 redraws/sec):
- **Mode:** INTERACTIVE (16ms debounce)
- **Before:** 24 redraws/sec
- **After:** 24-60 redraws/sec (slight increase acceptable)
- **Impact:** None (already optimal)

#### Medium Files (2,000 lines, 122 redraws/sec):
- **Mode:** HIGH_VOLUME (50ms debounce)
- **Before:** 122 redraws/sec
- **After:** 20-60 redraws/sec
- **Impact:** **-51% to -84% reduction**

#### Large Files (10,000 lines, 672 redraws/sec):
- **Mode:** HIGH_VOLUME (50ms debounce)
- **Before:** 672 redraws/sec
- **After:** 20-60 redraws/sec
- **Impact:** **-91% to -97% reduction**

#### Interactive Use (typing, vim):
- **Mode:** IMMEDIATE when typing, INTERACTIVE otherwise
- **Before:** 0ms lag
- **After:** 0ms lag (no change)
- **Impact:** None

---

## Edge Cases & Considerations

### 1. Mode Oscillation Prevention

**Problem:** Rapid switching between modes could cause jitter.

**Solution:**
- Require 500ms stability before returning to INTERACTIVE
- Use hysteresis (>100/sec to enter HIGH_VOLUME, <50/sec to exit)

### 2. User Input During Bulk Output

**Problem:** Typing while `cat` runs might feel laggy.

**Solution:**
- User input always uses IMMEDIATE priority
- Bypasses debounce queue
- Guarantees <16ms response

### 3. Cursor Blink During Debounce

**Problem:** Cursor might not blink smoothly if redraws are delayed.

**Solution:**
- Cursor blink uses separate `LaunchedEffect` (already implemented)
- Independent of main redraw logic
- No impact from debouncing

### 4. Scrolling Performance

**Problem:** Debouncing might hurt scroll performance.

**Solution:**
- `scrollArea()` calls `requestRedraw()` (normal priority)
- Adaptive mode detects scroll bursts
- Maintains smooth scrolling

### 5. Channel Overflow

**Problem:** If redraws queue faster than processing, channel could overflow.

**Solution:**
- Use `Channel.CONFLATED` mode
- Drops intermediate redraw requests
- Only latest request is kept
- This is **desired behavior** (coalescing)

---

## Testing Strategy

### Unit Tests:

1. **Mode Detection:**
   - Test burst detection algorithm
   - Verify threshold behavior (>100/sec → HIGH_VOLUME)
   - Check mode transitions

2. **Debouncing Logic:**
   - Test frame-rate limiting (60fps)
   - Verify immediate priority bypasses debounce
   - Check edge cases (rapid mode switches)

### Integration Tests:

1. **Small File Test:**
   - Verify no regression (should stay ~24-60/sec)
   - Mode should be INTERACTIVE

2. **Medium File Test:**
   - Verify reduction (122 → 60/sec)
   - Mode should switch to HIGH_VOLUME

3. **Large File Test:**
   - Verify 91% reduction (672 → 60/sec)
   - Mode should switch to HIGH_VOLUME quickly

4. **Interactive Test:**
   - Verify zero lag on typing
   - Verify immediate redraw on mouse events
   - Mode should reset to INTERACTIVE after input

### Performance Tests:

1. **CPU Usage:**
   - Measure before/after with Activity Monitor
   - Target: 60-80% reduction during bulk output

2. **Visual Quality:**
   - No stuttering during output
   - Smooth scrolling in vim/less
   - No dropped frames

---

## Configuration API (Future Enhancement)

```kotlin
// Optional: Allow users to tune performance
data class RedrawConfig(
    val interactiveDebounceMs: Long = 16,    // 60fps
    val highVolumeDebounceMs: Long = 50,     // 20fps
    val burstThreshold: Int = 100,            // redraws/sec
    val returnToInteractiveDelayMs: Long = 500
)

@Composable
fun ProperTerminal(
    command: String = System.getenv("SHELL") ?: "/bin/bash",
    arguments: List<String> = listOf("--login"),
    modifier: Modifier = Modifier,
    redrawConfig: RedrawConfig = RedrawConfig()  // NEW
) {
    // ...
}
```

---

## Risk Assessment

### Low Risk:
- ✅ Channel-based approach is well-tested pattern
- ✅ Coroutines are stable in Kotlin/Compose
- ✅ Small files won't be impacted
- ✅ User input always prioritized

### Medium Risk:
- ⚠️ Need careful testing of mode transitions
- ⚠️ TUI apps (vim, less) need validation
- ⚠️ Edge cases around channel overflow

### Mitigation:
- Comprehensive testing with real workloads
- Fallback to immediate mode if issues detected
- Configurable thresholds for fine-tuning

---

## Implementation Timeline

### Phase 2.1: Core Implementation (2 hours)
- Implement `RedrawMode` enum
- Add burst detection logic
- Create channel-based debouncing
- Update `requestRedraw()` API

### Phase 2.2: Integration (1 hour)
- Update `ProperTerminal.kt` for user input
- Add mode transition logging
- Remove old metrics code (keep final version)

### Phase 2.3: Testing (2 hours)
- Re-run all Phase 1 baseline tests
- Collect optimized metrics
- Verify interactive apps work smoothly
- Measure CPU usage reduction

### Phase 2.4: Documentation (30 min)
- Update CLAUDE.md
- Create before/after comparison
- Document configuration options

**Total Estimated Time:** 5.5 hours

---

## Success Criteria

### Must Have:
- ✅ 91% reduction in redraws for large files
- ✅ Zero perceptible lag for typing/vim
- ✅ No regression for small files
- ✅ Automatic mode detection works

### Nice to Have:
- ✅ 60-80% CPU usage reduction
- ✅ Smooth visual quality maintained
- ✅ Configurable performance modes
- ✅ Detailed performance logging

---

## Next Steps

1. ✅ Design review complete
2. 🔨 Implement `RedrawMode` and burst detection
3. 🔨 Implement channel-based debouncing
4. 🔨 Integrate with `ProperTerminal.kt`
5. 🧪 Test and validate
6. 📝 Document results

---

**Design Status:** ✅ Complete - Ready for Implementation
**Confidence Level:** Very High
**Expected Improvement:** 51-91% reduction in redraws
**Risk Level:** Low

---

**Document Version:** 1.0
**Author:** Claude Code
**Review Status:** Approved - Ready to Code
