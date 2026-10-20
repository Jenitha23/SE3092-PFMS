package com.pfms.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pfms.app.data.local.PfmsDatabase
import com.pfms.app.data.local.entity.IncomeEntity
import com.pfms.app.data.local.entity.SyncStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class MigrationFiveSixTest {
    @Test fun realVersionFiveIncomeSchemaPreservesPendingDataAndAllowsUserScopedIds() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-five-six-${UUID.randomUUID()}.db"
        val db = Room.databaseBuilder(context, PfmsDatabase::class.java, name).build()
        db.openHelper.writableDatabase // Create the other feature tables unchanged.
        db.close()
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, 0).use {
            it.execSQL("DROP TABLE incomes")
            it.execSQL("""CREATE TABLE incomes (
                id TEXT NOT NULL PRIMARY KEY, userId TEXT NOT NULL, amountMinor INTEGER NOT NULL,
                currency TEXT NOT NULL, source TEXT NOT NULL, receivedDate TEXT NOT NULL, note TEXT NOT NULL,
                amountLkrMinor INTEGER, exchangeRate TEXT, exchangeRateDate TEXT, paymentStatus TEXT NOT NULL,
                projectName TEXT, cryptoType TEXT, cryptoAsset TEXT, createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL, syncStatus TEXT NOT NULL, isDeleted INTEGER NOT NULL)""")
            it.execSQL("""INSERT INTO incomes VALUES ('same-id','a',10000,'USD','Salary','2026-10-20','Offline',
                3000000,'300.00','2026-10-20','Received',NULL,NULL,NULL,100,200,'PENDING_UPDATE',0)""")
            it.version = 5
        }
        val upgraded = Room.databaseBuilder(context, PfmsDatabase::class.java, name)
            .addMigrations(PfmsDatabase.MIGRATION_5_6).build()
        try {
            val row = upgraded.incomeDao().getIncomeById("a", "same-id")!!
            assertEquals(10000L, row.amountMinor)
            assertEquals(3000000L, row.amountLkrMinor)
            assertEquals(SyncStatus.PENDING_UPDATE, row.syncStatus)
            assertEquals("", row.revision)
            upgraded.incomeDao().upsertIncome(IncomeEntity("same-id", "b", amountMinor = 500))
            assertEquals(10000L, upgraded.incomeDao().getIncomeById("a", "same-id")!!.amountMinor)
            assertEquals(500L, upgraded.incomeDao().getIncomeById("b", "same-id")!!.amountMinor)
            assertEquals(6, upgraded.openHelper.writableDatabase.version)
        } finally { upgraded.close(); context.deleteDatabase(name) }
    }
}
