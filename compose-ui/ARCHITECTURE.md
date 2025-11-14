# JediTerm Compose Rendering Architecture

## High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     Terminal Composable                      │
│                       (Track 7)                              │
└────────────────────────┬────────────────────────────────────┘
                         │
                         │ Creates & Uses
                         ▼
┌─────────────────────────────────────────────────────────────┐
│              ComposeTerminalRenderer                         │
│           (Main Rendering Coordinator)                       │
│                                                              │
│  • beginFrame() / endFrame()                                │
│  • renderCharacter()                                        │
│  • renderTextRun()                                          │
│  • clearRegion()                                            │
└───┬─────────────────┬─────────────────┬────────────────────┘
    │                 │                 │
    │ Uses            │ Uses            │ Uses
    ▼                 ▼                 ▼
┌──────────┐   ┌──────────────┐   ┌──────────────┐
│  Font    │   │    Color     │   │  Compose     │
│ Manager  │   │   Mapper     │   │  DrawScope   │
│          │   │              │   │              │
│ • Cell   │   │ • 256-color  │   │ • drawText() │
│   dims   │   │   ANSI       │   │ • drawRect() │
│ • Style  │   │ • RGB color  │   │ • drawLine() │
│   cache  │   │ • Inverse    │   │              │
│ • CJK    │   │ • Dim        │   │              │
│   detect │   │              │   │              │
└─────┬────┘   └──────┬───────┘   └──────────────┘
      │                │
      │ Uses           │ Uses
      ▼                ▼
┌──────────────┐   ┌──────────────────┐
│   Platform   │   │  TerminalTheme   │
│  FontLoader  │   │                  │
│              │   │ • Default colors │
│ • Desktop    │   │ • ANSI palette   │
│ • Android    │   │ • Cursor color   │
│ • iOS        │   │ • Selection      │
│ • Web        │   │                  │
└──────────────┘   └──────────────────┘
```

## Component Interaction Flow

### Rendering a Frame

```
1. Terminal Composable
   ├─► beginFrame()
   │
2. For each visible line:
   │
   ├─► Parse line from TextBuffer (core module)
   │   ├─► Extract TextStyle and TerminalColor
   │   └─► Group consecutive chars with same style
   │
3. For each text run:
   │
   ├─► ColorMapper.toComposeColor()
   │   ├─► Convert indexed colors (0-255)
   │   ├─► Convert RGB colors
   │   └─► Apply inverse/dim effects
   │
   ├─► FontManager.getTextStyle()
   │   ├─► Return cached TextStyle
   │   └─► Apply bold/italic
   │
   ├─► TextMeasurer.measure()
   │   └─► Calculate text layout
   │
   ├─► DrawScope.drawRect()
   │   └─► Fill background
   │
   ├─► DrawScope.drawText()
   │   └─► Render characters
   │
   └─► Draw decorations (if needed)
       ├─► drawLine() for underline
       └─► drawLine() for strikethrough
   │
4. endFrame()
   └─► Track dirty regions
```

## Data Flow

```
┌────────────────┐
│  TextBuffer    │
│  (core module) │
└───────┬────────┘
        │
        │ Provides
        ▼
┌────────────────┐      ┌──────────────┐
│   TextStyle    │──────│TerminalColor │
│                │      │              │
│ • foreground   │      │ • index      │
│ • background   │      │ • rgb        │
│ • options:     │      └──────────────┘
│   - BOLD       │
│   - ITALIC     │      ┌──────────────┐
│   - UNDERLINE  │      │    Theme     │
│   - INVERSE    │      │              │
└───────┬────────┘      │ • colors[]   │
        │               │ • defaultFg  │
        │               │ • defaultBg  │
        │               └──────┬───────┘
        │                      │
        ▼                      ▼
┌─────────────────────────────────────┐
│      ColorMapper + FontManager      │
│                                     │
│  Convert terminal styles to         │
│  Compose rendering primitives       │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│     CharacterStyle (Compose)        │
│                                     │
│  • foreground: Color                │
│  • background: Color                │
│  • textStyle: TextStyle             │
│  • decorations: Boolean flags       │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│      ComposeTerminalRenderer        │
│                                     │
│  Renders to Canvas                  │
└─────────────────────────────────────┘
```

## Font System Architecture

```
┌──────────────────────────────────────┐
│      PlatformFontLoader (expect)     │
│                                      │
│  • getMonospaceFontFamily()         │
│  • loadMonospaceFont()              │
│  • isFontAvailable()                │
└────────────┬─────────────────────────┘
             │
             │ Platform-specific actual implementations
             │
    ┌────────┴────────┬────────┬────────┬────────┐
    │                 │        │        │        │
    ▼                 ▼        ▼        ▼        ▼
┌────────┐   ┌────────────┐  ┌────┐  ┌────┐  ┌──────┐
│Desktop │   │  Android   │  │iOS │  │ JS │  │ WASM │
│        │   │            │  │    │  │    │  │      │
│ Java   │   │  System    │  │ SF │  │Web │  │ Web  │
│ AWT    │   │  Fonts     │  │Mono│  │Safe│  │ Safe │
└───┬────┘   └─────┬──────┘  └─┬──┘  └─┬──┘  └───┬──┘
    │              │            │       │         │
    └──────────────┴────────────┴───────┴─────────┘
                   │
                   ▼
           ┌───────────────┐
           │  FontManager  │
           │               │
           │ • Cache font  │
           │   styles      │
           │ • Calculate   │
           │   metrics     │
           │ • Detect CJK  │
           └───────────────┘
```

## Color System Architecture

```
┌────────────────┐
│ TerminalColor  │
│  (Java class)  │
│                │
│ • index: int   │
│ • rgb: Color   │
└───────┬────────┘
        │
        ▼
┌────────────────────────────────┐
│         ColorMapper            │
│                                │
│  toComposeColor(termColor)     │
└────────┬───────────────────────┘
         │
         ├─ Indexed (0-255)?
         │  ├─ 0-15:    ANSI colors
         │  ├─ 16-231:  Color cube (6×6×6)
         │  └─ 232-255: Grayscale
         │
         └─ RGB? Direct conversion

         ▼
┌────────────────┐
│ Compose Color  │
│                │
│ • red: Float   │
│ • green: Float │
│ • blue: Float  │
│ • alpha: Float │
└────────────────┘
```

## Character Rendering Pipeline

```
Input: char + TextStyle + TerminalColor
  │
  ▼
┌─────────────────────────────────┐
│ 1. Color Conversion             │
│    • Map foreground color       │
│    • Map background color       │
│    • Apply inverse if needed    │
│    • Apply dim if needed        │
└──────────────┬──────────────────┘
               ▼
┌─────────────────────────────────┐
│ 2. Font Selection               │
│    • Check bold flag            │
│    • Check italic flag          │
│    • Get cached TextStyle       │
└──────────────┬──────────────────┘
               ▼
┌─────────────────────────────────┐
│ 3. Position Calculation         │
│    • x = col × cellWidth        │
│    • y = row × cellHeight       │
│    • Handle double-width (CJK)  │
└──────────────┬──────────────────┘
               ▼
┌─────────────────────────────────┐
│ 4. Background Rendering         │
│    • drawRect(background)       │
└──────────────┬──────────────────┘
               ▼
┌─────────────────────────────────┐
│ 5. Text Rendering               │
│    • TextMeasurer.measure()     │
│    • Calculate baseline         │
│    • drawText()                 │
└──────────────┬──────────────────┘
               ▼
┌─────────────────────────────────┐
│ 6. Decoration Rendering         │
│    • Underline: drawLine()      │
│    • Strikethrough: drawLine()  │
└─────────────────────────────────┘
```

## Performance Optimizations

### Text Run Batching

```
Without optimization:
┌─┐┌─┐┌─┐┌─┐┌─┐  5 draw calls for "Hello"
│H││e││l││l││o│
└─┘└─┘└─┘└─┘└─┘

With optimization:
┌───────────┐  1 draw call for "Hello"
│   Hello   │
└───────────┘

Result: ~80% fewer draw calls
```

### Font Style Caching

```
First render:
┌────────────────┐
│ Create styles  │ ← Expensive
│ • Normal       │
│ • Bold         │
│ • Italic       │
│ • Bold+Italic  │
└────────────────┘
       │
       ▼
┌────────────────┐
│ Cache in       │
│ FontManager    │
└────────────────┘

Subsequent renders:
┌────────────────┐
│ Lookup cached  │ ← Fast
│ style          │
└────────────────┘
```

### Dirty Region Tracking

```
Full screen (80×24):
┌──────────────────────────────┐
│ ████████████████████████████ │ 1920 cells
│ ████████████████████████████ │
│ ████████████████████████████ │
│ ... (24 lines)               │
└──────────────────────────────┘

Dirty region (only changed area):
┌──────────────────────────────┐
│                              │
│     ┌──────┐                 │ 10 cells
│     │██████│                 │
│     └──────┘                 │
└──────────────────────────────┘

Result: Render only 10 cells instead of 1920
```

## Integration with Core Module

```
┌────────────────────────────────────┐
│     JediTerm Core (Java)           │
│                                    │
│  • Terminal                        │
│  • TextBuffer                      │
│  • TextStyle                       │
│  • TerminalColor                   │
│  • JediEmulator                    │
└──────────────┬─────────────────────┘
               │
               │ Data model
               ▼
┌────────────────────────────────────┐
│   JediTerm Compose UI (Kotlin)     │
│                                    │
│  • TerminalRenderer (interface)    │
│  • ComposeTerminalRenderer (impl)  │
│  • FontManager                     │
│  • ColorMapper                     │
│  • PlatformFontLoader              │
└────────────────────────────────────┘
```

## File Organization

```
compose-ui/
├── src/
│   ├── commonMain/kotlin/org/jetbrains/jediterm/compose/
│   │   ├── TerminalRenderer.kt         (Interface)
│   │   ├── rendering/
│   │   │   ├── ComposeTerminalRenderer.kt  ← Main renderer
│   │   │   ├── FontManager.kt              ← Font system
│   │   │   ├── ColorMapper.kt              ← Color conversion
│   │   │   ├── PlatformFontLoader.kt       ← expect declarations
│   │   │   └── README.md                   ← Documentation
│   │   └── stubs/
│   │       └── StubImplementations.kt
│   │
│   ├── desktopMain/kotlin/.../rendering/
│   │   └── PlatformFontLoader.desktop.kt   ← JVM implementation
│   │
│   ├── androidMain/kotlin/.../rendering/
│   │   └── PlatformFontLoader.android.kt   ← Android implementation
│   │
│   ├── iosMain/kotlin/.../rendering/
│   │   └── PlatformFontLoader.ios.kt       ← iOS implementation
│   │
│   ├── jsMain/kotlin/.../rendering/
│   │   └── PlatformFontLoader.js.kt        ← Web implementation
│   │
│   ├── wasmJsMain/kotlin/.../rendering/
│   │   └── PlatformFontLoader.wasmJs.kt    ← WASM implementation
│   │
│   └── commonTest/kotlin/.../rendering/
│       ├── ColorMapperTest.kt
│       ├── FontManagerTest.kt
│       └── RendererIntegrationTest.kt
```

## Threading Model

```
┌──────────────┐
│  Main Thread │
│   (UI)       │
└──────┬───────┘
       │
       ├─► Terminal Composable (recomposition)
       │
       ├─► ComposeTerminalRenderer.renderCharacter()
       │   └─► Synchronous, on main thread
       │
       ├─► FontManager.getCellDimensions()
       │   └─► Cached, fast lookup
       │
       └─► ColorMapper.toComposeColor()
           └─► Pure function, no side effects

Note: All rendering happens on the Compose main thread.
No background threads needed for basic rendering.
```

## Extension Points

Future tracks can extend the rendering system:

```
ComposeTerminalRenderer
        │
        ├─► Track 5: CursorRenderer
        │   • Overlay cursor on text
        │   • Blink animation
        │
        ├─► Track 3: SelectionRenderer
        │   • Overlay selection highlight
        │   • Multi-line selection
        │
        └─► Future: Custom overlays
            • Hyperlinks
            • Search highlights
            • Diff markers
```

## Summary

The rendering architecture is designed for:
- **Performance**: Optimized rendering with caching and batching
- **Portability**: Platform-specific implementations via expect/actual
- **Extensibility**: Clean interfaces for future enhancements
- **Maintainability**: Clear separation of concerns
- **Testability**: Unit tests for all components
