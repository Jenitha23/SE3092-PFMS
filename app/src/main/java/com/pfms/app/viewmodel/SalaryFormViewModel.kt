package com.pfms.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.pfms.app.domain.model.SalaryTemplate
import com.pfms.app.domain.usecase.ExpenseRules
import com.pfms.app.domain.usecase.SalaryScheduleCalculator
import com.pfms.app.domain.usecase.dashboard.AppClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.math.BigDecimal
import java.time.format.DateTimeFormatter
import java.util.Locale

data class SalaryFormState(val amount: String = "", val formatted: String? = null, val day: Int = 25,
    val dayLabel: String = "25th of every month", val nextPayday: String = "", val enabled: Boolean = true,
    val dayPicker: Boolean = false, val help: Boolean = false, val saving: Boolean = false,
    val error: String? = null, val saveLabel: String = "Create Salary Template")

class SalaryFormViewModel(private val saved: SavedStateHandle, initial: SalaryTemplate?) : ViewModel() {
    private val editing = initial != null
    private var saving = false
    private var backendError: String? = null
    private var inputError: String? = null
    private var retry = false
    private val _state = MutableStateFlow(SalaryFormState())
    val state = _state.asStateFlow()
    init {
        if (!saved.contains("amount")) saved["amount"] = initial?.let { BigDecimal.valueOf(it.amountMinor, 2).toPlainString() } ?: ""
        if (!saved.contains("day")) saved["day"] = initial?.paymentDay?.coerceIn(1, 31) ?: 25
        if (!saved.contains("enabled")) saved["enabled"] = initial?.isEnabled ?: true
        refresh()
    }
    fun backend(isSaving: Boolean, error: String?, canRetry: Boolean) { saving = isSaving; backendError = error; retry = canRetry; refresh() }
    fun amount(value: String) { if (!saving) { saved["amount"] = value; inputError = null; refresh() } }
    fun day(value: Int) { if (!saving && value in 1..31) { saved["day"] = value; saved["picker"] = false; refresh() } }
    fun enabled(value: Boolean) { if (!saving) { saved["enabled"] = value; refresh() } }
    fun picker(value: Boolean) { if (!saving) { saved["picker"] = value; refresh() } }
    fun help(value: Boolean) { saved["help"] = value; refresh() }
    fun submit(save: (String, Int, Boolean) -> Unit) {
        if (saving) return
        val state = _state.value
        if (ExpenseRules.parseAmount(state.amount) == null) {
            inputError = "Enter a positive salary amount with up to 2 decimal places."
            refresh()
        } else save(state.amount.trim(), state.day, state.enabled)
    }
    private fun refresh() {
        val amount: String = saved["amount"] ?: ""
        val day: Int = saved["day"] ?: 25
        val suffix = if (day in 11..13) "th" else when (day % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
        _state.value = SalaryFormState(amount, ExpenseRules.parseAmount(amount)?.let { "LKR ${financeMoney(it)}" }, day,
            "$day$suffix of every month", "Next payday: ${SalaryScheduleCalculator.nextPayday(day, AppClock.today()).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))}",
            saved["enabled"] ?: true, saved["picker"] ?: false, saved["help"] ?: false, saving, inputError ?: backendError,
            if (retry && backendError != null) "Retry" else if (editing) "Update Salary Template" else "Create Salary Template")
    }
}

internal fun financeMoney(minor: Long): String = java.text.NumberFormat.getNumberInstance(Locale.US).apply {
    minimumFractionDigits = 2; maximumFractionDigits = 2
}.format(BigDecimal.valueOf(minor, 2))
