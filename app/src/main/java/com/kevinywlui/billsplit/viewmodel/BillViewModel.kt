package com.kevinywlui.billsplit.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kevinywlui.billsplit.data.BillHistoryRepository
import com.kevinywlui.billsplit.data.PeopleRepository
import com.kevinywlui.billsplit.data.SettingsRepository
import com.kevinywlui.billsplit.model.SavedBill
import com.kevinywlui.billsplit.model.BillSession
import com.kevinywlui.billsplit.model.LineItem
import com.kevinywlui.billsplit.model.Person
import com.kevinywlui.billsplit.ocr.ClaudeReceiptParser
import com.kevinywlui.billsplit.ocr.ReceiptParseError
import com.kevinywlui.billsplit.ocr.ReceiptParseException
import com.kevinywlui.billsplit.ocr.classifyParseError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class BillViewModel @JvmOverloads constructor(
    application: Application,
    private val repo: PeopleRepository = PeopleRepository(application),
    private val historyRepo: BillHistoryRepository = BillHistoryRepository(application),
    private val settingsRepo: SettingsRepository = SettingsRepository(application)
) : AndroidViewModel(application) {

    private val _billHistory = MutableStateFlow<List<SavedBill>>(emptyList())
    val billHistory: StateFlow<List<SavedBill>> = _billHistory.asStateFlow()

    private val _savedPeople = MutableStateFlow<List<Person>>(emptyList())
    val savedPeople: StateFlow<List<Person>> = _savedPeople.asStateFlow()

    private val _session = MutableStateFlow(BillSession())
    val session: StateFlow<BillSession> = _session.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var receiptJob: Job? = null

    private suspend fun saveBitmapToFile(bitmap: Bitmap): String? = withContext(Dispatchers.IO) {
        runCatching {
            val maxDim = 1568
            val scale = minOf(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height, 1f)
            val scaled = if (scale < 1f)
                Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
            else bitmap
            val dir = File(getApplication<Application>().filesDir, "receipts").also { it.mkdirs() }
            val file = File(dir, "${UUID.randomUUID()}.jpg")
            file.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            file.absolutePath
        }.getOrNull()
    }

    private fun deleteOrphanedImage(path: String?) {
        if (path != null && _billHistory.value.none { it.receiptImagePath == path }) {
            File(path).delete()
        }
    }

    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    init {
        viewModelScope.launch {
            repo.people.collect { _savedPeople.value = it }
        }
        viewModelScope.launch {
            historyRepo.bills.collect { _billHistory.value = it }
        }
        viewModelScope.launch {
            settingsRepo.apiKey.collect { _apiKey.value = it }
        }
    }

    fun setApiKey(key: String) {
        viewModelScope.launch { settingsRepo.setApiKey(key) }
    }

    private val _saveMessage = MutableStateFlow<String?>(null)
    val saveMessage: StateFlow<String?> = _saveMessage.asStateFlow()

    fun saveCurrentBill() {
        val s = _session.value
        if (s.items.isEmpty()) {
            _saveMessage.value = "Nothing to save — no items"
            return
        }
        val bill = SavedBill(
            people = s.people,
            items = s.items,
            tax = s.tax,
            tip = s.tip,
            otherFees = s.otherFees,
            grandTotal = s.effectiveTotal,
            restaurantName = s.restaurantName,
            venmoRequestedPersonIds = s.venmoRequestedPersonIds,
            finalShares = s.finalShares,
            receiptImagePath = s.receiptImagePath
        )
        viewModelScope.launch {
            historyRepo.saveBill(bill)
            _saveMessage.value = "Bill saved!"
        }
    }

    fun clearSaveMessage() { _saveMessage.value = null }

    fun deleteBill(billId: String) {
        viewModelScope.launch { historyRepo.deleteBill(billId) }
    }

    fun addNewPerson(name: String, venmoUsername: String = "") {
        if (name.isBlank()) return
        viewModelScope.launch {
            val person = repo.addPerson(name.trim(), venmoUsername.trim())
            _session.update { it.copy(people = it.people + person) }
        }
    }

    fun togglePersonInBill(personId: String) {
        val person = _savedPeople.value.find { it.id == personId } ?: return
        val inBill = _session.value.people.any { it.id == personId }
        _session.update { session ->
            if (inBill) session.copy(people = session.people.filter { it.id != personId })
            else session.copy(people = session.people + person)
        }
    }

    fun updatePerson(updated: Person) {
        viewModelScope.launch {
            repo.updatePerson(updated)
            _session.update { session ->
                session.copy(people = session.people.map { if (it.id == updated.id) updated else it })
            }
        }
    }

    fun deleteSavedPerson(personId: String) {
        viewModelScope.launch {
            repo.deletePerson(personId)
            _session.update { session ->
                session.copy(
                    people = session.people.filter { it.id != personId },
                    items = session.items.map { item ->
                        item.copy(assignedPersonIds = item.assignedPersonIds.filter { it != personId })
                    }
                )
            }
        }
    }

    fun addLineItem(name: String, price: Double) {
        _session.update { it.copy(items = it.items + LineItem(name = name, price = price)) }
    }

    fun loadBillIntoSession(bill: SavedBill) {
        _session.value = BillSession(
            people = bill.people,
            items = bill.items,
            tax = bill.tax,
            tip = bill.tip,
            otherFees = bill.otherFees,
            receiptTotal = bill.grandTotal,
            userTotal = null,
            restaurantName = bill.restaurantName,
            venmoRequestedPersonIds = bill.venmoRequestedPersonIds,
            receiptImagePath = bill.receiptImagePath
        )
    }

    fun setRestaurantName(name: String) {
        _session.update { it.copy(restaurantName = name) }
    }

    fun toggleVenmoRequested(personId: String) {
        _session.update {
            val updated = if (personId in it.venmoRequestedPersonIds)
                it.venmoRequestedPersonIds - personId
            else
                it.venmoRequestedPersonIds + personId
            it.copy(venmoRequestedPersonIds = updated)
        }
    }

    fun setUserTotal(total: Double?) {
        _session.update { it.copy(userTotal = total) }
    }

    fun setAdjustments(amount: Double) {
        _session.update { it.copy(adjustments = amount) }
    }

    fun updateLineItemPrice(itemId: String, price: Double) {
        if (price <= 0) return
        _session.update { session ->
            session.copy(items = session.items.map { if (it.id == itemId) it.copy(price = price) else it })
        }
    }

    fun removeLineItem(itemId: String) {
        _session.update { it.copy(items = it.items.filter { item -> item.id != itemId }) }
    }

    fun assignItem(itemId: String, personIds: List<String>) {
        _session.update { session ->
            session.copy(
                items = session.items.map { item ->
                    if (item.id == itemId) item.copy(assignedPersonIds = personIds) else item
                }
            )
        }
    }

    fun processReceiptImage(bitmap: Bitmap) {
        deleteOrphanedImage(_session.value.receiptImagePath)
        receiptJob?.cancel()
        var job: Job? = null
        job = viewModelScope.launch {
            _isProcessing.value = true
            _errorMessage.value = null
            try {
                val key = settingsRepo.getApiKey()
                if (key.isBlank()) {
                    _errorMessage.value = "Add your Anthropic API key in Settings to scan receipts. You can still add items manually."
                    return@launch
                }
                val imagePath = saveBitmapToFile(bitmap)
                val parsed = ClaudeReceiptParser.parse(bitmap, key)
                _session.update { it.copy(
                    items = parsed.items,
                    tax = parsed.tax,
                    tip = parsed.tip,
                    otherFees = parsed.otherFees,
                    receiptTotal = parsed.receiptTotal,
                    userTotal = null,
                    adjustments = 0.0,
                    restaurantName = parsed.restaurantName,
                    receiptImagePath = imagePath,
                    receiptTotalFromOcr = true
                ) }
                if (parsed.items.isEmpty()) {
                    _errorMessage.value = "No items detected. Add them manually."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("BillSplit", "Receipt parsing failed", e)
                _errorMessage.value = parseErrorMessage(e)
            } finally {
                // Only clear the spinner if a newer scan hasn't already taken over.
                if (receiptJob === job) _isProcessing.value = false
            }
        }
        receiptJob = job
    }

    private fun parseErrorMessage(e: Throwable): String = when {
        e is ReceiptParseException -> when (classifyParseError(e.statusCode)) {
            ReceiptParseError.AUTH ->
                "Your Anthropic API key was rejected. Check it in Settings."
            ReceiptParseError.RATE_LIMIT ->
                "Anthropic is rate-limiting requests. Wait a moment and try again."
            ReceiptParseError.SERVER ->
                "Anthropic had a server error. Try again in a moment."
            else -> "Could not read receipt. Add items manually."
        }
        e is java.io.IOException ->
            "No internet connection. Check your network and try again."
        else -> "Could not read receipt. Add items manually."
    }

    fun resetSession() {
        deleteOrphanedImage(_session.value.receiptImagePath)
        _session.value = BillSession()
        _errorMessage.value = null
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
