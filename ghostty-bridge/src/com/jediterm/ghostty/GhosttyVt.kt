package com.jediterm.ghostty

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.nio.charset.StandardCharsets
import java.nio.file.Path

/**
 * Thin Kotlin FFM (Project Panama) binding over the `libghostty-vt` C API.
 *
 * A single instance owns one `GhosttyTerminal`: you feed it raw VT bytes with [write] (exactly what
 * a PTY would send) and then read the resulting screen grid back cell-by-cell. JediTerm's emulator
 * (`JediEmulator`) and model mutator (`JediTerminal`) are not involved at all — ghostty is the
 * emulator.
 *
 * This binding intentionally exposes only the small slice of the C API needed to mirror the
 * screen + scrollback into a `TerminalTextBuffer`: terminal lifecycle, `vt_write`, `terminal_get`
 * for geometry, and the (untracked) grid-reference read path (`grid_ref` → `grid_ref_cell` →
 * `cell_get`).
 *
 * The shared library location is taken from the `ghostty.vt.lib` system property, e.g.
 * `-Dghostty.vt.lib=/path/to/libghostty-vt.dylib`.
 *
 * Not thread-safe: a single instance must be used from one thread (it uses a confined Arena and
 * reusable scratch buffers).
 */
class GhosttyVt(cols: Int, rows: Int, maxScrollback: Long) : AutoCloseable {

  // Shared (not confined): the embedding touches ghostty from more than one thread (the VT read
  // loop and the resize executor). Callers MUST serialize access externally (this class is not
  // internally synchronized and reuses scratch buffers).
  private val arena: Arena = Arena.ofShared()
  private val terminal: MemorySegment
  private var closed = false
  private var writePtyCallback: WritePtyCallback? = null

  // Reusable scratch buffers (this instance is single-threaded).
  private val scratchPoint: MemorySegment = arena.allocate(POINT)
  private val scratchGridRef: MemorySegment = arena.allocate(GRID_REF)
  private val scratchCell: MemorySegment = arena.allocate(8L)
  private val scratchOut: MemorySegment = arena.allocate(16L)
  private val scratchStyle: MemorySegment = arena.allocate(STYLE_SIZE)

  init {
    val opts = arena.allocate(OPTIONS)
    opts.set(C_SHORT, 0L, cols.toShort())
    opts.set(C_SHORT, 2L, rows.toShort())
    opts.set(C_LONG, 8L, maxScrollback)

    val outTerminal = arena.allocate(C_PTR)
    val result = try {
      TERMINAL_NEW.invokeExact(MemorySegment.NULL, outTerminal, opts) as Int
    } catch (t: Throwable) {
      throw RuntimeException("ghostty_terminal_new failed", t)
    }
    if (result != GHOSTTY_SUCCESS) {
      throw IllegalStateException("ghostty_terminal_new returned $result")
    }
    terminal = outTerminal.get(C_PTR, 0L)
  }

  /** Feed raw VT bytes to the engine (the only input path: equivalent to PTY output). */
  fun write(data: ByteArray) {
    ensureOpen()
    val buf = arena.allocate(maxOf(1, data.size).toLong())
    MemorySegment.copy(data, 0, buf, ValueLayout.JAVA_BYTE, 0L, data.size)
    try {
      TERMINAL_VT_WRITE.invokeExact(terminal, buf, data.size.toLong())
    } catch (t: Throwable) {
      throw RuntimeException("ghostty_terminal_vt_write failed", t)
    }
  }

  /** Convenience overload: encodes the string as UTF-8 and writes it. */
  fun write(s: String) {
    write(s.toByteArray(StandardCharsets.UTF_8))
  }

  /**
   * Resize the terminal. The primary screen reflows (when wraparound is enabled); the alternate
   * screen is truncated/extended without reflow — matching real terminal behavior.
   */
  fun resize(cols: Int, rows: Int) {
    ensureOpen()
    try {
      // cell pixel size is irrelevant for grid reflow; pass 1x1.
      val r = TERMINAL_RESIZE.invokeExact(terminal, cols.toShort(), rows.toShort(), 1, 1) as Int
      if (r != GHOSTTY_SUCCESS) {
        throw IllegalStateException("ghostty_terminal_resize returned $r")
      }
    } catch (t: Throwable) {
      throw RuntimeException("ghostty_terminal_resize failed", t)
    }
  }

  fun cols(): Int = terminalGetU16(DATA_COLS)

  fun rows(): Int = terminalGetU16(DATA_ROWS)

  /** Cursor column within the active area, 0-based. */
  fun cursorX(): Int = terminalGetU16(DATA_CURSOR_X)

  /** Cursor row within the active area, 0-based. */
  fun cursorY(): Int = terminalGetU16(DATA_CURSOR_Y)

  fun scrollbackRows(): Long = terminalGetSize(DATA_SCROLLBACK_ROWS)

  /** Whether the cursor is visible (DEC mode 25). */
  fun cursorVisible(): Boolean = terminalGetBool(DATA_CURSOR_VISIBLE)

  /** Whether the alternate screen is active. */
  fun altScreen(): Boolean = terminalGetU16(DATA_ACTIVE_SCREEN) == 1

  /** Whether any mouse tracking mode is active. */
  fun mouseTracking(): Boolean = terminalGetBool(DATA_MOUSE_TRACKING)

  /** Query a (packed) DEC/ANSI mode; see the MODE_* constants. */
  fun modeEnabled(packedMode: Int): Boolean {
    ensureOpen()
    scratchOut.set(C_BYTE, 0L, 0.toByte())
    try {
      val r = TERMINAL_MODE_GET.invokeExact(terminal, packedMode.toShort(), scratchOut) as Int
      // Unknown modes return GHOSTTY_INVALID_VALUE; treat as "not enabled".
      return r == GHOSTTY_SUCCESS && scratchOut.get(C_BYTE, 0L).toInt() != 0
    } catch (t: Throwable) {
      throw RuntimeException("ghostty_terminal_mode_get failed", t)
    }
  }

  /** The terminal title set via OSC 0/2, or "" if none. */
  fun title(): String {
    ensureOpen()
    scratchOut.fill(0.toByte()) // GhosttyString { const uint8_t* ptr; size_t len; }
    try {
      val r = TERMINAL_GET.invokeExact(terminal, DATA_TITLE, scratchOut) as Int
      if (r != GHOSTTY_SUCCESS) {
        return ""
      }
    } catch (t: Throwable) {
      throw RuntimeException("ghostty_terminal_get(TITLE) failed", t)
    }
    val ptr = scratchOut.get(C_LONG, 0L)
    val len = scratchOut.get(C_LONG, 8L)
    if (ptr == 0L || len <= 0L) {
      return ""
    }
    val bytes = MemorySegment.ofAddress(ptr).reinterpret(len).toArray(C_BYTE)
    return String(bytes, StandardCharsets.UTF_8)
  }

  /**
   * Install (or replace) the callback invoked when the engine needs to write bytes back to the pty
   * (DSR/DA/mode-report responses). Fired synchronously during [write]; the callback must not
   * re-enter this `GhosttyVt`. Once set it cannot be cleared via this method (set a no-op instead).
   */
  fun setWritePtyCallback(callback: WritePtyCallback) {
    ensureOpen()
    this.writePtyCallback = callback
    try {
      val handle = MethodHandles.lookup().bind(this, "onWritePty",
        MethodType.methodType(Void.TYPE, MemorySegment::class.java, MemorySegment::class.java,
          MemorySegment::class.java, java.lang.Long.TYPE))
      val stub = LINKER.upcallStub(handle,
        FunctionDescriptor.ofVoid(C_PTR, C_PTR, C_PTR, C_LONG), arena)
      val r = TERMINAL_SET.invokeExact(terminal, OPT_WRITE_PTY, stub) as Int
      if (r != GHOSTTY_SUCCESS) {
        throw IllegalStateException("ghostty_terminal_set(WRITE_PTY) returned $r")
      }
    } catch (t: Throwable) {
      throw RuntimeException("setWritePtyCallback failed", t)
    }
  }

  // Invoked from native code (the upcall stub) during ghostty_terminal_vt_write.
  @Suppress("unused", "UNUSED_PARAMETER")
  private fun onWritePty(terminal: MemorySegment, userdata: MemorySegment, data: MemorySegment, len: Long) {
    val cb = writePtyCallback
    if (cb == null || len <= 0L) {
      return
    }
    val bytes = data.reinterpret(len).toArray(C_BYTE)
    cb.onWrite(bytes)
  }

  /** Receives bytes the engine wants written back to the pty (e.g. DSR / device-attributes). */
  fun interface WritePtyCallback {
    fun onWrite(data: ByteArray)
  }

  /**
   * Resolve the cell at (tag, x, y) and fill [out] with its codepoint, wide property, and style in
   * a single grid-ref resolution. Returns false if the coordinate is out of bounds (in which case
   * [out] is reset to an empty, default-styled cell).
   */
  fun readCell(pointTag: Int, x: Int, y: Int, out: CellData): Boolean {
    ensureOpen()
    out.reset()
    scratchGridRef.fill(0.toByte())
    scratchGridRef.set(C_LONG, 0L, GRID_REF.byteSize()) // GHOSTTY_INIT_SIZED
    scratchPoint.fill(0.toByte())
    scratchPoint.set(C_INT, 0L, pointTag)
    scratchPoint.set(C_SHORT, 8L, x.toShort())
    scratchPoint.set(C_INT, 12L, y)
    try {
      if ((TERMINAL_GRID_REF.invokeExact(terminal, scratchPoint, scratchGridRef) as Int) != GHOSTTY_SUCCESS) {
        return false
      }
      if ((GRID_REF_CELL.invokeExact(scratchGridRef, scratchCell) as Int) != GHOSTTY_SUCCESS) {
        return false
      }
      val cell = scratchCell.get(C_LONG, 0L)
      out.codepoint = cellGetInt(cell, CELL_DATA_CODEPOINT)
      out.wide = cellGetInt(cell, CELL_DATA_WIDE)

      scratchStyle.fill(0.toByte())
      scratchStyle.set(C_LONG, 0L, STYLE_SIZE) // GHOSTTY_INIT_SIZED
      if ((GRID_REF_STYLE.invokeExact(scratchGridRef, scratchStyle) as Int) == GHOSTTY_SUCCESS) {
        out.fgTag = scratchStyle.get(C_INT, OFF_FG_TAG)
        out.fg = readColorValue(out.fgTag, OFF_FG_VAL)
        out.bgTag = scratchStyle.get(C_INT, OFF_BG_TAG)
        out.bg = readColorValue(out.bgTag, OFF_BG_VAL)
        out.bold = scratchStyle.get(C_BYTE, OFF_BOLD).toInt() != 0
        out.italic = scratchStyle.get(C_BYTE, OFF_ITALIC).toInt() != 0
        out.faint = scratchStyle.get(C_BYTE, OFF_FAINT).toInt() != 0
        out.blink = scratchStyle.get(C_BYTE, OFF_BLINK).toInt() != 0
        out.inverse = scratchStyle.get(C_BYTE, OFF_INVERSE).toInt() != 0
        out.invisible = scratchStyle.get(C_BYTE, OFF_INVISIBLE).toInt() != 0
        out.underline = scratchStyle.get(C_INT, OFF_UNDERLINE)
      }
      return true
    } catch (t: Throwable) {
      throw RuntimeException("ghostty cell read failed", t)
    }
  }

  /** For PALETTE: the index (0-255). For RGB: packed 0xRRGGBB. Else 0. */
  private fun readColorValue(tag: Int, valueOffset: Long): Int {
    if (tag == STYLE_COLOR_PALETTE) {
      return scratchStyle.get(C_BYTE, valueOffset).toInt() and 0xFF
    }
    if (tag == STYLE_COLOR_RGB) {
      val r = scratchStyle.get(C_BYTE, valueOffset).toInt() and 0xFF
      val g = scratchStyle.get(C_BYTE, valueOffset + 1).toInt() and 0xFF
      val b = scratchStyle.get(C_BYTE, valueOffset + 2).toInt() and 0xFF
      return (r shl 16) or (g shl 8) or b
    }
    return 0
  }

  /** Mutable, reusable holder for a cell's content + style (avoids per-cell allocation). */
  class CellData {
    var codepoint = 0
    var wide = 0
    var fgTag = 0
    var fg = 0 // palette index or packed 0xRRGGBB depending on fgTag
    var bgTag = 0
    var bg = 0
    var bold = false
    var italic = false
    var faint = false
    var blink = false
    var inverse = false
    var invisible = false
    var underline = 0

    internal fun reset() {
      codepoint = 0
      wide = WIDE_NARROW
      fgTag = STYLE_COLOR_NONE
      fg = 0
      bgTag = STYLE_COLOR_NONE
      bg = 0
      bold = false
      italic = false
      faint = false
      blink = false
      inverse = false
      invisible = false
      underline = 0
    }
  }

  /**
   * Resolve a cell in the given coordinate system and return its opaque `GhosttyCell` value, or
   * [CELL_INVALID] if the coordinate is out of bounds.
   */
  fun cellAt(pointTag: Int, x: Int, y: Int): Long {
    ensureOpen()
    scratchGridRef.fill(0.toByte())
    scratchGridRef.set(C_LONG, 0L, GRID_REF.byteSize()) // GHOSTTY_INIT_SIZED: set .size

    scratchPoint.fill(0.toByte())
    scratchPoint.set(C_INT, 0L, pointTag)
    scratchPoint.set(C_SHORT, 8L, x.toShort())
    scratchPoint.set(C_INT, 12L, y)

    try {
      val r = TERMINAL_GRID_REF.invokeExact(terminal, scratchPoint, scratchGridRef) as Int
      if (r != GHOSTTY_SUCCESS) {
        return CELL_INVALID
      }
      val r2 = GRID_REF_CELL.invokeExact(scratchGridRef, scratchCell) as Int
      if (r2 != GHOSTTY_SUCCESS) {
        return CELL_INVALID
      }
      return scratchCell.get(C_LONG, 0L)
    } catch (t: Throwable) {
      throw RuntimeException("ghostty grid-ref read failed", t)
    }
  }

  /** Read an int-typed field (codepoint, wide, ...) from a `GhosttyCell`. */
  fun cellGetInt(cell: Long, cellData: Int): Int {
    ensureOpen()
    scratchOut.set(C_INT, 0L, 0)
    try {
      val r = CELL_GET.invokeExact(cell, cellData, scratchOut) as Int
      if (r != GHOSTTY_SUCCESS) {
        throw IllegalStateException("ghostty_cell_get($cellData) returned $r")
      }
    } catch (t: Throwable) {
      throw RuntimeException("ghostty_cell_get failed", t)
    }
    return scratchOut.get(C_INT, 0L)
  }

  override fun close() {
    if (closed) {
      return
    }
    closed = true
    try {
      TERMINAL_FREE.invokeExact(terminal)
    } catch (t: Throwable) {
      throw RuntimeException("ghostty_terminal_free failed", t)
    } finally {
      arena.close()
    }
  }

  private fun terminalGetU16(dataKind: Int): Int {
    ensureOpen()
    scratchOut.set(C_SHORT, 0L, 0.toShort())
    try {
      val r = TERMINAL_GET.invokeExact(terminal, dataKind, scratchOut) as Int
      if (r != GHOSTTY_SUCCESS) {
        throw IllegalStateException("ghostty_terminal_get($dataKind) returned $r")
      }
    } catch (t: Throwable) {
      throw RuntimeException("ghostty_terminal_get failed", t)
    }
    return scratchOut.get(C_SHORT, 0L).toInt() and 0xFFFF
  }

  private fun terminalGetBool(dataKind: Int): Boolean {
    ensureOpen()
    scratchOut.set(C_BYTE, 0L, 0.toByte())
    try {
      val r = TERMINAL_GET.invokeExact(terminal, dataKind, scratchOut) as Int
      if (r != GHOSTTY_SUCCESS) {
        return false
      }
    } catch (t: Throwable) {
      throw RuntimeException("ghostty_terminal_get failed", t)
    }
    return scratchOut.get(C_BYTE, 0L).toInt() != 0
  }

  private fun terminalGetSize(dataKind: Int): Long {
    ensureOpen()
    scratchOut.set(C_LONG, 0L, 0L)
    try {
      val r = TERMINAL_GET.invokeExact(terminal, dataKind, scratchOut) as Int
      if (r != GHOSTTY_SUCCESS) {
        throw IllegalStateException("ghostty_terminal_get($dataKind) returned $r")
      }
    } catch (t: Throwable) {
      throw RuntimeException("ghostty_terminal_get failed", t)
    }
    return scratchOut.get(C_LONG, 0L)
  }

  private fun ensureOpen() {
    if (closed) {
      throw IllegalStateException("GhosttyVt is closed")
    }
  }

  companion object {
    // ---- GhosttyResult ----
    const val GHOSTTY_SUCCESS = 0

    // ---- GhosttyTerminalData ----
    const val DATA_COLS = 1
    const val DATA_ROWS = 2
    const val DATA_CURSOR_X = 3
    const val DATA_CURSOR_Y = 4
    const val DATA_ACTIVE_SCREEN = 6
    const val DATA_CURSOR_VISIBLE = 7
    const val DATA_MOUSE_TRACKING = 11
    const val DATA_TITLE = 12
    const val DATA_SCROLLBACK_ROWS = 15

    // ---- GhosttyPointTag ----
    const val POINT_TAG_ACTIVE = 0
    const val POINT_TAG_HISTORY = 3

    // ---- GhosttyCellData ----
    const val CELL_DATA_CODEPOINT = 1
    const val CELL_DATA_WIDE = 3

    // ---- GhosttyCellWide ----
    const val WIDE_NARROW = 0
    const val WIDE_WIDE = 1
    const val WIDE_SPACER_TAIL = 2
    const val WIDE_SPACER_HEAD = 3

    // ---- GhosttyStyleColorTag ----
    const val STYLE_COLOR_NONE = 0
    const val STYLE_COLOR_PALETTE = 1
    const val STYLE_COLOR_RGB = 2

    // ---- GhosttyTerminalOption ----
    const val OPT_WRITE_PTY = 1

    // ---- GhosttyMode packed ids (value | ansi<<15); all below are DEC private (ansi=false) ----
    const val MODE_DECCKM = 1            // application cursor keys
    const val MODE_KEYPAD_KEYS = 66      // application keypad
    const val MODE_X10_MOUSE = 9
    const val MODE_NORMAL_MOUSE = 1000
    const val MODE_BUTTON_MOUSE = 1002
    const val MODE_ANY_MOUSE = 1003
    const val MODE_UTF8_MOUSE = 1005
    const val MODE_SGR_MOUSE = 1006
    const val MODE_URXVT_MOUSE = 1015
    const val MODE_SGR_PIXELS_MOUSE = 1016
    const val MODE_BRACKETED_PASTE = 2004

    /** Sentinel returned by [cellAt] when a coordinate cannot be resolved. */
    const val CELL_INVALID = -1L

    // ---- ABI layouts ----
    private val C_INT = ValueLayout.JAVA_INT
    private val C_LONG = ValueLayout.JAVA_LONG // size_t / GhosttyCell (64-bit)
    private val C_SHORT = ValueLayout.JAVA_SHORT
    private val C_PTR = ValueLayout.ADDRESS
    private val C_BYTE = ValueLayout.JAVA_BYTE

    /** `struct { uint16 cols; uint16 rows; size_t max_scrollback; }` (16 bytes, align 8). */
    private val OPTIONS: MemoryLayout = MemoryLayout.structLayout(
      C_SHORT.withName("cols"),
      C_SHORT.withName("rows"),
      MemoryLayout.paddingLayout(4L),
      C_LONG.withName("max_scrollback"))

    /** `GhosttyPoint`: `enum tag; union { {u16 x; u32 y;}; u64[2]; }` (24 bytes, align 8). */
    private val POINT: MemoryLayout = MemoryLayout.structLayout(
      C_INT.withName("tag"),
      MemoryLayout.paddingLayout(4L),
      MemoryLayout.sequenceLayout(2L, C_LONG).withName("value"))

    /** `GhosttyGridRef`: `size_t size; void* node; uint16 x; uint16 y;` (24 bytes, align 8). */
    private val GRID_REF: MemoryLayout = MemoryLayout.structLayout(
      C_LONG.withName("size"),
      C_PTR.withName("node"),
      C_SHORT.withName("x"),
      C_SHORT.withName("y"),
      MemoryLayout.paddingLayout(4L))

    // GhosttyStyle (sized struct, 72 bytes). Each GhosttyStyleColor is {int tag; union{...} value} = 16
    // bytes (tag @+0, 8-byte value @+8). Field byte offsets used directly below:
    //   size@0  fg(tag@8,val@16)  bg(tag@24,val@32)  underline_color(tag@40,val@48)
    //   bold@56 italic@57 faint@58 blink@59 inverse@60 invisible@61 strikethrough@62 overline@63
    //   underline(int)@64 ; total padded to 72.
    private const val STYLE_SIZE = 72L
    private const val OFF_FG_TAG = 8L
    private const val OFF_FG_VAL = 16L
    private const val OFF_BG_TAG = 24L
    private const val OFF_BG_VAL = 32L
    private const val OFF_BOLD = 56L
    private const val OFF_ITALIC = 57L
    private const val OFF_FAINT = 58L
    private const val OFF_BLINK = 59L
    private const val OFF_INVERSE = 60L
    private const val OFF_INVISIBLE = 61L
    private const val OFF_UNDERLINE = 64L

    private val LINKER: Linker = Linker.nativeLinker()
    private val LIB_ARENA: Arena = Arena.ofShared()
    private val LOOKUP: SymbolLookup = openLibrary()

    private val TERMINAL_NEW: MethodHandle = downcall("ghostty_terminal_new",
      FunctionDescriptor.of(C_INT, C_PTR, C_PTR, OPTIONS))
    private val TERMINAL_FREE: MethodHandle = downcall("ghostty_terminal_free",
      FunctionDescriptor.ofVoid(C_PTR))
    private val TERMINAL_VT_WRITE: MethodHandle = downcall("ghostty_terminal_vt_write",
      FunctionDescriptor.ofVoid(C_PTR, C_PTR, C_LONG))
    private val TERMINAL_RESIZE: MethodHandle = downcall("ghostty_terminal_resize",
      FunctionDescriptor.of(C_INT, C_PTR, C_SHORT, C_SHORT, C_INT, C_INT))
    private val TERMINAL_GET: MethodHandle = downcall("ghostty_terminal_get",
      FunctionDescriptor.of(C_INT, C_PTR, C_INT, C_PTR))
    private val TERMINAL_GRID_REF: MethodHandle = downcall("ghostty_terminal_grid_ref",
      FunctionDescriptor.of(C_INT, C_PTR, POINT, C_PTR))
    private val GRID_REF_CELL: MethodHandle = downcall("ghostty_grid_ref_cell",
      FunctionDescriptor.of(C_INT, C_PTR, C_PTR))
    private val GRID_REF_STYLE: MethodHandle = downcall("ghostty_grid_ref_style",
      FunctionDescriptor.of(C_INT, C_PTR, C_PTR))
    private val CELL_GET: MethodHandle = downcall("ghostty_cell_get",
      FunctionDescriptor.of(C_INT, C_LONG, C_INT, C_PTR))
    private val TERMINAL_MODE_GET: MethodHandle = downcall("ghostty_terminal_mode_get",
      FunctionDescriptor.of(C_INT, C_PTR, C_SHORT, C_PTR))
    private val TERMINAL_SET: MethodHandle = downcall("ghostty_terminal_set",
      FunctionDescriptor.of(C_INT, C_PTR, C_INT, C_PTR))

    private fun openLibrary(): SymbolLookup {
      val path = System.getProperty("ghostty.vt.lib")
      if (path.isNullOrBlank()) {
        throw IllegalStateException(
          "System property 'ghostty.vt.lib' is not set. Point it at libghostty-vt.dylib, e.g. " +
            "-Dghostty.vt.lib=\$REPO/ghostty/zig-out/lib/libghostty-vt.dylib " +
            "(build it with: cd ghostty && zig build -Demit-lib-vt).")
      }
      return SymbolLookup.libraryLookup(Path.of(path), LIB_ARENA)
    }

    private fun downcall(symbol: String, descriptor: FunctionDescriptor): MethodHandle {
      val address = LOOKUP.find(symbol)
        .orElseThrow { IllegalStateException("libghostty-vt symbol not found: $symbol") }
      return LINKER.downcallHandle(address, descriptor)
    }
  }
}
