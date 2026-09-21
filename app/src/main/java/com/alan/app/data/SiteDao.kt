package com.alan.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SiteDao {
    @Query("SELECT * FROM sites ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Site>>

    @Query("SELECT * FROM sites ORDER BY createdAt DESC")
    suspend fun getAll(): List<Site>

    @Query("SELECT * FROM sites WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): Site?

    @Query("SELECT * FROM sites WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<Site?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(site: Site)

    @Query("DELETE FROM sites WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE sites SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: SiteStatus)

    @Query("UPDATE sites SET requestCount = requestCount + 1 WHERE id = :id")
    suspend fun incrementRequestCount(id: String)

    @Query("UPDATE sites SET uptimeStartMs = :ts WHERE id = :id")
    suspend fun updateUptimeStart(id: String, ts: Long?)

    @Query("UPDATE sites SET lastHealth = :health WHERE id = :id")
    suspend fun updateLastHealth(id: String, health: String)
}
