package org.jetbrains.jediterm.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Pre-connection loading screen displayed while terminal is initializing.
 *
 * Displays:
 * - Loading spinner during [ConnectionState.Initializing] and [ConnectionState.Connecting]
 * - Error message with retry button for [ConnectionState.Error]
 * - Nothing when [ConnectionState.Connected] (parent handles terminal rendering)
 *
 * @param state Current connection state
 * @param onRetry Callback invoked when user clicks retry button after error
 */
@Composable
fun PreConnectScreen(
    state: ConnectionState,
    onRetry: () -> Unit = {}
) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF1E1E1E)),
        contentAlignment = Alignment.Center
    ) {
        when (state) {
            is ConnectionState.Initializing,
            is ConnectionState.Connecting -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Connecting to shell...",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
            }
            is ConnectionState.Error -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "⚠️ Connection Failed",
                        color = Color.Red,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.message,
                        color = Color.White,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onRetry) {
                        Text("Retry")
                    }
                }
            }
            is ConnectionState.Connected -> {
                // Connected state handled by parent - show nothing here
            }
        }
    }
}
