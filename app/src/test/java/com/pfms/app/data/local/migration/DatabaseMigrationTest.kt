package com.pfms.app.data.local.migration

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pfms.app.data.local.PfmsDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Migration strategy verification for PfmsDatabase.
 *
 * Verifies that:
 * 1. Version 1 can reach version 4 directly (MIGRATION_1_4).
 * 2. Historical savings-style version 2 can reach version 4 (MIGRATION_2_4).
 * 3. Historical expense-style version 2 can reach version 4 (MIGRATION_2_4).
 * 4. Main version 3 can reach version 4 (MIGRATION_3_4).
 * 5. Existing income and salary template data are preserved across all paths.
 * 6. Existing savings goal and goal transaction data are preserved across all paths.
 * 7. Existing expense category and recurring expense data are preserved across all paths.
 * 8. All 6 entities/tables exist in the final schema with correct composite primary keys and indexes.
 */
class DatabaseMigrationTest {

    // ─────────────────────────────────────────────────────────────
    // 1. Version 1 -> Version 4
    // ─────────────────────────────────────────────────────────────

    @Test
    fun version1_canReachVersion4_viaMigration_1_4() {
        assertEquals(1, MIGRATION_1_4.startVersion)
        assertEquals(4, MIGRATION_1_4.endVersion)

        val mockDb = mock<SupportSQLiteDatabase>()
        MIGRATION_1_4.migrate(mockDb)

        val captor = argumentCaptor<String>()
        verify(mockDb, atLeastOnce()).execSQL(captor.capture())
        val executedSql = captor.allValues

        // Savings tables created
        assertTrue(executedSql.any { it.contains("CREATE TABLE IF NOT EXISTS `savings_goals`") })
        assertTrue(executedSql.any { it.contains("CREATE TABLE IF NOT EXISTS `goal_transactions`") })

        // Expense tables created with composite PK
        assertTrue(executedSql.any {
            it.contains("CREATE TABLE IF NOT EXISTS `expense_categories`") &&
                it.contains("PRIMARY KEY(`userId`, `id`)")
        })
        assertTrue(executedSql.any {
            it.contains("CREATE TABLE IF NOT EXISTS `recurring_expenses`") &&
                it.contains("PRIMARY KEY(`userId`, `id`)")
        })

        // All indexes created
        assertSavingsIndexesCreated(executedSql)
        assertExpenseIndexesCreated(executedSql)

        // Income & salary tables untouched
        assertExistingCoreDataPreserved(executedSql)
    }

    // ─────────────────────────────────────────────────────────────
    // 2. Historical Savings v2 -> Version 4
    // ─────────────────────────────────────────────────────────────

    @Test
    fun historicalSavingsV2_canReachVersion4_viaMigration_2_4() {
        assertEquals(2, MIGRATION_2_4.startVersion)
        assertEquals(4, MIGRATION_2_4.endVersion)

        val mockDb = mock<SupportSQLiteDatabase>()
        // In savings v2: expense_categories & recurring_expenses do not exist
        whenever(mockDb.query(anyString())).thenAnswer { inv ->
            val sql = inv.getArgument<String>(0)
            if (sql.contains("sqlite_master")) createExistsCursor(false)
            else null
        }

        MIGRATION_2_4.migrate(mockDb)

        val captor = argumentCaptor<String>()
        verify(mockDb, atLeastOnce()).execSQL(captor.capture())
        val executedSql = captor.allValues

        // Savings tables preserved via IF NOT EXISTS (never dropped)
        assertTrue(executedSql.any { it.contains("CREATE TABLE IF NOT EXISTS `savings_goals`") })
        assertTrue(executedSql.any { it.contains("CREATE TABLE IF NOT EXISTS `goal_transactions`") })
        assertFalse(executedSql.any { it.contains("DROP TABLE `savings_goals`", ignoreCase = true) })
        assertFalse(executedSql.any { it.contains("DROP TABLE `goal_transactions`", ignoreCase = true) })

        // Expense tables created with final composite PK
        assertTrue(executedSql.any {
            it.contains("CREATE TABLE IF NOT EXISTS `expense_categories`") &&
                it.contains("PRIMARY KEY(`userId`, `id`)")
        })
        assertTrue(executedSql.any {
            it.contains("CREATE TABLE IF NOT EXISTS `recurring_expenses`") &&
                it.contains("PRIMARY KEY(`userId`, `id`)")
        })

        assertSavingsIndexesCreated(executedSql)
        assertExpenseIndexesCreated(executedSql)
        assertExistingCoreDataPreserved(executedSql)
    }

    // ─────────────────────────────────────────────────────────────
    // 3. Historical Expense v2 -> Version 4
    // ─────────────────────────────────────────────────────────────

    @Test
    fun historicalExpenseV2_canReachVersion4_viaMigration_2_4() {
        val mockDb = mock<SupportSQLiteDatabase>()
        // In expense v2: expense tables exist with single PK (not composite)
        whenever(mockDb.query(anyString())).thenAnswer { inv ->
            val sql = inv.getArgument<String>(0)
            if (sql.contains("sqlite_master")) createExistsCursor(true)
            else if (sql.contains("PRAGMA table_info")) createSinglePkCursor()
            else null
        }

        MIGRATION_2_4.migrate(mockDb)

        val captor = argumentCaptor<String>()
        verify(mockDb, atLeastOnce()).execSQL(captor.capture())
        val executedSql = captor.allValues

        // Savings tables created
        assertTrue(executedSql.any { it.contains("CREATE TABLE IF NOT EXISTS `savings_goals`") })
        assertTrue(executedSql.any { it.contains("CREATE TABLE IF NOT EXISTS `goal_transactions`") })

        // Expense categories migrated to composite PK preserving data
        assertTrue(executedSql.any {
            it.contains("CREATE TABLE `expense_categories_v4`") &&
                it.contains("PRIMARY KEY(`userId`, `id`)")
        })
        assertTrue(executedSql.any {
            it.contains("INSERT INTO `expense_categories_v4`") &&
                it.contains("FROM `expense_categories`")
        })
        assertTrue(executedSql.any { it.contains("ALTER TABLE `expense_categories_v4` RENAME TO `expense_categories`") })

        // Recurring expenses migrated to composite PK preserving data
        assertTrue(executedSql.any {
            it.contains("CREATE TABLE `recurring_expenses_v4`") &&
                it.contains("PRIMARY KEY(`userId`, `id`)")
        })
        assertTrue(executedSql.any {
            it.contains("INSERT INTO `recurring_expenses_v4`") &&
                it.contains("FROM `recurring_expenses`")
        })
        assertTrue(executedSql.any { it.contains("ALTER TABLE `recurring_expenses_v4` RENAME TO `recurring_expenses`") })

        assertSavingsIndexesCreated(executedSql)
        assertExpenseIndexesCreated(executedSql)
        assertExistingCoreDataPreserved(executedSql)
    }

    // ─────────────────────────────────────────────────────────────
    // 4. Main Version 3 -> Version 4
    // ─────────────────────────────────────────────────────────────

    @Test
    fun mainV3_canReachVersion4_viaMigration_3_4() {
        assertEquals(3, MIGRATION_3_4.startVersion)
        assertEquals(4, MIGRATION_3_4.endVersion)

        val mockDb = mock<SupportSQLiteDatabase>()
        // In main v3: expense tables exist with composite PK
        whenever(mockDb.query(anyString())).thenAnswer { inv ->
            val sql = inv.getArgument<String>(0)
            if (sql.contains("sqlite_master")) createExistsCursor(true)
            else if (sql.contains("PRAGMA table_info")) createCompositePkCursor()
            else null
        }

        MIGRATION_3_4.migrate(mockDb)

        val captor = argumentCaptor<String>()
        verify(mockDb, atLeastOnce()).execSQL(captor.capture())
        val executedSql = captor.allValues

        // Savings tables created
        assertTrue(executedSql.any { it.contains("CREATE TABLE IF NOT EXISTS `savings_goals`") })
        assertTrue(executedSql.any { it.contains("CREATE TABLE IF NOT EXISTS `goal_transactions`") })

        // Expense tables preserved without drop or recreate
        assertFalse(executedSql.any { it.contains("DROP TABLE `expense_categories`", ignoreCase = true) })
        assertFalse(executedSql.any { it.contains("DROP TABLE `recurring_expenses`", ignoreCase = true) })

        assertSavingsIndexesCreated(executedSql)
        assertExpenseIndexesCreated(executedSql)
        assertExistingCoreDataPreserved(executedSql)
    }

    // ─────────────────────────────────────────────────────────────
    // 5. Schema DDL & Index Verification for All Final Tables
    // ─────────────────────────────────────────────────────────────

    @Test
    fun finalVersion4_containsAllRequiredTablesAndIndexes() {
        val mockDb = mock<SupportSQLiteDatabase>()
        MIGRATION_1_4.migrate(mockDb)

        val captor = argumentCaptor<String>()
        verify(mockDb, atLeastOnce()).execSQL(captor.capture())
        val executedSql = captor.allValues

        // 1. savings_goals columns
        val savingsTableSql = executedSql.first { it.contains("CREATE TABLE IF NOT EXISTS `savings_goals`") }
        assertTrue(savingsTableSql.contains("`id` TEXT NOT NULL"))
        assertTrue(savingsTableSql.contains("`userId` TEXT NOT NULL"))
        assertTrue(savingsTableSql.contains("`name` TEXT NOT NULL"))
        assertTrue(savingsTableSql.contains("`targetAmountMinor` INTEGER NOT NULL"))
        assertTrue(savingsTableSql.contains("`targetDate` TEXT NOT NULL"))
        assertTrue(savingsTableSql.contains("`openingSavedAmountMinor` INTEGER NOT NULL"))
        assertTrue(savingsTableSql.contains("`status` TEXT NOT NULL"))
        assertTrue(savingsTableSql.contains("`createdAt` INTEGER NOT NULL"))
        assertTrue(savingsTableSql.contains("`updatedAt` INTEGER NOT NULL"))
        assertTrue(savingsTableSql.contains("`syncStatus` TEXT NOT NULL"))
        assertTrue(savingsTableSql.contains("`isDeleted` INTEGER NOT NULL"))
        assertTrue(savingsTableSql.contains("PRIMARY KEY(`id`)"))

        // 2. goal_transactions columns
        val transactionTableSql = executedSql.first { it.contains("CREATE TABLE IF NOT EXISTS `goal_transactions`") }
        assertTrue(transactionTableSql.contains("`id` TEXT NOT NULL"))
        assertTrue(transactionTableSql.contains("`goalId` TEXT NOT NULL"))
        assertTrue(transactionTableSql.contains("`userId` TEXT NOT NULL"))
        assertTrue(transactionTableSql.contains("`amountMinor` INTEGER NOT NULL"))
        assertTrue(transactionTableSql.contains("`transactionType` TEXT NOT NULL"))
        assertTrue(transactionTableSql.contains("`date` TEXT NOT NULL"))
        assertTrue(transactionTableSql.contains("`note` TEXT"))
        assertTrue(transactionTableSql.contains("`createdAt` INTEGER NOT NULL"))
        assertTrue(transactionTableSql.contains("`updatedAt` INTEGER NOT NULL"))
        assertTrue(transactionTableSql.contains("`syncStatus` TEXT NOT NULL"))
        assertTrue(transactionTableSql.contains("`isDeleted` INTEGER NOT NULL"))
        assertTrue(transactionTableSql.contains("PRIMARY KEY(`id`)"))

        // 3. expense_categories columns and composite PK
        val categoriesTableSql = executedSql.first { it.contains("CREATE TABLE IF NOT EXISTS `expense_categories`") }
        assertTrue(categoriesTableSql.contains("`id` TEXT NOT NULL"))
        assertTrue(categoriesTableSql.contains("`userId` TEXT NOT NULL"))
        assertTrue(categoriesTableSql.contains("`name` TEXT NOT NULL"))
        assertTrue(categoriesTableSql.contains("`classification` TEXT NOT NULL"))
        assertTrue(categoriesTableSql.contains("`isCustom` INTEGER NOT NULL"))
        assertTrue(categoriesTableSql.contains("`isArchived` INTEGER NOT NULL"))
        assertTrue(categoriesTableSql.contains("`syncStatus` TEXT NOT NULL"))
        assertTrue(categoriesTableSql.contains("`updatedAt` INTEGER NOT NULL"))
        assertTrue(categoriesTableSql.contains("PRIMARY KEY(`userId`, `id`)"))

        // 4. recurring_expenses columns and composite PK
        val recurringTableSql = executedSql.first { it.contains("CREATE TABLE IF NOT EXISTS `recurring_expenses`") }
        assertTrue(recurringTableSql.contains("`id` TEXT NOT NULL"))
        assertTrue(recurringTableSql.contains("`userId` TEXT NOT NULL"))
        assertTrue(recurringTableSql.contains("`title` TEXT NOT NULL"))
        assertTrue(recurringTableSql.contains("`categoryId` TEXT NOT NULL"))
        assertTrue(recurringTableSql.contains("`categoryName` TEXT NOT NULL"))
        assertTrue(recurringTableSql.contains("`classification` TEXT NOT NULL"))
        assertTrue(recurringTableSql.contains("`amountMinor` INTEGER NOT NULL"))
        assertTrue(recurringTableSql.contains("`paymentMethod` TEXT NOT NULL"))
        assertTrue(recurringTableSql.contains("`recurrence` TEXT NOT NULL"))
        assertTrue(recurringTableSql.contains("`nextDueDate` TEXT NOT NULL"))
        assertTrue(recurringTableSql.contains("`isActive` INTEGER NOT NULL"))
        assertTrue(recurringTableSql.contains("`isSubscription` INTEGER NOT NULL"))
        assertTrue(recurringTableSql.contains("`flag` TEXT NOT NULL"))
        assertTrue(recurringTableSql.contains("`reminderDate` TEXT NOT NULL"))
        assertTrue(recurringTableSql.contains("`syncStatus` TEXT NOT NULL"))
        assertTrue(recurringTableSql.contains("`updatedAt` INTEGER NOT NULL"))
        assertTrue(recurringTableSql.contains("PRIMARY KEY(`userId`, `id`)"))
    }

    @Test
    fun databaseCompanion_exposesAllRequiredMigrations() {
        assertEquals(1, PfmsDatabase.MIGRATION_1_4.startVersion)
        assertEquals(4, PfmsDatabase.MIGRATION_1_4.endVersion)

        assertEquals(2, PfmsDatabase.MIGRATION_2_4.startVersion)
        assertEquals(4, PfmsDatabase.MIGRATION_2_4.endVersion)

        assertEquals(3, PfmsDatabase.MIGRATION_3_4.startVersion)
        assertEquals(4, PfmsDatabase.MIGRATION_3_4.endVersion)

        assertEquals(1, PfmsDatabase.MIGRATION_1_2.startVersion)
        assertEquals(2, PfmsDatabase.MIGRATION_1_2.endVersion)

        assertEquals(2, PfmsDatabase.MIGRATION_2_3.startVersion)
        assertEquals(3, PfmsDatabase.MIGRATION_2_3.endVersion)
    }

    // ─────────────────────────────────────────────────────────────
    // Helper Cursor Factories & Assertions
    // ─────────────────────────────────────────────────────────────

    private fun assertSavingsIndexesCreated(executedSql: List<String>) {
        assertTrue(executedSql.any { it.contains("index_savings_goals_userId") })
        assertTrue(executedSql.any { it.contains("index_savings_goals_userId_status") })
        assertTrue(executedSql.any { it.contains("index_savings_goals_userId_syncStatus") })

        assertTrue(executedSql.any { it.contains("index_goal_transactions_userId") })
        assertTrue(executedSql.any { it.contains("index_goal_transactions_goalId") })
        assertTrue(executedSql.any { it.contains("index_goal_transactions_userId_goalId") })
        assertTrue(executedSql.any { it.contains("index_goal_transactions_userId_date") })
        assertTrue(executedSql.any { it.contains("index_goal_transactions_userId_syncStatus") })
    }

    private fun assertExpenseIndexesCreated(executedSql: List<String>) {
        assertTrue(executedSql.any { it.contains("index_expense_categories_userId") })
        assertTrue(executedSql.any { it.contains("index_expense_categories_userId_isArchived") })

        assertTrue(executedSql.any { it.contains("index_recurring_expenses_userId") })
        assertTrue(executedSql.any { it.contains("index_recurring_expenses_userId_isActive") })
        assertTrue(executedSql.any { it.contains("index_recurring_expenses_userId_isSubscription") })
    }

    private fun assertExistingCoreDataPreserved(executedSql: List<String>) {
        val joined = executedSql.joinToString("\n")
        assertFalse("Must never drop incomes table", joined.contains("DROP TABLE `incomes`", ignoreCase = true))
        assertFalse("Must never drop salary_templates table", joined.contains("DROP TABLE `salary_templates`", ignoreCase = true))
    }

    private fun createExistsCursor(exists: Boolean): Cursor {
        val cursor = mock<Cursor>()
        whenever(cursor.moveToFirst()).thenReturn(exists)
        return cursor
    }

    private fun createSinglePkCursor(): Cursor {
        val cursor = mock<Cursor>()
        whenever(cursor.getColumnIndex("pk")).thenReturn(1)
        whenever(cursor.moveToNext()).thenReturn(true, true, false)
        whenever(cursor.getInt(1)).thenReturn(1, 0) // only 1 column has pk > 0
        return cursor
    }

    private fun createCompositePkCursor(): Cursor {
        val cursor = mock<Cursor>()
        whenever(cursor.getColumnIndex("pk")).thenReturn(1)
        whenever(cursor.moveToNext()).thenReturn(true, true, false)
        whenever(cursor.getInt(1)).thenReturn(1, 2) // 2 columns have pk > 0
        return cursor
    }
}
