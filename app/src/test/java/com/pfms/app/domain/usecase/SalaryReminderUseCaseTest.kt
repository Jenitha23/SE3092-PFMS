
package com.pfms.app.domain.usecase

import com.pfms.app.domain.model.SalaryTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SalaryReminderUseCaseTest {

    private val useCase = SalaryReminderUseCase()

    private val template = SalaryTemplate(
        id = "salary-template-1",
        userId = "test-user",
        amountMinor = 13000000L,
        currency = "LKR",
        paymentDay = 25,
        isEnabled = true
    )

    @Test
    fun `reminder not displayed before salary day`() {
        val result = useCase.shouldShowReminder(
            template,
            LocalDate.of(2026, 10, 24)
        )

        assertFalse(result)
    }

    @Test
    fun `reminder displayed on salary day`() {
        val result = useCase.shouldShowReminder(
            template,
            LocalDate.of(2026, 10, 25)
        )

        assertTrue(result)
    }

    @Test
    fun `reminder displayed after salary day if unconfirmed`() {
        val result = useCase.shouldShowReminder(
            template,
            LocalDate.of(2026, 10, 28)
        )

        assertTrue(result)
    }

    @Test
    fun `reminder hidden after monthly confirmation`() {
        val confirmedTemplate = template.copy(
            lastConfirmedMonth = "2026-10"
        )

        val result = useCase.shouldShowReminder(
            confirmedTemplate,
            LocalDate.of(2026, 10, 28)
        )

        assertFalse(result)
    }

    @Test
    fun `reminder returns next month`() {
        val confirmedTemplate = template.copy(
            lastConfirmedMonth = "2026-10"
        )

        val result = useCase.shouldShowReminder(
            confirmedTemplate,
            LocalDate.of(2026, 11, 25)
        )

        assertTrue(result)
    }

    @Test
    fun `disabled template does not display reminder`() {
        val disabledTemplate = template.copy(
            isEnabled = false
        )

        val result = useCase.shouldShowReminder(
            disabledTemplate,
            LocalDate.of(2026, 10, 25)
        )

        assertFalse(result)
    }

    @Test
    fun `invalid salary amount does not trigger reminder`() {
        val invalidTemplate = template.copy(
            amountMinor = 0L
        )

        val result = useCase.shouldShowReminder(
            invalidTemplate,
            LocalDate.of(2026, 10, 25)
        )

        assertFalse(result)
    }

    @Test
    fun `current month key is formatted correctly`() {
        val result = useCase.getCurrentMonthKey(
            LocalDate.of(2026, 10, 25)
        )

        assertEquals("2026-10", result)
    }
}
