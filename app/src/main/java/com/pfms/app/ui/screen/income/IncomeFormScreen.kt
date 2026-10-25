package com.pfms.app.ui.screen.income

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pfms.app.domain.model.Income
import com.pfms.app.viewmodel.IncomeFormBackend
import com.pfms.app.viewmodel.IncomeFormValues
import com.pfms.app.viewmodel.IncomeFormViewModel
import java.math.BigDecimal

typealias IncomeFormInput = IncomeFormValues

/** Route adapter: forwards repository state and user actions to the draft ViewModel. */
@Composable
fun IncomeFormScreen(
    onBack: () -> Unit,
    onSave: (IncomeFormInput) -> Unit,
    modifier: Modifier = Modifier,
    initialIncome: Income? = null,
    isSaving: Boolean = false,
    errorMessage: String? = null,
    exchangeRate: BigDecimal? = null,
    exchangeRateDate: String? = null,
    isRateFetching: Boolean = false,
    rateFetchError: String? = null,
    isManualRateMode: Boolean = false,
    onFetchExchangeRate: (String) -> Unit = {},
    onSetManualRate: (String, String) -> Boolean = { _, _ -> false },
    onEnableManualRate: () -> Unit = {},
    onClearExchangeRate: () -> Unit = {},
    canRetry: Boolean = false,
    isCachedExchangeRate: Boolean = false,
    isSavedExchangeRate: Boolean = false
) {
    val draft: IncomeFormViewModel = viewModel(key = "income-form-${initialIncome?.id ?: "new"}",
        factory = viewModelFactory { initializer { IncomeFormViewModel(createSavedStateHandle(), initialIncome) } })
    val state by draft.state.collectAsStateWithLifecycle()
    val backend = IncomeFormBackend(isSaving, errorMessage, canRetry, exchangeRate, exchangeRateDate,
        isRateFetching, rateFetchError, isManualRateMode, isCachedExchangeRate, isSavedExchangeRate)
    LaunchedEffect(draft, backend) {
        draft.updateBackend(backend)
        draft.ensureRate(onFetchExchangeRate)
    }
    IncomeFormContent(state, IncomeFormActions(
        onBack = onBack, onSave = { draft.submit(onSetManualRate, onSave) },
        onAmount = draft::amountChanged, onSource = draft::sourceChanged,
        onCurrency = { draft.currencyChanged(it, onFetchExchangeRate, onClearExchangeRate) },
        onIncrement = draft::increment, onProject = draft::projectChanged, onStatus = draft::statusChanged,
        onCryptoType = draft::cryptoTypeChanged, onAsset = draft::assetChanged,
        onNote = draft::noteChanged, onToggleNotes = draft::showNotes,
        onDatePicker = draft::showDatePicker,
        onDate = { draft.dateSelected(it, onFetchExchangeRate, onClearExchangeRate) },
        onInfo = draft::showInfo, onManualRate = draft::manualRateChanged,
        onEnableManualRate = onEnableManualRate,
        onRefreshRate = { draft.refreshRate(onFetchExchangeRate) }
    ), modifier)
}
