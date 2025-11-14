# JediTerm Architecture Analysis

## Project Overview
JediTerm is a pure Java terminal emulator with a clean separation between core terminal emulation logic and Swing-based UI implementation. The codebase is organized into three main modules:
- **core**: Terminal emulation engine (framework-agnostic)
- **ui**: Swing/AWT-based UI components
- **JediTerm**: Demo application and utilities

## Module Structure

### Core Module (`/core/src`)
**Purpose**: Platform-independent terminal emulation logic

**Key Packages**:
1. `com.jediterm.terminal`
   - Core terminal interfaces and classes
   - Main interface: `Terminal.java` - command execution interface
   - Main interface: `TerminalDisplay.java` - display callbacks for terminal output

2. `com.jediterm.terminal.emulator`
   - `JediEmulator.java` - Main ANSI escape sequence parser and processor
   - `Emulator.java` - Base emulator class
   - `DataStreamIteratingEmulator.java` - Iterates over terminal data stream
   - `ControlSequence.java` - CSI sequence representation
   - `ColorPaletteImpl.java` - Color management

3. `com.jediterm.terminal.model`
   - `TerminalTextBuffer.kt` - Main data model storing terminal text/styles
   - `TerminalLine.java` - Individual line representation
   - `CharBuffer.java` - Character buffer
   - `StyleState.java` - Current text styling state
   - `TerminalSelection.java` - Text selection management
   - Mouse handling, hyperlink support, text processing

4. `com.jediterm.terminal.emulator.mouse`
   - Mouse mode/format handling

5. `com.jediterm.terminal.emulator.charset`
   - Character set support (G0, G1, etc.)
   - DEC box drawing characters

6. `com.jediterm.core`
   - Platform abstractions
   - Input/output types
   - Compatibility utilities

**Key Dependencies**:
- SLF4J (logging)
- JetBrains Annotations
- No Swing/AWT dependencies

### UI Module (`/ui/src`)
**Purpose**: Swing/AWT-based rendering and interaction

**Key Packages**:
1. `com.jediterm.terminal.ui` (27 UI classes)
   - **TerminalPanel.java** (2138 lines)
     - Main JComponent for rendering terminal text
     - Extends JComponent, implements TerminalDisplay
     - Responsibilities:
       * Graphics rendering (painComponent method - draws characters/cursor/selection)
       * Keyboard input handling (processKeyEvent, handleKeyEvent)
       * Mouse event handling (click, drag, wheel scroll)
       * Text selection management
       * Find/search highlighting
       * Copy/paste handling
       * Cursor blinking and styling
       * Input method composition support
       * Scrolling and scroll region management
     - Uses BufferedImage for rendering
     - Font management (normal, bold, italic, bold-italic variants)
     - Character width/height metrics calculation

   - **JediTermWidget.java** (606 lines)
     - Main terminal widget container
     - Extends JPanel, implements TerminalWidget
     - Contains:
       * TerminalPanel (rendering)
       * JScrollBar (vertical scrolling)
       * JLayeredPane (layout management with terminal, scrollbar, find component layers)
       * TerminalStarter (emulator execution)
       * TypeAhead model
     - Responsible for:
       * Session management (start/stop)
       * Connection management (TtyConnector)
       * Search component integration
       * Executor service management

   - **TerminalWidget.java** (interface)
     - Abstraction for terminal widget implementation
     - Provides access to terminal sessions, components, display

   - **TerminalSession.java** (interface)
     - Terminal session management interface

   - Other UI components:
     - `JediTermSearchComponent.java` - Search interface
     - `JediTermDefaultSearchComponent.java` - Default search impl
     - `BlinkingTextTracker.java` - Blinking text handling
     - `AwtTransformers.java` - Swing/AWT color/input conversions
     - `Cell.java` - Terminal cell representation

2. `com.jediterm.terminal.ui.settings`
   - `SettingsProvider.java` (interface)
   - `DefaultSettingsProvider.java`
   - `SystemSettingsProvider.java`
   - `UserSettingsProvider.java`
   - Configuration for colors, fonts, behavior

3. `com.jediterm.terminal.ui.input`
   - `AwtMouseEvent.java` - AWT mouse event wrapper
   - `AwtMouseWheelEvent.java` - AWT mouse wheel wrapper
   - Input type conversions

4. `com.jediterm.terminal.ui.hyperlinks`
   - Hyperlink handling UI

**Dependencies**:
- Swing (javax.swing)
- AWT (java.awt)
- Core module

### JediTerm Module (`/JediTerm/src`)
**Purpose**: Demo application and utilities

Contains example applications and debugging utilities

## Key Architectural Patterns

### 1. Core-UI Separation

The architecture cleanly separates terminal logic from rendering:

```
Input Events → TerminalStarter → JediEmulator → Terminal Interface
                                                      ↓
                                            TerminalTextBuffer (Model)
                                                      ↓
                            TerminalDisplay (TerminalPanel) → Render to Graphics2D
```

- Core logic knows nothing about Swing
- UI renders based on Terminal model changes
- Clean interface contracts: Terminal, TerminalDisplay, TtyConnector

### 2. Observer Pattern

- `TerminalModelListener` - Listens for text buffer changes
- `TerminalHistoryBufferListener` - Listens for history changes
- `TerminalWidgetListener` - Listens for session events
- Listeners trigger UI repaints via `repaint()` callbacks

### 3. Threading Model

- Terminal emulation runs in background thread (UnboundedExecutorService)
- UI updates marshaled to EDT via SwingUtilities.invokeLater()
- Text buffer protected with ReentrantLock (myLock in TerminalTextBuffer)
- Atomic references for thread-safe state

### 4. Abstraction Layers

**Input Abstraction**:
- Core has platform-independent InputEvent, KeyEvent, MouseEvent
- UI wraps AWT events: AwtMouseEvent, AwtMouseWheelEvent
- Transformers convert between representations

**Output Abstraction**:
- Terminal is the command interface (what to display/do)
- TerminalDisplay is the callback interface (how to display it)
- TerminalPanel implements TerminalDisplay for Swing

**Settings Abstraction**:
- SettingsProvider interface for all configuration
- Implementations can provide different behaviors
- Used for fonts, colors, keyboard mappings, mouse modes, etc.

### 5. Rendering Pipeline

```
Terminal Emulation (TerminalStarter)
    ↓
TerminalTextBuffer (stores styled characters)
    ↓
Model Listener Callback → repaint()
    ↓
Timer (WeakRedrawTimer, 50fps max)
    ↓
paintComponent(Graphics2D)
    ↓
1. Lock text buffer
2. Process visible lines via StyledTextConsumer
3. Draw characters with proper styling
4. Draw cursor
5. Draw selection overlay
6. Draw find results overlay
7. Unlock buffer
```

**Text Rendering Details**:
- Uses BufferedImage for character metrics
- Separate fonts for normal/bold/italic/bold-italic
- Character width calculated (primarily monospaced, with warnings for non-monospaced)
- Line spacing configurable
- Descent calculation for proper vertical alignment
- Double-width character (DWC) support
- Unicode normalization handling
- Hyperlink highlighting with hover effects

### 6. Input Handling

**Keyboard**:
- TerminalKeyHandler extends KeyAdapter
- Processes both KEY_PRESSED and KEY_TYPED events
- Maps to terminal escape codes via Terminal.getCodeForKey()
- Special handling: Alt+key sends ESC+key, Ctrl+Space sends NUL, etc.
- Input method composition (for CJK input)

**Mouse**:
- Three types: local (copy/paste), remote (app reporting), and hybrid
- Can be forced or conditional on Shift key
- Scroll wheel can send arrow keys in alternate buffer mode
- Click handling: single (position), double (word select), triple (line select)
- Right-click for context menu

### 7. Data Structures

**TerminalTextBuffer**:
- Main model: stores all terminal text with styles
- Two buffers: history (scrollback) and screen
- Can switch to alternate buffer (for full-screen apps)
- Thread-safe access with ReentrantLock
- Supports configurable history size
- Wrapped line tracking

**TerminalLine**:
- Represents one line of terminal text
- Contains styled character entries
- Tracks if line is wrapped (continuation of previous line)

**CharBuffer**:
- Efficient character storage for rendering
- Supports NUL characters and special markers
- Clipping and sub-buffer operations

## Key Classes and Responsibilities

### Core (Non-Swing)

| Class | Responsibility |
|-------|-----------------|
| `JediEmulator` | Parse and execute ANSI escape sequences |
| `Terminal` | Interface for terminal operations (cursor, text, modes) |
| `TerminalDisplay` | Interface for display callbacks |
| `TerminalTextBuffer` | Data model - stores styled text |
| `TtyConnector` | Interface to PTY/shell process |
| `TerminalStarter` | Orchestrates emulator loop |
| `TextStyle` | Text styling (colors, bold, italic, etc.) |
| `ColorPalette` | 256-color palette management |

### UI (Swing-based)

| Class | Responsibility |
|-------|-----------------|
| `TerminalPanel` | JComponent rendering terminal to Graphics2D |
| `JediTermWidget` | JPanel container for panel + scrollbar + search |
| `TerminalPanel$TerminalCursor` | Cursor state and rendering |
| `BlinkingTextTracker` | Manage blinking text state |
| `SettingsProvider` | Configuration interface |
| `AwtTransformers` | AWT↔Core type conversions |

## Swing/AWT Dependencies

**Heavy Swing Usage**:
- `javax.swing.JComponent` - TerminalPanel base
- `javax.swing.JPanel` - JediTermWidget base
- `javax.swing.JScrollBar` - Scrolling
- `javax.swing.JLayeredPane` - Component layering
- `javax.swing.Timer` - Repaint scheduling (WeakRedrawTimer)
- `javax.swing.event.ChangeListener` - Scroll model listening

**Heavy AWT Usage**:
- `java.awt.Graphics2D` - Drawing operations
- `java.awt.Font` - Font management
- `java.awt.FontMetrics` - Character metrics
- `java.awt.event.KeyEvent` - Keyboard input
- `java.awt.event.MouseEvent` - Mouse input
- `java.awt.image.BufferedImage` - Offscreen rendering
- `java.awt.Toolkit` - Platform services (clipboard, beep)
- `java.awt.Desktop` - URL opening

**UI Patterns**:
- Layout manager (custom TerminalLayout)
- Component hierarchy (JPanel → JLayeredPane → Components)
- Event handlers (KeyListener, MouseListener, MouseMotionListener, MouseWheelListener, FocusListener, ComponentListener, HierarchyListener, InputMethodListener)
- Model-View pattern (BoundedRangeModel for scrolling)

## Potential Compose Multiplatform Adaptation Points

**Highly Reusable** (can be extracted):
1. Core module entirely - no Swing dependencies
2. TerminalPanel rendering logic (with abstraction)
3. Input handling (requires retargeting to Compose key events)
4. Text selection logic
5. Cursor management
6. Hyperlink detection
7. Search functionality

**Requires Retargeting**:
1. Graphics rendering (Graphics2D → Compose Canvas)
2. Font/metrics calculation (AWT → Compose measured text)
3. Event handling (AWT events → Compose events)
4. Scrolling (JScrollBar → Compose ScrollState/LazyColumn)
5. Layout (custom TerminalLayout → Compose Row/Column)
6. Settings storage (currently interface, needs implementation)
7. Clipboard access (AWT Toolkit → Compose platform APIs)
8. Threading (SwingUtilities.invokeLater → Compose dispatchers)
9. Timers (Swing Timer → Compose LaunchedEffect)

**New Implementations Needed**:
1. Compose-specific SettingsProvider implementation
2. Platform-specific PTY connectors
3. Clipboard integration
4. Font rendering engine
5. Color picker/palette UI

