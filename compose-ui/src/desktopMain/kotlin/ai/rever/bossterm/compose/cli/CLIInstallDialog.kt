package ai.rever.bossterm.compose.cli

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Dialog for installing/uninstalling the bossterm CLI tool.
 */
@Composable
fun CLIInstallDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    isFirstRun: Boolean = false
) {
    if (!visible) return

    val scope = rememberCoroutineScope()
    var isInstalled by remember { mutableStateOf(CLIInstaller.isInstalled()) }
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    DialogWindow(
        onCloseRequest = onDismiss,
        title = "Install Command Line Tool",
        resizable = false,
        state = rememberDialogState(size = DpSize(450.dp, 280.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1E1E1E))
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
            Column {
                Text(
                    text = if (isFirstRun) "Welcome to BossTerm!" else "Command Line Tool",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = if (isInstalled) {
                        "The 'bossterm' command is installed and ready to use."
                    } else {
                        "Install the 'bossterm' command to open BossTerm from your terminal."
                    },
                    color = Color(0xFFB0B0B0),
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Usage examples:",
                    color = Color(0xFF808080),
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Column(
                    modifier = Modifier
                        .background(Color(0xFF2B2B2B), RoundedCornerShape(4.dp))
                        .padding(8.dp)
                ) {
                    Text("bossterm", color = Color(0xFF6A9955), fontSize = 12.sp)
                    Text("bossterm ~/Projects", color = Color(0xFF6A9955), fontSize = 12.sp)
                    Text("bossterm -d /path/to/dir", color = Color(0xFF6A9955), fontSize = 12.sp)
                }
            }

            // Status message
            if (statusMessage != null) {
                Text(
                    text = statusMessage!!,
                    color = if (statusMessage!!.startsWith("Error")) Color(0xFFE57373) else Color(0xFF81C784),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            // Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color(0xFF4A90E2),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                }

                if (isInstalled) {
                    // Uninstall button
                    Button(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                statusMessage = null
                                val result = withContext(Dispatchers.IO) {
                                    CLIInstaller.uninstall()
                                }
                                isLoading = false
                                when (result) {
                                    is CLIInstaller.InstallResult.Success -> {
                                        statusMessage = "Uninstalled successfully"
                                        isInstalled = false
                                    }
                                    is CLIInstaller.InstallResult.Cancelled -> {
                                        statusMessage = "Uninstall cancelled"
                                    }
                                    is CLIInstaller.InstallResult.Error -> {
                                        statusMessage = "Error: ${result.message}"
                                    }
                                }
                            }
                        },
                        enabled = !isLoading,
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color(0xFF424242),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("Uninstall", fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                }

                // Install/Reinstall button
                if (!isInstalled || CLIInstaller.needsUpdate()) {
                    Button(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                statusMessage = null
                                val result = withContext(Dispatchers.IO) {
                                    CLIInstaller.install()
                                }
                                isLoading = false
                                when (result) {
                                    is CLIInstaller.InstallResult.Success -> {
                                        statusMessage = "Installed successfully! You can now use 'bossterm' command."
                                        isInstalled = true
                                    }
                                    is CLIInstaller.InstallResult.Cancelled -> {
                                        statusMessage = "Installation cancelled"
                                    }
                                    is CLIInstaller.InstallResult.Error -> {
                                        statusMessage = "Error: ${result.message}"
                                    }
                                }
                            }
                        },
                        enabled = !isLoading,
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color(0xFF4A90E2),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text(
                            if (isInstalled) "Update" else "Install",
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                }

                // Close/Skip button
                Button(
                    onClick = onDismiss,
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFF424242),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(
                        if (isFirstRun && !isInstalled) "Skip" else "Close",
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
