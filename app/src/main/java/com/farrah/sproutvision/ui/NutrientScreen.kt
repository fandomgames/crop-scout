package com.farrah.sproutvision.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.farrah.sproutvision.data.NutrientProduct

/**
 * Nutrient inventory in Manage. Setup uses the existing keyboard text
 * input (this is a desk-style setup screen, not in-headset feeding).
 * Feeding itself stays hands-only on TrayDetail.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutrientScreen(
    products: List<NutrientProduct>,
    onSave: (NutrientProduct) -> Unit,
    onDelete: (NutrientProduct) -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit
) {
    var editing by remember { mutableStateOf<NutrientProduct?>(null) }
    var adding by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nutrients") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
        if (adding || editing != null) {
            NutrientForm(
                initial = editing,
                onSave = {
                    onSave(it)
                    adding = false
                    editing = null
                },
                onCancel = {
                    adding = false
                    editing = null
                },
                modifier = Modifier.padding(padding)
            )
        } else {
            Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
                Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Add nutrient")
                }
                Spacer(Modifier.height(12.dp))
                if (products.isEmpty()) {
                    Text(
                        "No nutrients yet. Add your bottles and powders here first.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(products, key = { it.id }) { product ->
                            NutrientRow(
                                product = product,
                                onEdit = { editing = product },
                                onDelete = { onDelete(product) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NutrientRow(
    product: NutrientProduct,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var deleteGate by remember(product.id) { mutableStateOf<ConfirmGate<Unit>>(ConfirmGate.Idle) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(product.name, style = MaterialTheme.typography.titleSmall)
            Text(
                listOfNotNull(
                    product.brand.ifBlank { null },
                    product.npk?.ifBlank { null }?.let { "NPK $it" },
                    product.form.ifBlank { null }
                ).joinToString(" · ").ifBlank { "—" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Dose: ${product.doseAmount} ${product.doseUnit} / ${product.dosePerLiters} L",
                style = MaterialTheme.typography.bodyMedium
            )
            if (deleteGate is ConfirmGate.Staged) {
                Spacer(Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "Delete ${product.name}? Schedules and log entries using it go too.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Row(modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = { deleteGate = cancelAct() }) { Text("Cancel") }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = {
                                val (act, next) = takeAct(deleteGate)
                                deleteGate = next
                                if (act != null) onDelete()
                            }) {
                                Text("Delete", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onEdit) { Text("Edit") }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { deleteGate = stageAct(Unit) }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun NutrientForm(
    initial: NutrientProduct?,
    onSave: (NutrientProduct) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name ?: "") }
    var brand by remember(initial?.id) { mutableStateOf(initial?.brand ?: "") }
    var npk by remember(initial?.id) { mutableStateOf(initial?.npk ?: "") }
    var form by remember(initial?.id) { mutableStateOf(initial?.form ?: "Liquid") }
    var amount by remember(initial?.id) { mutableStateOf(initial?.doseAmount?.toString() ?: "") }
    var unit by remember(initial?.id) { mutableStateOf(initial?.doseUnit ?: "ml") }
    var perLiters by remember(initial?.id) { mutableStateOf(initial?.dosePerLiters?.toString() ?: "") }
    val amountValue = amount.toDoubleOrNull()
    val perLitersValue = perLiters.toDoubleOrNull()
    val valid = name.isNotBlank() && form.isNotBlank() && unit.isNotBlank() &&
        amountValue != null && amountValue > 0 && perLitersValue != null && perLitersValue > 0
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            if (initial == null) "Add nutrient" else "Edit nutrient",
            style = MaterialTheme.typography.titleMedium
        )
        OutlinedTextField(
            value = name, onValueChange = { name = it },
            label = { Text("Product") }, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = brand, onValueChange = { brand = it },
            label = { Text("Brand") }, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = npk, onValueChange = { npk = it },
            label = { Text("NPK (optional, e.g. 3-1-2)") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = form, onValueChange = { form = it },
            label = { Text("Form (Liquid / Powder)") }, modifier = Modifier.fillMaxWidth()
        )
        Text(
            "Dosage rule: amount per liters at full strength",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = amount, onValueChange = { amount = it },
            label = { Text("Dose amount") }, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = unit, onValueChange = { unit = it },
            label = { Text("Dose unit (ml / g / tsp)") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = perLiters, onValueChange = { perLiters = it },
            label = { Text("Per liters of reservoir") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                onSave(
                    NutrientProduct(
                        id = initial?.id ?: 0,
                        name = name.trim(),
                        brand = brand.trim(),
                        npk = npk.trim().ifBlank { null },
                        form = form.trim(),
                        doseAmount = amountValue!!,
                        doseUnit = unit.trim(),
                        dosePerLiters = perLitersValue!!
                    )
                )
            },
            enabled = valid,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save nutrient")
        }
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel")
        }
    }
}
