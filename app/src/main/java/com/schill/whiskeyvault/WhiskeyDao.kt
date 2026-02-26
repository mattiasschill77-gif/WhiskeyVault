package com.schill.whiskeyvault

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WhiskeyDao {

    @Query("SELECT * FROM whiskey_table ORDER BY name ASC")
    fun getAllWhiskeys(): Flow<List<Whiskey>>

    // Den här raden är "magin" för betygen:
    // Om flaskan redan finns (samma ID), ersätt den med den nya infon (t.ex. nytt betyg)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWhiskey(whiskey: Whiskey)

    @Delete
    suspend fun deleteWhiskey(whiskey: Whiskey)

    @Query("SELECT * FROM whiskey_table WHERE id = :id")
    suspend fun getWhiskeyById(id: Int): Whiskey?

    // Bonus: Om du vill kunna se dina topplistor senare
    @Query("SELECT * FROM whiskey_table ORDER BY rating DESC LIMIT 5")
    fun getTopRated(): Flow<List<Whiskey>>
}