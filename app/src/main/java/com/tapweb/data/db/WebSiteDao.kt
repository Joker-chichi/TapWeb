package com.tapweb.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WebSiteDao {
    @Query("SELECT * FROM websites ORDER BY sortOrder ASC, createdAt DESC")
    fun getAll(): Flow<List<WebSiteEntity>>

    @Query("SELECT * FROM websites WHERE id = :id")
    suspend fun getById(id: Long): WebSiteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(website: WebSiteEntity): Long

    @Update
    suspend fun update(website: WebSiteEntity)

    @Delete
    suspend fun delete(website: WebSiteEntity)

    @Query("DELETE FROM websites WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT MAX(sortOrder) FROM websites")
    suspend fun getMaxSortOrder(): Int?

    @Query("SELECT IFNULL(MAX(id), 0) + 1 FROM websites")
    suspend fun getNextId(): Long
}
