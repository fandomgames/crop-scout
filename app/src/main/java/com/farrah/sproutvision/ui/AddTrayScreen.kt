package com.farrah.sproutvision.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.farrah.sproutvision.data.VarietyDefaults
import com.farrah.sproutvision.util.MILLIS_PER_DAY
import com.farrah.sproutvision.util.formatDate
import com.farrah.sproutvision.util.startOfDay

private enum class TrayKbField { Name, CustomVariety }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTrayScreen(
    savedVarieties: List<String> = emptyList(),
    onSave: (name: String, variety: String, seededAt: Long) -> Unit,
    onCancel: () -> Unit,
    onClose: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var variety by remember { mutableStateOf(VarietyDefaults.all.first().name) }
    var customVariety by remember { mutableStateOf("") }
    var isCustom by remember { mutableStateOf(false) }
    var seededAt by remember { mutableStateOf(startOfDay(System.currentTimeMillis())) }
    var varietyExpanded by remember { mutableStateOf(false) }
    // In-panel keyboard target: the system keyboard never appears in these
    // panels, so focusing a field raises PanelKeyboard instead. Buttons
    // don't steal touch focus, so Done is the only dismiss path needed.
    var kbTarget by remember { mutableStateOf<TrayKbField?>(null) }
    val focusManager = LocalFocusManager.current

    val effectiveVariety = if (isCustom) customVariety.trim() else variety
    // Expected days is set later from the tray detail screen — creation
    // only needs a name and a variety.
    val canSave = name.isNotBlank() && effectiveVariety.isNotBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add tray") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Close menu")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Tray name") },
                    placeholder = { Text("e.g. Basil — Tray A") },
                    modifier = Modifier.fillMaxWidth()
                        .onFocusChanged { if (it.isFocused) kbTarget = TrayKbField.Name },
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))

                Text("Variety", style = MaterialTheme.typography.titleSmall)
                OutlinedButton(
                    onClick = { varietyExpanded = !varietyExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isCustom) "Custom…" else variety)
                }
                if (varietyExpanded) {
                    Column {
                        VarietyDefaults.all.forEach { info ->
                            VarietyOptionRow(
                                label = "${info.name} (~${info.daysToHarvest}d)",
                                selected = !isCustom && variety == info.name,
                                onClick = {
                                    variety = info.name
                                    isCustom = false
                                    varietyExpanded = false
                                }
                            )
                        }
                        if (savedVarieties.isNotEmpty()) {
                            Text(
                                "Saved",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            savedVarieties.forEach { name ->
                                VarietyOptionRow(
                                    label = name,
                                    selected = !isCustom && variety == name,
                                    onClick = {
                                        variety = name
                                        isCustom = false
                                        varietyExpanded = false
                                    }
                                )
                            }
                        }
                        VarietyOptionRow(
                            label = "Custom…",
                            selected = isCustom,
                            onClick = { isCustom = true; varietyExpanded = false }
                        )
                    }
                }

                if (isCustom) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = customVariety,
                        onValueChange = { customVariety = it },
                        label = { Text("Custom variety") },
                        modifier = Modifier.fillMaxWidth()
                            .onFocusChanged { if (it.isFocused) kbTarget = TrayKbField.CustomVariety },
                        singleLine = true
                    )
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { seededAt -= MILLIS_PER_DAY }) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous day")
                    }
                    Text(
                        "Seeded: ${formatDate(seededAt)}",
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                    IconButton(onClick = { seededAt += MILLIS_PER_DAY }) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = "Next day")
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    "Expected harvest days can be added later from the tray's detail screen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { onSave(name.trim(), effectiveVariety, seededAt) },
                    enabled = canSave,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save tray")
                }
            }
            if (kbTarget != null) {
                Spacer(Modifier.height(8.dp))
                PanelKeyboard(
                    onKey = { key ->
                        when (kbTarget) {
                            TrayKbField.Name -> name = panelKeyboardEdit(name, key)
                            TrayKbField.CustomVariety ->
                                customVariety = panelKeyboardEdit(customVariety, key)
                            null -> Unit
                        }
                    },
                    onDone = { kbTarget = null; focusManager.clearFocus() }
                )
            }
        }
    }
}

@Composable
private fun VarietyOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .selectable(selected = selected, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}
