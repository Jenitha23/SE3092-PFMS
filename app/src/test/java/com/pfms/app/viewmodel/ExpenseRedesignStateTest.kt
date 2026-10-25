package com.pfms.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.pfms.app.domain.model.*
import com.pfms.app.domain.usecase.SalaryScheduleCalculator
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ExpenseRedesignStateTest {
    private val categories = listOf(ExpenseCategoryChoice("rent", "Rent", ExpenseClassification.COMMITTED),
        ExpenseCategoryChoice("food", "Food & Dining", ExpenseClassification.DISCRETIONARY))
    private val expenses = listOf(Expense("rent", amountMinor = 55100000, categoryId = "rent", categoryName = "Rent", classification = ExpenseClassification.COMMITTED, date = "2026-10-01"),
        Expense("food", amountMinor = 350000, categoryId = "food", categoryName = "Food & Dining", merchant = "Lunch", date = "2026-10-20"),
        Expense("previous", amountMinor = 1000000, categoryName = "Other", date = "2026-09-01"))

    @Test fun monthlyTotalsAreIndependentOfSearchAndFilters() {
        val vm = ExpenseListViewModel(SavedStateHandle())
        vm.month("2026-10"); vm.sync(expenses, false, false, null)
        assertEquals("LKR 554,500.00", vm.state.value.total)
        assertEquals(2, vm.state.value.rows.size)
        vm.query("Lunch")
        assertEquals("1 expense", vm.state.value.count)
        assertEquals("LKR 554,500.00", vm.state.value.total)
        assertEquals("−LKR 3,500.00", vm.state.value.rows.single().amount)
        vm.clearFilters(); vm.classification(ExpenseClassification.COMMITTED)
        assertEquals("rent", vm.state.value.rows.single().id)
        vm.month("2026-09"); vm.clearFilters()
        assertEquals("LKR 10,000.00", vm.state.value.total)
    }

    @Test fun deleteRequiresExplicitConfirmationAndSortUsesDates() {
        val vm = ExpenseListViewModel(SavedStateHandle())
        vm.month("2026-10"); vm.sync(expenses, false, false, null)
        assertEquals("food", vm.state.value.rows.first().id)
        vm.sort(); assertEquals("rent", vm.state.value.rows.first().id)
        var deleted: String? = null
        vm.delete("rent"); assertNull(deleted)
        vm.delete(null); vm.confirmDelete { deleted = it }; assertNull(deleted)
        vm.delete("rent"); vm.confirmDelete { deleted = it }; assertEquals("rent", deleted)
    }

    @Test fun quickDraftPersistsAllEditedFieldsAndCategoryDefault() {
        val saved = SavedStateHandle()
        val vm = ExpenseDraftViewModel(saved, null, ExpensePaymentMethod.CARD, quick = true)
        vm.sync(categories, emptyList(), false, null, false)
        vm.amount("1250.25"); vm.increment(500)
        vm.category("food"); vm.payment(ExpensePaymentMethod.DIGITAL_WALLET_PLATFORM)
        vm.classification(ExpenseClassification.COMMITTED); vm.note("Business lunch")
        val restored = ExpenseDraftViewModel(saved, null, ExpensePaymentMethod.CASH, quick = true)
        restored.sync(categories, emptyList(), false, "Retry", true)
        var result: ExpenseFormData? = null
        restored.submit { result = it }
        assertEquals("1750.25", result?.amount)
        assertEquals("food", result?.categoryId)
        assertEquals(ExpensePaymentMethod.DIGITAL_WALLET_PLATFORM, result?.paymentMethod)
        assertEquals(ExpenseClassification.COMMITTED, result?.classificationOverride)
        assertEquals("Business lunch", result?.note)
        restored.category("rent")
        assertFalse(restored.state.value.overridden)
        assertEquals(ExpenseClassification.COMMITTED, restored.state.value.classification)
    }

    @Test fun repeatedEntryGetsTodaysDateAndFailureRetainsFields() {
        val vm = ExpenseDraftViewModel(SavedStateHandle(), null, ExpensePaymentMethod.CASH)
        vm.sync(categories, expenses, false, null, false)
        vm.repeat("food")
        assertEquals("3500.00", vm.state.value.amount)
        assertEquals(com.pfms.app.domain.usecase.dashboard.AppClock.today().toString(), vm.state.value.date)
        vm.sync(categories, expenses, true, null, false)
        vm.amount("9000")
        assertEquals("3500.00", vm.state.value.amount)
        vm.submit { fail("No duplicate save during persistence") }
        vm.sync(categories, expenses, false, "Connection unavailable", true)
        assertEquals("Lunch", vm.state.value.merchant)
        assertEquals("Retry", vm.state.value.saveLabel)
    }

    @Test fun invalidDraftCannotBeSubmitted() {
        val vm = ExpenseDraftViewModel(SavedStateHandle(), null, ExpensePaymentMethod.CASH)
        vm.sync(categories, emptyList(), false, null, false)
        vm.amount("1.234"); vm.submit { fail("Invalid amount") }
        assertNotNull(vm.state.value.error)
        vm.amount("100"); vm.submit { fail("Category required") }
        assertEquals("Select an expense category.", vm.state.value.error)
    }

    @Test fun salaryScheduleHandlesMonthEndsAndLeapYears() {
        assertEquals(LocalDate.of(2028, 2, 29), SalaryScheduleCalculator.nextPayday(31, LocalDate.of(2028, 2, 1)))
        assertEquals(LocalDate.of(2026, 2, 28), SalaryScheduleCalculator.nextPayday(31, LocalDate.of(2026, 2, 1)))
        assertEquals(LocalDate.of(2026, 11, 25), SalaryScheduleCalculator.nextPayday(25, LocalDate.of(2026, 10, 26)))
        assertEquals(LocalDate.of(2026, 10, 25), SalaryScheduleCalculator.nextPayday(25, LocalDate.of(2026, 10, 25)))
    }

    @Test fun salaryDraftRetainsPaydayAndReminderSettingAfterFailure() {
        val saved = SavedStateHandle()
        val initial = SalaryTemplate("salary", amountMinor = 25000000)
        val vm = SalaryFormViewModel(saved, initial)
        vm.amount("300000.50"); vm.day(31); vm.enabled(false)
        vm.backend(false, "Unable to save", true)
        val restored = SalaryFormViewModel(saved, initial)
        restored.backend(false, "Unable to save", true)
        var result: Triple<String, Int, Boolean>? = null
        restored.submit { amount, day, enabled -> result = Triple(amount, day, enabled) }
        assertEquals(Triple("300000.50", 31, false), result)
        assertEquals("LKR 300,000.50", restored.state.value.formatted)
    }
}
