package com.pfms.app.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_6_7_CRYPTO = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `crypto_trades` (
              `id` TEXT NOT NULL, `userId` TEXT NOT NULL,
              `asset` TEXT NOT NULL, `side` TEXT NOT NULL,
              `quantity` TEXT NOT NULL, `grossLkrMinor` INTEGER NOT NULL,
              `feeLkrMinor` INTEGER NOT NULL, `date` TEXT NOT NULL,
              `createdAt` INTEGER NOT NULL, `originalCurrency` TEXT NOT NULL,
              `originalGrossMinor` INTEGER NOT NULL, `originalFeeMinor` INTEGER NOT NULL,
              `exchangeRate` TEXT, `note` TEXT NOT NULL,
              `realizedLkrMinor` INTEGER, `syncStatus` TEXT NOT NULL,
              PRIMARY KEY(`userId`, `id`)
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_crypto_trades_userId_asset_date` ON `crypto_trades` (`userId`,`asset`,`date`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_crypto_trades_userId_syncStatus` ON `crypto_trades` (`userId`,`syncStatus`)")
    }
}
