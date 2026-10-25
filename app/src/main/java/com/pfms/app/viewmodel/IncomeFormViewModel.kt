package com.pfms.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.pfms.app.domain.model.Income
import com.pfms.app.domain.usecase.IncomeEntryAmount
import com.pfms.app.domain.usecase.dashboard.AppClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

data class IncomeFormValues(
    val amount: String, val source: String, val currency: String, val receivedDate: String,
    val note: String, val projectName: String? = null, val paymentStatus: String = "Received",
    val cryptoType: String? = null, val cryptoAsset: String? = null
)

data class IncomeFormSource(val value: String, val label: String, val icon: IncomeRecordIcon)
data class IncomeQuickAmount(val label: String, val majorUnits: Long)
data class IncomeFormBackend(
    val isSaving: Boolean = false, val error: String? = null, val canRetry: Boolean = false,
    val rate: BigDecimal? = null, val rateDate: String? = null, val fetching: Boolean = false,
    val rateError: String? = null, val manual: Boolean = false, val cached: Boolean = false
)

data class IncomeFormState(
    val title: String = "Add Income", val saveLabel: String = "Save Income",
    val amount: String = "", val currency: String = "LKR", val source: String = "Salary", val sourceIndex: Int = 0,
    val sources: List<IncomeFormSource> = listOf(
        IncomeFormSource("Salary", "Salary", IncomeRecordIcon.SALARY),
        IncomeFormSource("Freelance", "Freelance", IncomeRecordIcon.FREELANCE),
        IncomeFormSource("Google AdSense", "AdSense", IncomeRecordIcon.ADSENSE),
        IncomeFormSource("Crypto", "Crypto", IncomeRecordIcon.CRYPTO),
        IncomeFormSource("Other", "Other", IncomeRecordIcon.OTHER)),
    val receivedDate: String = "", val dateLabel: String = "", val dateMillis: Long = 0,
    val projectName: String = "", val paymentStatus: String = "Received",
    val isFreelance: Boolean = false, val isCrypto: Boolean = false,
    val cryptoType: String = "Gain", val cryptoAsset: String = "BTC",
    val assets: List<String> = listOf("BTC", "ETH", "USDT", "SOL", "BNB", "XRP", "ADA"),
    val note: String = "", val notesExpanded: Boolean = false,
    val datePickerOpen: Boolean = false, val infoOpen: Boolean = false,
    val quickAmounts: List<IncomeQuickAmount> = emptyList(),
    val amountSummary: String? = null, val convertedAmount: String? = null,
    val rateLabel: String = "", val rateDetail: String? = null, val rateNotice: String? = null,
    val isUsd: Boolean = false, val manualRate: String = "", val manualRateVisible: Boolean = false,
    val isRateFetching: Boolean = false, val isSaving: Boolean = false,
    val error: String? = null, val amountError: String? = null, val projectError: String? = null
)

/** Owns the draft and actions. Repository saving and rate retrieval remain in IncomeViewModel. */
class IncomeFormViewModel(
    private val saved: SavedStateHandle,
    private val initial: Income? = null
) : ViewModel() {
    private var backend = IncomeFormBackend()
    private var requestedRateKey: String? = null
    private var amountError: String? = null
    private var projectError: String? = null
    private var actionError: String? = null
    private val _state = MutableStateFlow(IncomeFormState())
    val state = _state.asStateFlow()

    init {
        seed("amount", initial?.let { BigDecimal.valueOf(it.amountMinor, 2).toPlainString() } ?: "")
        seed("source", initial?.source ?: "Salary")
        seed("currency", initial?.currency ?: "LKR")
        seed("date", initial?.receivedDate ?: AppClock.today().toString())
        seed("project", initial?.projectName.orEmpty())
        seed("status", initial?.paymentStatus ?: "Received")
        seed("cryptoType", initial?.cryptoType ?: "Gain")
        seed("asset", initial?.cryptoAsset ?: "BTC")
        seed("note", initial?.note.orEmpty())
        seed("manualRate", initial?.exchangeRate.orEmpty())
        if (!saved.contains("notesOpen")) saved["notesOpen"] = initial?.note?.isNotBlank() == true
        refresh()
    }

    private fun seed(key: String, value: String) { if (!saved.contains(key)) saved[key] = value }
    private fun text(key: String): String = saved[key] ?: ""
    private fun flag(key: String): Boolean = saved[key] ?: false
    private fun change(key: String, value: String) {
        if (backend.isSaving) return
        saved[key] = value
        actionError = null
        refresh()
    }

    fun amountChanged(value: String) { amountError = null; change("amount", value) }
    fun sourceChanged(value: String) {
        if (value !in _state.value.sources.map { it.value }) return
        projectError = null
        change("source", value)
    }
    fun projectChanged(value: String) { projectError = null; change("project", value) }
    fun statusChanged(value: String) { if (value in listOf("Received", "Expected")) change("status", value) }
    fun cryptoTypeChanged(value: String) { if (value in listOf("Gain", "Loss")) change("cryptoType", value) }
    fun assetChanged(value: String) = change("asset", value.uppercase(Locale.ENGLISH).take(20))
    fun noteChanged(value: String) = change("note", value)
    fun manualRateChanged(value: String) = change("manualRate", value)
    fun showNotes() { saved["notesOpen"] = !flag("notesOpen"); refresh() }
    fun showDatePicker(open: Boolean) { if (!backend.isSaving) { saved["dateOpen"] = open; refresh() } }
    fun showInfo(open: Boolean) { saved["infoOpen"] = open; refresh() }

    fun increment(majorUnits: Long) {
        if (backend.isSaving || majorUnits !in _state.value.quickAmounts.map { it.majorUnits }) return
        val next = IncomeEntryAmount.increment(text("amount"), majorUnits)
        amountError = if (next == null) "Enter a valid amount before adding more." else null
        if (next != null) saved["amount"] = next
        refresh()
    }

    fun currencyChanged(value: String, fetch: (String) -> Unit, clear: () -> Unit) {
        if (backend.isSaving || value !in listOf("LKR", "USD") || value == text("currency")) return
        resetRate(clear)
        saved["currency"] = value
        refresh()
        ensureRate(fetch)
    }

    fun dateSelected(millis: Long?, fetch: (String) -> Unit, clear: () -> Unit) {
        if (backend.isSaving || millis == null) return
        val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()
        if (date != text("date")) {
            resetRate(clear)
            saved["date"] = date
            ensureRate(fetch)
        }
        saved["dateOpen"] = false
        refresh()
    }

    private fun resetRate(clear: () -> Unit) {
        requestedRateKey = null
        saved["manualRate"] = ""
        backend = backend.copy(rate = null, rateDate = null, fetching = false, rateError = null, manual = false, cached = false)
        clear()
    }

    fun updateBackend(value: IncomeFormBackend) {
        val changedRate = backend.rate != value.rate
        backend = value
        if (changedRate && value.rate != null) saved["manualRate"] = value.rate.toPlainString()
        refresh()
    }

    fun ensureRate(fetch: (String) -> Unit) {
        if (text("currency") != "USD" || requestedRateKey == text("date")) return
        requestedRateKey = text("date")
        // A supplied rate for the original edit date must survive opening/reconfirming that date.
        if (initial?.currency == "USD" && initial.receivedDate == text("date") && backend.rate != null) return
        fetch(text("date"))
    }

    fun refreshRate(fetch: (String) -> Unit) { if (!backend.isSaving) fetch(text("date")) }

    fun submit(setManualRate: (String, String) -> Boolean, save: (IncomeFormValues) -> Unit) {
        if (backend.isSaving) return
        amountError = if (IncomeEntryAmount.parseMinor(text("amount")) == null) "Enter a valid positive amount with up to 2 decimal places." else null
        projectError = if (text("source") == "Freelance" && text("project").isBlank()) "Project / client name is required." else null
        actionError = null
        if (amountError != null || projectError != null) {
            actionError = "Check the highlighted fields before saving."
            refresh()
            return
        }
        if (text("currency") == "USD") {
            if (backend.fetching) actionError = "Please wait for the exchange rate or enter a manual rate."
            else if (backend.manual) {
                if (!setManualRate(text("manualRate"), text("date"))) actionError = "Enter a valid positive USD/LKR exchange rate."
            } else if (backend.rate == null) actionError = "Fetch an exchange rate or enter it manually before saving."
            if (actionError != null) { refresh(); return }
        }
        val isFreelance = text("source") == "Freelance"
        val isCrypto = text("source") == "Crypto"
        save(IncomeFormValues(text("amount").trim(), text("source"), text("currency"), text("date"),
            text("note").trim(), text("project").trim().takeIf { isFreelance },
            if (isFreelance) text("status") else "Received", text("cryptoType").takeIf { isCrypto },
            text("asset").trim().takeIf { isCrypto && it.isNotEmpty() }))
    }

    private fun refresh() {
        val usd = text("currency") == "USD"
        val rate = if (backend.manual) text("manualRate").toBigDecimalOrNull() else backend.rate
        val amountMinor = IncomeEntryAmount.parseMinor(text("amount"))
        val preview = if (usd) IncomeEntryAmount.lkrPreview(text("amount"), rate, initial, text("date")) else null
        val date = LocalDate.parse(text("date"))
        val rateLabel = when {
            backend.fetching -> "Fetching rate"
            backend.manual -> "Manual rate"
            backend.cached -> "Cached rate"
            initial?.currency == "USD" && initial.receivedDate == text("date") && initial.exchangeRate == backend.rate?.toPlainString() -> "Saved rate"
            backend.rate != null -> "Dated rate"
            else -> "Rate needed"
        }
        _state.value = IncomeFormState(
            title = if (initial == null) "Add Income" else "Edit Income",
            saveLabel = if (backend.canRetry && backend.error != null) "Retry" else if (initial == null) "Save Income" else "Save Changes",
            amount = text("amount"), currency = text("currency"), source = text("source"),
            sourceIndex = listOf("Salary", "Freelance", "Google AdSense", "Crypto", "Other").indexOf(text("source")).coerceAtLeast(0),
            receivedDate = text("date"), dateLabel = date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)),
            dateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            projectName = text("project"), paymentStatus = text("status"),
            isFreelance = text("source") == "Freelance", isCrypto = text("source") == "Crypto",
            cryptoType = text("cryptoType"), cryptoAsset = text("asset"), note = text("note"),
            notesExpanded = flag("notesOpen"),
            datePickerOpen = flag("dateOpen"), infoOpen = flag("infoOpen"),
            quickAmounts = if (usd) listOf(IncomeQuickAmount("+50", 50), IncomeQuickAmount("+100", 100), IncomeQuickAmount("+500", 500), IncomeQuickAmount("+1k", 1_000))
                else listOf(IncomeQuickAmount("+5k", 5_000), IncomeQuickAmount("+10k", 10_000), IncomeQuickAmount("+50k", 50_000), IncomeQuickAmount("+100k", 100_000)),
            amountSummary = amountMinor?.let { "${text("currency")} ${money(it)}" },
            convertedAmount = preview?.let { "LKR ${money(it)}" }, isUsd = usd,
            rateLabel = rateLabel,
            rateDetail = rate?.takeIf { it > BigDecimal.ZERO }?.let {
                "1 USD = ${it.stripTrailingZeros().toPlainString()} LKR • ${if (backend.manual) text("date") else backend.rateDate ?: text("date")}" },
            rateNotice = backend.rateError, manualRate = text("manualRate"), manualRateVisible = usd && backend.manual,
            isRateFetching = backend.fetching, isSaving = backend.isSaving,
            error = actionError ?: backend.error, amountError = amountError, projectError = projectError
        )
    }

    private fun money(minor: Long): String = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 2; maximumFractionDigits = 2
    }.format(BigDecimal.valueOf(minor, 2))
}
