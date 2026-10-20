package com.pfms.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pfms.app.data.local.PfmsDatabase
import com.pfms.app.data.local.entity.ExpenseCategoryEntity
import com.pfms.app.data.local.entity.IncomeEntity
import com.pfms.app.data.local.entity.SalaryTemplateEntity
import com.pfms.app.data.local.entity.SavingsGoalEntity
import com.pfms.app.data.local.entity.SyncStatus
import com.pfms.app.data.local.entity.toEntity
import com.pfms.app.domain.model.Expense
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DatabaseVersionFourUpgradeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "version-four-upgrade-${UUID.randomUUID()}.db"
    private var db: PfmsDatabase? = null
    private val userId = "migration-user"

    private fun open(): PfmsDatabase = Room.databaseBuilder(context, PfmsDatabase::class.java, name)
        .addMigrations(PfmsDatabase.MIGRATION_4_5)
        .build().also { db = it }

    @After
    fun close() {
        db?.close()
        context.deleteDatabase(name)
    }

    @Test
    fun expenseOnlyVersionFourUpgradesWithoutLosingPendingData() = runBlocking {
        val income = IncomeEntity("income", userId, amountMinor = 500000, syncStatus = SyncStatus.PENDING_INSERT)
        val salary = SalaryTemplateEntity("salary", userId, amountMinor = 900000, syncStatus = SyncStatus.PENDING_UPDATE)
        val expense = Expense(id = "expense", userId = userId, amountMinor = 125050, date = "2026-10-19")
            .toEntity().copy(syncStatus = "PENDING_INSERT", revision = "unsynced-revision")
        val category = ExpenseCategoryEntity("rent", userId, "Rent", "COMMITTED", false, false)
        open().apply {
            incomeDao().upsertIncome(income)
            salaryTemplateDao().upsertSalaryTemplate(salary)
            expenseDao().upsert(expense)
            expenseCategoryDao().upsertCategory(category)
            close()
        }
        // Reproduce the pre-savings version 4 schema and identity from the launch crash.
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, 0).use {
            it.execSQL("DROP TABLE savings_goals")
            it.execSQL("DROP TABLE goal_transactions")
            it.execSQL("UPDATE room_master_table SET identity_hash = 'f0076143b8a5adbada0eba68b54ffa9c' WHERE id = 42")
            it.version = 4
        }

        val upgraded = open()
        assertEquals(income, upgraded.incomeDao().getIncomeById(userId, income.id))
        assertEquals(salary, upgraded.salaryTemplateDao().getSalaryTemplate(userId))
        assertEquals(expense, upgraded.expenseDao().get(userId, expense.id))
        assertEquals(category, upgraded.expenseCategoryDao().observeCategories(userId).first().single())
        assertNull(upgraded.savingsGoalDao().getActiveGoal(userId))
        assertEquals(5, upgraded.openHelper.writableDatabase.version)
        upgraded.close()
        assertEquals(expense, open().expenseDao().get(userId, expense.id))
    }

    @Test
    fun mergedVersionFourUpgradesWithoutLosingSavingsGoals() = runBlocking {
        val goal = SavingsGoalEntity("goal", userId, "Emergency fund", targetAmountMinor = 1000000,
            targetDate = "2027-01-01", syncStatus = SyncStatus.PENDING_INSERT)
        open().apply {
            savingsGoalDao().insertGoal(goal)
            close()
        }
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, 0).use {
            it.execSQL("UPDATE room_master_table SET identity_hash = '4be8207592b7eb88b72c6adbd6b89d7f' WHERE id = 42")
            it.version = 4
        }

        assertEquals(goal, open().savingsGoalDao().getActiveGoal(userId))
        assertEquals(5, db!!.openHelper.writableDatabase.version)
    }
}
