package com.pfms.app.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

data class HistoryDisplay(val count: String, val income: String, val expense: String, val net: String, val negative: Boolean,
    val rows: List<FinanceTransactionDisplay>)
data class HistoryScreenState(val display: HistoryDisplay? = null, val filtersOpen: Boolean = false, val picker: String? = null,
    val dateMillis: Long? = null)

class HistoryScreenViewModel : ViewModel() {
    private val _state = MutableStateFlow(HistoryScreenState())
    val state = _state.asStateFlow()
    fun sync(source: TransactionHistoryUiState) {
        val summary = source.summary
        _state.update { it.copy(display = HistoryDisplay("${summary.count} ${if (summary.count == 1) "transaction" else "transactions"}" + if (source.filter.isActive) " match" else "",
            DashboardPresentationMapper.money(summary.incomeMinor), DashboardPresentationMapper.money(summary.expenseMinor),
            DashboardPresentationMapper.money(summary.netMinor), summary.netMinor < 0, source.visible.map(FinanceTransactionPresentation::create))) }
    }
    fun filters(open: Boolean) { _state.update { it.copy(filtersOpen = open) } }
    fun picker(target: String?, date: LocalDate? = null) { _state.update { it.copy(picker = target, dateMillis = date?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()) } }
    fun select(millis: Long?, start: LocalDate?, end: LocalDate?, save: (LocalDate?, LocalDate?) -> Unit) {
        if (millis == null) return
        val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
        if (_state.value.picker == "start") save(date, end) else save(start, date)
        picker(null)
    }
}
