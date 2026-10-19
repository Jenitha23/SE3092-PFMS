package com.pfms.app.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Compatibility-safe Room database migrations for PFMS.
 *
 * Database Versions:
 * - Version 1: Initial schema (incomes, salary_templates)
 * - Savings Version 2 (historical): Added savings_goals, goal_transactions
 * - Expense Version 2 (historical): Added expense_categories, recurring_expenses (single PK `id`)
 * - Version 3 (historical main): Migrated expense_categories and recurring_expenses to composite PK (`userId`, `id`)
 * - Version 4 (merged final): Contains all 6 entities:
 *     1. IncomeEntity (incomes)
 *     2. SalaryTemplateEntity (salary_templates)
 *     3. SavingsGoalEntity (savings_goals)
 *     4. GoalTransactionEntity (goal_transactions)
 *     5. ExpenseCategoryEntity (expense_categories: composite PK `userId`, `id`)
 *     6. RecurringExpenseEntity (recurring_expenses: composite PK `userId`, `id`)
 */

internal fun createSavingsTables(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `savings_goals` (
            `id` TEXT NOT NULL,
            `userId` TEXT NOT NULL,
            `name` TEXT NOT NULL,
            `targetAmountMinor` INTEGER NOT NULL,
            `targetDate` TEXT NOT NULL,
            `openingSavedAmountMinor` INTEGER NOT NULL,
            `status` TEXT NOT NULL,
            `createdAt` INTEGER NOT NULL,
            `updatedAt` INTEGER NOT NULL,
            `syncStatus` TEXT NOT NULL,
            `isDeleted` INTEGER NOT NULL,
            PRIMARY KEY(`id`)
        )
        """.trimIndent()
    )

    db.execSQL("CREATE INDEX IF NOT EXISTS `index_savings_goals_userId` ON `savings_goals` (`userId`)")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_savings_goals_userId_status` ON `savings_goals` (`userId`, `status`)")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_savings_goals_userId_syncStatus` ON `savings_goals` (`userId`, `syncStatus`)")

    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `goal_transactions` (
            `id` TEXT NOT NULL,
            `goalId` TEXT NOT NULL,
            `userId` TEXT NOT NULL,
            `amountMinor` INTEGER NOT NULL,
            `transactionType` TEXT NOT NULL,
            `date` TEXT NOT NULL,
            `note` TEXT,
            `createdAt` INTEGER NOT NULL,
            `updatedAt` INTEGER NOT NULL,
            `syncStatus` TEXT NOT NULL,
            `isDeleted` INTEGER NOT NULL,
            PRIMARY KEY(`id`)
        )
        """.trimIndent()
    )

    db.execSQL("CREATE INDEX IF NOT EXISTS `index_goal_transactions_userId` ON `goal_transactions` (`userId`)")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_goal_transactions_goalId` ON `goal_transactions` (`goalId`)")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_goal_transactions_userId_goalId` ON `goal_transactions` (`userId`, `goalId`)")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_goal_transactions_userId_date` ON `goal_transactions` (`userId`, `date`)")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_goal_transactions_userId_syncStatus` ON `goal_transactions` (`userId`, `syncStatus`)")
}

internal fun tableExists(db: SupportSQLiteDatabase, tableName: String): Boolean {
    return try {
        val cursor = db.query(
            "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = '$tableName'"
        )
        cursor?.use { it.moveToFirst() } ?: false
    } catch (_: Throwable) {
        false
    }
}

internal fun isCompositePk(db: SupportSQLiteDatabase, tableName: String): Boolean {
    return try {
        val cursor = db.query("PRAGMA table_info(`$tableName`)")
        cursor?.use { c ->
            val pkIndex = c.getColumnIndex("pk")
            if (pkIndex == -1) return false
            var pkCount = 0
            while (c.moveToNext()) {
                if (c.getInt(pkIndex) > 0) {
                    pkCount++
                }
            }
            pkCount > 1
        } ?: false
    } catch (_: Throwable) {
        false
    }
}

internal fun createOrReconcileExpenseTables(db: SupportSQLiteDatabase) {
    // 1. expense_categories
    if (!tableExists(db, "expense_categories")) {
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
                PRIMARY KEY(`userId`, `id`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_categories_userId` ON `expense_categories` (`userId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_categories_userId_isArchived` ON `expense_categories` (`userId`, `isArchived`)")
    } else if (!isCompositePk(db, "expense_categories")) {
        db.execSQL(
            """
            CREATE TABLE `expense_categories_v4` (
                `id` TEXT NOT NULL,
                `userId` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `classification` TEXT NOT NULL,
                `isCustom` INTEGER NOT NULL,
                `isArchived` INTEGER NOT NULL,
                `syncStatus` TEXT NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`userId`, `id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `expense_categories_v4` (`id`, `userId`, `name`, `classification`, `isCustom`, `isArchived`, `syncStatus`, `updatedAt`)
            SELECT `id`, `userId`, `name`, `classification`, `isCustom`, `isArchived`, `syncStatus`, `updatedAt` FROM `expense_categories`
            """.trimIndent()
        )
        db.execSQL("DROP TABLE `expense_categories`")
        db.execSQL("ALTER TABLE `expense_categories_v4` RENAME TO `expense_categories`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_categories_userId` ON `expense_categories` (`userId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_categories_userId_isArchived` ON `expense_categories` (`userId`, `isArchived`)")
    } else {
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_categories_userId` ON `expense_categories` (`userId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_categories_userId_isArchived` ON `expense_categories` (`userId`, `isArchived`)")
    }

    // 2. recurring_expenses
    if (!tableExists(db, "recurring_expenses")) {
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
                PRIMARY KEY(`userId`, `id`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_expenses_userId` ON `recurring_expenses` (`userId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_expenses_userId_isActive` ON `recurring_expenses` (`userId`, `isActive`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_expenses_userId_isSubscription` ON `recurring_expenses` (`userId`, `isSubscription`)")
    } else if (!isCompositePk(db, "recurring_expenses")) {
        db.execSQL(
            """
            CREATE TABLE `recurring_expenses_v4` (
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
                PRIMARY KEY(`userId`, `id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `recurring_expenses_v4` (`id`, `userId`, `title`, `categoryId`, `categoryName`, `classification`, `amountMinor`, `paymentMethod`, `recurrence`, `nextDueDate`, `isActive`, `isSubscription`, `flag`, `reminderDate`, `syncStatus`, `updatedAt`)
            SELECT `id`, `userId`, `title`, `categoryId`, `categoryName`, `classification`, `amountMinor`, `paymentMethod`, `recurrence`, `nextDueDate`, `isActive`, `isSubscription`, `flag`, `reminderDate`, `syncStatus`, `updatedAt` FROM `recurring_expenses`
            """.trimIndent()
        )
        db.execSQL("DROP TABLE `recurring_expenses`")
        db.execSQL("ALTER TABLE `recurring_expenses_v4` RENAME TO `recurring_expenses`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_expenses_userId` ON `recurring_expenses` (`userId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_expenses_userId_isActive` ON `recurring_expenses` (`userId`, `isActive`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_expenses_userId_isSubscription` ON `recurring_expenses` (`userId`, `isSubscription`)")
    } else {
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_expenses_userId` ON `recurring_expenses` (`userId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_expenses_userId_isActive` ON `recurring_expenses` (`userId`, `isActive`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_expenses_userId_isSubscription` ON `recurring_expenses` (`userId`, `isSubscription`)")
    }
}

/**
 * The `expenses` table (offline-first expense storage, added on main). Idempotent, so it is safe on
 * every upgrade path. Without it a device upgrading from an older version would fail Room's schema
 * validation, because the entity exists but the table was never created.
 */
internal fun createExpensesTable(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `expenses` (
            `userId` TEXT NOT NULL,
            `id` TEXT NOT NULL,
            `amountMinor` INTEGER NOT NULL,
            `currency` TEXT NOT NULL,
            `categoryId` TEXT NOT NULL,
            `categoryName` TEXT NOT NULL,
            `classification` TEXT NOT NULL,
            `paymentMethod` TEXT NOT NULL,
            `date` TEXT NOT NULL,
            `merchant` TEXT NOT NULL,
            `note` TEXT NOT NULL,
            `createdAt` INTEGER NOT NULL,
            `updatedAt` INTEGER NOT NULL,
            `syncStatus` TEXT NOT NULL,
            `isDeleted` INTEGER NOT NULL,
            `revision` TEXT NOT NULL,
            `allowDuplicate` INTEGER NOT NULL,
            `needsConfirmation` INTEGER NOT NULL,
            PRIMARY KEY(`userId`, `id`)
        )
        """.trimIndent()
    )
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_userId` ON `expenses` (`userId`)")
}

/** Everything version 4 needs: savings tables, expense categories/recurring, and expenses. */
internal fun upgradeToVersion4(db: SupportSQLiteDatabase) {
    createSavingsTables(db)
    createOrReconcileExpenseTables(db)
    createExpensesTable(db)
}

/**
 * Migration 1 -> 4: Direct upgrade from version 1 (incomes, salary_templates) to version 4.
 * Creates savings tables and expense tables with final composite primary keys.
 */
val MIGRATION_1_4 = object : Migration(1, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        upgradeToVersion4(db)
    }
}

/**
 * Migration 2 -> 4: Compatibility-safe upgrade from either historical version 2:
 * - Savings branch v2 (savings tables exist, expense tables do not exist)
 * - Expense branch v2 (expense tables exist with single PK, savings tables do not exist)
 *
 * Dynamically reconciles both without data loss.
 */
val MIGRATION_2_4 = object : Migration(2, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        upgradeToVersion4(db)
    }
}

/**
 * Migration 3 -> 4: Upgrade from main branch version 3:
 * - Expense tables exist with composite PK
 * - Adds savings tables and all indices without altering existing expense/income data.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        upgradeToVersion4(db)
    }
}

/**
 * Historical expense migration 1 -> 2 (retained for backward compatibility and tests).
 */
val HISTORICAL_EXPENSE_MIGRATION_1_2 = object : Migration(1, 2) {
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

/**
 * Historical expense migration 2 -> 3 (retained for backward compatibility and tests).
 */
val HISTORICAL_EXPENSE_MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE expense_categories_v3 (
            id TEXT NOT NULL, userId TEXT NOT NULL, name TEXT NOT NULL,
            classification TEXT NOT NULL, isCustom INTEGER NOT NULL,
            isArchived INTEGER NOT NULL, syncStatus TEXT NOT NULL, updatedAt INTEGER NOT NULL,
            PRIMARY KEY(userId, id))"""
        )
        db.execSQL("INSERT INTO expense_categories_v3 SELECT * FROM expense_categories")
        db.execSQL("DROP TABLE expense_categories")
        db.execSQL("ALTER TABLE expense_categories_v3 RENAME TO expense_categories")
        db.execSQL("CREATE INDEX index_expense_categories_userId ON expense_categories(userId)")
        db.execSQL("CREATE INDEX index_expense_categories_userId_isArchived ON expense_categories(userId, isArchived)")
        db.execSQL(
            """CREATE TABLE recurring_expenses_v3 (
            id TEXT NOT NULL, userId TEXT NOT NULL, title TEXT NOT NULL,
            categoryId TEXT NOT NULL, categoryName TEXT NOT NULL, classification TEXT NOT NULL,
            amountMinor INTEGER NOT NULL, paymentMethod TEXT NOT NULL, recurrence TEXT NOT NULL,
            nextDueDate TEXT NOT NULL, isActive INTEGER NOT NULL, isSubscription INTEGER NOT NULL,
            flag TEXT NOT NULL, reminderDate TEXT NOT NULL, syncStatus TEXT NOT NULL,
            updatedAt INTEGER NOT NULL, PRIMARY KEY(userId, id))"""
        )
        db.execSQL("INSERT INTO recurring_expenses_v3 SELECT * FROM recurring_expenses")
        db.execSQL("DROP TABLE recurring_expenses")
        db.execSQL("ALTER TABLE recurring_expenses_v3 RENAME TO recurring_expenses")
        db.execSQL("CREATE INDEX index_recurring_expenses_userId ON recurring_expenses(userId)")
        db.execSQL("CREATE INDEX index_recurring_expenses_userId_isActive ON recurring_expenses(userId, isActive)")
        db.execSQL("CREATE INDEX index_recurring_expenses_userId_isSubscription ON recurring_expenses(userId, isSubscription)")
    }
}

/**
 * Historical savings migration 1 -> 2 (retained for backward compatibility).
 */
val HISTORICAL_SAVINGS_MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        createSavingsTables(db)
    }
}

val MIGRATION_1_2 = HISTORICAL_EXPENSE_MIGRATION_1_2
val MIGRATION_2_3 = HISTORICAL_EXPENSE_MIGRATION_2_3
