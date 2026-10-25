package com.pfms.app.viewmodel

import com.pfms.app.data.FakeAdvancedExpenseRepository
import com.pfms.app.domain.model.*
import com.pfms.app.domain.repository.AdvancedExpenseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class AdvancedExpenseRedesignStateTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }

    @Test fun categoryEditorPreservesDefaultsAndArchiveRestoreKeepsIdentity() = runTest(dispatcher) {
        val repo = FakeAdvancedExpenseRepository()
        val vm = AdvancedExpenseViewModel(repo)
        backgroundScope.launch { vm.screen.collect {} }
        vm.ensureDefaults(); advanceUntilIdle()
        val original = vm.screen.value.activeCategories.first()
        vm.editCategory(original); vm.requestArchive(original); advanceUntilIdle()
        assertNull(vm.screen.value.controls.categoryEditor.original)
        assertNull(vm.screen.value.controls.archiveTarget)
        vm.categoryName("  Books  "); vm.categoryClassification(ExpenseClassification.COMMITTED)
        vm.saveCategoryDraft(); advanceUntilIdle()
        val custom = vm.screen.value.activeCategories.single { it.custom }
        assertEquals("Books", custom.name)
        assertEquals(ExpenseClassification.COMMITTED, custom.classification)
        vm.editCategory(custom); vm.categoryName("Learning"); vm.saveCategoryDraft(); advanceUntilIdle()
        val renamed = vm.screen.value.activeCategories.single { it.custom }
        assertEquals(custom.id, renamed.id)
        vm.requestArchive(renamed); vm.archiveSelectedCategory(); advanceUntilIdle()
        assertEquals("Learning", vm.screen.value.archivedCategories.single().name)
        vm.restoreCategory(vm.screen.value.archivedCategories.single()); advanceUntilIdle()
        assertEquals(custom.id, vm.screen.value.activeCategories.single { it.custom }.id)
    }

    @Test fun templateValidationAndExactAmountKeepOriginalScheduleFields() = runTest(dispatcher) {
        val repo = FakeAdvancedExpenseRepository()
        val vm = AdvancedExpenseViewModel(repo)
        backgroundScope.launch { vm.screen.collect {} }
        vm.ensureDefaults(); advanceUntilIdle()
        vm.openTemplate(true); vm.templateTitle("  Cloud storage  "); vm.templateAmount("12.345")
        vm.saveTemplateDraft(); advanceUntilIdle()
        assertTrue(vm.templates.value.isEmpty())
        assertNotNull(vm.screen.value.controls.editor?.error)
        vm.templateAmount("1999.99"); vm.templateRecurrence(Recurrence.WEEKLY)
        vm.templatePayment(ExpensePaymentMethod.DIGITAL_WALLET_PLATFORM)
        vm.templateDate(LocalDate.of(2026, 11, 2).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), false)
        vm.templateDate(LocalDate.of(2026, 10, 30).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), true)
        vm.templateFlag(SubscriptionFlag.REVIEW); vm.saveTemplateDraft(); advanceUntilIdle()
        val template = vm.templates.value.single()
        assertEquals("Cloud storage", template.title)
        assertEquals(199999L, template.amountMinor)
        assertEquals(Recurrence.WEEKLY, template.recurrence)
        assertEquals(ExpensePaymentMethod.DIGITAL_WALLET_PLATFORM, template.paymentMethod)
        assertEquals("2026-11-02", template.nextDueDate)
        assertEquals("2026-10-30", template.reminderDate)
        assertEquals(SubscriptionFlag.REVIEW, template.flag)
        assertNull(vm.screen.value.controls.editor)
    }

    @Test fun failedSaveRetainsDraftAndRetryUsesSameTemplateId() = runTest(dispatcher) {
        val backing = FakeAdvancedExpenseRepository()
        var fail = true
        val ids = mutableListOf<String>()
        val repo = object : AdvancedExpenseRepository by backing {
            override suspend fun saveTemplate(template: RecurringExpense) {
                ids += template.id
                if (fail) throw java.io.IOException("Offline")
                backing.saveTemplate(template)
            }
        }
        val vm = AdvancedExpenseViewModel(repo)
        backgroundScope.launch { vm.screen.collect {} }
        vm.ensureDefaults(); advanceUntilIdle()
        vm.openTemplate(false); vm.templateTitle("Internet"); vm.templateAmount("1500.50")
        vm.saveTemplateDraft(); advanceUntilIdle()
        assertEquals("Internet", vm.screen.value.controls.editor?.title)
        assertEquals("1500.50", vm.screen.value.controls.editor?.amount)
        assertTrue(vm.canRetry.value)
        fail = false; vm.retry(); advanceUntilIdle()
        assertEquals(1, ids.distinct().size)
        assertEquals(150050L, vm.templates.value.single().amountMinor)
        assertNull(vm.screen.value.controls.editor)
    }

    @Test fun confirmingAdjustedPaymentUsesScheduledDateAndAdvancesOnlyAfterSuccess() = runTest(dispatcher) {
        val repo = FakeAdvancedExpenseRepository()
        val vm = AdvancedExpenseViewModel(repo)
        backgroundScope.launch { vm.screen.collect {} }
        val template = RecurringExpense(id = "rent", title = "Rent", amountMinor = 100000L, nextDueDate = "2026-01-31")
        vm.saveTemplate(template); advanceUntilIdle()
        vm.requestConfirmation(template); vm.confirmationAmount("0"); vm.confirmSelectedTemplate(); advanceUntilIdle()
        assertEquals(0, repo.confirmedCount)
        assertNotNull(vm.screen.value.controls.confirmError)
        vm.confirmationAmount("1250.75"); vm.confirmSelectedTemplate(); advanceUntilIdle()
        assertEquals(125075L, repo.createdExpenses.single()["amountMinor"])
        assertEquals("2026-01-31", repo.createdExpenses.single()["date"])
        assertEquals("2026-02-28", vm.templates.value.single().nextDueDate)
        assertNull(vm.screen.value.controls.confirmTarget)
    }

    @Test fun subscriptionsUseDomainMonthlyEstimateAndExcludePausedOrCancelledItems() = runTest(dispatcher) {
        val repo = FakeAdvancedExpenseRepository()
        val vm = AdvancedExpenseViewModel(repo)
        backgroundScope.launch { vm.screen.collect {} }
        val weekly = RecurringExpense(id = "weekly", title = "Weekly", amountMinor = 30000, recurrence = Recurrence.WEEKLY,
            nextDueDate = "2000-01-01", reminderDate = "2000-01-01", subscription = true)
        vm.saveTemplate(weekly); advanceUntilIdle()
        vm.saveTemplate(weekly.copy(id = "paused", active = false)); advanceUntilIdle()
        vm.saveTemplate(weekly.copy(id = "cancelled", flag = SubscriptionFlag.CANCEL)); advanceUntilIdle()
        vm.saveTemplate(weekly.copy(id = "recurring", subscription = false)); advanceUntilIdle()
        assertEquals("LKR 1,300.00", vm.screen.value.monthlyTotal)
        assertEquals(1, vm.screen.value.activeSubscriptions)
        assertEquals(1, vm.screen.value.dueSubscriptions)
        assertEquals(1, vm.screen.value.reviewSubscriptions)
        assertEquals(1, vm.screen.value.recurring.size)
        vm.dismissReview(weekly); advanceUntilIdle()
        assertEquals(0, vm.screen.value.reviewSubscriptions)
        vm.cancelSubscription(vm.templates.value.first { it.id == "weekly" }); advanceUntilIdle()
        assertEquals("LKR 0.00", vm.screen.value.monthlyTotal)
        val cancelled = vm.templates.value.first { it.id == "weekly" }
        vm.toggleActive(cancelled); advanceUntilIdle()
        assertEquals("LKR 1,300.00", vm.screen.value.monthlyTotal)
        assertEquals(SubscriptionFlag.NONE, vm.templates.value.first { it.id == "weekly" }.flag)
    }

    @Test fun editKeepsPausedTemplateIdentityAndDeleteRequiresExplicitAction() = runTest(dispatcher) {
        val repo = FakeAdvancedExpenseRepository()
        val vm = AdvancedExpenseViewModel(repo)
        backgroundScope.launch { vm.screen.collect {} }
        vm.ensureDefaults(); advanceUntilIdle()
        val category = vm.categories.value.first()
        val original = RecurringExpense(id = "paused", title = "Wi-Fi", categoryId = category.id, amountMinor = 100000,
            nextDueDate = "2026-11-25", active = false, subscription = true)
        vm.saveTemplate(original); advanceUntilIdle()
        vm.openTemplate(true, original); vm.templateAmount("2000.00"); vm.saveTemplateDraft(); advanceUntilIdle()
        val saved = vm.templates.value.single()
        assertEquals("paused", saved.id); assertFalse(saved.active); assertTrue(saved.subscription)
        vm.requestDelete(saved); advanceUntilIdle()
        assertEquals(1, vm.templates.value.size)
        vm.requestDelete(null); advanceUntilIdle()
        assertEquals(1, vm.templates.value.size)
        vm.requestDelete(saved); vm.deleteSelectedTemplate(); advanceUntilIdle()
        assertTrue(vm.templates.value.isEmpty())
    }
}
