package com.pfms.app.data.local.migration

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class DatabaseMigrationTest {

    @Test
    fun migration_1_to_2_hasCorrectVersions() {
        assertEquals(1, MIGRATION_1_2.startVersion)
        assertEquals(2, MIGRATION_1_2.endVersion)
    }

    @Test
    fun migration_1_to_2_createsNewTablesAndIndexesWithoutDroppingExistingTables() {
        val mockDb = mock<SupportSQLiteDatabase>()
        MIGRATION_1_2.migrate(mockDb)

        val captor = argumentCaptor<String>()
        verify(mockDb, atLeastOnce()).execSQL(captor.capture())

        val executedSqlList = captor.allValues
        val joinedSql = executedSqlList.joinToString("\n")

        // 1. Verify savings_goals table is created
        assertTrue(
            "Must execute CREATE TABLE for savings_goals",
            executedSqlList.any { it.contains("CREATE TABLE IF NOT EXISTS `savings_goals`") }
        )

        // 2. Verify all savings_goals indices are created
        assertTrue(
            "Must create index_savings_goals_userId",
            executedSqlList.any { it.contains("index_savings_goals_userId") }
        )
        assertTrue(
            "Must create index_savings_goals_userId_status",
            executedSqlList.any { it.contains("index_savings_goals_userId_status") }
        )
        assertTrue(
            "Must create index_savings_goals_userId_syncStatus",
            executedSqlList.any { it.contains("index_savings_goals_userId_syncStatus") }
        )

        // 3. Verify goal_transactions table is created
        assertTrue(
            "Must execute CREATE TABLE for goal_transactions",
            executedSqlList.any { it.contains("CREATE TABLE IF NOT EXISTS `goal_transactions`") }
        )

        // 4. Verify all goal_transactions indices are created
        assertTrue(
            "Must create index_goal_transactions_userId",
            executedSqlList.any { it.contains("index_goal_transactions_userId") }
        )
        assertTrue(
            "Must create index_goal_transactions_goalId",
            executedSqlList.any { it.contains("index_goal_transactions_goalId") }
        )
        assertTrue(
            "Must create index_goal_transactions_userId_goalId",
            executedSqlList.any { it.contains("index_goal_transactions_userId_goalId") }
        )
        assertTrue(
            "Must create index_goal_transactions_userId_date",
            executedSqlList.any { it.contains("index_goal_transactions_userId_date") }
        )
        assertTrue(
            "Must create index_goal_transactions_userId_syncStatus",
            executedSqlList.any { it.contains("index_goal_transactions_userId_syncStatus") }
        )

        // 5. Verify NO DROP statements are executed (destructive changes forbidden)
        assertFalse(
            "Migration 1->2 must never drop tables or recreate existing data",
            joinedSql.contains("DROP TABLE", ignoreCase = true)
        )
    }
}
