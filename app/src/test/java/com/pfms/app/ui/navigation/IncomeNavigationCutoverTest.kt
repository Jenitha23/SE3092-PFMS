package com.pfms.app.ui.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class IncomeNavigationCutoverTest {

    @Test
    fun `IncomeViewModel has HiltViewModel and Inject constructor in source`() {
        val file = File("src/main/java/com/pfms/app/viewmodel/IncomeViewModel.kt")
        assertTrue("IncomeViewModel.kt must exist", file.exists())
        val content = file.readText()
        assertTrue("Must have @HiltViewModel", content.contains("@HiltViewModel"))
        assertTrue("Must have @Inject constructor", content.contains("@Inject constructor"))
    }

    @Test
    fun `SalaryViewModel has HiltViewModel and Inject constructor in source`() {
        val file = File("src/main/java/com/pfms/app/viewmodel/SalaryViewModel.kt")
        assertTrue("SalaryViewModel.kt must exist", file.exists())
        val content = file.readText()
        assertTrue("Must have @HiltViewModel", content.contains("@HiltViewModel"))
        assertTrue("Must have @Inject constructor", content.contains("@Inject constructor"))
    }

    @Test
    fun `SalaryReminderUseCase has Inject constructor in source`() {
        val file = File("src/main/java/com/pfms/app/domain/usecase/SalaryReminderUseCase.kt")
        assertTrue("SalaryReminderUseCase.kt must exist", file.exists())
        val content = file.readText()
        assertTrue("Must have @Inject constructor", content.contains("@Inject constructor"))
    }

    @Test
    fun `PfmsNavGraph no longer contains demoIncomes or storage integration pending placeholder`() {
        val navGraphFile = File("src/main/java/com/pfms/app/ui/navigation/PfmsNavGraph.kt")
        assertTrue("PfmsNavGraph.kt must exist", navGraphFile.exists())
        val content = navGraphFile.readText()
        assertFalse("PfmsNavGraph must not contain demoIncomes", content.contains("demoIncomes"))
        assertFalse("PfmsNavGraph must not contain Storage Integration Pending", content.contains("Storage Integration Pending"))
        assertTrue("PfmsNavGraph must inject IncomeViewModel", content.contains("incomeViewModel: IncomeViewModel"))
        assertTrue("PfmsNavGraph must inject SalaryViewModel", content.contains("salaryViewModel: SalaryViewModel"))
    }
}
