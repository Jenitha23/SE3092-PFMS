package com.pfms.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pfms.app.data.local.dao.ExpenseCategoryDao
import com.pfms.app.data.local.dao.IncomeDao
import com.pfms.app.data.local.dao.RecurringExpenseDao
import com.pfms.app.data.local.dao.SalaryTemplateDao
import com.pfms.app.data.local.entity.ExpenseCategoryEntity
import com.pfms.app.data.local.entity.IncomeEntity
import com.pfms.app.data.local.entity.RecurringExpenseEntity
import com.pfms.app.data.local.entity.SalaryTemplateEntity
import com.pfms.app.data.local.entity.ExpenseEntity
import com.pfms.app.data.local.dao.ExpenseDao

/**
 * Main Room database for the Personal Financial Management System (PFMS).
 * Provides offline caching and sync persistence for income, categories, recurring templates, and salary data.
 */
@Database(
    entities = [
        IncomeEntity::class,
        ExpenseEntity::class,
        SalaryTemplateEntity::class,
        ExpenseCategoryEntity::class,
        RecurringExpenseEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class PfmsDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun incomeDao(): IncomeDao
    abstract fun salaryTemplateDao(): SalaryTemplateDao
    abstract fun expenseCategoryDao(): ExpenseCategoryDao
    abstract fun recurringExpenseDao(): RecurringExpenseDao

    companion object {
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS expenses (
                    userId TEXT NOT NULL, id TEXT NOT NULL, amountMinor INTEGER NOT NULL,
                    currency TEXT NOT NULL, categoryId TEXT NOT NULL, categoryName TEXT NOT NULL,
                    classification TEXT NOT NULL, paymentMethod TEXT NOT NULL, date TEXT NOT NULL,
                    merchant TEXT NOT NULL, note TEXT NOT NULL, createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL, syncStatus TEXT NOT NULL, isDeleted INTEGER NOT NULL,
                    revision TEXT NOT NULL, allowDuplicate INTEGER NOT NULL, needsConfirmation INTEGER NOT NULL,
                    PRIMARY KEY(userId,id))""")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_userId ON expenses(userId)")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Default category IDs are shared by every account on this device.
                db.execSQL("""CREATE TABLE expense_categories_v3 (
                    id TEXT NOT NULL, userId TEXT NOT NULL, name TEXT NOT NULL,
                    classification TEXT NOT NULL, isCustom INTEGER NOT NULL,
                    isArchived INTEGER NOT NULL, syncStatus TEXT NOT NULL, updatedAt INTEGER NOT NULL,
                    PRIMARY KEY(userId, id))""")
                db.execSQL("INSERT INTO expense_categories_v3 SELECT * FROM expense_categories")
                db.execSQL("DROP TABLE expense_categories")
                db.execSQL("ALTER TABLE expense_categories_v3 RENAME TO expense_categories")
                db.execSQL("CREATE INDEX index_expense_categories_userId ON expense_categories(userId)")
                db.execSQL("CREATE INDEX index_expense_categories_userId_isArchived ON expense_categories(userId, isArchived)")
                db.execSQL("""CREATE TABLE recurring_expenses_v3 (
                    id TEXT NOT NULL, userId TEXT NOT NULL, title TEXT NOT NULL,
                    categoryId TEXT NOT NULL, categoryName TEXT NOT NULL, classification TEXT NOT NULL,
                    amountMinor INTEGER NOT NULL, paymentMethod TEXT NOT NULL, recurrence TEXT NOT NULL,
                    nextDueDate TEXT NOT NULL, isActive INTEGER NOT NULL, isSubscription INTEGER NOT NULL,
                    flag TEXT NOT NULL, reminderDate TEXT NOT NULL, syncStatus TEXT NOT NULL,
                    updatedAt INTEGER NOT NULL, PRIMARY KEY(userId, id))""")
                db.execSQL("INSERT INTO recurring_expenses_v3 SELECT * FROM recurring_expenses")
                db.execSQL("DROP TABLE recurring_expenses")
                db.execSQL("ALTER TABLE recurring_expenses_v3 RENAME TO recurring_expenses")
                db.execSQL("CREATE INDEX index_recurring_expenses_userId ON recurring_expenses(userId)")
                db.execSQL("CREATE INDEX index_recurring_expenses_userId_isActive ON recurring_expenses(userId, isActive)")
                db.execSQL("CREATE INDEX index_recurring_expenses_userId_isSubscription ON recurring_expenses(userId, isSubscription)")
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `expense_categories` (
                        `id` TEXT NOT NULL,
                        `userId` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `classification` TEXT NOT NULL,
                        `isCustom` INTEGER NOT NULL,
                        `isArchived` INTEGER NOT NULL,
                        `syncStatus` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_categories_userId` ON `expense_categories` (`userId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_categories_userId_isArchived` ON `expense_categories` (`userId`, `isArchived`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `recurring_expenses` (
                        `id` TEXT NOT NULL,
                        `userId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `categoryId` TEXT NOT NULL,
                        `categoryName` TEXT NOT NULL,
                        `classification` TEXT NOT NULL,
                        `amountMinor` INTEGER NOT NULL,
                        `paymentMethod` TEXT NOT NULL,
                        `recurrence` TEXT NOT NULL,
                        `nextDueDate` TEXT NOT NULL,
                        `isActive` INTEGER NOT NULL,
                        `isSubscription` INTEGER NOT NULL,
                        `flag` TEXT NOT NULL,
                        `reminderDate` TEXT NOT NULL,
                        `syncStatus` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_expenses_userId` ON `recurring_expenses` (`userId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_expenses_userId_isActive` ON `recurring_expenses` (`userId`, `isActive`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_expenses_userId_isSubscription` ON `recurring_expenses` (`userId`, `isSubscription`)")
            }
        }
    }
}
