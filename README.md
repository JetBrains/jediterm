# BossTerm

[![CI](https://github.com/kshivang/BossTerm/actions/workflows/test.yml/badge.svg)](https://github.com/kshivang/BossTerm/actions/workflows/test.yml)
[![Release](https://github.com/kshivang/BossTerm/actions/workflows/release.yml/badge.svg)](https://github.com/kshivang/BossTerm/releases)
[![Maven Central](https://img.shields.io/maven-central/v/com.risaboss/bossterm-core)](https://central.sonatype.com/namespace/com.risaboss)

A modern terminal emulator built with **Kotlin** and **Compose Desktop**.

BossTerm is a high-performance terminal emulator designed for developers who want a fast, customizable, and feature-rich terminal experience on macOS, Linux, and Windows.

## Installation

### macOS (Homebrew)

```bash
brew tap kshivang/bossterm
brew install --cask bossterm
```

### macOS (DMG)

Download the latest DMG from [GitHub Releases](https://github.com/kshivang/BossTerm/releases) and drag BossTerm to Applications.

### Build from Source

```bash
git clone https://github.com/kshivang/BossTerm.git
cd BossTerm
./gradlew :compose-ui:run
```

## Features

- **Native Performance** - Built with Kotlin/Compose Desktop for smooth rendering
- **Multiple Windows** - Cmd/Ctrl+N opens new window, each with independent tabs
- **Multiple Tabs** - Ctrl+T new tab, Ctrl+W close, Ctrl+Tab switch
- **Xterm Emulation** - Full VT100/Xterm compatibility
- **256 Colors** - Full color support including true color (24-bit)
- **Mouse Support** - Click, scroll, and drag support for terminal apps (vim, tmux, htop)
- **Search** - Ctrl/Cmd+F to search terminal history
- **Hyperlink Detection** - Auto-detect URLs, file paths, emails with Ctrl+Click to open
- **Copy/Paste** - Standard clipboard operations + copy-on-select option
- **Drag & Drop** - Drop files onto terminal to paste shell-escaped paths (iTerm2 style)
- **IME Support** - Full Chinese/Japanese/Korean input method support
- **Debug Tools** - Built-in terminal debugging with Ctrl+Shift+D
- **OSC 7 Support** - Working directory tracking for new tabs
- **Customizable** - JSON-based settings at `~/.bossterm/settings.json`

## Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| Ctrl/Cmd+N | New window |
| Ctrl/Cmd+T | New tab |
| Ctrl/Cmd+W | Close tab |
| Ctrl+Tab | Next tab |
| Ctrl+Shift+Tab | Previous tab |
| Ctrl/Cmd+1-9 | Jump to tab |
| Ctrl/Cmd+F | Search |
| Ctrl/Cmd+C | Copy |
| Ctrl/Cmd+V | Paste |
| Ctrl/Cmd+Shift+D | Toggle debug panel |
| Ctrl+Space | Toggle IME |

## Shell Integration

Enable working directory tracking for new tabs:

**Bash** (`~/.bashrc`):
```bash
PROMPT_COMMAND='echo -ne "\033]7;file://${HOSTNAME}${PWD}\007"'
```

**Zsh** (`~/.zshrc`):
```bash
precmd() { echo -ne "\033]7;file://${HOST}${PWD}\007" }
```

## Project Structure

```
BossTerm/
├── bossterm-core-mpp/     # Core terminal emulation library
│   └── src/jvmMain/kotlin/ai/rever/bossterm/
│       ├── core/          # Core utilities and types
│       └── terminal/      # Terminal emulator implementation
├── compose-ui/            # Compose Desktop UI
│   └── src/desktopMain/kotlin/ai/rever/bossterm/compose/
│       ├── ui/            # Main terminal composable (ProperTerminal)
│       ├── terminal/      # Terminal data stream handling
│       ├── input/         # Mouse/keyboard input handling
│       ├── rendering/     # Canvas rendering engine
│       ├── tabs/          # Tab management
│       ├── search/        # Search functionality
│       ├── debug/         # Debug tools
│       ├── settings/      # Settings management
│       └── demo/          # Demo application entry point
└── .github/workflows/     # CI configuration
```

## Configuration

Settings are stored in `~/.bossterm/settings.json`:

```json
{
  "copyOnSelect": true,
  "pasteOnMiddleClick": true,
  "scrollbackLines": 10000,
  "cursorBlinkRate": 500,
  "enableMouseReporting": true
}
```

## Embedding in Your App

BossTerm provides embeddable terminal libraries for Kotlin Multiplatform projects.

### Gradle Setup

**Maven Central** (recommended):

[![Maven Central](https://img.shields.io/maven-central/v/com.risaboss/bossterm-core)](https://central.sonatype.com/namespace/com.risaboss)

```kotlin
// build.gradle.kts
repositories {
    mavenCentral()
}

dependencies {
    // Core terminal emulation engine
    implementation("com.risaboss:bossterm-core:<version>")

    // Compose Desktop UI component
    implementation("com.risaboss:bossterm-compose:<version>")
}
```

**JitPack** (alternative):

[![JitPack](https://jitpack.io/v/kshivang/BossTerm.svg)](https://jitpack.io/#kshivang/BossTerm)

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven { url = uri("https://jitpack.io") }
    }
}

// build.gradle.kts
dependencies {
    implementation("com.github.kshivang.BossTerm:bossterm-core-mpp:<version>")
    implementation("com.github.kshivang.BossTerm:compose-ui:<version>")
}
```

**GitHub Packages** (requires authentication):

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven {
            url = uri("https://maven.pkg.github.com/kshivang/BossTerm")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

// build.gradle.kts
dependencies {
    implementation("com.risaboss:bossterm-core:<version>")
    implementation("com.risaboss:bossterm-compose:<version>")
}
```

### Usage

```kotlin
import ai.rever.bossterm.compose.EmbeddableTerminal
import ai.rever.bossterm.compose.rememberEmbeddableTerminalState

@Composable
fun MyApp() {
    // Basic usage - uses default settings from ~/.bossterm/settings.json
    EmbeddableTerminal()

    // With custom settings path
    EmbeddableTerminal(settingsPath = "/path/to/settings.json")

    // With callbacks
    EmbeddableTerminal(
        onOutput = { output -> println(output) },
        onTitleChange = { title -> window.title = title },
        onExit = { code -> println("Shell exited: $code") },
        onReady = { println("Terminal ready!") }
    )

    // Programmatic control
    val state = rememberEmbeddableTerminalState()

    Button(onClick = { state.write("ls -la\n") }) {
        Text("Run ls")
    }

    EmbeddableTerminal(state = state)
}
```

## Technology Stack

- **Kotlin** - Modern JVM language
- **Compose Desktop** - Declarative UI framework
- **Pty4J** - PTY support for local terminal sessions
- **ICU4J** - Unicode/grapheme cluster support

## Contributing

Contributions are welcome! Please feel free to submit issues and pull requests.

## License

BossTerm is dual-licensed under:
- [LGPLv3](LICENSE-LGPLv3.txt)
- [Apache 2.0](LICENSE-APACHE-2.0.txt)

You may select either license at your option.

## Acknowledgments

BossTerm is a fork of [JediTerm](https://github.com/JetBrains/jediterm) by JetBrains, completely rewritten with Kotlin and Compose Desktop.

---

**Built by [Risa AI](https://risalabs.ai)**
