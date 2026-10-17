package com.pfms.app

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pfms.app.data.local.PfmsDatabase
import com.pfms.app.data.local.entity.ExpenseCategoryEntity
import com.pfms.app.data.local.entity.RecurringExpenseEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ExpenseRoomIntegrationTest {
    private lateinit var db: PfmsDatabase
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PfmsDatabase::class.java).build()
    }
    @After fun close() = db.close()

    private fun category(user: String) = ExpenseCategoryEntity("rent", user, "Rent", "COMMITTED", false, false)
    private fun template(id: String, user: String = "a") = RecurringExpenseEntity(
        id, user, "Streaming", "subscriptions", "Subscriptions", "COMMITTED", 10000,
        "CASH", "MONTHLY", "2020-01-01", true, true, "NONE", ""
    )

    @Test fun defaultCategoriesAreIsolatedAcrossAccounts() = runBlocking {
        val dao = db.expenseCategoryDao()
        dao.upsertCategories(listOf(category("a"), category("b")))
        assertEquals(1, dao.observeCategories("a").first().size)
        assertEquals(1, dao.observeCategories("b").first().size)
        dao.replaceForUser("a", emptyList())
        assertTrue(dao.observeCategories("a").first().isEmpty())
        assertEquals(1, dao.observeCategories("b").first().size)
    }

    @Test fun serverSnapshotRemovesDeletedRemindersWithoutTouchingOtherAccounts() = runBlocking {
        val dao = db.recurringExpenseDao()
        dao.upsertTemplates(listOf(template("deleted"), template("kept"), template("deleted", "b")))
        dao.replaceForUser("a", listOf(template("kept")))
        assertEquals(listOf("kept"), dao.getActiveTemplates("a").map { it.id })
        assertEquals(listOf("deleted"), dao.getActiveTemplates("b").map { it.id })
        dao.replaceForUser("a", emptyList())
        assertTrue(dao.getActiveTemplates("a").isEmpty())
    }

    @Test fun pausedAndCancelledSubscriptionsAreNotReturnedAsActiveReminders() = runBlocking {
        val dao = db.recurringExpenseDao()
        dao.upsertTemplates(listOf(template("active"), template("paused").copy(isActive = false),
            template("cancelled").copy(isActive = false, flag = "CANCEL")))
        assertEquals(listOf("active"), dao.getActiveTemplates("a").map { it.id })
    }

    @Test fun migrationPreservesCachedDataAndAllowsSharedIdsAcrossUsers() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "expense-migration-${UUID.randomUUID()}.db"
        val factory = FrameworkSQLiteOpenHelperFactory()
        fun helper(version: Int) = factory.create(SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name).callback(object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(db: SupportSQLiteDatabase) = PfmsDatabase.MIGRATION_1_2.migrate(db)
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
                    PfmsDatabase.MIGRATION_2_3.migrate(db)
            }).build())
        try {
            helper(2).use { old ->
                old.writableDatabase.execSQL("INSERT INTO expense_categories VALUES ('rent','a','Rent','COMMITTED',0,0,'SYNCED',1)")
                old.writableDatabase.execSQL("INSERT INTO recurring_expenses VALUES ('sub','a','Streaming','rent','Rent','COMMITTED',10000,'CASH','MONTHLY','2020-01-01',1,1,'NONE','','SYNCED',1)")
            }
            helper(3).use { migrated ->
                val sql = migrated.writableDatabase
                sql.execSQL("INSERT INTO expense_categories VALUES ('rent','b','Rent','COMMITTED',0,0,'SYNCED',1)")
                sql.execSQL("INSERT INTO recurring_expenses SELECT id,'b',title,categoryId,categoryName,classification,amountMinor,paymentMethod,recurrence,nextDueDate,isActive,isSubscription,flag,reminderDate,syncStatus,updatedAt FROM recurring_expenses WHERE userId='a'")
                sql.query("SELECT COUNT(*) FROM expense_categories").use { assertTrue(it.moveToFirst()); assertEquals(2, it.getInt(0)) }
                sql.query("SELECT COUNT(*) FROM recurring_expenses").use { assertTrue(it.moveToFirst()); assertEquals(2, it.getInt(0)) }
            }
        } finally {
            context.deleteDatabase(name)
        }
    }
}
