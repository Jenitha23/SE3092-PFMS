package com.pfms.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.Transaction
import com.pfms.app.data.local.entity.ExpenseCategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseCategoryDao {

    @Query("SELECT * FROM expense_categories WHERE userId = :userId ORDER BY isArchived ASC, name ASC")
    fun observeCategories(userId: String): Flow<List<ExpenseCategoryEntity>>

    @Query("SELECT * FROM expense_categories WHERE userId = :userId AND isArchived = 0 ORDER BY name ASC")
    fun observeActiveCategories(userId: String): Flow<List<ExpenseCategoryEntity>>

    @Query("SELECT * FROM expense_categories WHERE userId = :userId AND id = :id LIMIT 1")
    suspend fun getCategoryById(userId: String, id: String): ExpenseCategoryEntity?

    @Upsert
    suspend fun upsertCategories(categories: List<ExpenseCategoryEntity>)

    @Upsert
    suspend fun upsertCategory(category: ExpenseCategoryEntity)

    @Query("DELETE FROM expense_categories WHERE userId = :userId AND id = :id")
    suspend fun deleteCategory(userId: String, id: String)

    @Query("DELETE FROM expense_categories WHERE userId = :userId")
    suspend fun clearAllForUser(userId: String)

    @Transaction
    suspend fun replaceForUser(userId: String, categories: List<ExpenseCategoryEntity>) {
        clearAllForUser(userId)
        upsertCategories(categories)
    }
}
