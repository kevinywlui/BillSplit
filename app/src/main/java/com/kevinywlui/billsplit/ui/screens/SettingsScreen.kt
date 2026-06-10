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
        }
    }
}
