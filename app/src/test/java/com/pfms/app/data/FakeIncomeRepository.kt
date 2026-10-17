package com.pfms.app.data

import com.pfms.app.domain.model.Income
import com.pfms.app.domain.repository.IncomeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeIncomeRepository : IncomeRepository {

    val incomes = MutableStateFlow<List<Income>>(emptyList())

    override fun observeIncome(): Flow<List<Income>> = incomes.asStateFlow()

    override suspend fun addIncome(income: Income) {
        incomes.value = incomes.value + income
    }

    override suspend fun updateIncome(income: Income) {
        incomes.value = incomes.value.map { if (it.id == income.id) income else it }
    }

    override suspend fun deleteIncome(incomeId: String) {
        incomes.value = incomes.value.filterNot { it.id == incomeId }
    }

    override suspend fun getIncomeById(incomeId: String): Income? =
        incomes.value.firstOrNull { it.id == incomeId }
}
