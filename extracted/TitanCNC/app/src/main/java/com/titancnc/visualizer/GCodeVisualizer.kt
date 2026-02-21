package com.titancnc.visualizer

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.SystemClock
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*

/**
 * OpenGL ES 2.0 G-Code Visualizer
 * 
 * Features:
 * - Real-time toolpath rendering
 * - 3D navigation (pan, zoom, rotate)
 * - Different colors for rapid vs cutting moves
 * - Current position indicator
 * - Work envelope visualization
 */

class GCodeVisualizer(context: Context) : GLSurfaceView(context) {
    
    private val renderer: GCodeRenderer
    
    init {
        setEGLContextClientVersion(2)
        renderer = GCodeRenderer()
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }
    
    fun setToolpath(toolpath: List<ToolpathSegment>) {
        renderer.setToolpath(toolpath)
        requestRender()
    }
    
    fun setCurrentPosition(position: Position3D) {
        renderer.setCurrentPosition(position)
        requestRender()
    }
    
    fun setWorkEnvelope(min: Position3D, max: Position3D) {
        renderer.setWorkEnvelope(min, max)
        requestRender()
    }
    
    fun resetView() {
        renderer.resetView()
        requestRender()
    }
    
    fun rotateCamera(deltaX: Float, deltaY: Float) {
        renderer.rotateCamera(deltaX, deltaY)
        requestRender()
    }
    
    fun zoomCamera(delta: Float) {
        renderer.zoomCamera(delta)
        requestRender()
    }
    
    fun panCamera(deltaX: Float, deltaY: Float) {
        renderer.panCamera(deltaX, deltaY)
        requestRender()
    }
}

data class Position3D(val x: Float, val y: Float, val z: Float)

data class ToolpathSegment(
    val start: Position3D,
    val end: Position3D,
    val isRapid: Boolean = false,
    val feedRate: Float = 0f,
    val lineNumber: Int = 0
)

class GCodeRenderer : GLSurfaceView.Renderer {
    
    private val mvpMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    
    private var toolpathBuffer: LineBuffer? = null
    private var rapidBuffer: LineBuffer? = null
    private var gridBuffer: LineBuffer? = null
    private var axesBuffer: LineBuffer? = null
    private var positionMarker: PointMarker? = null
    
    private var cameraDistance = 200f
    private var cameraRotationX = -30f
    private var cameraRotationY = 45f
    private var cameraTargetX = 0f
    private var cameraTargetY = 0f
    private var cameraTargetZ = 0f
    
    private var toolpath: List<ToolpathSegment> = emptyList()
    private var currentPosition = Position3D(0f, 0f, 0f)
    
    // Shader programs
    private var lineProgram: Int = 0
    private var pointProgram: Int = 0
    
    companion object {
        private const val COORDS_PER_VERTEX = 3
        private const val VERTEX_STRIDE = COORDS_PER_VERTEX * 4 // 4 bytes per float
        
        // Colors
        private val COLOR_RAPID = floatArrayOf(1.0f, 0.5f, 0.0f, 1.0f)      // Orange
        private val COLOR_CUTTING = floatArrayOf(0.0f, 0.8f, 1.0f, 1.0f)    // Cyan
        private val COLOR_GRID = floatArrayOf(0.2f, 0.2f, 0.2f, 1.0f)       // Dark gray
        private val COLOR_AXIS_X = floatArrayOf(1.0f, 0.2f, 0.2f, 1.0f)     // Red
        private val COLOR_AXIS_Y = floatArrayOf(0.2f, 1.0f, 0.2f, 1.0f)     // Green
        private val COLOR_AXIS_Z = floatArrayOf(0.2f, 0.2f, 1.0f, 1.0f)     // Blue
        private val COLOR_POSITION = floatArrayOf(1.0f, 1.0f, 0.0f, 1.0f)   // Yellow
    }
    
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.07f, 0.07f, 0.07f, 1.0f) // #121212
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glLineWidth(2f)
        
        // Initialize shaders
        lineProgram = createProgram(vertexShaderCode, fragmentShaderCode)
        pointProgram = createProgram(pointVertexShaderCode, pointFragmentShaderCode)
        
        // Create grid
        createGrid()
        
        // Create axes
        createAxes()
        
        // Create position marker
        positionMarker = PointMarker(0f, 0f, 0f, 5f)
    }
    
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        
        val ratio: Float = width.toFloat() / height.toFloat()
        Matrix.perspectiveM(projectionMatrix, 0, 45f, ratio, 1f, 1000f)
    }
    
    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        
        // Set up view matrix
        updateViewMatrix()
        
        // Draw grid
        gridBuffer?.draw(COLOR_GRID, 1f)
        
        // Draw axes
        axesBuffer?.draw(COLOR_AXIS_X, 3f)
        
        // Draw rapid moves
        rapidBuffer?.draw(COLOR_RAPID, 1f)
        
        // Draw cutting moves
        toolpathBuffer?.draw(COLOR_CUTTING, 2f)
        
        // Draw current position
        positionMarker?.let {
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, currentPosition.x, currentPosition.y, currentPosition.z)
            Matrix.multiplyMM(mvpMatrix, 0, viewMatrix, 0, modelMatrix, 0)
            Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, mvpMatrix, 0)
            it.draw(mvpMatrix, COLOR_POSITION)
        }
    }
    
    private fun updateViewMatrix() {
        Matrix.setLookAtM(
            viewMatrix, 0,
            cameraTargetX + cameraDistance * cos(Math.toRadians(cameraRotationY.toDouble())).toFloat() * cos(Math.toRadians(cameraRotationX.toDouble())).toFloat(),
            cameraTargetY + cameraDistance * sin(Math.toRadians(cameraRotationX.toDouble())).toFloat(),
            cameraTargetZ + cameraDistance * sin(Math.toRadians(cameraRotationY.toDouble())).toFloat() * cos(Math.toRadians(cameraRotationX.toDouble())).toFloat(),
            cameraTargetX, cameraTargetY, cameraTargetZ,
            0f, 1f, 0f
        )
    }
    
    fun setToolpath(newToolpath: List<ToolpathSegment>) {
        toolpath = newToolpath
        
        val cuttingLines = mutableListOf<Float>()
        val rapidLines = mutableListOf<Float>()
        
        toolpath.forEach { segment ->
            val line = floatArrayOf(
                segment.start.x, segment.start.y, segment.start.z,
                segment.end.x, segment.end.y, segment.end.z
            )
            
            if (segment.isRapid) {
                rapidLines.addAll(line.toList())
            } else {
                cuttingLines.addAll(line.toList())
            }
        }
        
        if (cuttingLines.isNotEmpty()) {
            toolpathBuffer = LineBuffer(cuttingLines.toFloatArray())
        }
        
        if (rapidLines.isNotEmpty()) {
            rapidBuffer = LineBuffer(rapidLines.toFloatArray())
        }
        
        // Auto-center camera on toolpath
        if (toolpath.isNotEmpty()) {
            val allPoints = toolpath.flatMap { listOf(it.start, it.end) }
            val minX = allPoints.minOf { it.x }
            val maxX = allPoints.maxOf { it.x }
            val minY = allPoints.minOf { it.y }
            val maxY = allPoints.maxOf { it.y }
            val minZ = allPoints.minOf { it.z }
            val maxZ = allPoints.maxOf { it.z }
            
            cameraTargetX = (minX + maxX) / 2
            cameraTargetY = (minY + maxY) / 2
            cameraTargetZ = (minZ + maxZ) / 2
            
            val size = maxOf(maxX - minX, maxY - minY, maxZ - minZ)
            cameraDistance = size * 1.5f
        }
    }
    
    fun setCurrentPosition(position: Position3D) {
        currentPosition = position
    }
    
    fun setWorkEnvelope(min: Position3D, max: Position3D) {
        // Could visualize work envelope here
    }
    
    fun resetView() {
        cameraDistance = 200f
        cameraRotationX = -30f
        cameraRotationY = 45f
        cameraTargetX = 0f
        cameraTargetY = 0f
        cameraTargetZ = 0f
    }
    
    fun rotateCamera(deltaX: Float, deltaY: Float) {
        cameraRotationY += deltaX * 0.5f
        cameraRotationX += deltaY * 0.5f
        cameraRotationX = cameraRotationX.coerceIn(-89f, 89f)
    }
    
    fun zoomCamera(delta: Float) {
        cameraDistance *= (1 - delta * 0.01f)
        cameraDistance = cameraDistance.coerceIn(10f, 1000f)
    }
    
    fun panCamera(deltaX: Float, deltaY: Float) {
        val factor = cameraDistance * 0.001f
        cameraTargetX += deltaX * factor * cos(Math.toRadians(cameraRotationY.toDouble())).toFloat()
        cameraTargetZ += deltaX * factor * sin(Math.toRadians(cameraRotationY.toDouble())).toFloat()
        cameraTargetY -= deltaY * factor
    }
    
    private fun createGrid() {
        val gridSize = 100f
        val gridStep = 10f
        val vertices = mutableListOf<Float>()
        
        // Grid lines
        for (i in -10..10) {
            val pos = i * gridStep
            // X lines
            vertices.addAll(listOf(-gridSize, 0f, pos, gridSize, 0f, pos))
            // Z lines
            vertices.addAll(listOf(pos, 0f, -gridSize, pos, 0f, gridSize))
        }
        
        gridBuffer = LineBuffer(vertices.toFloatArray())
    }
    
    private fun createAxes() {
        val axisLength = 50f
        val vertices = floatArrayOf(
            // X axis (red)
            0f, 0f, 0f, axisLength, 0f, 0f,
            // Y axis (green)
            0f, 0f, 0f, 0f, axisLength, 0f,
            // Z axis (blue)
            0f, 0f, 0f, 0f, 0f, axisLength
        )
        
        axesBuffer = LineBuffer(vertices)
    }
    
    private fun createProgram(vertexShaderCode: String, fragmentShaderCode: String): Int {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)
        
        return GLES20.glCreateProgram().also { program ->
            GLES20.glAttachShader(program, vertexShader)
            GLES20.glAttachShader(program, fragmentShader)
            GLES20.glLinkProgram(program)
        }
    }
    
    private fun loadShader(type: Int, shaderCode: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
        }
    }
    
    // Shaders
    private val vertexShaderCode = """
        uniform mat4 uMVPMatrix;
        attribute vec4 vPosition;
        void main() {
            gl_Position = uMVPMatrix * vPosition;
        }
    """.trimIndent()
    
    private val fragmentShaderCode = """
        precision mediump float;
        uniform vec4 vColor;
        void main() {
            gl_FragColor = vColor;
        }
    """.trimIndent()
    
    private val pointVertexShaderCode = """
        uniform mat4 uMVPMatrix;
        attribute vec4 vPosition;
        void main() {
            gl_Position = uMVPMatrix * vPosition;
            gl_PointSize = 20.0;
        }
    """.trimIndent()
    
    private val pointFragmentShaderCode = """
        precision mediump float;
        uniform vec4 vColor;
        void main() {
            gl_FragColor = vColor;
        }
    """.trimIndent()
    
    inner class LineBuffer(private val vertices: FloatArray) {
        private val vertexBuffer: FloatBuffer
        private val vertexCount: Int = vertices.size / COORDS_PER_VERTEX
        
        init {
            val bb = ByteBuffer.allocateDirect(vertices.size * 4)
            bb.order(ByteOrder.nativeOrder())
            vertexBuffer = bb.asFloatBuffer()
            vertexBuffer.put(vertices)
            vertexBuffer.position(0)
        }
        
        fun draw(color: FloatArray, lineWidth: Float) {
            GLES20.glUseProgram(lineProgram)
            
            val positionHandle = GLES20.glGetAttribLocation(lineProgram, "vPosition")
            GLES20.glEnableVertexAttribArray(positionHandle)
            GLES20.glVertexAttribPointer(positionHandle, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, VERTEX_STRIDE, vertexBuffer)
            
            val colorHandle = GLES20.glGetUniformLocation(lineProgram, "vColor")
            GLES20.glUniform4fv(colorHandle, 1, color, 0)
            
            val mvpMatrixHandle = GLES20.glGetUniformLocation(lineProgram, "uMVPMatrix")
            Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)
            GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
            
            GLES20.glLineWidth(lineWidth)
            GLES20.glDrawArrays(GLES20.GL_LINES, 0, vertexCount)
            
            GLES20.glDisableVertexAttribArray(positionHandle)
        }
    }
    
    inner class PointMarker(x: Float, y: Float, z: Float, private val size: Float) {
        private val vertexBuffer: FloatBuffer
        
        init {
            val vertices = floatArrayOf(x, y, z)
            val bb = ByteBuffer.allocateDirect(vertices.size * 4)
            bb.order(ByteOrder.nativeOrder())
            vertexBuffer = bb.asFloatBuffer()
            vertexBuffer.put(vertices)
            vertexBuffer.position(0)
        }
        
        fun draw(mvpMatrix: FloatArray, color: FloatArray) {
            GLES20.glUseProgram(pointProgram)
            
            val positionHandle = GLES20.glGetAttribLocation(pointProgram, "vPosition")
            GLES20.glEnableVertexAttribArray(positionHandle)
            GLES20.glVertexAttribPointer(positionHandle, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, VERTEX_STRIDE, vertexBuffer)
            
            val colorHandle = GLES20.glGetUniformLocation(pointProgram, "vColor")
            GLES20.glUniform4fv(colorHandle, 1, color, 0)
            
            val mvpMatrixHandle = GLES20.glGetUniformLocation(pointProgram, "uMVPMatrix")
            GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
            
            GLES20.glDrawArrays(GLES20.GL_POINTS, 0, 1)
            
            GLES20.glDisableVertexAttribArray(positionHandle)
        }
    }
}
