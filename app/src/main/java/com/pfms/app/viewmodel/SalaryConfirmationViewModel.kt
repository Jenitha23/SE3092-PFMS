package com.pfms.app.viewmodel

import androidx.lifecycle.ViewModel
import com.pfms.app.domain.usecase.IncomeEntryAmount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.math.BigDecimal

data class SalaryConfirmationState(val amount: String = "", val error: String? = null)
class SalaryConfirmationViewModel : ViewModel() {
    private val _state = MutableStateFlow(SalaryConfirmationState())
    val state = _state.asStateFlow()
    private var templateId: String? = null
    fun sync(id: String, minor: Long) { if (templateId != id) { templateId = id; _state.value = SalaryConfirmationState(BigDecimal.valueOf(minor, 2).toPlainString()) } }
    fun edit(value: String) { _state.value = SalaryConfirmationState(value) }
    fun confirm(save: (String) -> Unit) {
        val value = _state.value.amount.trim()
        if (IncomeEntryAmount.parseMinor(value) == null) _state.value = _state.value.copy(error = "Enter a valid positive amount with up to 2 decimal places.")
        else save(value)
    }
}
