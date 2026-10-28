package com.pfms.app.data

import com.pfms.app.domain.model.GoalStatus
import com.pfms.app.domain.model.GoalTransaction
import com.pfms.app.domain.model.SavingsGoal
import com.pfms.app.domain.repository.SavingsGoalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeSavingsGoalRepository : SavingsGoalRepository {

    val restoration = MutableStateFlow(com.pfms.app.domain.model.SavingsRestorationState())
    override fun observeRestoration() = restoration
    override fun clearRestorationError() { restoration.value = com.pfms.app.domain.model.SavingsRestorationState() }

    val goals = MutableStateFlow<List<SavingsGoal>>(emptyList())
    val transactions = MutableStateFlow<List<GoalTransaction>>(emptyList())

    var failureException: Throwable? = null

    override fun observeActiveGoal(): Flow<SavingsGoal?> =
        goals.map { list -> list.firstOrNull { it.status == GoalStatus.ACTIVE } }

    override fun observeTransactions(goalId: String): Flow<List<GoalTransaction>> =
        transactions.map { list -> list.filter { it.goalId == goalId } }

    override suspend fun getGoalById(goalId: String): Result<SavingsGoal?> {
        failureException?.let { return Result.failure(it) }
        return Result.success(goals.value.firstOrNull { it.id == goalId })
    }

    override suspend fun createGoal(goal: SavingsGoal): Result<Unit> {
        failureException?.let { return Result.failure(it) }
        if (goal.status == GoalStatus.ACTIVE && goals.value.any { it.status == GoalStatus.ACTIVE && it.id != goal.id }) {
            return Result.failure(IllegalStateException("An active savings goal already exists"))
        }
        goals.value = goals.value + goal
        return Result.success(Unit)
    }

    override suspend fun updateGoal(goal: SavingsGoal): Result<Unit> {
        failureException?.let { return Result.failure(it) }
        if (goal.status == GoalStatus.ACTIVE && goals.value.any { it.status == GoalStatus.ACTIVE && it.id != goal.id }) {
            return Result.failure(IllegalStateException("An active savings goal already exists"))
        }
        goals.value = goals.value.map { if (it.id == goal.id) goal else it }
        return Result.success(Unit)
    }

    override suspend fun archiveGoal(goalId: String): Result<Unit> {
        failureException?.let { return Result.failure(it) }
        val goal = goals.value.firstOrNull { it.id == goalId }
            ?: return Result.failure(IllegalArgumentException("Goal not found"))
        goals.value = goals.value.map {
            if (it.id == goalId) it.copy(status = GoalStatus.ARCHIVED) else it
        }
        return Result.success(Unit)
    }

    override suspend fun deleteGoal(goalId: String): Result<Unit> {
        failureException?.let { return Result.failure(it) }
        goals.value = goals.value.filterNot { it.id == goalId }
        return Result.success(Unit)
    }

    override suspend fun addTransaction(transaction: GoalTransaction): Result<Unit> {
        failureException?.let { return Result.failure(it) }
        transactions.value = transactions.value + transaction
        return Result.success(Unit)
    }

    override suspend fun updateTransaction(transaction: GoalTransaction): Result<Unit> {
        failureException?.let { return Result.failure(it) }
        transactions.value = transactions.value.map { if (it.id == transaction.id) transaction else it }
        return Result.success(Unit)
    }

    override suspend fun deleteTransaction(transactionId: String): Result<Unit> {
        failureException?.let { return Result.failure(it) }
        transactions.value = transactions.value.filterNot { it.id == transactionId }
        return Result.success(Unit)
    }

    override suspend fun syncPendingGoals(): Result<Int> = Result.success(0)

    override suspend fun syncPendingTransactions(): Result<Int> = Result.success(0)

    override suspend fun refreshRemoteSavingsData(): Result<Unit> {
        failureException?.let { return Result.failure(it) }
        return Result.success(Unit)
    }
}
