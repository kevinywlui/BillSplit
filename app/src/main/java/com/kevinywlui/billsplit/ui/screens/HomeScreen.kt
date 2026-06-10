package com.kevinywlui.billsplit.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.kevinywlui.billsplit.model.Person
import com.kevinywlui.billsplit.ui.components.PersonAvatar
import com.kevinywlui.billsplit.viewmodel.BillViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: BillViewModel,
    onStartCamera: () -> Unit,
    onViewHistory: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val session by viewModel.session.collectAsState()
    val savedPeople by viewModel.savedPeople.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()
    var showAddSheet by remember { mutableStateOf(false) }
    var editingPerson by remember { mutableStateOf<Person?>(null) }
    var showKeyPrompt by remember { mutableStateOf(false) }

    val billPersonIds = session.people.map { it.id }.toSet()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        topBar = {
            TopAppBar(
                title = { Text("Bill Split") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                actions = {
                    IconButton(onClick = onViewHistory) {
                        Icon(Icons.Default.History, contentDescription = "History")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddSheet = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add person")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(4.dp))
            Text("Who's splitting?", style = MaterialTheme.typography.titleMedium)

            if (savedPeople.isEmpty()) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        "Tap + to add people",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(savedPeople, key = { it.id }) { person ->
                        val inBill = person.id in billPersonIds
                        SavedPersonCard(
                            person = person,
                            inBill = inBill,
                            onToggle = { viewModel.togglePersonInBill(person.id) },
                            onEdit = { editingPerson = person }
                        )
                    }
                }
            }

            val billCount = session.people.size
            if (billCount < 2) {
                Text(
                    if (savedPeople.isEmpty()) "Add at least 2 people to continue"
                    else "Select at least 2 people for this bill",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = { if (apiKey.isBlank()) showKeyPrompt = true else onStartCamera() },
                enabled = billCount >= 2,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Scan Receipt")
            }

            Spacer(Modifier.height(4.dp))
        }
    }

    if (showKeyPrompt) {
        AlertDialog(
            onDismissRequest = { showKeyPrompt = false },
            title = { Text("API key needed") },
            text = {
                Text(
                    "Receipt scanning needs an Anthropic API key. Add one in Settings, " +
                        "or scan anyway and enter the items manually."
                )
            },
            confirmButton = {
                TextButton(onClick = { showKeyPrompt = false; onOpenSettings() }) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showKeyPrompt = false; onStartCamera() }) {
                    Text("Scan anyway")
                }
            }
        )
    }

    if (showAddSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            AddPersonSheetContent(
                onDismiss = { showAddSheet = false },
                onAdd = { name, venmo ->
                    viewModel.addNewPerson(name, venmo)
                    showAddSheet = false
                }
            )
        }
    }

    editingPerson?.let { person ->
        ModalBottomSheet(
            onDismissRequest = { editingPerson = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            EditPersonSheetContent(
                person = person,
                onDismiss = { editingPerson = null },
                onSave = { updated ->
                    viewModel.updatePerson(updated)
                    editingPerson = null
                },
                onDelete = {
                    viewModel.deleteSavedPerson(person.id)
                    editingPerson = null
                }
            )
        }
    }
}

@Composable
private fun SavedPersonCard(
    person: Person,
    inBill: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (inBill) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PersonAvatar(person = person, size = 44.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(person.name, style = MaterialTheme.typography.bodyLarge)
                if (person.venmoUsername.isNotBlank()) {
                    Text(
                        "@${person.venmoUsername}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (inBill) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "In bill",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AddPersonSheetContent(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var venmo by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Add Person", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
        )
        OutlinedTextField(
            value = venmo,
            onValueChange = { venmo = it },
            label = { Text("Venmo username (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onAdd(name, venmo) })
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
            Button(
                onClick = { onAdd(name, venmo) },
                enabled = name.isNotBlank(),
                modifier = Modifier.weight(1f)
            ) { Text("Add") }
        }
    }
}

@Composable
private fun EditPersonSheetContent(
    person: Person,
    onDismiss: () -> Unit,
    onSave: (Person) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember { mutableStateOf(person.name) }
    var venmo by remember { mutableStateOf(person.venmoUsername) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete ${person.name}?") },
            text = { Text("This will remove them from your saved people.") },
            confirmButton = {
                TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Edit Person", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = venmo,
            onValueChange = { venmo = it },
            label = { Text("Venmo username (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
            Button(
                onClick = { onSave(person.copy(name = name.trim(), venmoUsername = venmo.trim())) },
                enabled = name.isNotBlank(),
                modifier = Modifier.weight(1f)
            ) { Text("Save") }
        }
        TextButton(
            onClick = { showDeleteConfirm = true },
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Delete person permanently")
        }
    }
}
