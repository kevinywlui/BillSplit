package com.kevinywlui.billsplit.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.kevinywlui.billsplit.ocr.ReceiptModel
import com.kevinywlui.billsplit.viewmodel.BillViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: BillViewModel,
    onBack: () -> Unit
) {
    val savedKey by viewModel.apiKey.collectAsState()
    var keyInput by remember(savedKey) { mutableStateOf(savedKey) }
    var revealed by remember { mutableStateOf(false) }
    val savedModelId by viewModel.receiptModelId.collectAsState()
    val context = LocalContext.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Anthropic API key",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                "Receipt scanning sends the photo to Claude to read the line items. " +
                    "Your key is stored only on this device — it is never bundled into the " +
                    "app or shared. Without a key you can still add items manually.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = keyInput,
                onValueChange = { keyInput = it },
                label = { Text("sk-ant-…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (revealed) VisualTransformation.None
                    else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { revealed = !revealed }) {
                        Icon(
                            if (revealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (revealed) "Hide key" else "Show key"
                        )
                    }
                }
            )

            Button(
                onClick = {
                    viewModel.setApiKey(keyInput)
                    Toast.makeText(context, "API key saved", Toast.LENGTH_SHORT).show()
                    onBack()
                },
                enabled = keyInput.trim() != savedKey,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save")
            }

            if (savedKey.isNotBlank()) {
                Text(
                    "A key is currently saved.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            TextButton(
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://console.anthropic.com/settings/keys"))
                        )
                    }
                }
            ) {
                Text("Get a key at console.anthropic.com")
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                "Receipt model",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "Which Claude model reads the receipt. A more capable model can help " +
                    "with messy or faint receipts, but costs more and is a little slower. " +
                    "Pick a preset, or choose Custom to enter any Anthropic model id.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ModelPicker(
                savedId = savedModelId,
                onSelectId = { viewModel.setReceiptModelId(it) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelPicker(
    savedId: String,
    onSelectId: (String) -> Unit
) {
    // The saved id is either one of the presets, or a custom id the user typed.
    val preset = remember(savedId) { ReceiptModel.entries.find { it.id == savedId } }
    var expanded by remember { mutableStateOf(false) }
    var customMode by remember(savedId) { mutableStateOf(preset == null) }
    var customText by remember(savedId) { mutableStateOf(if (preset == null) savedId else "") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = if (customMode) "Custom model id" else (preset?.label ?: "Custom model id"),
                onValueChange = {},
                readOnly = true,
                label = { Text("Model") },
                supportingText = {
                    Text(if (customMode) "Enter any Anthropic model id below" else (preset?.blurb ?: ""))
                },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                ReceiptModel.entries.forEach { model ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(model.label)
                                Text(
                                    model.blurb,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        onClick = {
                            customMode = false
                            onSelectId(model.id)
                            expanded = false
                        }
                    )
                }
                DropdownMenuItem(
                    text = {
                        Column {
                            Text("Custom…")
                            Text(
                                "Type any Anthropic model id",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = {
                        customMode = true
                        expanded = false
                    }
                )
            }
        }

        if (customMode) {
            OutlinedTextField(
                value = customText,
                onValueChange = { customText = it },
                label = { Text("Model id") },
                placeholder = { Text("claude-…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { onSelectId(customText) },
                enabled = customText.trim().isNotEmpty() && customText.trim() != savedId,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Use this model")
            }
        }
    }
}
