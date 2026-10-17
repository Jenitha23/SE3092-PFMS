package com.pfms.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.Transaction
import com.pfms.app.data.local.entity.RecurringExpenseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringExpenseDao {

    @Query("SELECT * FROM recurring_expenses WHERE userId = :userId ORDER BY nextDueDate ASC")
    fun observeTemplates(userId: String): Flow<List<RecurringExpenseEntity>>

    @Query("SELECT * FROM recurring_expenses WHERE userId = :userId AND isSubscription = 0 ORDER BY nextDueDate ASC")
    fun observeRecurringExpenses(userId: String): Flow<List<RecurringExpenseEntity>>

    @Query("SELECT * FROM recurring_expenses WHERE userId = :userId AND isSubscription = 1 ORDER BY nextDueDate ASC")
    fun observeSubscriptions(userId: String): Flow<List<RecurringExpenseEntity>>

    @Query("SELECT * FROM recurring_expenses WHERE userId = :userId AND isActive = 1")
    suspend fun getActiveTemplates(userId: String): List<RecurringExpenseEntity>

    @Query("SELECT * FROM recurring_expenses WHERE userId = :userId AND id = :id LIMIT 1")
    suspend fun getTemplateById(userId: String, id: String): RecurringExpenseEntity?

    @Upsert
    suspend fun upsertTemplates(templates: List<RecurringExpenseEntity>)

    @Upsert
    suspend fun upsertTemplate(template: RecurringExpenseEntity)

    @Query("DELETE FROM recurring_expenses WHERE userId = :userId AND id = :id")
    suspend fun deleteTemplate(userId: String, id: String)

    @Query("DELETE FROM recurring_expenses WHERE userId = :userId")
    suspend fun clearAllForUser(userId: String)

    @Transaction
    suspend fun replaceForUser(userId: String, templates: List<RecurringExpenseEntity>) {
        clearAllForUser(userId)
        upsertTemplates(templates)
    }
}
