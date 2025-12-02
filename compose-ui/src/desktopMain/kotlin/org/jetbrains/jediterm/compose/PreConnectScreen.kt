package org.jetbrains.jediterm.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Pre-connection loading screen displayed while terminal is initializing.
 *
 * Displays:
 * - Loading spinner during [ConnectionState.Initializing] and [ConnectionState.Connecting]
 * - Error message with retry button for [ConnectionState.Error]
 * - Input field for [ConnectionState.RequiresInput] (password prompts, 2FA codes)
 * - Informational message for [ConnectionState.ShowMessage]
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
                        text = "Connection Failed",
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
            is ConnectionState.RequiresInput -> {
                PreConnectInputForm(
                    prompt = state.prompt,
                    isPassword = state.isPassword,
                    defaultValue = state.defaultValue,
                    onSubmit = state.onSubmit,
                    onCancel = state.onCancel
                )
            }
            is ConnectionState.ShowMessage -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = state.message,
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
            }
            is ConnectionState.RequiresSelection -> {
                PreConnectSelectionForm(
                    prompt = state.prompt,
                    options = state.options,
                    defaultIndex = state.defaultIndex,
                    onSelect = state.onSelect,
                    onCancel = state.onCancel
                )
            }
            is ConnectionState.Connected -> {
                // Connected state handled by parent - show nothing here
            }
        }
    }
}

/**
 * Input form for pre-connection user input (passwords, authentication codes, etc.)
 */
@Composable
private fun PreConnectInputForm(
    prompt: String,
    isPassword: Boolean,
    defaultValue: String?,
    onSubmit: (String) -> Unit,
    onCancel: (() -> Unit)?
) {
    var inputValue by remember { mutableStateOf(defaultValue ?: "") }
    val focusRequester = remember { FocusRequester() }

    // Auto-focus the input field
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(32.dp).widthIn(max = 400.dp)
    ) {
        // Prompt text
        Text(
            text = prompt,
            color = Color.White,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Input field
        OutlinedTextField(
            value = inputValue,
            onValueChange = { inputValue = it },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        when (event.key) {
                            Key.Enter -> {
                                onSubmit(inputValue)
                                true
                            }
                            Key.Escape -> {
                                onCancel?.invoke()
                                true
                            }
                            else -> false
                        }
                    } else {
                        false
                    }
                },
            visualTransformation = if (isPassword) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            singleLine = true,
            placeholder = {
                Text(
                    text = if (isPassword) "Enter password..." else "Enter value...",
                    color = Color.Gray
                )
            },
            colors = TextFieldDefaults.outlinedTextFieldColors(
                textColor = Color.White,
                cursorColor = Color.White,
                focusedBorderColor = Color(0xFF5C9FFF),
                unfocusedBorderColor = Color.Gray,
                backgroundColor = Color(0xFF2B2B2B)
            ),
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { onSubmit(inputValue) }
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Action buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (onCancel != null) {
                OutlinedButton(
                    onClick = { onCancel() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        backgroundColor = Color.Transparent,
                        contentColor = Color.White
                    ),
                    border = BorderStroke(1.dp, Color.Gray)
                ) {
                    Text("Cancel")
                }
            }
            Button(
                onClick = { onSubmit(inputValue) },
                modifier = if (onCancel != null) Modifier.weight(1f) else Modifier.fillMaxWidth()
            ) {
                Text("Submit")
            }
        }

        // Help text
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Press Enter to submit" + if (onCancel != null) ", Escape to cancel" else "",
            color = Color.Gray,
            fontSize = 12.sp
        )
    }
}

/**
 * Dropdown selection form for choosing from predefined options.
 */
@Composable
private fun PreConnectSelectionForm(
    prompt: String,
    options: List<ConnectionState.SelectOption>,
    defaultIndex: Int,
    onSelect: (String) -> Unit,
    onCancel: (() -> Unit)?
) {
    var selectedIndex by remember { mutableStateOf(defaultIndex.coerceIn(0, options.lastIndex)) }
    var expanded by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(32.dp).widthIn(max = 400.dp)
    ) {
        // Prompt text
        Text(
            text = prompt,
            color = Color.White,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Dropdown selector
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    backgroundColor = Color(0xFF2B2B2B),
                    contentColor = Color.White
                ),
                border = BorderStroke(1.dp, Color.Gray)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = options.getOrNull(selectedIndex)?.label ?: "Select...",
                        color = Color.White
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Dropdown",
                        tint = Color.White
                    )
                }
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .background(Color(0xFF2B2B2B))
                    .widthIn(min = 300.dp)
            ) {
                options.forEachIndexed { index, option ->
                    DropdownMenuItem(
                        onClick = {
                            selectedIndex = index
                            expanded = false
                        },
                        modifier = Modifier.background(
                            if (index == selectedIndex) Color(0xFF3C3C3C) else Color.Transparent
                        )
                    ) {
                        Text(
                            text = option.label,
                            color = Color.White
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Action buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (onCancel != null) {
                OutlinedButton(
                    onClick = { onCancel() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        backgroundColor = Color.Transparent,
                        contentColor = Color.White
                    ),
                    border = BorderStroke(1.dp, Color.Gray)
                ) {
                    Text("Cancel")
                }
            }
            Button(
                onClick = {
                    options.getOrNull(selectedIndex)?.let { onSelect(it.value) }
                },
                modifier = if (onCancel != null) Modifier.weight(1f) else Modifier.fillMaxWidth()
            ) {
                Text("Continue")
            }
        }
    }
}
