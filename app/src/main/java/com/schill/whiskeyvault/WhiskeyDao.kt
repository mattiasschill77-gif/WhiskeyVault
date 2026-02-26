package com.schill.whiskeyvault

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WhiskeyDao {
    @Query("SELECT * FROM whiskey_table ORDER BY name ASC")
    fun getAllWhiskeys(): Flow<List<Whiskey>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWhiskey(whiskey: Whiskey)

    @Delete
    suspend fun deleteWhiskey(whiskey: Whiskey)

    @Update
    suspend fun updateWhiskey(whiskey: Whiskey)
}