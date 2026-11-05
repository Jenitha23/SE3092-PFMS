package com.pfms.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pfms.app.data.local.entity.CryptoTradeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CryptoTradeDao {
    @Query("SELECT * FROM crypto_trades WHERE userId=:uid ORDER BY date,createdAt,id")
    suspend fun all(uid: String): List<CryptoTradeEntity>

    @Query("SELECT * FROM crypto_trades WHERE userId=:uid AND id=:id LIMIT 1")
    suspend fun byId(uid: String, id: String): CryptoTradeEntity?

    @Query("SELECT * FROM crypto_trades WHERE userId=:uid ORDER BY date DESC,createdAt DESC")
    fun observe(uid: String): Flow<List<CryptoTradeEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(row: CryptoTradeEntity)

    @Query("SELECT * FROM crypto_trades WHERE userId=:uid AND syncStatus!='SYNCED' ORDER BY createdAt")
    suspend fun pending(uid: String): List<CryptoTradeEntity>

    @Query("UPDATE crypto_trades SET syncStatus='SYNCED' WHERE userId=:uid AND id=:id AND syncStatus='PENDING_INSERT'")
    suspend fun markSynced(uid: String, id: String)
}
