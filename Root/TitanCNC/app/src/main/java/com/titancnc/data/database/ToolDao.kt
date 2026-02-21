package com.titancnc.data.database

import androidx.room.*
import com.titancnc.data.model.Tool
import com.titancnc.data.model.ToolType
import kotlinx.coroutines.flow.Flow

@Dao
interface ToolDao {
    
    @Query("SELECT * FROM tools ORDER BY name ASC")
    fun getAllTools(): Flow<List<Tool>>
    
    @Query("SELECT * FROM tools WHERE isFavorite = 1 ORDER BY name ASC")
    fun getFavoriteTools(): Flow<List<Tool>>
    
    @Query("SELECT * FROM tools WHERE toolType = :type ORDER BY name ASC")
    fun getToolsByType(type: ToolType): Flow<List<Tool>>
    
    @Query("SELECT * FROM tools WHERE id = :id")
    suspend fun getToolById(id: Long): Tool?
    
    @Query("SELECT * FROM tools WHERE name LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%'")
    fun searchTools(query: String): Flow<List<Tool>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTool(tool: Tool): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTools(tools: List<Tool>)
    
    @Update
    suspend fun updateTool(tool: Tool)
    
    @Delete
    suspend fun deleteTool(tool: Tool)
    
    @Query("DELETE FROM tools WHERE id = :id")
    suspend fun deleteToolById(id: Long)
    
    @Query("SELECT COUNT(*) FROM tools")
    suspend fun getToolCount(): Int
    
    @Query("UPDATE tools SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: Long, isFavorite: Boolean)
}
