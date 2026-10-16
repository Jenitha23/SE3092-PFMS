package com.pfms.app.data.local.mapper

import com.pfms.app.data.local.entity.GoalTransactionEntity
import com.pfms.app.data.local.entity.SavingsGoalEntity
import com.pfms.app.data.local.entity.SyncStatus
import com.pfms.app.domain.model.GoalStatus
import com.pfms.app.domain.model.GoalTransaction
import com.pfms.app.domain.model.GoalTransactionType
import com.pfms.app.domain.model.SavingsGoal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SavingsGoalLocalMappersTest {

    @Test
    fun savingsGoal_toEntity_and_toDomain_roundTrip_preservesAllFields() {
        val domainGoal = SavingsGoal(
            id = "goal-1",
            userId = "user-123",
            name = "Emergency Fund",
            targetAmountMinor = 50_000_000L,
            targetDate = "2027-12-31",
            openingSavedAmountMinor = 5_000_000L,
            status = GoalStatus.ACTIVE,
            createdAt = 1000L,
            updatedAt = 2000L
        )

        val entity = domainGoal.toEntity(
            syncStatus = SyncStatus.PENDING_INSERT,
            isDeleted = false
        )

        // Verify entity fields
        assertEquals("goal-1", entity.id)
        assertEquals("user-123", entity.userId)
        assertEquals("Emergency Fund", entity.name)
        assertEquals(50_000_000L, entity.targetAmountMinor)
        assertEquals("2027-12-31", entity.targetDate)
        assertEquals(5_000_000L, entity.openingSavedAmountMinor)
        assertEquals("ACTIVE", entity.status)
        assertEquals(1000L, entity.createdAt)
        assertEquals(2000L, entity.updatedAt)
        assertEquals(SyncStatus.PENDING_INSERT, entity.syncStatus)
        assertFalse(entity.isDeleted)

        // Verify round-trip back to domain
        val mappedBack = entity.toDomain()
        assertEquals(domainGoal, mappedBack)
    }

    @Test
    fun goalTransaction_toEntity_and_toDomain_roundTrip_preservesAllFields() {
        val domainTx = GoalTransaction(
            id = "tx-1",
            goalId = "goal-1",
            userId = "user-123",
            amountMinor = 250_000L,
            transactionType = GoalTransactionType.CONTRIBUTION,
            date = "2026-10-15",
            note = "Initial deposit",
            createdAt = 3000L,
            updatedAt = 4000L
        )

        val entity = domainTx.toEntity(
            syncStatus = SyncStatus.PENDING_UPDATE,
            isDeleted = false
        )

        // Verify entity fields
        assertEquals("tx-1", entity.id)
        assertEquals("goal-1", entity.goalId)
        assertEquals("user-123", entity.userId)
        assertEquals(250_000L, entity.amountMinor)
        assertEquals("CONTRIBUTION", entity.transactionType)
        assertEquals("2026-10-15", entity.date)
        assertEquals("Initial deposit", entity.note)
        assertEquals(3000L, entity.createdAt)
        assertEquals(4000L, entity.updatedAt)
        assertEquals(SyncStatus.PENDING_UPDATE, entity.syncStatus)
        assertFalse(entity.isDeleted)

        // Verify round-trip back to domain
        val mappedBack = entity.toDomain()
        assertEquals(domainTx, mappedBack)
    }

    @Test
    fun savingsGoalEntity_malformedStatus_fallsBackSafelyToActive() {
        val entity = SavingsGoalEntity(
            id = "goal-bad",
            userId = "user-123",
            name = "Test",
            status = "UNKNOWN_OR_CORRUPTED_VALUE"
        )

        val domain = entity.toDomain()
        assertEquals(GoalStatus.ACTIVE, domain.status)
    }

    @Test
    fun savingsGoalEntity_caseInsensitiveStatus_mapsCorrectly() {
        val archivedEntity = SavingsGoalEntity(
            id = "goal-archived",
            userId = "user-123",
            name = "Test",
            status = "archived"
        )
        assertEquals(GoalStatus.ARCHIVED, archivedEntity.toDomain().status)

        val completedEntity = SavingsGoalEntity(
            id = "goal-completed",
            userId = "user-123",
            name = "Test",
            status = "Completed"
        )
        assertEquals(GoalStatus.COMPLETED, completedEntity.toDomain().status)
    }

    @Test
    fun goalTransactionEntity_malformedType_fallsBackSafelyToContribution() {
        val entity = GoalTransactionEntity(
            id = "tx-bad",
            goalId = "goal-1",
            userId = "user-123",
            transactionType = "UNKNOWN_TRANSACTION_TYPE"
        )

        val domain = entity.toDomain()
        assertEquals(GoalTransactionType.CONTRIBUTION, domain.transactionType)
    }

    @Test
    fun goalTransactionEntity_caseInsensitiveType_mapsCorrectly() {
        val withdrawalEntity = GoalTransactionEntity(
            id = "tx-w",
            goalId = "goal-1",
            userId = "user-123",
            transactionType = "withdrawal"
        )
        assertEquals(GoalTransactionType.WITHDRAWAL, withdrawalEntity.toDomain().transactionType)
    }

    @Test
    fun goalTransactionEntity_nullableNote_preserved() {
        val entity = GoalTransactionEntity(
            id = "tx-no-note",
            goalId = "goal-1",
            userId = "user-123",
            note = null
        )

        assertNull(entity.toDomain().note)
    }

    @Test
    fun listMappers_mapAllElements() {
        val entities = listOf(
            SavingsGoalEntity(id = "g-1", userId = "u-1", name = "Goal 1"),
            SavingsGoalEntity(id = "g-2", userId = "u-1", name = "Goal 2")
        )
        val domainList = entities.toDomain()
        assertEquals(2, domainList.size)
        assertEquals("g-1", domainList[0].id)
        assertEquals("g-2", domainList[1].id)
    }
}
