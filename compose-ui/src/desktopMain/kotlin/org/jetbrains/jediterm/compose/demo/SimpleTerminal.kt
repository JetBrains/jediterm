package org.jetbrains.jediterm.compose.demo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.jediterm.compose.PlatformServices
import org.jetbrains.jediterm.compose.getPlatformServices

@Composable
fun SimpleTerminal(
    command: String = System.getenv("SHELL") ?: "/bin/bash",
    arguments: List<String> = listOf("--login"),
    modifier: Modifier = Modifier
) {
    var outputBuffer by remember { mutableStateOf("") }
    var processHandle by remember { mutableStateOf<PlatformServices.ProcessService.ProcessHandle?>(null) }
    var isFocused by remember { mutableStateOf(false) }
    var inputEcho by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val textMeasurer = rememberTextMeasurer()

    // Initialize process
    LaunchedEffect(Unit) {
        val services = getPlatformServices()
        val config = PlatformServices.ProcessService.ProcessConfig(
            command = command,
            arguments = arguments,
            environment = System.getenv(),
            workingDirectory = System.getProperty("user.home")
        )

        val handle = services.getProcessService().spawnProcess(config)
        processHandle = handle

        // Send initial newline to get prompt
        launch {
            kotlinx.coroutines.delay(100)
            handle?.write("\n")
        }

        // Read output in background
        launch {
            while (handle != null && handle.isAlive()) {
                val output = withContext(Dispatchers.IO) {
                    handle.read()
                }
                if (output != null) {
                    // Strip ANSI escape sequences for now
                    val cleanOutput = output.replace(Regex("\u001B\\[[0-9;]*[a-zA-Z]"), "")
                        .replace(Regex("\u001B\\][0-9];.*?\u0007"), "")  // Remove OSC sequences
                        .replace("\u001B\\[\\?[0-9]+[hl]".toRegex(), "")  // Remove mode changes
                        .replace("\r", "")  // Remove carriage returns

                    outputBuffer += cleanOutput
                    // Keep buffer reasonable size
                    if (outputBuffer.length > 50000) {
                        outputBuffer = outputBuffer.takeLast(40000)
                    }
                }
            }
        }

        focusRequester.requestFocus()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    scope.launch {
                        val char = when (keyEvent.key) {
                            Key.Enter -> {
                                inputEcho = ""
                                "\r"
                            }
                            Key.Backspace -> {
                                if (inputEcho.isNotEmpty()) {
                                    inputEcho = inputEcho.dropLast(1)
                                }
                                "\u007F"  // DEL character for backspace
                            }
                            Key.Tab -> "\t"
                            Key.Escape -> "\u001B"
                            Key.DirectionUp -> "\u001B[A"
                            Key.DirectionDown -> "\u001B[B"
                            Key.DirectionRight -> "\u001B[C"
                            Key.DirectionLeft -> "\u001B[D"
                            else -> {
                                val code = keyEvent.utf16CodePoint
                                if (code > 0) {
                                    val c = code.toChar().toString()
                                    inputEcho += c
                                    c
                                } else ""
                            }
                        }
                        if (char.isNotEmpty()) {
                            processHandle?.write(char)
                        }
                    }
                    true
                } else false
            }
            .focusRequester(focusRequester)
            .focusable()
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            val lines = outputBuffer.split("\n").takeLast(40)
            val lineHeight = 20f

            lines.forEachIndexed { index, line ->
                drawText(
                    textMeasurer = textMeasurer,
                    text = line,
                    topLeft = Offset(0f, index * lineHeight),
                    style = TextStyle(
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp
                    )
                )
            }

            // Show input echo and focus indicator at bottom
            if (isFocused) {
                if (inputEcho.isNotEmpty()) {
                    drawText(
                        textMeasurer = textMeasurer,
                        text = "Typing: $inputEcho█",
                        topLeft = Offset(0f, size.height - 30f),
                        style = TextStyle(
                            color = Color.Yellow,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    )
                } else {
                    drawText(
                        textMeasurer = textMeasurer,
                        text = "[Terminal Ready - Type here]",
                        topLeft = Offset(0f, size.height - 30f),
                        style = TextStyle(
                            color = Color.Green,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    )
                }
            } else {
                drawText(
                    textMeasurer = textMeasurer,
                    text = "[Click to focus terminal]",
                    topLeft = Offset(0f, size.height - 30f),
                    style = TextStyle(
                        color = Color.Gray,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            scope.launch {
                processHandle?.kill()
            }
        }
    }
}
