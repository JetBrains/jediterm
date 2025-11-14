# Track 7: Quick Start Guide

## Overview

Track 7 provides the complete public API and integration layer for JediTerm Compose Multiplatform. This guide will help you quickly understand and use the API.

## 5-Minute Quick Start

### 1. Basic Terminal

The simplest way to use the terminal:

```kotlin
@Composable
fun App() {
    Terminal(
        command = "/bin/bash",
        modifier = Modifier.fillMaxSize()
    )
}
```

### 2. With State Management

For more control:

```kotlin
@Composable
fun App() {
    val state = rememberTerminalState()
    val controller = rememberTerminalController(state)

    LaunchedEffect(Unit) {
        controller.connect("/bin/bash", listOf("--login"))
    }

    Terminal(
        state = state,
        controller = controller,
        modifier = Modifier.fillMaxSize()
    )
}
```

### 3. Custom Configuration

With custom theme and settings:

```kotlin
@Composable
fun App() {
    val config = TerminalState.TerminalConfig(
        columns = 120,
        rows = 40,
        theme = TerminalState.TerminalTheme.dark()
    )

    val state = rememberTerminalState(config)
    val controller = rememberTerminalController(state)

    Terminal(
        state = state,
        controller = controller,
        modifier = Modifier.fillMaxSize(),
        onTitleChange = { title -> println("Title: $title") },
        onBell = { println("Beep!") }
    )
}
```

## Core Concepts

### TerminalState

Observable state holder using Kotlin StateFlow:

```kotlin
val state = rememberTerminalState()

// Observe state in Compose
val theme by state.theme.collectAsState()
val isConnected by state.isConnected.collectAsState()
val dimensions by state.dimensions.collectAsState()

// Update state
state.updateTheme(TerminalState.TerminalTheme.light())
state.setDimensions(100, 30)
state.scrollBy(10)
```

### TerminalController

API for terminal operations:

```kotlin
val controller = rememberTerminalController(state)

// Connect to process
controller.connect("/bin/zsh", listOf("--login"))

// Send input
controller.sendText("ls -la\n")

// Operations
controller.clearScreen()
controller.reset()
val selectedText = controller.copySelection()

// Cleanup
DisposableEffect(controller) {
    onDispose { controller.dispose() }
}
```

### Themes

Built-in themes or custom:

```kotlin
// Built-in
val darkTheme = TerminalState.TerminalTheme.dark()
val lightTheme = TerminalState.TerminalTheme.light()

// Custom
val customTheme = TerminalState.TerminalTheme(
    defaultForeground = Color(0xFF00FF00),
    defaultBackground = Color(0xFF000000),
    cursorColor = Color(0xFF00FF00),
    selectionBackground = Color(0x8000FF00)
)

state.updateTheme(customTheme)
```

## Common Patterns

### Sending Commands

```kotlin
@Composable
fun TerminalWithButtons() {
    val controller = rememberTerminalController(state)
    val scope = rememberCoroutineScope()

    Column {
        Row {
            Button(onClick = {
                scope.launch { controller.sendText("pwd\n") }
            }) { Text("PWD") }

            Button(onClick = {
                scope.launch { controller.sendText("date\n") }
            }) { Text("Date") }
        }

        Terminal(
            state = state,
            controller = controller,
            modifier = Modifier.weight(1f)
        )
    }
}
```

### Monitoring State

```kotlin
@Composable
fun TerminalWithStatus() {
    val state = rememberTerminalState()
    val isConnected by state.isConnected.collectAsState()
    val dimensions by state.dimensions.collectAsState()

    Column {
        StatusBar(
            connected = isConnected,
            dimensions = dimensions
        )

        Terminal(
            state = state,
            controller = rememberTerminalController(state),
            modifier = Modifier.weight(1f)
        )
    }
}
```

### Dynamic Configuration

```kotlin
@Composable
fun ConfigurableTerminal() {
    var fontSize by remember { mutableStateOf(14f) }
    val state = rememberTerminalState()

    Column {
        Slider(
            value = fontSize,
            onValueChange = { fontSize = it },
            valueRange = 8f..24f
        )

        Terminal(
            state = state,
            controller = rememberTerminalController(state),
            modifier = Modifier.weight(1f)
        )
    }
}
```

## Architecture Overview

```
┌─────────────────────────────────────┐
│     Terminal Composable             │  ← Your app uses this
│  (Public API - Multiplatform)       │
└──────────────┬──────────────────────┘
               │
               ↓
┌─────────────────────────────────────┐
│      TerminalOrchestrator           │  ← Coordinates components
│   (Tracks 1-7 Integration)          │
└──┬──────────┬──────────┬────────────┘
   │          │          │
   ↓          ↓          ↓
┌─────┐  ┌────────┐  ┌──────────┐
│State│  │Control │  │Components│      ← Track implementations
└─────┘  └────────┘  └──────────┘
   │          │          │
   └──────────┴──────────┘
               ↓
┌─────────────────────────────────────┐
│       EmulatorBridge                │  ← Platform-specific
│   (JediTerm Core Integration)       │
└─────────────────────────────────────┘
```

## Key Files

### API Layer
- `TerminalState.kt` - State interface (in `commonMain`)
- `api/TerminalStateImpl.kt` - State implementation
- `api/TerminalControllerImpl.kt` - Controller implementation
- `integration/TerminalOrchestrator.kt` - Component coordinator

### Platform Implementations
- `Terminal.desktop.kt` - Desktop/JVM implementation
- `Terminal.android.kt` - Android implementation
- `Terminal.ios.kt` - iOS implementation
- `Terminal.js.kt` - JavaScript implementation

## Next Steps

1. **Read the full documentation**: `TRACK7_API_INTEGRATION.md`
2. **Check the implementation status**: `TRACK7_IMPLEMENTATION_CHECKLIST.md`
3. **Try the examples** in this guide
4. **Explore platform-specific features** in respective `.kt` files
5. **Contribute** to platform implementations as needed

## Current Status

- ✅ **API Layer**: Complete and production-ready
- ✅ **Desktop**: Orchestration complete, pending component implementations
- ⏳ **Other Platforms**: Stub implementations, need full development
- ⏳ **Components**: Integration points ready, waiting for Tracks 1-6

## Getting Help

- Detailed API documentation: `TRACK7_API_INTEGRATION.md`
- Implementation checklist: `TRACK7_IMPLEMENTATION_CHECKLIST.md`
- Component interfaces: See `compose/` package
- Core integration: See `integration/` package

## FAQs

### Q: Can I use this in production?
**A:** The API layer is production-ready. Desktop implementation needs full EmulatorBridge integration. Other platforms are in development.

### Q: Which platforms are supported?
**A:** API supports all platforms. Desktop is primary focus. Android, iOS, JS, and Wasm support coming soon.

### Q: How do I integrate with existing JediTerm code?
**A:** Through `EmulatorBridge`. Desktop implementation provides the integration point with JediTerm core classes.

### Q: Can I customize the rendering?
**A:** Yes! Implement `TerminalRenderer` and other component interfaces. See Track 1-6 for details.

### Q: How is state management done?
**A:** Using Kotlin `StateFlow`. All state is reactive and thread-safe. Perfect for Compose.

### Q: What about performance?
**A:** Designed for 60 FPS rendering with damage tracking. Text buffer snapshots are immutable and efficient.

## Simple Complete Example

```kotlin
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.jediterm.compose.*

@Composable
fun MyTerminalApp() {
    var darkMode by remember { mutableStateOf(true) }

    val config = TerminalState.TerminalConfig(
        theme = if (darkMode) {
            TerminalState.TerminalTheme.dark()
        } else {
            TerminalState.TerminalTheme.light()
        }
    )

    val state = rememberTerminalState(config)
    val controller = rememberTerminalController(state)
    val scope = rememberCoroutineScope()

    // Connect on start
    LaunchedEffect(Unit) {
        controller.connect("/bin/bash", listOf("--login"))
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Toolbar
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = {
                scope.launch { controller.sendText("clear\n") }
            }) {
                Text("Clear")
            }

            Button(onClick = {
                darkMode = !darkMode
                state.updateTheme(
                    if (darkMode) TerminalState.TerminalTheme.dark()
                    else TerminalState.TerminalTheme.light()
                )
            }) {
                Text(if (darkMode) "Light" else "Dark")
            }
        }

        // Terminal
        Terminal(
            state = state,
            controller = controller,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            onTitleChange = { title ->
                println("Terminal title changed: $title")
            },
            onBell = {
                println("Bell!")
            }
        )
    }

    // Cleanup
    DisposableEffect(controller) {
        onDispose {
            controller.dispose()
        }
    }
}
```

---

**Ready to build?** Start with the simple examples above and gradually explore more advanced features as needed!
