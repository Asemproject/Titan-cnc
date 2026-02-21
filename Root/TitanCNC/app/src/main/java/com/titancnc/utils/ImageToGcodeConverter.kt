package com.titancnc.utils

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.util.*
import kotlin.math.*

/**
 * Image-to-GCode Converter using OpenCV
 * 
 * Features:
 * - Raster-to-GCode conversion
 * - Dithering algorithms (Floyd-Steinberg, Atkinson, Jarvis-Judice-Ninke)
 * - Thresholding (Binary, Adaptive, Otsu)
 * - Power Scaling (S-min/max for laser)
 * - Scan Direction (Horizontal, Vertical, Diagonal)
 * - Real-time path preview
 */

sealed class DitheringAlgorithm {
    data object None : DitheringAlgorithm()
    data object FloydSteinberg : DitheringAlgorithm()
    data object Atkinson : DitheringAlgorithm()
    data object JarvisJudiceNinke : DitheringAlgorithm()
    data object Stucki : DitheringAlgorithm()
    data object Bayer : DitheringAlgorithm()
}

sealed class ThresholdMethod {
    data object Binary : ThresholdMethod()
    data object Adaptive : ThresholdMethod()
    data object Otsu : ThresholdMethod()
    data object Triangle : ThresholdMethod()
}

sealed class ScanDirection {
    data object Horizontal : ScanDirection()
    data object Vertical : ScanDirection()
    data object Diagonal : ScanDirection()
    data object Hilbert : ScanDirection()
}

enum class EngravingMode {
    LASER_RASTER,      // Laser raster engraving
    LASER_VECTOR,      // Laser vector tracing
    CNC_V_CARVE,       // V-bit carving
    CNC_RELIEF,        // 3D relief
    CNC_POCKET         // Pocket clearing
}

data class ConversionSettings(
    val targetWidth: Float = 100f,           // mm
    val targetHeight: Float = 100f,          // mm
    val resolution: Float = 0.1f,            // mm per pixel (smaller = higher res)
    val feedRate: Int = 1000,                // mm/min
    val rapidFeed: Int = 3000,               // mm/min for non-cutting moves
    val zSafe: Float = 5f,                   // Safe height for travel
    val zWork: Float = 0f,                   // Working depth/height
    val maxDepth: Float = 3f,                // Maximum cut depth (for CNC)
    val stepDown: Float = 0.5f,              // Depth per pass (for CNC)
    val sMin: Int = 0,                       // Min spindle/laser power (S value)
    val sMax: Int = 1000,                    // Max spindle/laser power (S value)
    val dithering: DitheringAlgorithm = DitheringAlgorithm.FloydSteinberg,
    val thresholdMethod: ThresholdMethod = ThresholdMethod.Otsu,
    val thresholdValue: Int = 128,           // 0-255 for binary threshold
    val scanDirection: ScanDirection = ScanDirection.Horizontal,
    val invertImage: Boolean = false,
    val engravingMode: EngravingMode = EngravingMode.LASER_RASTER,
    val beamDiameter: Float = 0.1f,          // Laser spot size mm
    val angle: Float = 0f,                   // Scan angle in degrees
    val overscan: Float = 0f,                // mm to overscan
    val bidirectional: Boolean = true,       // Scan both directions
    val trimWhite: Boolean = true,           // Skip white areas
    val gamma: Float = 1.0f,                 // Gamma correction
    val contrast: Float = 1.0f,              // Contrast adjustment
    val brightness: Float = 0f               // Brightness adjustment (-255 to 255)
)

data class ConversionProgress(
    val stage: String,
    val percentComplete: Float,
    val linesGenerated: Int,
    val estimatedTimeSeconds: Long
)

data class PreviewPath(
    val paths: List<PathSegment>,
    val bounds: RectF,
    val totalDistance: Float,
    val estimatedTime: Long
)

data class PathSegment(
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val power: Int,  // 0-1000 for laser power
    val isRapid: Boolean = false
)

class ImageToGcodeConverter {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    companion object {
        init {
            System.loadLibrary("opencv_java4")
        }
        
        const val MM_PER_INCH = 25.4f
    }

    /**
     * Convert bitmap image to G-code
     */
    suspend fun convert(
        bitmap: Bitmap,
        settings: ConversionSettings,
        onProgress: (ConversionProgress) -> Unit = {}
    ): Result<List<String>> = withContext(Dispatchers.Default) {
        try {
            onProgress(ConversionProgress("Loading image...", 5f, 0, 0))
            
            // Convert bitmap to OpenCV Mat
            val mat = Mat()
            Utils.bitmapToMat(bitmap, mat)
            
            onProgress(ConversionProgress("Preprocessing...", 10f, 0, 0))
            
            // Preprocess image
            val processedMat = preprocessImage(mat, settings)
            
            onProgress(ConversionProgress("Generating G-code...", 30f, 0, 0))
            
            // Generate G-code based on mode
            val gcode = when (settings.engravingMode) {
                EngravingMode.LASER_RASTER -> generateLaserRasterGcode(processedMat, settings, onProgress)
                EngravingMode.LASER_VECTOR -> generateLaserVectorGcode(processedMat, settings, onProgress)
                EngravingMode.CNC_V_CARVE -> generateVCarveGcode(processedMat, settings, onProgress)
                EngravingMode.CNC_RELIEF -> generateReliefGcode(processedMat, settings, onProgress)
                EngravingMode.CNC_POCKET -> generatePocketGcode(processedMat, settings, onProgress)
            }
            
            // Cleanup
            mat.release()
            processedMat.release()
            
            onProgress(ConversionProgress("Complete!", 100f, gcode.size, 0))
            
            Result.success(gcode)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Generate preview path for visualization
     */
    suspend fun generatePreview(
        bitmap: Bitmap,
        settings: ConversionSettings
    ): Result<PreviewPath> = withContext(Dispatchers.Default) {
        try {
            val mat = Mat()
            Utils.bitmapToMat(bitmap, mat)
            val processedMat = preprocessImage(mat, settings)
            
            val paths = mutableListOf<PathSegment>()
            val width = processedMat.cols()
            val height = processedMat.rows()
            val pixelSize = settings.resolution
            
            when (settings.scanDirection) {
                ScanDirection.Horizontal -> {
                    for (y in 0 until height) {
                        val yPos = (height - 1 - y) * pixelSize
                        var xStart = 0
                        var inSegment = false
                        
                        for (x in 0 until width) {
                            val intensity = processedMat.get(y, x)[0].toInt()
                            val hasPower = intensity < 255
                            
                            if (hasPower && !inSegment) {
                                xStart = x
                                inSegment = true
                            } else if ((!hasPower || x == width - 1) && inSegment) {
                                val xEnd = if (hasPower && x == width - 1) x + 1 else x
                                val power = if (settings.dithering == DitheringAlgorithm.None) {
                                    ((255 - processedMat.get(y, (xStart + xEnd) / 2)[0]) / 255f * settings.sMax).toInt()
                                } else settings.sMax
                                
                                paths.add(PathSegment(
                                    xStart * pixelSize, yPos,
                                    xEnd * pixelSize, yPos,
                                    power.coerceIn(settings.sMin, settings.sMax)
                                ))
                                inSegment = false
                            }
                        }
                    }
                }
                ScanDirection.Vertical -> {
                    for (x in 0 until width) {
                        val xPos = x * pixelSize
                        var yStart = 0
                        var inSegment = false
                        
                        for (y in 0 until height) {
                            val intensity = processedMat.get(y, x)[0].toInt()
                            val hasPower = intensity < 255
                            
                            if (hasPower && !inSegment) {
                                yStart = y
                                inSegment = true
                            } else if ((!hasPower || y == height - 1) && inSegment) {
                                val yEnd = if (hasPower && y == height - 1) y + 1 else y
                                val power = ((255 - processedMat.get((yStart + yEnd) / 2, x)[0]) / 255f * settings.sMax).toInt()
                                
                                paths.add(PathSegment(
                                    xPos, (height - 1 - yStart) * pixelSize,
                                    xPos, (height - 1 - yEnd) * pixelSize,
                                    power.coerceIn(settings.sMin, settings.sMax)
                                ))
                                inSegment = false
                            }
                        }
                    }
                }
                else -> { /* Other directions */ }
            }
            
            val bounds = RectF(0f, 0f, width * pixelSize, height * pixelSize)
            val totalDistance = paths.sumOf { 
                sqrt(((it.endX - it.startX).toDouble().pow(2) + (it.endY - it.startY).toDouble().pow(2)))
            }.toFloat()
            val estimatedTime = (totalDistance / settings.feedRate * 60).toLong()
            
            mat.release()
            processedMat.release()
            
            Result.success(PreviewPath(paths, bounds, totalDistance, estimatedTime))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Preprocess image: grayscale, adjust, dither, threshold
     */
    private fun preprocessImage(input: Mat, settings: ConversionSettings): Mat {
        var mat = input.clone()
        
        // Convert to grayscale if needed
        if (mat.channels() > 1) {
            val gray = Mat()
            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY)
            mat.release()
            mat = gray
        }
        
        // Calculate target dimensions
        val targetWidthPx = (settings.targetWidth / settings.resolution).toInt()
        val targetHeightPx = (settings.targetHeight / settings.resolution).toInt()
        
        // Resize maintaining aspect ratio
        val resized = Mat()
        Imgproc.resize(mat, resized, Size(targetWidthPx.toDouble(), targetHeightPx.toDouble()), 0.0, 0.0, Imgproc.INTER_LANCZOS4)
        mat.release()
        mat = resized
        
        // Apply brightness and contrast
        if (settings.brightness != 0f || settings.contrast != 1f) {
            mat.convertTo(mat, -1, settings.contrast.toDouble(), settings.brightness.toDouble())
        }
        
        // Apply gamma correction
        if (settings.gamma != 1.0f) {
            val lut = Mat(1, 256, CvType.CV_8U)
            val lutData = FloatArray(256)
            for (i in 0..255) {
                lutData[i] = (255.0 * (i / 255.0).pow(1.0 / settings.gamma)).toFloat()
            }
            lut.put(0, 0, lutData.map { it.toDouble() }.toDoubleArray())
            Core.LUT(mat, lut, mat)
            lut.release()
        }
        
        // Invert if requested
        if (settings.invertImage) {
            Core.bitwise_not(mat, mat)
        }
        
        // Apply dithering
        when (settings.dithering) {
            DitheringAlgorithm.FloydSteinberg -> applyFloydSteinbergDithering(mat)
            DitheringAlgorithm.Atkinson -> applyAtkinsonDithering(mat)
            DitheringAlgorithm.JarvisJudiceNinke -> applyJarvisDithering(mat)
            DitheringAlgorithm.Stucki -> applyStuckiDithering(mat)
            DitheringAlgorithm.Bayer -> applyBayerDithering(mat)
            DitheringAlgorithm.None -> { /* No dithering */ }
        }
        
        // Apply thresholding
        when (settings.thresholdMethod) {
            ThresholdMethod.Binary -> {
                Imgproc.threshold(mat, mat, settings.thresholdValue.toDouble(), 255.0, Imgproc.THRESH_BINARY)
            }
            ThresholdMethod.Otsu -> {
                Imgproc.threshold(mat, mat, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)
            }
            ThresholdMethod.Adaptive -> {
                Imgproc.adaptiveThreshold(mat, mat, 255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, 
                    Imgproc.THRESH_BINARY, 11, 2.0)
            }
            ThresholdMethod.Triangle -> {
                Imgproc.threshold(mat, mat, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_TRIANGLE)
            }
        }
        
        return mat
    }

    /**
     * Floyd-Steinberg Dithering
     */
    private fun applyFloydSteinbergDithering(mat: Mat) {
        val width = mat.cols()
        val height = mat.rows()
        val errors = Array(height) { FloatArray(width) { 0f } }
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                val oldPixel = (mat.get(y, x)[0] + errors[y][x]).coerceIn(0f, 255f)
                val newPixel = if (oldPixel < 128) 0f else 255f
                val error = oldPixel - newPixel
                
                mat.put(y, x, newPixel)
                
                // Distribute error
                if (x + 1 < width) errors[y][x + 1] += error * 7 / 16
                if (y + 1 < height) {
                    if (x > 0) errors[y + 1][x - 1] += error * 3 / 16
                    errors[y + 1][x] += error * 5 / 16
                    if (x + 1 < width) errors[y + 1][x + 1] += error * 1 / 16
                }
            }
        }
    }

    /**
     * Atkinson Dithering (Apple II style)
     */
    private fun applyAtkinsonDithering(mat: Mat) {
        val width = mat.cols()
        val height = mat.rows()
        val errors = Array(height) { FloatArray(width) { 0f } }
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                val oldPixel = (mat.get(y, x)[0] + errors[y][x]).coerceIn(0f, 255f)
                val newPixel = if (oldPixel < 128) 0f else 255f
                val error = (oldPixel - newPixel) / 8
                
                mat.put(y, x, newPixel)
                
                // Distribute error to 6 neighbors
                if (x + 1 < width) errors[y][x + 1] += error
                if (x + 2 < width) errors[y][x + 2] += error
                if (y + 1 < height) {
                    if (x > 0) errors[y + 1][x - 1] += error
                    errors[y + 1][x] += error
                    if (x + 1 < width) errors[y + 1][x + 1] += error
                }
                if (y + 2 < height) errors[y + 2][x] += error
            }
        }
    }

    /**
     * Jarvis-Judice-Ninke Dithering
     */
    private fun applyJarvisDithering(mat: Mat) {
        val width = mat.cols()
        val height = mat.rows()
        val errors = Array(height) { FloatArray(width) { 0f } }
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                val oldPixel = (mat.get(y, x)[0] + errors[y][x]).coerceIn(0f, 255f)
                val newPixel = if (oldPixel < 128) 0f else 255f
                val error = oldPixel - newPixel
                
                mat.put(y, x, newPixel)
                
                // Distribute error (12 neighbors)
                val distribution = listOf(
                    Pair(x + 1, y) to 7 / 48f,
                    Pair(x + 2, y) to 5 / 48f,
                    Pair(x - 2, y + 1) to 3 / 48f,
                    Pair(x - 1, y + 1) to 5 / 48f,
                    Pair(x, y + 1) to 7 / 48f,
                    Pair(x + 1, y + 1) to 5 / 48f,
                    Pair(x + 2, y + 1) to 3 / 48f,
                    Pair(x - 2, y + 2) to 1 / 48f,
                    Pair(x - 1, y + 2) to 3 / 48f,
                    Pair(x, y + 2) to 5 / 48f,
                    Pair(x + 1, y + 2) to 3 / 48f,
                    Pair(x + 2, y + 2) to 1 / 48f
                )
                
                distribution.forEach { (pos, weight) ->
                    if (pos.first in 0 until width && pos.second in 0 until height) {
                        errors[pos.second][pos.first] += error * weight
                    }
                }
            }
        }
    }

    /**
     * Stucki Dithering
     */
    private fun applyStuckiDithering(mat: Mat) {
        val width = mat.cols()
        val height = mat.rows()
        val errors = Array(height) { FloatArray(width) { 0f } }
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                val oldPixel = (mat.get(y, x)[0] + errors[y][x]).coerceIn(0f, 255f)
                val newPixel = if (oldPixel < 128) 0f else 255f
                val error = oldPixel - newPixel
                
                mat.put(y, x, newPixel)
                
                // Distribute error (12 neighbors with different weights)
                val distribution = listOf(
                    Pair(x + 1, y) to 8 / 42f,
                    Pair(x + 2, y) to 4 / 42f,
                    Pair(x - 2, y + 1) to 2 / 42f,
                    Pair(x - 1, y + 1) to 4 / 42f,
                    Pair(x, y + 1) to 8 / 42f,
                    Pair(x + 1, y + 1) to 4 / 42f,
                    Pair(x + 2, y + 1) to 2 / 42f,
                    Pair(x - 2, y + 2) to 1 / 42f,
                    Pair(x - 1, y + 2) to 2 / 42f,
                    Pair(x, y + 2) to 4 / 42f,
                    Pair(x + 1, y + 2) to 2 / 42f,
                    Pair(x + 2, y + 2) to 1 / 42f
                )
                
                distribution.forEach { (pos, weight) ->
                    if (pos.first in 0 until width && pos.second in 0 until height) {
                        errors[pos.second][pos.first] += error * weight
                    }
                }
            }
        }
    }

    /**
     * Bayer Ordered Dithering
     */
    private fun applyBayerDithering(mat: Mat) {
        val bayerMatrix = arrayOf(
            intArrayOf(0, 8, 2, 10),
            intArrayOf(12, 4, 14, 6),
            intArrayOf(3, 11, 1, 9),
            intArrayOf(15, 7, 13, 5)
        )
        
        val width = mat.cols()
        val height = mat.rows()
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                val threshold = (bayerMatrix[y % 4][x % 4] + 1) * 16 - 1
                val pixel = mat.get(y, x)[0]
                mat.put(y, x, if (pixel < threshold) 0.0 else 255.0)
            }
        }
    }

    /**
     * Generate Laser Raster G-code
     */
    private suspend fun generateLaserRasterGcode(
        mat: Mat,
        settings: ConversionSettings,
        onProgress: (ConversionProgress) -> Unit
    ): List<String> {
        val gcode = mutableListOf<String>()
        val width = mat.cols()
        val height = mat.rows()
        val pixelSize = settings.resolution
        
        // Header
        gcode.add("; Titan CNC - Laser Raster Engraving")
        gcode.add("; Generated by ImageToGcodeConverter")
        gcode.add("G21 ; Metric units")
        gcode.add("G90 ; Absolute positioning")
        gcode.add("G0 F${settings.rapidFeed}")
        gcode.add("G1 F${settings.feedRate}")
        gcode.add("M3 S0 ; Laser on, power 0")
        gcode.add("G4 P100 ; Dwell for laser warmup")
        
        var lineCount = 7
        var processedRows = 0
        
        when (settings.scanDirection) {
            ScanDirection.Horizontal -> {
                for (y in 0 until height) {
                    val yPos = (height - 1 - y) * pixelSize
                    val scanRight = settings.bidirectional || y % 2 == 0
                    
                    if (scanRight) {
                        generateHorizontalScanLine(mat, y, yPos, 0, width - 1, 1, pixelSize, settings, gcode)
                    } else {
                        generateHorizontalScanLine(mat, y, yPos, width - 1, 0, -1, pixelSize, settings, gcode)
                    }
                    
                    processedRows++
                    if (processedRows % 10 == 0) {
                        val percent = 30f + (processedRows.toFloat() / height * 70f)
                        onProgress(ConversionProgress("Generating scan lines...", percent, gcode.size, 0))
                    }
                }
            }
            ScanDirection.Vertical -> {
                for (x in 0 until width) {
                    val xPos = x * pixelSize
                    val scanDown = settings.bidirectional || x % 2 == 0
                    
                    if (scanDown) {
                        generateVerticalScanLine(mat, x, xPos, 0, height - 1, 1, pixelSize, settings, gcode)
                    } else {
                        generateVerticalScanLine(mat, x, xPos, height - 1, 0, -1, pixelSize, settings, gcode)
                    }
                    
                    processedRows++
                    if (processedRows % 10 == 0) {
                        val percent = 30f + (processedRows.toFloat() / width * 70f)
                        onProgress(ConversionProgress("Generating scan lines...", percent, gcode.size, 0))
                    }
                }
            }
            else -> { /* Other scan directions */ }
        }
        
        // Footer
        gcode.add("M5 ; Laser off")
        gcode.add("G0 X0 Y0 ; Return to origin")
        gcode.add("; End of G-code")
        
        return gcode
    }

    private fun generateHorizontalScanLine(
        mat: Mat, y: Int, yPos: Float,
        xStart: Int, xEnd: Int, xStep: Int,
        pixelSize: Float, settings: ConversionSettings,
        gcode: MutableList<String>
    ) {
        var inSegment = false
        var segmentStart = 0
        var segmentPower = 0
        
        var x = xStart
        while (if (xStep > 0) x <= xEnd else x >= xEnd) {
            val intensity = mat.get(y, x)[0].toInt()
            val power = ((255 - intensity) / 255f * settings.sMax).toInt().coerceIn(settings.sMin, settings.sMax)
            val hasPower = power > settings.sMin
            
            if (settings.trimWhite && !hasPower) {
                if (inSegment) {
                    // End current segment
                    val xStartPos = min(segmentStart, x - xStep) * pixelSize
                    val xEndPos = max(segmentStart, x - xStep) * pixelSize
                    gcode.add("G1 X${"%.3f".format(xEndPos)} S$segmentPower")
                    gcode.add("M5")
                    inSegment = false
                }
            } else {
                if (!inSegment) {
                    // Start new segment
                    segmentStart = x
                    segmentPower = power
                    val xPos = x * pixelSize
                    gcode.add("G0 X${"%.3f".format(xPos)} Y${"%.3f".format(yPos)}")
                    gcode.add("M3 S$power")
                    inSegment = true
                } else if (power != segmentPower && settings.dithering == DitheringAlgorithm.None) {
                    // Power change - intermediate point
                    val xPos = x * pixelSize
                    gcode.add("G1 X${"%.3f".format(xPos)} S$segmentPower")
                    segmentPower = power
                }
            }
            
            x += xStep
        }
        
        // Close final segment
        if (inSegment) {
            val xEndPos = xEnd * pixelSize
            gcode.add("G1 X${"%.3f".format(xEndPos)} S$segmentPower")
            gcode.add("M5")
        }
    }

    private fun generateVerticalScanLine(
        mat: Mat, x: Int, xPos: Float,
        yStart: Int, yEnd: Int, yStep: Int,
        pixelSize: Float, settings: ConversionSettings,
        gcode: MutableList<String>
    ) {
        var inSegment = false
        var segmentStart = 0
        var segmentPower = 0
        val height = mat.rows()
        
        var y = yStart
        while (if (yStep > 0) y <= yEnd else y >= yEnd) {
            val intensity = mat.get(y, x)[0].toInt()
            val power = ((255 - intensity) / 255f * settings.sMax).toInt().coerceIn(settings.sMin, settings.sMax)
            val hasPower = power > settings.sMin
            
            if (settings.trimWhite && !hasPower) {
                if (inSegment) {
                    val yStartPos = (height - 1 - max(segmentStart, y - yStep)) * pixelSize
                    val yEndPos = (height - 1 - min(segmentStart, y - yStep)) * pixelSize
                    gcode.add("G1 Y${"%.3f".format(yEndPos)} S$segmentPower")
                    gcode.add("M5")
                    inSegment = false
                }
            } else {
                if (!inSegment) {
                    segmentStart = y
                    segmentPower = power
                    val yPos = (height - 1 - y) * pixelSize
                    gcode.add("G0 X${"%.3f".format(xPos)} Y${"%.3f".format(yPos)}")
                    gcode.add("M3 S$power")
                    inSegment = true
                } else if (power != segmentPower && settings.dithering == DitheringAlgorithm.None) {
                    val yPos = (height - 1 - y) * pixelSize
                    gcode.add("G1 Y${"%.3f".format(yPos)} S$segmentPower")
                    segmentPower = power
                }
            }
            
            y += yStep
        }
        
        if (inSegment) {
            val yEndPos = (height - 1 - yEnd) * pixelSize
            gcode.add("G1 Y${"%.3f".format(yEndPos)} S$segmentPower")
            gcode.add("M5")
        }
    }

    /**
     * Generate Laser Vector G-code (contour tracing)
     */
    private suspend fun generateLaserVectorGcode(
        mat: Mat,
        settings: ConversionSettings,
        onProgress: (ConversionProgress) -> Unit
    ): List<String> {
        val gcode = mutableListOf<String>()
        
        gcode.add("; Titan CNC - Laser Vector Engraving")
        gcode.add("G21 ; Metric units")
        gcode.add("G90 ; Absolute positioning")
        gcode.add("G0 F${settings.rapidFeed}")
        gcode.add("G1 F${settings.feedRate}")
        
        // Find contours
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(mat, contours, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE)
        
        val pixelSize = settings.resolution
        val height = mat.rows()
        
        contours.forEachIndexed { index, contour ->
            if (contour.rows() < 3) return@forEachIndexed
            
            // Approximate polygon
            val approxCurve = MatOfPoint2f()
            val contour2f = MatOfPoint2f(*contour.toArray())
            val epsilon = 0.001 * Imgproc.arcLength(contour2f, true)
            Imgproc.approxPolyDP(contour2f, approxCurve, epsilon, true)
            
            val points = approxCurve.toArray()
            if (points.isNotEmpty()) {
                // Move to first point
                val firstPoint = points[0]
                val x0 = firstPoint.x * pixelSize
                val y0 = (height - 1 - firstPoint.y) * pixelSize
                gcode.add("G0 X${"%.3f".format(x0)} Y${"%.3f".format(y0)}")
                gcode.add("M3 S${settings.sMax}")
                
                // Trace contour
                for (i in 1 until points.size) {
                    val p = points[i]
                    val x = p.x * pixelSize
                    val y = (height - 1 - p.y) * pixelSize
                    gcode.add("G1 X${"%.3f".format(x)} Y${"%.3f".format(y)}")
                }
                
                // Close contour
                gcode.add("G1 X${"%.3f".format(x0)} Y${"%.3f".format(y0)}")
                gcode.add("M5")
            }
            
            approxCurve.release()
            contour2f.release()
        }
        
        hierarchy.release()
        contours.forEach { it.release() }
        
        gcode.add("G0 X0 Y0")
        gcode.add("; End of G-code")
        
        return gcode
    }

    /**
     * Generate V-Carve G-code
     */
    private suspend fun generateVCarveGcode(
        mat: Mat,
        settings: ConversionSettings,
        onProgress: (ConversionProgress) -> Unit
    ): List<String> {
        // V-carve implementation would calculate toolpaths based on pixel intensity
        // mapping to depth, then generating toolpaths with appropriate stepover
        val gcode = mutableListOf<String>()
        gcode.add("; Titan CNC - V-Carve (Placeholder)")
        gcode.add("; Full V-carve implementation requires additional algorithms")
        gcode.addAll(generateReliefGcode(mat, settings, onProgress))
        return gcode
    }

    /**
     * Generate 3D Relief G-code
     */
    private suspend fun generateReliefGcode(
        mat: Mat,
        settings: ConversionSettings,
        onProgress: (ConversionProgress) -> Unit
    ): List<String> {
        val gcode = mutableListOf<String>()
        val width = mat.cols()
        val height = mat.rows()
        val pixelSize = settings.resolution
        
        gcode.add("; Titan CNC - 3D Relief")
        gcode.add("G21 ; Metric units")
        gcode.add("G90 ; Absolute positioning")
        gcode.add("G0 F${settings.rapidFeed}")
        gcode.add("M3 S${settings.sMax}")
        
        // Calculate number of passes
        val numPasses = ceil(settings.maxDepth / settings.stepDown).toInt()
        
        for (pass in 1..numPasses) {
            val zDepth = -(pass * settings.stepDown).coerceAtMost(settings.maxDepth)
            gcode.add("; Pass $pass/$numPasses, Z=$zDepth")
            
            for (y in 0 until height) {
                val yPos = (height - 1 - y) * pixelSize
                gcode.add("G0 Y${"%.3f".format(yPos)}")
                
                for (x in 0 until width) {
                    val intensity = mat.get(y, x)[0].toInt()
                    val depthFactor = (255 - intensity) / 255f
                    val z = zDepth * depthFactor
                    
                    if (z < 0) {
                        val xPos = x * pixelSize
                        gcode.add("G1 X${"%.3f".format(xPos)} Z${"%.3f".format(z)} F${settings.feedRate}")
                    }
                }
            }
        }
        
        gcode.add("G0 Z${settings.zSafe}")
        gcode.add("G0 X0 Y0")
        gcode.add("M5")
        gcode.add("; End of G-code")
        
        return gcode
    }

    /**
     * Generate Pocket Clearing G-code
     */
    private suspend fun generatePocketGcode(
        mat: Mat,
        settings: ConversionSettings,
        onProgress: (ConversionProgress) -> Unit
    ): List<String> {
        val gcode = mutableListOf<String>()
        
        gcode.add("; Titan CNC - Pocket Clearing")
        gcode.add("G21 ; Metric units")
        gcode.add("G90 ; Absolute positioning")
        gcode.add("G0 F${settings.rapidFeed}")
        
        // Find contours of dark regions
        val inverted = Mat()
        Core.bitwise_not(mat, inverted)
        
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(inverted, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        
        val pixelSize = settings.resolution
        val height = mat.rows()
        val numPasses = ceil(settings.maxDepth / settings.stepDown).toInt()
        
        contours.forEach { contour ->
            if (contour.rows() < 3) return@forEach
            
            for (pass in 1..numPasses) {
                val zDepth = -(pass * settings.stepDown).coerceAtMost(settings.maxDepth)
                
                val points = contour.toArray()
                if (points.isNotEmpty()) {
                    val firstPoint = points[0]
                    val x0 = firstPoint.x * pixelSize
                    val y0 = (height - 1 - firstPoint.y) * pixelSize
                    
                    gcode.add("G0 X${"%.3f".format(x0)} Y${"%.3f".format(y0)}")
                    gcode.add("G1 Z${"%.3f".format(zDepth)} F${settings.feedRate}")
                    gcode.add("M3 S${settings.sMax}")
                    
                    for (i in 1 until points.size) {
                        val p = points[i]
                        val x = p.x * pixelSize
                        val y = (height - 1 - p.y) * pixelSize
                        gcode.add("G1 X${"%.3f".format(x)} Y${"%.3f".format(y)}")
                    }
                    
                    gcode.add("G1 X${"%.3f".format(x0)} Y${"%.3f".format(y0)}")
                    gcode.add("G0 Z${settings.zSafe}")
                }
            }
        }
        
        inverted.release()
        hierarchy.release()
        contours.forEach { it.release() }
        
        gcode.add("G0 X0 Y0")
        gcode.add("M5")
        gcode.add("; End of G-code")
        
        return gcode
    }

    fun cleanup() {
        scope.cancel()
    }
}

// Extension for RectF
data class RectF(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}
