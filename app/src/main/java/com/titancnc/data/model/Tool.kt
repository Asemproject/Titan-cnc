package com.titancnc.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters

enum class ToolType {
    END_MILL,
    BALL_NOSE,
    V_BIT,
    DRILL,
    CHAMFER,
    THREAD_MILL,
    TAPERED,
    ENGRAVING,
    ROUGHING_END_MILL,
    FACING_MILL
}

enum class Material {
    CARBIDE,
    HSS,
    COBALT,
    DIAMOND,
    CERAMIC
}

@Entity(tableName = "tools")
@TypeConverters(ToolConverters::class)
data class Tool(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val toolType: ToolType,
    val material: Material = Material.CARBIDE,
    
    // Geometry
    val diameter: Float,           // mm
    val fluteLength: Float,        // mm
    val overallLength: Float,      // mm
    val shankDiameter: Float,      // mm
    val numberOfFlutes: Int = 2,
    
    // V-bit specific
    val vBitAngle: Float? = null,  // degrees, for V-bits
    
    // Ball nose specific
    val cornerRadius: Float? = null, // mm, for ball nose
    
    // Cutting parameters
    val maxDepthOfCut: Float,      // mm
    val maxStepdown: Float,        // mm
    val recommendedFeedRate: Int,  // mm/min
    val recommendedPlungeRate: Int,// mm/min
    val recommendedSpindleSpeed: Int, // RPM
    val maxSpindleSpeed: Int = 24000,
    
    // Chipload calculation
    val recommendedChipLoad: Float, // mm/tooth
    
    // Additional info
    val manufacturer: String = "",
    val partNumber: String = "",
    val coolantRecommended: Boolean = false,
    val notes: String = "",
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

// Material properties for chipload calculations
data class MaterialProperties(
    val name: String,
    val hardness: Hardness,  // For reference
    val recommendedChiploadRange: ClosedFloatingPointRange<Float>, // mm/tooth
    val surfaceSpeedRange: ClosedFloatingPointRange<Float> // m/min
)

enum class Hardness {
    VERY_SOFT, SOFT, MEDIUM, HARD, VERY_HARD
}

// Predefined material properties
object MaterialDatabase {
    val materials = mapOf(
        // Soft materials
        "Soft Wood (Pine)" to MaterialProperties(
            name = "Soft Wood (Pine)",
            hardness = Hardness.VERY_SOFT,
            recommendedChiploadRange = 0.05f..0.15f,
            surfaceSpeedRange = 300f..500f
        ),
        "Hard Wood (Oak)" to MaterialProperties(
            name = "Hard Wood (Oak)",
            hardness = Hardness.SOFT,
            recommendedChiploadRange = 0.03f..0.10f,
            surfaceSpeedRange = 200f..400f
        ),
        "MDF" to MaterialProperties(
            name = "MDF",
            hardness = Hardness.VERY_SOFT,
            recommendedChiploadRange = 0.05f..0.12f,
            surfaceSpeedRange = 250f..450f
        ),
        "Plywood" to MaterialProperties(
            name = "Plywood",
            hardness = Hardness.SOFT,
            recommendedChiploadRange = 0.04f..0.10f,
            surfaceSpeedRange = 200f..350f
        ),
        
        // Plastics
        "Acrylic" to MaterialProperties(
            name = "Acrylic",
            hardness = Hardness.SOFT,
            recommendedChiploadRange = 0.03f..0.08f,
            surfaceSpeedRange = 150f..300f
        ),
        "ABS" to MaterialProperties(
            name = "ABS",
            hardness = Hardness.SOFT,
            recommendedChiploadRange = 0.04f..0.10f,
            surfaceSpeedRange = 150f..300f
        ),
        "Delrin" to MaterialProperties(
            name = "Delrin",
            hardness = Hardness.SOFT,
            recommendedChiploadRange = 0.05f..0.12f,
            surfaceSpeedRange = 100f..250f
        ),
        
        // Aluminum
        "Aluminum 6061" to MaterialProperties(
            name = "Aluminum 6061",
            hardness = Hardness.MEDIUM,
            recommendedChiploadRange = 0.01f..0.05f,
            surfaceSpeedRange = 150f..300f
        ),
        "Aluminum 7075" to MaterialProperties(
            name = "Aluminum 7075",
            hardness = Hardness.MEDIUM,
            recommendedChiploadRange = 0.01f..0.04f,
            surfaceSpeedRange = 120f..250f
        ),
        
        // Steels
        "Mild Steel" to MaterialProperties(
            name = "Mild Steel",
            hardness = Hardness.HARD,
            recommendedChiploadRange = 0.005f..0.02f,
            surfaceSpeedRange = 50f..100f
        ),
        "Stainless Steel 304" to MaterialProperties(
            name = "Stainless Steel 304",
            hardness = Hardness.VERY_HARD,
            recommendedChiploadRange = 0.003f..0.015f,
            surfaceSpeedRange = 30f..60f
        ),
        "Tool Steel" to MaterialProperties(
            name = "Tool Steel",
            hardness = Hardness.VERY_HARD,
            recommendedChiploadRange = 0.002f..0.01f,
            surfaceSpeedRange = 20f..50f
        ),
        
        // Others
        "Brass" to MaterialProperties(
            name = "Brass",
            hardness = Hardness.MEDIUM,
            recommendedChiploadRange = 0.02f..0.06f,
            surfaceSpeedRange = 100f..200f
        ),
        "Copper" to MaterialProperties(
            name = "Copper",
            hardness = Hardness.MEDIUM,
            recommendedChiploadRange = 0.02f..0.05f,
            surfaceSpeedRange = 80f..150f
        ),
        "Foam" to MaterialProperties(
            name = "Foam",
            hardness = Hardness.VERY_SOFT,
            recommendedChiploadRange = 0.10f..0.30f,
            surfaceSpeedRange = 400f..800f
        ),
        "Carbon Fiber" to MaterialProperties(
            name = "Carbon Fiber",
            hardness = Hardness.HARD,
            recommendedChiploadRange = 0.005f..0.02f,
            surfaceSpeedRange = 100f..200f
        )
    )
}

class ToolConverters {
    @TypeConverter
    fun fromToolType(value: ToolType): String = value.name
    
    @TypeConverter
    fun toToolType(value: String): ToolType = ToolType.valueOf(value)
    
    @TypeConverter
    fun fromMaterial(value: Material): String = value.name
    
    @TypeConverter
    fun toMaterial(value: String): Material = Material.valueOf(value)
}
