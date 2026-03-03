package com.rrimal.notetaker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TodoKeywordsDao {
    @Query("SELECT * FROM todo_keywords_config WHERE id = 0")
    suspend fun getConfig(): TodoKeywordsConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfig(config: TodoKeywordsConfigEntity)
}
