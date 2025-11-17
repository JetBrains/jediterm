# JediTermKt Performance Benchmarks

This directory contains performance testing and benchmarking scripts for the terminal rendering optimization project.

## Scripts

### Quick Tests
- **`quick_test.sh`** - Fast verification test (500 lines)
- **`interactive_test.sh`** - Interactive typing test

### Comprehensive Tests
- **`benchmark_baseline.sh`** - Full baseline measurement suite (8 scenarios)
- **`automated_baseline_test.sh`** - Automated guided baseline tests (4 scenarios)
- **`test_phase2_optimized.sh`** - Phase 2 optimization validation tests

### Legacy
- **`test_terminal_rendering.sh`** - Original rendering tests

## Test Results

Results are saved to `/tmp/` directory:
- `/tmp/baseline_results_*.txt` - Baseline metrics (Phase 1)
- `/tmp/phase2_optimized_results_*.txt` - Optimized metrics (Phase 2)
- `/tmp/jediterm_baseline_metrics/` - Comprehensive baseline data

## Documentation

See `../docs/optimization/` for detailed analysis:
- `PHASE1_ANALYSIS.md` - Baseline performance analysis
- `PHASE2_DESIGN.md` - Adaptive debouncing design
- `PHASE2_IMPLEMENTATION.md` - Implementation details
- `BASELINE_RESULTS.md` - Performance results summary

## Quick Start

```bash
# Run optimized performance test
./test_phase2_optimized.sh

# Or manual test
cd ..
./gradlew :compose-ui:run --no-daemon
# In terminal: cat /tmp/test_10000.txt
```

## Results Summary

**Phase 2 Optimization (Adaptive Debouncing):**
- **99.8% reduction** in redraws for large files (30,232 → 19 redraws)
- **97.7% reduction** for medium files (6,092 → 138 redraws)
- **99.1% reduction** for small files (1,694 → 16 redraws)
- **Zero typing lag** - user input prioritization works perfectly
- User feedback: "Became more snappy really good"

Last updated: November 17, 2025
