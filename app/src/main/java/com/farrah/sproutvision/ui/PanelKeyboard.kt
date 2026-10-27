package com.farrah.sproutvision.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * In-panel typing fallback. Tapping a text field in a Spatial SDK panel
 * focuses it (cursor blinks) but the Quest system keyboard never appears —
 * the panel's view lives on a virtual-display Presentation whose
 * showSoftInput the Horizon keyboard service silently ignores, and the
 * activity-window flag workaround does not unblock it (verified
 * in-headset, SDK 0.10.1). So typing goes through this QWERTY pad
 * instead: plain buttons, which hand pinch already clicks reliably.
 *
 * Editing is intentionally end-of-text only (append / drop-last): every
 * field using this is a short single line (tray name, API key, days).
 */
sealed interface PanelKey {
    data class Char(val c: kotlin.Char) : PanelKey
    data object Backspace : PanelKey
}

fun panelKeyboardEdit(current: String, key: PanelKey): String = when (key) {
    is PanelKey.Char -> current + key.c
    PanelKey.Backspace -> current.dropLast(1)
}

@Composable
fun PanelKeyboard(
    onKey: (PanelKey) -> Unit,
    onDone: () -> Unit,
    numericFirst: Boolean = false,
    modifier: Modifier = Modifier
) {
    var numeric by remember(numericFirst) { mutableStateOf(numericFirst) }
    var caps by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (!numeric) {
            KeyRow("qwertyuiop", caps) { onKey(PanelKey.Char(it)) }
            KeyRow("asdfghjkl", caps) { onKey(PanelKey.Char(it)) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (caps) {
                    Button(
                        onClick = { caps = false },
                        modifier = Modifier.weight(1.5f).height(52.dp)
                    ) { Text("Shift", style = MaterialTheme.typography.titleMedium) }
                } else {
                    OutlinedButton(
                        onClick = { caps = true },
                        modifier = Modifier.weight(1.5f).height(52.dp)
                    ) { Text("Shift", style = MaterialTheme.typography.titleMedium) }
                }
                for (c in "zxcvbnm") {
                    KeyButton(
                        label = if (caps) c.uppercaseChar().toString() else c.toString(),
                        onClick = {
                            onKey(PanelKey.Char(if (caps) c.uppercaseChar() else c))
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                BackspaceKey(
                    onClick = { onKey(PanelKey.Backspace) },
                    modifier = Modifier.weight(1.5f)
                )
            }
        } else {
            KeyRow("1234567890", false) { onKey(PanelKey.Char(it)) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (c in "-_./@+=") {
                    KeyButton(
                        label = c.toString(),
                        onClick = { onKey(PanelKey.Char(c)) },
                        modifier = Modifier.weight(1f)
                    )
                }
                BackspaceKey(
                    onClick = { onKey(PanelKey.Backspace) },
                    modifier = Modifier.weight(1.5f)
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedButton(
                onClick = { numeric = !numeric },
                modifier = Modifier.weight(1.5f).height(52.dp)
            ) {
                Text(
                    if (numeric) "ABC" else "123",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            OutlinedButton(
                onClick = { onKey(PanelKey.Char(' ')) },
                modifier = Modifier.weight(5f).height(52.dp)
            ) {
                Text("Space", style = MaterialTheme.typography.titleMedium)
            }
            Button(
                onClick = onDone,
                modifier = Modifier.weight(2f).height(52.dp)
            ) {
                Text("Done", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun KeyRow(letters: String, caps: Boolean, onKey: (kotlin.Char) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        for (c in letters) {
            val shown = if (caps) c.uppercaseChar() else c
            KeyButton(
                label = shown.toString(),
                onClick = { onKey(shown) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun KeyButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(52.dp)
    ) {
        Text(label, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun BackspaceKey(onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(52.dp)
    ) {
        Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Backspace")
    }
}
