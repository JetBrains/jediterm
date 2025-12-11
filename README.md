# BossTerm

[![CI](https://github.com/kshivang/BossTerm/actions/workflows/test.yml/badge.svg)](https://github.com/kshivang/BossTerm/actions/workflows/test.yml)
[![Release](https://github.com/kshivang/BossTerm/actions/workflows/release.yml/badge.svg)](https://github.com/kshivang/BossTerm/releases)
[![Download DMG](https://img.shields.io/github/v/release/kshivang/BossTerm?label=Download%20DMG&logo=apple)](https://github.com/kshivang/BossTerm/releases/latest)
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

- **Native Performance** - Built with Kotlin/Compose Desktop for smooth 60fps rendering
- **Multiple Windows** - Cmd/Ctrl+N opens new window, each with independent tabs
- **Multiple Tabs** - Ctrl+T new tab, Ctrl+W close, Ctrl+Tab switch
- **Split Panes** - Horizontal/vertical splits with Cmd+D / Cmd+Shift+D
- **Themes** - Built-in theme presets (Dracula, Solarized, Nord, etc.) with custom theme support
- **Window Transparency** - Adjustable opacity with background blur effects
- **Background Images** - Custom background images with blur and opacity controls
- **Xterm Emulation** - Full VT100/Xterm compatibility
- **True Color** - Full 256 color and 24-bit true color support
- **Mouse Reporting** - Click, scroll, and drag support for terminal apps (vim, tmux, htop, less, fzf)
- **Full Unicode** - Emoji (👨‍👩‍👧‍👦), variation selectors (☁️), surrogate pairs, combining characters
- **Nerd Fonts** - Built-in support for powerline symbols and devicons
- **Inline Images** - Display images in terminal via iTerm2's imgcat (OSC 1337)
- **Progress Bar** - Visual progress indicator for long-running commands (OSC 1337)
- **Search** - Ctrl/Cmd+F to search terminal history with regex support
- **Hyperlink Detection** - Auto-detect URLs, file paths, emails with Ctrl+Click to open
- **Copy/Paste** - Standard clipboard + copy-on-select + middle-click paste + OSC 52
- **Context Menu** - Right-click for Copy, Paste, Clear, Select All
- **Drag & Drop** - Drop files onto terminal to paste shell-escaped paths (iTerm2 style)
- **Auto-Scroll Selection** - Drag selection beyond bounds to scroll through history
- **IME Support** - Full Chinese/Japanese/Korean input method support
- **Visual Bell** - Configurable visual flash for BEL character
- **Command Notifications** - System notifications when long commands complete (OSC 133)
- **OSC 7 Support** - Working directory tracking for new tabs
- **Settings UI** - Full GUI settings panel with live preview
- **Debug Tools** - Built-in terminal debugging with Ctrl+Shift+D
- **Customizable** - JSON-based settings at `~/.bossterm/settings.json`

## Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| Ctrl/Cmd+N | New window |
| Ctrl/Cmd+T | New tab |
| Ctrl/Cmd+W | Close tab/pane |
| Ctrl+Tab | Next tab |
| Ctrl+Shift+Tab | Previous tab |
| Ctrl/Cmd+1-9 | Jump to tab |
| Ctrl/Cmd+D | Split pane vertically |
| Ctrl/Cmd+Shift+D | Split pane horizontally |
| Ctrl/Cmd+Option+Arrow | Navigate between panes |
| Ctrl/Cmd+, | Open settings |
| Ctrl/Cmd+F | Search |
| Ctrl/Cmd+C | Copy |
| Ctrl/Cmd+V | Paste |
| Ctrl+Space | Toggle IME |

## Shell Integration

Enable working directory tracking and command completion notifications:

**Bash** (`~/.bashrc`):
```bash
# OSC 7 (directory tracking) + OSC 133 (command notifications)
__prompt_command() {
    local exit_code=$?
    echo -ne "\033]133;D;${exit_code}\007"  # Command finished
    echo -ne "\033]133;A\007"                # Prompt starting
    echo -ne "\033]7;file://${HOSTNAME}${PWD}\007"  # Working directory
}
PROMPT_COMMAND='__prompt_command'
trap 'echo -ne "\033]133;B\007"' DEBUG  # Command starting
```

**Zsh** (`~/.zshrc`):
```bash
# OSC 7 (directory tracking) + OSC 133 (command notifications)
precmd() {
    local exit_code=$?
    print -Pn "\e]133;D;${exit_code}\a"      # Command finished
    print -Pn "\e]133;A\a"                   # Prompt starting
    print -Pn "\e]7;file://${HOST}${PWD}\a"  # Working directory
}
preexec() { print -Pn "\e]133;B\a" }         # Command starting
```

This enables:
- New tabs inherit working directory from active tab
- System notifications when commands > 5 seconds complete while window is unfocused

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
  "enableMouseReporting": true,
  "notifyOnCommandComplete": true,
  "notifyMinDurationSeconds": 5
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
