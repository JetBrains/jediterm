package org.jetbrains.jediterm.compose.demo

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "JediTerm Compose - Proper Terminal (JediTerminal Integration)"
    ) {
        ProperTerminal()
    }
}
