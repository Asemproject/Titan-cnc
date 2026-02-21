package com.titancnc.utils

import com.titancnc.data.model.MaterialDatabase
import com.titancnc.data.model.Tool
import kotlin.math.PI
import kotlin.math.roundToInt

/**
 * Chipload Calculator for CNC machining
 * 
 * Formulas:
 * - Feed Rate (mm/min) = Chip Load (mm/tooth) × Number of Flutes × RPM
 * - Chip Load (mm/tooth) = Feed Rate / (Number of Flutes × RPM)
 * - RPM = (Surface Speed × 1000) / (π × Tool Diameter)
 */

data class CuttingParameters(
    val feedRate: Int,           // mm/min
    val plungeRate: Int,         // mm/min
    val spindleSpeed: Int,       // RPM
    val stepdown: Float,         // mm
    val stepover: Float,         // mm (percentage of tool diameter)
    val chipLoad: Float,         // mm/tooth
    val materialRemovalRate: Float, // cm³/min
    val estimatedCuttingForce: Float, // N (approximate)
)

data class CalculationInput(
    val tool: Tool,
    val materialName: String,
    val desiredChipload: Float? = null,
    val desiredFeedRate: Int? = null,
    val desiredSpindleSpeed: Int? = null,
    val cutDepth: Float? = null,
    val cutWidth: Float? = null,
    val operationType: OperationType = OperationType.SLOTTING
)

enum class OperationType {
    SLOTTING,           // Full width cut
    PROFILING,          // Side milling
    POCKETING,          // Pocket clearing
    ADAPTIVE,           // High efficiency milling
    FINISHING,          // Light finish pass
    DRILLING,           // Plunge drilling
    RAMPING             // Ramping entry
}

class ChiploadCalculator {
    
    companion object {
        // Safety factors
        const val CONSERVATIVE_FACTOR = 0.7f
        const val AGGRESSIVE_FACTOR = 1.2f
        
        // Stepover recommendations (% of tool diameter)
        val STEPOVER_SLOTTING = 1.0f
        val STEPOVER_PROFILING = 0.4f
        val STEPOVER_POCKETING = 0.6f
        val STEPOVER_ADAPTIVE = 0.15f
        val STEPOVER_FINISHING = 0.15f
        
        // Stepdown recommendations (% of tool diameter)
        val STEPDOWN_ROUGHING = 1.0f
        val STEPDOWN_FINISHING = 0.2f
        val STEPDOWN_ADAPTIVE = 2.0f
    }
    
    /**
     * Calculate optimal cutting parameters based on input
     */
    fun calculate(input: CalculationInput): CuttingParameters {
        val material = MaterialDatabase.materials[input.materialName]
            ?: MaterialDatabase.materials["Soft Wood (Pine)"]!!
        
        val tool = input.tool
        
        // Determine chipload
        val chipLoad = input.desiredChipload 
            ?: material.recommendedChiploadRange.start + 
               (material.recommendedChiploadRange.endInclusive - material.recommendedChiploadRange.start) / 2
        
        // Calculate spindle speed from surface speed
        val surfaceSpeed = (material.surfaceSpeedRange.start + material.surfaceSpeedRange.endInclusive) / 2
        val calculatedRpm = ((surfaceSpeed * 1000) / (PI * tool.diameter)).roundToInt()
        
        // Use desired RPM if provided, otherwise use calculated
        val spindleSpeed = input.desiredSpindleSpeed 
            ?: calculatedRpm.coerceIn(1000, tool.maxSpindleSpeed)
        
        // Calculate feed rate
        val calculatedFeedRate = (chipLoad * tool.numberOfFlutes * spindleSpeed).roundToInt()
        
        // Use desired feed rate if provided, otherwise use calculated
        val feedRate = input.desiredFeedRate 
            ?: calculatedFeedRate.coerceIn(100, 10000)
        
        // Recalculate actual chipload based on final values
        val actualChipLoad = feedRate.toFloat() / (tool.numberOfFlutes * spindleSpeed)
        
        // Calculate stepdown based on operation type
        val stepdown = when (input.operationType) {
            OperationType.SLOTTING -> tool.diameter * STEPDOWN_ROUGHING
            OperationType.PROFILING -> tool.diameter * STEPDOWN_ROUGHING
            OperationType.POCKETING -> tool.diameter * STEPDOWN_ROUGHING
            OperationType.ADAPTIVE -> tool.diameter * STEPDOWN_ADAPTIVE
            OperationType.FINISHING -> tool.diameter * STEPDOWN_FINISHING
            OperationType.DRILLING -> tool.fluteLength * 0.8f
            OperationType.RAMPING -> tool.diameter * 0.5f
        }.coerceAtMost(tool.maxStepdown)
        
        // Calculate stepover based on operation type
        val stepover = when (input.operationType) {
            OperationType.SLOTTING -> STEPOVER_SLOTTING
            OperationType.PROFILING -> STEPOVER_PROFILING
            OperationType.POCKETING -> STEPOVER_POCKETING
            OperationType.ADAPTIVE -> STEPOVER_ADAPTIVE
            OperationType.FINISHING -> STEPOVER_FINISHING
            OperationType.DRILLING -> 1.0f
            OperationType.RAMPING -> STEPOVER_SLOTTING
        }
        
        // Calculate plunge rate (typically 50% of feed rate)
        val plungeRate = (feedRate * 0.5f).roundToInt().coerceAtMost(tool.recommendedPlungeRate)
        
        // Calculate material removal rate (MRR)
        // MRR = Feed Rate × Axial Depth × Radial Depth
        val actualCutDepth = input.cutDepth ?: stepdown
        val actualCutWidth = input.cutWidth ?: (tool.diameter * stepover)
        val mrr = (feedRate * actualCutDepth * actualCutWidth) / 1000f // cm³/min
        
        // Estimate cutting force (simplified)
        // F = Kc × h × b (where Kc is specific cutting force)
        val specificCuttingForce = when (material.hardness) {
            com.titancnc.data.model.Hardness.VERY_SOFT -> 300f
            com.titancnc.data.model.Hardness.SOFT -> 500f
            com.titancnc.data.model.Hardness.MEDIUM -> 1000f
            com.titancnc.data.model.Hardness.HARD -> 2000f
            com.titancnc.data.model.Hardness.VERY_HARD -> 3000f
        }
        val estimatedForce = specificCuttingForce * actualCutDepth * actualCutWidth / 100f
        
        return CuttingParameters(
            feedRate = feedRate,
            plungeRate = plungeRate,
            spindleSpeed = spindleSpeed,
            stepdown = actualCutDepth,
            stepover = stepover,
            chipLoad = actualChipLoad,
            materialRemovalRate = mrr,
            estimatedCuttingForce = estimatedForce
        )
    }
    
    /**
     * Calculate feed rate from chipload
     */
    fun calculateFeedRate(
        chipload: Float,
        numFlutes: Int,
        rpm: Int
    ): Int {
        return (chipload * numFlutes * rpm).roundToInt()
    }
    
    /**
     * Calculate chipload from feed rate
     */
    fun calculateChipload(
        feedRate: Int,
        numFlutes: Int,
        rpm: Int
    ): Float {
        return if (numFlutes > 0 && rpm > 0) {
            feedRate.toFloat() / (numFlutes * rpm)
        } else 0f
    }
    
    /**
     * Calculate spindle speed from surface speed
     */
    fun calculateRPM(
        surfaceSpeed: Float,  // m/min
        toolDiameter: Float   // mm
    ): Int {
        return ((surfaceSpeed * 1000) / (PI * toolDiameter)).roundToInt()
    }
    
    /**
     * Calculate surface speed from RPM
     */
    fun calculateSurfaceSpeed(
        rpm: Int,
        toolDiameter: Float  // mm
    ): Float {
        return (PI * toolDiameter * rpm / 1000).toFloat()
    }
    
    /**
     * Get recommended parameters for a specific material and tool combination
     */
    fun getRecommendedParameters(
        tool: Tool,
        materialName: String,
        operationType: OperationType = OperationType.SLOTTING
    ): CuttingParameters {
        return calculate(
            CalculationInput(
                tool = tool,
                materialName = materialName,
                operationType = operationType
            )
        )
    }
    
    /**
     * Get conservative parameters (safer for unknown conditions)
     */
    fun getConservativeParameters(
        tool: Tool,
        materialName: String,
        operationType: OperationType = OperationType.SLOTTING
    ): CuttingParameters {
        val params = getRecommendedParameters(tool, materialName, operationType)
        return params.copy(
            feedRate = (params.feedRate * CONSERVATIVE_FACTOR).roundToInt(),
            stepdown = params.stepdown * CONSERVATIVE_FACTOR,
            spindleSpeed = (params.spindleSpeed * CONSERVATIVE_FACTOR).roundToInt()
        )
    }
    
    /**
     * Get aggressive parameters (for high productivity)
     */
    fun getAggressiveParameters(
        tool: Tool,
        materialName: String,
        operationType: OperationType = OperationType.SLOTTING
    ): CuttingParameters {
        val params = getRecommendedParameters(tool, materialName, operationType)
        return params.copy(
            feedRate = (params.feedRate * AGGRESSIVE_FACTOR).roundToInt().coerceAtMost(10000),
            stepdown = (params.stepdown * AGGRESSIVE_FACTOR).coerceAtMost(tool.maxStepdown),
            spindleSpeed = params.spindleSpeed.coerceAtMost(tool.maxSpindleSpeed)
        )
    }
    
    /**
     * Validate if parameters are within safe limits
     */
    fun validateParameters(
        params: CuttingParameters,
        tool: Tool
    ): List<String> {
        val warnings = mutableListOf<String>()
        
        if (params.feedRate > tool.recommendedFeedRate * 1.5f) {
            warnings.add("Feed rate exceeds recommended by more than 50%")
        }
        
        if (params.spindleSpeed > tool.maxSpindleSpeed) {
            warnings.add("Spindle speed exceeds maximum rating")
        }
        
        if (params.stepdown > tool.maxStepdown) {
            warnings.add("Stepdown exceeds maximum recommended")
        }
        
        if (params.chipLoad > tool.recommendedChipLoad * 2f) {
            warnings.add("Chip load is very high - risk of tool breakage")
        }
        
        if (params.chipLoad < tool.recommendedChipLoad * 0.1f) {
            warnings.add("Chip load is very low - may cause rubbing")
        }
        
        return warnings
    }
    
    /**
     * Calculate estimated machining time
     */
    fun estimateMachiningTime(
        cutLength: Float,     // mm
        params: CuttingParameters
    ): Float {
        return cutLength / params.feedRate  // minutes
    }
    
    /**
     * Calculate tool life estimate (simplified)
     */
    fun estimateToolLife(
        tool: Tool,
        materialName: String,
        params: CuttingParameters
    ): Float {
        val material = MaterialDatabase.materials[materialName]
            ?: return 120f // Default 2 hours
        
        // Base tool life in minutes
        val baseLife = when (tool.material) {
            com.titancnc.data.model.Material.CARBIDE -> 120f
            com.titancnc.data.model.Material.HSS -> 30f
            com.titancnc.data.model.Material.COBALT -> 60f
            com.titancnc.data.model.Material.DIAMOND -> 480f
            com.titancnc.data.model.Material.CERAMIC -> 90f
        }
        
        // Adjust for material hardness
        val hardnessFactor = when (material.hardness) {
            com.titancnc.data.model.Hardness.VERY_SOFT -> 1.5f
            com.titancnc.data.model.Hardness.SOFT -> 1.2f
            com.titancnc.data.model.Hardness.MEDIUM -> 1.0f
            com.titancnc.data.model.Hardness.HARD -> 0.6f
            com.titancnc.data.model.Hardness.VERY_HARD -> 0.3f
        }
        
        // Adjust for chipload (optimal is around recommended)
        val chiploadFactor = if (params.chipLoad > tool.recommendedChipLoad * 1.5f) {
            0.7f
        } else if (params.chipLoad < tool.recommendedChipLoad * 0.5f) {
            0.8f
        } else {
            1.0f
        }
        
        return baseLife * hardnessFactor * chiploadFactor
    }
}
