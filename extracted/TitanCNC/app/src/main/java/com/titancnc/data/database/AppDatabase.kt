package com.titancnc.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.titancnc.data.model.Tool
import com.titancnc.data.model.ToolConverters
import com.titancnc.data.model.ToolType
import com.titancnc.data.model.Material
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [Tool::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(ToolConverters::class)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun toolDao(): ToolDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "titancnc_database"
                )
                    .addCallback(DatabaseCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
    
    private class DatabaseCallback : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                CoroutineScope(Dispatchers.IO).launch {
                    populateDefaultTools(database.toolDao())
                }
            }
        }
        
        private suspend fun populateDefaultTools(toolDao: ToolDao) {
            // Pre-populate with some common tools
            val defaultTools = listOf(
                // 1/8" End Mills
                Tool(
                    name = "1/8\" 2F Carbide EM",
                    description = "Standard 2-flute carbide end mill",
                    toolType = ToolType.END_MILL,
                    material = Material.CARBIDE,
                    diameter = 3.175f,
                    fluteLength = 12f,
                    overallLength = 38f,
                    shankDiameter = 3.175f,
                    numberOfFlutes = 2,
                    maxDepthOfCut = 6f,
                    maxStepdown = 1.5f,
                    recommendedFeedRate = 800,
                    recommendedPlungeRate = 400,
                    recommendedSpindleSpeed = 12000,
                    recommendedChipLoad = 0.033f,
                    manufacturer = "Generic"
                ),
                Tool(
                    name = "1/8\" 3F Carbide EM",
                    description = "3-flute carbide end mill for aluminum",
                    toolType = ToolType.END_MILL,
                    material = Material.CARBIDE,
                    diameter = 3.175f,
                    fluteLength = 12f,
                    overallLength = 38f,
                    shankDiameter = 3.175f,
                    numberOfFlutes = 3,
                    maxDepthOfCut = 6f,
                    maxStepdown = 1.5f,
                    recommendedFeedRate = 1200,
                    recommendedPlungeRate = 600,
                    recommendedSpindleSpeed = 12000,
                    recommendedChipLoad = 0.033f,
                    manufacturer = "Generic"
                ),
                // 1/4" End Mills
                Tool(
                    name = "1/4\" 2F Carbide EM",
                    description = "Standard 2-flute carbide end mill",
                    toolType = ToolType.END_MILL,
                    material = Material.CARBIDE,
                    diameter = 6.35f,
                    fluteLength = 19f,
                    overallLength = 50f,
                    shankDiameter = 6.35f,
                    numberOfFlutes = 2,
                    maxDepthOfCut = 9f,
                    maxStepdown = 3f,
                    recommendedFeedRate = 1200,
                    recommendedPlungeRate = 600,
                    recommendedSpindleSpeed = 10000,
                    recommendedChipLoad = 0.05f,
                    manufacturer = "Generic"
                ),
                // Ball Nose
                Tool(
                    name = "1/8\" Ball Nose",
                    description = "2-flute ball nose end mill for 3D finishing",
                    toolType = ToolType.BALL_NOSE,
                    material = Material.CARBIDE,
                    diameter = 3.175f,
                    fluteLength = 12f,
                    overallLength = 38f,
                    shankDiameter = 3.175f,
                    numberOfFlutes = 2,
                    cornerRadius = 1.5875f,
                    maxDepthOfCut = 6f,
                    maxStepdown = 0.5f,
                    recommendedFeedRate = 600,
                    recommendedPlungeRate = 300,
                    recommendedSpindleSpeed = 14000,
                    recommendedChipLoad = 0.021f,
                    manufacturer = "Generic"
                ),
                Tool(
                    name = "1/4\" Ball Nose",
                    description = "2-flute ball nose end mill for 3D finishing",
                    toolType = ToolType.BALL_NOSE,
                    material = Material.CARBIDE,
                    diameter = 6.35f,
                    fluteLength = 19f,
                    overallLength = 50f,
                    shankDiameter = 6.35f,
                    numberOfFlutes = 2,
                    cornerRadius = 3.175f,
                    maxDepthOfCut = 9f,
                    maxStepdown = 1f,
                    recommendedFeedRate = 1000,
                    recommendedPlungeRate = 500,
                    recommendedSpindleSpeed = 12000,
                    recommendedChipLoad = 0.042f,
                    manufacturer = "Generic"
                ),
                // V-Bits
                Tool(
                    name = "30° V-Bit",
                    description = "30-degree V-bit for engraving",
                    toolType = ToolType.V_BIT,
                    material = Material.CARBIDE,
                    diameter = 6f,
                    fluteLength = 10f,
                    overallLength = 40f,
                    shankDiameter = 6.35f,
                    numberOfFlutes = 2,
                    vBitAngle = 30f,
                    maxDepthOfCut = 5f,
                    maxStepdown = 1f,
                    recommendedFeedRate = 800,
                    recommendedPlungeRate = 400,
                    recommendedSpindleSpeed = 16000,
                    recommendedChipLoad = 0.025f,
                    manufacturer = "Generic"
                ),
                Tool(
                    name = "60° V-Bit",
                    description = "60-degree V-bit for engraving",
                    toolType = ToolType.V_BIT,
                    material = Material.CARBIDE,
                    diameter = 6f,
                    fluteLength = 10f,
                    overallLength = 40f,
                    shankDiameter = 6.35f,
                    numberOfFlutes = 2,
                    vBitAngle = 60f,
                    maxDepthOfCut = 5f,
                    maxStepdown = 1f,
                    recommendedFeedRate = 800,
                    recommendedPlungeRate = 400,
                    recommendedSpindleSpeed = 16000,
                    recommendedChipLoad = 0.025f,
                    manufacturer = "Generic"
                ),
                Tool(
                    name = "90° V-Bit",
                    description = "90-degree V-bit for chamfering",
                    toolType = ToolType.V_BIT,
                    material = Material.CARBIDE,
                    diameter = 6f,
                    fluteLength = 10f,
                    overallLength = 40f,
                    shankDiameter = 6.35f,
                    numberOfFlutes = 2,
                    vBitAngle = 90f,
                    maxDepthOfCut = 5f,
                    maxStepdown = 1f,
                    recommendedFeedRate = 800,
                    recommendedPlungeRate = 400,
                    recommendedSpindleSpeed = 16000,
                    recommendedChipLoad = 0.025f,
                    manufacturer = "Generic"
                ),
                // Drills
                Tool(
                    name = "1/8\" Drill",
                    description = "Carbide drill bit",
                    toolType = ToolType.DRILL,
                    material = Material.CARBIDE,
                    diameter = 3.175f,
                    fluteLength = 20f,
                    overallLength = 45f,
                    shankDiameter = 3.175f,
                    numberOfFlutes = 2,
                    maxDepthOfCut = 15f,
                    maxStepdown = 15f,
                    recommendedFeedRate = 400,
                    recommendedPlungeRate = 400,
                    recommendedSpindleSpeed = 10000,
                    recommendedChipLoad = 0.02f,
                    manufacturer = "Generic"
                ),
                // Engraving
                Tool(
                    name = "20° Engraving Bit",
                    description = "Fine engraving bit",
                    toolType = ToolType.ENGRAVING,
                    material = Material.CARBIDE,
                    diameter = 3.175f,
                    fluteLength = 8f,
                    overallLength = 38f,
                    shankDiameter = 3.175f,
                    numberOfFlutes = 1,
                    vBitAngle = 20f,
                    maxDepthOfCut = 2f,
                    maxStepdown = 0.5f,
                    recommendedFeedRate = 400,
                    recommendedPlungeRate = 200,
                    recommendedSpindleSpeed = 18000,
                    recommendedChipLoad = 0.022f,
                    manufacturer = "Generic"
                ),
                // Roughing
                Tool(
                    name = "1/4\" Roughing EM",
                    description = "Roughing end mill with chip breaker",
                    toolType = ToolType.ROUGHING_END_MILL,
                    material = Material.CARBIDE,
                    diameter = 6.35f,
                    fluteLength = 19f,
                    overallLength = 50f,
                    shankDiameter = 6.35f,
                    numberOfFlutes = 3,
                    maxDepthOfCut = 9f,
                    maxStepdown = 4f,
                    recommendedFeedRate = 1500,
                    recommendedPlungeRate = 750,
                    recommendedSpindleSpeed = 9000,
                    recommendedChipLoad = 0.056f,
                    manufacturer = "Generic"
                )
            )
            
            toolDao.insertTools(defaultTools)
        }
    }
}
