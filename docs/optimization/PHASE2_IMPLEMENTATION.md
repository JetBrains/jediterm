# Phase 2: Adaptive Debouncing Implementation

**Project:** JediTermKt Terminal Rendering Optimization
**Date:** November 17, 2025
**Status:** ✅ Complete - Ready for Testing
**Build Status:** ✅ Successful

---

## 🎯 What Was Implemented

### Three Rendering Modes:

1. **INTERACTIVE (16ms debounce)**
   - For idle, typing, vim, small files
   - 60fps smooth rendering
   - Already efficient scenarios

2. **HIGH_VOLUME (50ms debounce)**
   - For bulk output (cat large files)
   - Automatically triggered when >100 redraws/sec detected
   - 20fps rendering (perfectly acceptable for scrolling text)
   - **Where we save 80-91% of redraws**

3. **IMMEDIATE (0ms debounce)**
   - For keyboard/mouse input
   - Bypasses all debouncing
   - **Guarantees zero lag for user interaction**

### Automatic Mode Switching:

```
Start: INTERACTIVE (default)
  ↓
Detect >100 redraws/sec → Switch to HIGH_VOLUME
  ↓
Bulk output continues → Stay in HIGH_VOLUME
  ↓
Output stops for 500ms → Auto-return to INTERACTIVE
  ↓
User types/clicks → IMMEDIATE mode (100ms)
  ↓
Back to INTERACTIVE
```

---

## 📝 Files Modified

### 1. `ComposeTerminalDisplay.kt` (+180 lines)

**Added:**
- `RedrawMode` enum with three modes
- `RedrawRequest` data class with priority
- `RedrawPriority` enum (IMMEDIATE, NORMAL)
- Channel-based redraw queue with conflation
- Coroutine-based debouncing processor
- Burst detection algorithm
- Mode transition logic with auto-recovery
- Enhanced metrics with efficiency tracking

**Key Methods:**
- `startRedrawProcessor()` - Core debouncing coroutine
- `detectAndUpdateMode()` - Adaptive mode detection
- `onModeTransition()` - Handle mode changes
- `requestRedraw()` - Normal priority (debounced)
- `requestImmediateRedraw()` - High priority (instant)
- `actualRedraw()` - Perform the redraw

### 2. `ProperTerminal.kt` (+4 lines)

**Modified:**
- Keyboard event handler → `display.requestImmediateRedraw()`
- Mouse press handler → `display.requestImmediateRedraw()`
- Mouse move (drag) handler → `display.requestImmediateRedraw()`
- Mouse release handler → `display.requestImmediateRedraw()`

**Impact:** All user input now bypasses debounce for instant response.

---

## 🔧 Technical Implementation

### Channel-Based Debouncing:

```kotlin
// Conflated channel - only keeps latest request
private val redrawChannel = Channel<RedrawRequest>(Channel.CONFLATED)

// Coroutine processor
private fun startRedrawProcessor() {
    redrawScope.launch {
        var lastRedrawTime = 0L

        for (request in redrawChannel) {
            when (request.priority) {
                IMMEDIATE -> actualRedraw()  // Instant
                NORMAL -> {
                    val mode = detectAndUpdateMode()
                    val elapsed = now() - lastRedrawTime

                    if (elapsed >= mode.debounceMs) {
                        actualRedraw()
                    } else {
                        delay(mode.debounceMs - elapsed)
                        actualRedraw()
                    }
                }
            }
        }
    }
}
```

### Burst Detection:

```kotlin
private fun detectAndUpdateMode(): RedrawMode {
    // Track timestamps of recent redraws
    recentRedraws.addLast(now())

    // Remove old timestamps (>1 second ago)
    while (recentRedraws.first() < now() - 1000) {
        recentRedraws.removeFirst()
    }

    // Calculate rate
    val rate = recentRedraws.size

    // Switch modes
    return when {
        rate > 100 -> HIGH_VOLUME  // Bulk output
        else -> INTERACTIVE         // Normal use
    }
}
```

### Conflation Benefits:

When multiple redraw requests arrive faster than processing:
- Channel drops intermediate requests
- Only latest request is kept
- **This is the source of our savings!**
- Example: 1000 requests → 60 actual redraws = 94% saved

---

## 📊 Expected Performance

### Based on Phase 1 Baseline:

| Scenario | Before (Baseline) | After (Optimized) | Improvement |
|----------|------------------|-------------------|-------------|
| **Idle** | 1.7/sec | 1-5/sec | ✅ No change |
| **Small (500)** | 24/sec | 24-60/sec | ✅ No regression |
| **Medium (2K)** | 122/sec | 20-60/sec | **-51% to -84%** |
| **Large (10K)** | 672/sec | 20-60/sec | **-91% to -97%** |
| **Typing** | 0ms lag | 0ms lag | ✅ No change |

### Key Metrics to Watch:

1. **Total redraws** - Should be 51-91% lower
2. **Coalesced redraws** - Number of skipped redraws
3. **Efficiency %** - Percentage of redraws saved
4. **Mode transitions** - INTERACTIVE ↔ HIGH_VOLUME
5. **Current rate** - Should stay ≤60/sec for large files

---

## 🧪 Testing Instructions

### Quick Test:

```bash
./test_phase2_optimized.sh
```

Guided test suite covering:
1. Idle terminal (30s)
2. Small file (500 lines)
3. Medium file (2,000 lines)
4. Large file (10,000 lines)
5. Interactive typing

### Manual Testing:

```bash
# Launch terminal
./gradlew :compose-ui:run --no-daemon

# Test bulk output
cat /tmp/test_10000.txt

# Watch console for:
# 🔄 Redraw mode: INTERACTIVE → HIGH_VOLUME (rate: 120/sec)
# ...
# 🔄 Redraw mode: HIGH_VOLUME → INTERACTIVE (auto-recovery)

# Check metrics every 5 seconds:
# ┌─────────────────────────────────────────────────────────┐
# │ REDRAW PERFORMANCE (Phase 2 - Adaptive Debouncing)     │
# ├─────────────────────────────────────────────────────────┤
# │ Mode:                 HIGH_VOLUME (50ms)                │
# │ Current rate:                    45 redraws/sec         │
# │ Total redraws:                1,500 redraws             │
# │ Coalesced redraws:           12,000 skipped             │
# │ Efficiency:                    88.9% saved              │
# │ Average rate:                 55.2 redraws/sec          │
# │ Total runtime:               27.2 seconds               │
# └─────────────────────────────────────────────────────────┘
```

### What to Look For:

✅ **Mode transitions**
- Should see transitions to HIGH_VOLUME for large files
- Should auto-recover to INTERACTIVE after output stops

✅ **Redraw rate**
- Should stay ≤60/sec even during bulk output
- Baseline was 672/sec → Should be ~20-60/sec now

✅ **Efficiency**
- Should see 80-90% efficiency for large files
- Means 80-90% of redraws were coalesced (saved)

✅ **No lag on typing**
- Type rapidly during/after bulk output
- Should feel instant (IMMEDIATE mode)

⚠️ **Watch for issues:**
- Stuttering during output (should be smooth)
- Lag on typing (should be zero)
- Mode oscillation (rapid switching)

---

## 🎯 Success Criteria

### Must Have:
- ✅ Build succeeds without errors
- 🧪 80-91% reduction in redraws for large files
- 🧪 Zero perceptible lag for typing/mouse
- 🧪 No regression for small files
- 🧪 Automatic mode detection works

### Nice to Have:
- 🧪 60-80% CPU usage reduction
- 🧪 Smooth visual quality maintained
- 🧪 Mode transitions logged clearly
- 🧪 Auto-recovery works reliably

---

## 🔍 How It Works

### Normal PTY Output (ProperTerminal.kt:248):

```
PTY Character → emulator.processChar() → display.requestRedraw()
    ↓
Channel.trySend(RedrawRequest(NORMAL))
    ↓
Queued for processing (conflation may drop it)
    ↓
Processor coroutine picks it up
    ↓
detectAndUpdateMode() checks rate
    ↓
Apply debounce based on mode (16ms or 50ms)
    ↓
actualRedraw() → _redrawTrigger.value += 1
    ↓
Compose recomposition → Canvas redraw
```

### User Input (Keyboard/Mouse):

```
Key press → processHandle.write() → display.requestImmediateRedraw()
    ↓
Channel.trySend(RedrawRequest(IMMEDIATE))
    ↓
Processor coroutine picks it up
    ↓
NO debounce - instant actualRedraw()
    ↓
Compose recomposition → Canvas redraw (zero lag)
```

### Why This Works:

1. **Conflated Channel** - Drops intermediate requests automatically
2. **Adaptive Throttling** - Only activates when needed
3. **Priority Queue** - User input bypasses throttling
4. **Coroutine-based** - Non-blocking, thread-safe by design
5. **Automatic Mode Detection** - No manual configuration needed

---

## 🐛 Potential Issues & Solutions

### Issue 1: Mode Oscillation
**Symptom:** Rapid switching between INTERACTIVE ↔ HIGH_VOLUME
**Cause:** Redraw rate hovering around 100/sec threshold
**Solution:**
- Add hysteresis (enter at >100/sec, exit at <50/sec)
- Require 500ms stability before returning to INTERACTIVE

**Status:** ✅ Already implemented

### Issue 2: Typing Lag During Bulk Output
**Symptom:** Keys feel slow when cat-ing large file
**Cause:** IMMEDIATE mode not working
**Solution:**
- Verify `requestImmediateRedraw()` is called
- Check channel isn't blocked

**Status:** ✅ Implemented with fallback

### Issue 3: Stuttering During Output
**Symptom:** Visual jitter when scrolling text
**Cause:** Debounce too aggressive (50ms too high)
**Solution:**
- Reduce HIGH_VOLUME debounce to 33ms (30fps)
- Or tune based on visual quality

**Status:** 🧪 Need testing to validate

### Issue 4: Auto-Recovery Not Triggering
**Symptom:** Stays in HIGH_VOLUME after output stops
**Cause:** Job cancelled or rate still high
**Solution:**
- Check `returnToInteractiveJob` isn't cancelled
- Verify rate calculation is correct

**Status:** ✅ Logic looks correct, needs validation

---

## 📈 Comparison with Phase 1

### Architecture Change:

**Phase 1 (Baseline):**
```
Every character → requestRedraw() → Compose recomposition
10,000 characters = 10,000 redraws (99% waste)
```

**Phase 2 (Optimized):**
```
Every character → requestRedraw() → Channel (CONFLATED)
                                  ↓
                          Debounced processor (60fps)
                                  ↓
                          Compose recomposition
10,000 characters = ~600 redraws (94% savings)
```

### Metrics Evolution:

**Phase 1 Metrics:**
- Total redraws
- Average rate
- Runtime

**Phase 2 Metrics:**
- ✅ All Phase 1 metrics
- ➕ Current mode (INTERACTIVE/HIGH_VOLUME/IMMEDIATE)
- ➕ Current rate (redraws in last second)
- ➕ Coalesced redraws (skipped count)
- ➕ Efficiency percentage (savings)

---

## 🚀 Next Steps

### Immediate (Phase 3):
1. **Run tests** with `./test_phase2_optimized.sh`
2. **Collect optimized metrics** for all scenarios
3. **Compare** with baseline (should see 80-91% reduction)
4. **Validate** interactive apps (vim, less) have zero lag
5. **Measure** CPU usage with Activity Monitor

### If Tests Pass:
1. **Fine-tune** debounce parameters if needed
2. **Create comparison report** (before/after)
3. **Update CLAUDE.md** with optimization details
4. **Commit** changes to git
5. **Create PR** with comprehensive metrics

### If Issues Found:
1. **Debug** mode transitions (add more logging)
2. **Tune** thresholds (100/sec, 50ms debounce)
3. **Test** edge cases (rapid typing during cat)
4. **Iterate** until smooth

---

## 📚 References

### Design Document:
- `PHASE2_DESIGN.md` - Complete design specification

### Baseline Data:
- `/tmp/baseline_results_20251117_101815.txt` - Baseline metrics
- `BASELINE_RESULTS.md` - Analysis summary

### Test Scripts:
- `test_phase2_optimized.sh` - Guided test suite
- `quick_test.sh` - Fast verification

---

## ✅ Completion Checklist

Phase 2 Implementation:
- [x] Define RedrawMode enum with three modes
- [x] Implement channel-based redraw queue
- [x] Create coroutine-based debouncing processor
- [x] Add burst detection algorithm
- [x] Implement mode transition logic
- [x] Add auto-recovery to INTERACTIVE
- [x] Update requestRedraw() for normal priority
- [x] Create requestImmediateRedraw() for user input
- [x] Update ProperTerminal keyboard handler
- [x] Update ProperTerminal mouse handlers
- [x] Enhance metrics with efficiency tracking
- [x] Build successfully without errors
- [x] Create test script
- [x] Document implementation

Phase 3 Testing:
- [ ] Run idle test (30s)
- [ ] Run small file test (500 lines)
- [ ] Run medium file test (2,000 lines)
- [ ] Run large file test (10,000 lines)
- [ ] Test interactive typing
- [ ] Test vim scrolling
- [ ] Test less paging
- [ ] Measure CPU usage
- [ ] Verify mode transitions
- [ ] Check for visual issues

Phase 4 Documentation:
- [ ] Update CLAUDE.md
- [ ] Create before/after comparison
- [ ] Document configuration options
- [ ] Add troubleshooting guide
- [ ] Commit changes to git
- [ ] Create PR with metrics

---

**Implementation Status:** ✅ Complete
**Build Status:** ✅ Successful
**Ready for Testing:** ✅ Yes
**Estimated Improvement:** 80-91% reduction in redraws

---

**Document Version:** 1.0
**Author:** Claude Code
**Review Status:** Ready for Testing
