package com.kevinywlui.billsplit.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kevinywlui.billsplit.model.LineItem
import com.kevinywlui.billsplit.model.Person
import com.kevinywlui.billsplit.ui.components.PersonAvatar
import com.kevinywlui.billsplit.ui.components.avatarColors
import com.kevinywlui.billsplit.viewmodel.BillViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemAssignmentScreen(
    viewModel: BillViewModel,
    onNavigateToSummary: () -> Unit,
    onBack: () -> Unit
) {
    val session by viewModel.session.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    var editingPriceItem by remember { mutableStateOf<LineItem?>(null) }
    var showAddItemSheet by remember { mutableStateOf(false) }

    if (isProcessing) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text("Reading receipt...")
            }
        }
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        topBar = {
            TopAppBar(
                title = { Text("Assign Items") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddItemSheet = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add item manually")
                    }
                }
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.padding(16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    errorMessage?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    val unassigned = session.unassignedItems.size
                    if (unassigned > 0) {
                        Text(
                            "$unassigned item(s) not yet assigned",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = onNavigateToSummary,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("View Summary")
                    }
                }
            }
        }
    ) { padding ->
        if (session.items.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("No items found.", style = MaterialTheme.typography.bodyLarge)
                    Button(onClick = { showAddItemSheet = true }) {
                        Text("Add Item Manually")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(session.items, key = { it.id }) { item ->
                    LineItemCard(
                        item = item,
                        people = session.people,
                        onTogglePerson = { personId ->
                            val current = item.assignedPersonIds
                            val updated = if (personId in current) current - personId else current + personId
                            viewModel.assignItem(item.id, updated)
                        },
                        onEditPrice = { editingPriceItem = item },
                        onDelete = { viewModel.removeLineItem(item.id) }
                    )
                }
                item {
                    TotalsSection(
                        restaurantName = session.restaurantName,
                        receiptTotal = session.effectiveReceiptTotal,
                        adjustments = session.adjustments,
                        onEditRestaurantName = { viewModel.setRestaurantName(it) },
                        onEditReceiptTotal = { viewModel.setUserTotal(it) },
                        onEditAdjustments = { viewModel.setAdjustments(it) }
                    )
                }
            }
        }
    }

    editingPriceItem?.let { item ->
        ModalBottomSheet(
            onDismissRequest = { editingPriceItem = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            EditPriceSheetContent(
                item = item,
                onDismiss = { editingPriceItem = null },
                onSave = { price ->
                    viewModel.updateLineItemPrice(item.id, price)
                    editingPriceItem = null
                }
            )
        }
    }

    if (showAddItemSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddItemSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            AddItemSheetContent(
                onDismiss = { showAddItemSheet = false },
                onAdd = { name, price ->
                    viewModel.addLineItem(name, price)
                    showAddItemSheet = false
                }
            )
        }
    }
}

@Composable
private fun LineItemCard(
    item: LineItem,
    people: List<Person>,
    onTogglePerson: (String) -> Unit,
    onEditPrice: () -> Unit,
    onDelete: () -> Unit
) {
    val assignedPeople = people.filter { it.id in item.assignedPersonIds }

    val cardColor = when {
        !item.isAssigned -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        assignedPeople.size == 1 -> avatarColors[assignedPeople[0].avatarColorIndex % avatarColors.size].copy(alpha = 0.25f)
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(
                    "$${"%.2f".format(item.price)}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.clickable(onClick = onEditPrice)
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (people.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    people.forEach { person ->
                        val isAssigned = person.id in item.assignedPersonIds
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .alpha(if (isAssigned) 1f else 0.35f)
                                .clickable { onTogglePerson(person.id) }
                        ) {
                            PersonAvatar(person = person, size = 38.dp)
                            if (isAssigned) {
                                Box(
                                    Modifier
                                        .matchParentSize()
                                        .border(2.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TotalsSection(
    restaurantName: String,
    receiptTotal: Double,
    adjustments: Double,
    onEditRestaurantName: (String) -> Unit,
    onEditReceiptTotal: (Double) -> Unit,
    onEditAdjustments: (Double) -> Unit
) {
    var editingAmountLabel by remember { mutableStateOf<String?>(null) }
    var editingValue by remember { mutableStateOf(0.0) }
    var editingOnSave by remember { mutableStateOf<(Double) -> Unit>({}) }
    var editingBase by remember { mutableStateOf<Double?>(null) }
    var showRestaurantEdit by remember { mutableStateOf(false) }

    fun startAmountEdit(label: String, current: Double, base: Double? = null, onSave: (Double) -> Unit) {
        editingAmountLabel = label
        editingValue = current
        editingBase = base
        editingOnSave = onSave
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Totals", style = MaterialTheme.typography.titleSmall)
            HorizontalDivider()
            TextRow("Restaurant name", restaurantName.ifBlank { "—" }) { showRestaurantEdit = true }
            FeeRow("Receipt total", receiptTotal) {
                startAmountEdit("Receipt total", receiptTotal, onSave = onEditReceiptTotal)
            }
            FeeRow(
                label = "Adjustments (usually tip)",
                amount = adjustments,
                pct = if (adjustments > 0 && receiptTotal > 0) (adjustments / receiptTotal) * 100 else null
            ) {
                startAmountEdit("Adjustments (usually tip)", adjustments, base = receiptTotal, onSave = onEditAdjustments)
            }
        }
    }

    editingAmountLabel?.let { label ->
        ModalBottomSheet(
            onDismissRequest = { editingAmountLabel = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            EditAmountSheetContent(
                label = label,
                current = editingValue,
                onDismiss = { editingAmountLabel = null },
                onSave = { editingOnSave(it); editingAmountLabel = null },
                baseForPercentage = editingBase
            )
        }
    }

    if (showRestaurantEdit) {
        ModalBottomSheet(
            onDismissRequest = { showRestaurantEdit = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            EditTextSheetContent(
                label = "Restaurant name",
                current = restaurantName,
                onDismiss = { showRestaurantEdit = false },
                onSave = { onEditRestaurantName(it); showRestaurantEdit = false }
            )
        }
    }
}

@Composable
private fun TextRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun FeeRow(label: String, amount: Double, pct: Double? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        val amountText = "$${"%.2f".format(amount)}"
        val displayText = if (pct != null) "$amountText (${"%.0f".format(pct)}%)" else amountText
        Text(displayText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun EditAmountSheetContent(
    label: String,
    current: Double,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit,
    baseForPercentage: Double? = null
) {
    // 0 = flat $, 1 = % tip, 2 = set final total (modes 1 & 2 only available when baseForPercentage != null)
    var mode by remember { mutableStateOf(0) }
    var text by remember { mutableStateOf("%.2f".format(current)) }

    val dollarValue: Double? = when {
        mode == 1 && baseForPercentage != null ->
            text.toDoubleOrNull()?.let { pct -> (pct / 100.0) * baseForPercentage }
        mode == 2 && baseForPercentage != null ->
            text.toDoubleOrNull()?.let { total -> total - baseForPercentage }
        else -> text.toDoubleOrNull()
    }
    val saveEnabled = when (mode) {
        2 -> text.toDoubleOrNull()?.let { it >= 0 } == true
        else -> dollarValue != null && dollarValue >= 0
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(label, style = MaterialTheme.typography.titleLarge)
        if (baseForPercentage != null) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = mode == 0,
                    onClick = { if (mode != 0) { mode = 0; text = "0.00" } },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                ) { Text("$ Amount") }
                SegmentedButton(
                    selected = mode == 1,
                    onClick = { if (mode != 1) { mode = 1; text = "0.00" } },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                ) { Text("% Tip") }
                SegmentedButton(
                    selected = mode == 2,
                    onClick = {
                        if (mode != 2) {
                            mode = 2
                            text = "%.2f".format(baseForPercentage + current)
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                ) { Text("$ Total") }
            }
        }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            prefix = if (mode != 1) { { Text("$") } } else null,
            suffix = if (mode == 1) { { Text("%") } } else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        if (baseForPercentage != null && baseForPercentage > 0) {
            val helperText: String? = when (mode) {
                1 -> dollarValue?.let { "= ${"$"}${"%.2f".format(it)}" }
                2 -> dollarValue?.let { adj ->
                    val pct = (adj / baseForPercentage) * 100
                    "adjustment = ${"$"}${"%.2f".format(adj)} (${"%.1f".format(pct)}%)"
                }
                else -> null
            }
            if (helperText != null) {
                Text(
                    helperText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
            Button(
                onClick = { dollarValue?.let { onSave(it) } },
                enabled = saveEnabled,
                modifier = Modifier.weight(1f)
            ) { Text("Save") }
        }
    }
}

@Composable
private fun EditTextSheetContent(label: String, current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(current) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(label, style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
            Button(onClick = { onSave(text.trim()) }, modifier = Modifier.weight(1f)) { Text("Save") }
        }
    }
}

@Composable
private fun AddItemSheetContent(onDismiss: () -> Unit, onAdd: (String, Double) -> Unit) {
    var name by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("") }
    val price = priceText.toDoubleOrNull()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Add Item", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Item name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = priceText,
            onValueChange = { priceText = it },
            label = { Text("Price") },
            singleLine = true,
            prefix = { Text("$") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
            Button(
                onClick = { price?.let { onAdd(name, it) } },
                enabled = name.isNotBlank() && price != null && price > 0,
                modifier = Modifier.weight(1f)
            ) { Text("Add") }
        }
    }
}

@Composable
private fun EditPriceSheetContent(item: LineItem, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var priceText by remember { mutableStateOf("%.2f".format(item.price)) }
    val price = priceText.toDoubleOrNull()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(item.name, style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = priceText,
            onValueChange = { priceText = it },
            label = { Text("Price") },
            singleLine = true,
            prefix = { Text("$") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
            Button(
                onClick = { price?.let { onSave(it) } },
                enabled = price != null && price > 0,
                modifier = Modifier.weight(1f)
            ) { Text("Save") }
        }
    }
}
