package com.kevinywlui.billsplit.ui.screens

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.kevinywlui.billsplit.model.BillSession
import com.kevinywlui.billsplit.model.Person
import com.kevinywlui.billsplit.viewmodel.BillViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(
    viewModel: BillViewModel,
    onBack: () -> Unit,
    onNewBill: () -> Unit
) {
    val session by viewModel.session.collectAsState()
    val saveMessage by viewModel.saveMessage.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showRestaurantEdit by remember { mutableStateOf(false) }
    var showVenmoHelp by remember { mutableStateOf(false) }

    LaunchedEffect(saveMessage) {
        saveMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSaveMessage()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Summary") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showVenmoHelp = true }) {
                        Icon(
                            Icons.Outlined.HelpOutline,
                            contentDescription = "Venmo request help"
                        )
                    }
                }
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(onClick = { viewModel.saveCurrentBill() }, modifier = Modifier.weight(1f)) {
                        Text("Save Bill")
                    }
                    Button(onClick = onNewBill, modifier = Modifier.weight(1f)) {
                        Text("New Bill")
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            item {
                BillTotalsCard(session, onEditRestaurantName = { showRestaurantEdit = true })
            }

            items(session.people, key = { it.id }) { person ->
                PersonSummaryCard(
                    person = person,
                    session = session,
                    onRequestVenmo = { amount, note ->
                        // The ?recipients= query form is what Venmo's desktop web honors
                        // (confirmed working on desktop). With Venmo link handling disabled
                        // this opens in the browser — which must have "Desktop site" on for
                        // the amount/note to prefill.
                        val uri = Uri.parse(
                            "https://venmo.com/?txn=charge&audience=private" +
                                "&recipients=${person.venmoUsername}" +
                                "&amount=${"%.2f".format(amount)}" +
                                "&note=${URLEncoder.encode(note, "UTF-8")}"
                        )
                        val launched = runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                        }.isSuccess
                        if (launched) viewModel.toggleVenmoRequested(person.id)
                    },
                    onToggleVenmoRequested = { viewModel.toggleVenmoRequested(person.id) }
                )
            }

            session.receiptImagePath?.let { imagePath ->
                item {
                    ReceiptImageCard(imagePath)
                }
            }

            val unassigned = session.unassignedItems
            if (unassigned.isNotEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "Unassigned Items",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            unassigned.forEach { item ->
                                Text(
                                    "${item.name} — $${"%.2f".format(item.price)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }

        }
    }

    if (showRestaurantEdit) {
        ModalBottomSheet(
            onDismissRequest = { showRestaurantEdit = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            EditRestaurantNameSheet(
                current = session.restaurantName,
                onDismiss = { showRestaurantEdit = false },
                onSave = { viewModel.setRestaurantName(it); showRestaurantEdit = false }
            )
        }
    }

    if (showVenmoHelp) {
        VenmoHelpDialog(onDismiss = { showVenmoHelp = false })
    }
}

@Composable
private fun VenmoHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Got it") }
        },
        icon = { Icon(Icons.Outlined.HelpOutline, contentDescription = null) },
        title = { Text("Making Venmo requests work") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Venmo no longer lets apps pre-fill a payment request on phones — " +
                        "their mobile app and mobile website both ignore the amount and note. " +
                        "Only Venmo's full desktop website still fills them in. So the request " +
                        "button opens that desktop page in your browser, which needs a one-time " +
                        "setup:",
                    style = MaterialTheme.typography.bodyMedium
                )

                HelpStep(
                    n = "1",
                    title = "Stop Venmo from grabbing the link",
                    body = "Otherwise the Venmo app intercepts the link and opens a blank " +
                        "request. In Android Settings → Apps → Venmo → \"Open by default\", " +
                        "turn OFF \"Open supported links\". Now the link opens in your browser."
                )
                HelpStep(
                    n = "2",
                    title = "Turn on \"Desktop site\" in your browser",
                    body = "Venmo's mobile site strips the pre-filled amount and note; only " +
                        "the desktop site keeps them. In your browser, enable \"Desktop site\" " +
                        "(in Chrome: ⋮ menu → Desktop site). Tip: in Chrome's Settings → Site " +
                        "settings → Desktop site you can leave it always on."
                )

                Text(
                    "After that, tapping a request opens Venmo in your browser with the amount " +
                        "and note already filled in — you just confirm.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    )
}

@Composable
private fun HelpStep(n: String, title: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(24.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    n,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BillTotalsCard(session: BillSession, onEditRestaurantName: () -> Unit) {
    val feesTotal = session.effectiveTotal - session.subtotal
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    if (session.restaurantName.isNotBlank()) {
                        Text(session.restaurantName, style = MaterialTheme.typography.titleMedium)
                        Text("Bill Total", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text("Bill Total", style = MaterialTheme.typography.titleMedium)
                    }
                }
                IconButton(onClick = onEditRestaurantName, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit restaurant name", modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
            TotalRow("Food subtotal", session.subtotal)
            if (feesTotal > 0) TotalRow("Fees, taxes & tip", feesTotal)
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            TotalRow("Total", session.effectiveTotal, bold = true)
        }
    }
}

@Composable
private fun TotalRow(label: String, amount: Double, bold: Boolean = false) {
    val style = if (bold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = style)
        Text("$${"%.2f".format(amount)}", style = style)
    }
}

@Composable
private fun PersonSummaryCard(
    person: Person,
    session: BillSession,
    onRequestVenmo: (amount: Double, note: String) -> Unit,
    onToggleVenmoRequested: () -> Unit
) {
    val total = session.totalFor(person.id)
    val foodShare = session.foodShareFor(person.id)
    val feeShare = session.feeShareFor(person.id)
    val fraction = session.fractionFor(person.id)
    val assignedItems = session.items.filter { person.id in it.assignedPersonIds }
    val isRequested = person.id in session.venmoRequestedPersonIds

    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(person.name, style = MaterialTheme.typography.titleMedium)
                Text("%.0f%% of bill".format(fraction * 100),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(2.dp))

            assignedItems.forEach { item ->
                val label = if (item.assignedPersonIds.size > 1) "${item.name} (÷${item.assignedPersonIds.size})" else item.name
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$${"%.2f".format(item.shareFor(person.id))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            TotalRow("Food subtotal", foodShare)
            if (feeShare > 0) TotalRow("Fees, taxes & tip", feeShare)
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            TotalRow("Total", total, bold = true)

            if (person.venmoUsername.isNotBlank() && total > 0) {
                val note = run {
                    val prefix = if (session.restaurantName.isNotBlank()) "${session.restaurantName}: " else ""
                    val feesPart = if (feeShare > 0) {
                        val pct = if (foodShare > 0) (feeShare / foodShare) * 100 else 0.0
                        "Fees/Tip (${"$"}${"%.2f".format(feeShare)}=${"%.0f".format(pct)}%)"
                    } else null
                    val totalPart = "= ${"$"}${"%.2f".format(total)}"

                    fun assemble(itemParts: List<String>): String {
                        val parts = buildList { addAll(itemParts); if (feesPart != null) add(feesPart) }
                        return "$prefix${parts.joinToString(" + ")} $totalPart"
                    }

                    val itemParts = assignedItems.map { item ->
                        val label = if (item.assignedPersonIds.size > 1)
                            "${item.name} ÷${item.assignedPersonIds.size}" else item.name
                        "$label (${"$"}${"%.2f".format(item.shareFor(person.id))})"
                    }

                    val full = assemble(itemParts)
                    if (full.length <= 280) return@run full

                    // Collapse individual items into a count summary
                    val collapsed = assemble(listOf("${assignedItems.size} items (${"$"}${"%.2f".format(foodShare)})"))
                    if (collapsed.length <= 280) return@run collapsed

                    // Last resort: hard truncate with ellipsis
                    collapsed.take(277) + "..."
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = { onRequestVenmo(total, note) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Request $${"%.2f".format(total)} via Venmo")
                }
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onToggleVenmoRequested),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (isRequested) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (isRequested) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (isRequested) "Requested" else "Mark as requested",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isRequested) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ReceiptImageCard(imagePath: String) {
    var expanded by remember { mutableStateOf(false) }
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(imagePath) {
        bitmap = withContext(Dispatchers.IO) {
            BitmapFactory.decodeFile(imagePath)?.asImageBitmap()
        }
    }

    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Receipt Photo", style = MaterialTheme.typography.titleSmall)
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (expanded) {
                bitmap?.let {
                    Image(
                        bitmap = it,
                        contentDescription = "Receipt",
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.FillWidth
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditRestaurantNameSheet(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(current) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Restaurant name", style = MaterialTheme.typography.titleLarge)
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
