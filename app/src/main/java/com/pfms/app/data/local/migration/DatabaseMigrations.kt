package com.pfms.app.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room database migration definitions.
 * MIGRATION_1_2 adds savings_goals and goal_transactions tables along with all required indexes.
 * Preserves all existing data (incomes, salary_templates) without any drops or destructive changes.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
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
}
