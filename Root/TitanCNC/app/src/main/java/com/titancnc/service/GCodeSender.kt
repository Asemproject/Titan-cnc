package com.titancnc.service

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * GRBL Serial Buffer Character Counting Implementation
 * 
 * GRBL v1.1 has a 128-byte serial receive buffer.
 * This implementation uses character counting to prevent buffer overflow
 * and ensure smooth streaming without stuttering.
 */

sealed class SendState {
    data object Idle : SendState()
    data object Sending : SendState()
    data object Paused : SendState()
    data object Completed : SendState()
    data class Error(val message: String) : SendState()
}

data class JobProgress(
    val totalLines: Int,
    val sentLines: Int,
    val completedLines: Int,
    val bufferFill: Int,
    val percentComplete: Float,
    val estimatedTimeRemaining: Long, // in seconds
    val currentLine: String = ""
)

data class GrblStatus(
    val state: GrblMachineState,
    val mpos: Position = Position(),
    val wpos: Position = Position(),
    val feedRate: Int = 0,
    val spindleSpeed: Int = 0,
    val lineNumber: Int = 0,
    val bufferAvailable: Int = 128,
    val pinState: PinState = PinState()
)

data class Position(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f,
    val a: Float = 0f
) {
    operator fun minus(other: Position): Position {
        return Position(
            x = this.x - other.x,
            y = this.y - other.y,
            z = this.z - other.z,
            a = this.a - other.a
        )
    }
    
    fun toWpos(mpos: Position, workOffset: WorkOffset): Position {
        return Position(
            x = mpos.x - workOffset.x,
            y = mpos.y - workOffset.y,
            z = mpos.z - workOffset.z,
            a = mpos.a - workOffset.a
        )
    }
}

data class WorkOffset(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f,
    val a: Float = 0f
)

data class PinState(
    val limitX: Boolean = false,
    val limitY: Boolean = false,
    val limitZ: Boolean = false,
    val limitA: Boolean = false,
    val probe: Boolean = false,
    val door: Boolean = false,
    val hold: Boolean = false,
    val softReset: Boolean = false,
    val cycleStart: Boolean = false
)

enum class GrblMachineState {
    IDLE,       // Ready for commands
    RUN,        // Executing motion
    HOLD,       // Feed hold active
    JOG,        // Jogging mode
    ALARM,      // Alarm state (requires reset)
    CHECK,      // Check mode ($C)
    DOOR,       // Door open (safety)
    SLEEP,      // Sleep mode
    HOME,       // Homing in progress
    UNKNOWN     // Unknown state
}

sealed class GrblResponse {
    data class Ok(val lineNumber: Int? = null) : GrblResponse()
    data class Error(val code: Int, val message: String, val lineNumber: Int? = null) : GrblResponse()
    data class Status(val status: GrblStatus) : GrblResponse()
    data class Setting(val id: Int, val value: Double, val description: String) : GrblResponse()
    data class Feedback(val message: String) : GrblResponse()
    data class Alarm(val code: Int, val message: String) : GrblResponse()
    data class ProbeResult(val position: Position, val success: Boolean) : GrblResponse()
    data class Startup(val version: String) : GrblResponse()
    data class Unknown(val raw: String) : GrblResponse()
}

@Singleton
class GCodeSender @Inject constructor(
    private val connectionManager: ConnectionManager
) {
    companion object {
        const val GRBL_BUFFER_SIZE = 128
        const val GRBL_BUFFER_SAFE = 100  // Leave some headroom
        const val DEFAULT_FEED_RATE = 1000
        const val DEFAULT_SPINDLE_SPEED = 10000
        
        // GRBL Real-Time Commands
        const val CMD_STATUS_QUERY = "?"
        const val CMD_CYCLE_START = "~"
        const val CMD_FEED_HOLD = "!"
        const val CMD_SOFT_RESET = 0x18.toChar().toString()
        const val CMD_SAFETY_DOOR = 0x84.toChar().toString()
        const val CMD_JOG_CANCEL = 0x85.toChar().toString()
        const val CMD_FEED_OVERRIDE_100 = 0x90.toChar().toString()
        const val CMD_FEED_OVERRIDE_PLUS_10 = 0x91.toChar().toString()
        const val CMD_FEED_OVERRIDE_MINUS_10 = 0x92.toChar().toString()
        const val CMD_RAPID_OVERRIDE_100 = 0x95.toChar().toString()
        const val CMD_SPINDLE_OVERRIDE_100 = 0x99.toChar().toString()
        const val CMD_SPINDLE_OVERRIDE_PLUS_10 = 0x9A.toChar().toString()
        const val CMD_SPINDLE_OVERRIDE_MINUS_10 = 0x9B.toChar().toString()
        const val CMD_SPINDLE_STOP = 0x9E.toChar().toString()
        const val CMD_COOLANT_FLOOD_TOGGLE = 0xA0.toChar().toString()
        const val CMD_COOLANT_MIST_TOGGLE = 0xA1.toChar().toString()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // State flows
    private val _sendState = MutableStateFlow<SendState>(SendState.Idle)
    val sendState: StateFlow<SendState> = _sendState.asStateFlow()
    
    private val _progress = MutableStateFlow(JobProgress(0, 0, 0, 0, 0f, 0))
    val progress: StateFlow<JobProgress> = _progress.asStateFlow()
    
    private val _grblStatus = MutableStateFlow(GrblStatus(GrblMachineState.UNKNOWN))
    val grblStatus: StateFlow<GrblStatus> = _grblStatus.asStateFlow()
    
    private val _grblSettings = MutableStateFlow<Map<Int, Double>>(emptyMap())
    val grblSettings: StateFlow<Map<Int, Double>> = _grblSettings.asStateFlow()
    
    // Buffer management
    private val bufferMutex = Mutex()
    private val lineQueue = ConcurrentLinkedQueue<QueuedLine>()
    private val pendingLines = mutableMapOf<Int, String>() // lineNumber -> line
    private val lineCounter = AtomicInteger(0)
    private val charsInBuffer = AtomicInteger(0)
    private val isSending = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)
    
    // Work offsets
    private val workOffsets = mutableMapOf(
        0 to WorkOffset(), // G54
        1 to WorkOffset(), // G55
        2 to WorkOffset(), // G56
        3 to WorkOffset(), // G57
        4 to WorkOffset(), // G58
        5 to WorkOffset()  // G59
    )
    private var currentWCS = 0 // Current Work Coordinate System
    
    // Status polling
    private var statusJob: Job? = null
    
    data class QueuedLine(
        val lineNumber: Int,
        val content: String,
        val charCount: Int
    )

    init {
        // Start listening for responses
        scope.launch {
            connectionManager.receivedData.collect { data ->
                processResponse(data)
            }
        }
        
        // Start status polling when connected
        scope.launch {
            connectionManager.connectionState.collect { state ->
                when (state) {
                    is ConnectionState.Connected -> startStatusPolling()
                    else -> stopStatusPolling()
                }
            }
        }
    }

    // ==================== Buffer Management ====================

    /**
     * Character Counting Protocol for GRBL
     * Tracks exact bytes in GRBL's receive buffer to prevent overflow
     */
    private fun getLineCharCount(line: String): Int {
        return line.length + 1 // +1 for newline character
    }

    private suspend fun canSendLine(line: String): Boolean {
        val needed = getLineCharCount(line)
        return bufferMutex.withLock {
            charsInBuffer.get() + needed <= GRBL_BUFFER_SAFE
        }
    }

    private suspend fun addToBuffer(line: String, lineNumber: Int) {
        val charCount = getLineCharCount(line)
        bufferMutex.withLock {
            charsInBuffer.addAndGet(charCount)
            pendingLines[lineNumber] = line
        }
        updateProgress()
    }

    private suspend fun removeFromBuffer(lineNumber: Int) {
        bufferMutex.withLock {
            pendingLines.remove(lineNumber)?.let { line ->
                charsInBuffer.addAndGet(-getLineCharCount(line))
            }
        }
        updateProgress()
    }

    private fun updateProgress() {
        val total = _progress.value.totalLines
        val sent = lineCounter.get()
        val completed = sent - pendingLines.size
        val bufferFill = charsInBuffer.get()
        val percent = if (total > 0) (completed.toFloat() / total * 100) else 0f
        
        _progress.value = _progress.value.copy(
            sentLines = sent,
            completedLines = completed,
            bufferFill = bufferFill,
            percentComplete = percent
        )
    }

    // ==================== G-Code Streaming ====================

    /**
     * Send a complete G-code job with character counting flow control
     */
    suspend fun sendJob(gcodeLines: List<String>): Result<Unit> {
        if (isSending.get()) {
            return Result.failure(IllegalStateException("Already sending a job"))
        }
        
        if (!connectionManager.isConnected()) {
            return Result.failure(IllegalStateException("Not connected to CNC"))
        }
        
        // Reset state
        isSending.set(true)
        isPaused.set(false)
        _sendState.value = SendState.Sending
        lineQueue.clear()
        
        // Filter and queue lines
        val filteredLines = gcodeLines.map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("(") && !it.startsWith(";") }
        
        _progress.value = JobProgress(
            totalLines = filteredLines.size,
            sentLines = 0,
            completedLines = 0,
            bufferFill = 0,
            percentComplete = 0f,
            estimatedTimeRemaining = 0
        )
        
        // Queue all lines
        filteredLines.forEachIndexed { index, line ->
            lineQueue.offer(QueuedLine(index + 1, line, getLineCharCount(line)))
        }
        
        // Start streaming
        return try {
            streamJob()
            _sendState.value = SendState.Completed
            Result.success(Unit)
        } catch (e: Exception) {
            _sendState.value = SendState.Error(e.message ?: "Streaming error")
            Result.failure(e)
        } finally {
            isSending.set(false)
        }
    }

    private suspend fun streamJob() {
        while (isSending.get() && (lineQueue.isNotEmpty() || pendingLines.isNotEmpty())) {
            if (isPaused.get()) {
                delay(10)
                continue
            }
            
            // Check for errors
            if (_grblStatus.value.state == GrblMachineState.ALARM) {
                throw IllegalStateException("GRBL in alarm state: Job halted")
            }
            
            // Send next line if buffer has space
            val nextLine = lineQueue.peek()
            if (nextLine != null && canSendLine(nextLine.content)) {
                lineQueue.poll()
                sendLineInternal(nextLine)
            } else {
                delay(1) // Small delay to prevent busy-waiting
            }
        }
        
        // Wait for all pending lines to complete
        while (pendingLines.isNotEmpty() && isSending.get()) {
            delay(10)
        }
    }

    private suspend fun sendLineInternal(queuedLine: QueuedLine) {
        val lineWithNumber = "N${queuedLine.lineNumber}${queuedLine.content}"
        
        connectionManager.send(lineWithNumber).onSuccess {
            addToBuffer(queuedLine.content, queuedLine.lineNumber)
            lineCounter.incrementAndGet()
            _progress.value = _progress.value.copy(currentLine = queuedLine.content)
        }.onFailure {
            throw it
        }
    }

    /**
     * Send a single command (for jogging, MDI, etc.)
     */
    suspend fun sendCommand(command: String): Result<Unit> {
        return connectionManager.send(command.trim())
    }

    /**
     * Send real-time command (immediate, no buffering)
     */
    suspend fun sendRealtimeCommand(command: String): Result<Unit> {
        return connectionManager.send(command)
    }

    // ==================== Job Control ====================

    fun pauseJob() {
        isPaused.set(true)
        _sendState.value = SendState.Paused
        scope.launch {
            sendRealtimeCommand(CMD_FEED_HOLD)
        }
    }

    fun resumeJob() {
        isPaused.set(false)
        _sendState.value = SendState.Sending
        scope.launch {
            sendRealtimeCommand(CMD_CYCLE_START)
        }
    }

    fun stopJob() {
        isSending.set(false)
        scope.launch {
            sendRealtimeCommand(CMD_SOFT_RESET)
            delay(100)
            clearBuffer()
            _sendState.value = SendState.Idle
        }
    }

    private suspend fun clearBuffer() {
        bufferMutex.withLock {
            lineQueue.clear()
            pendingLines.clear()
            charsInBuffer.set(0)
            lineCounter.set(0)
        }
    }

    // ==================== Response Processing ====================

    private fun processResponse(data: String) {
        when {
            data == "ok" -> {
                // Simple ok response - acknowledge most recent line
                acknowledgeLine()
            }
            data.startsWith("ok:") -> {
                // Ok with line number
                val lineNum = data.substringAfter("ok:").toIntOrNull()
                lineNum?.let { scope.launch { removeFromBuffer(it) } }
            }
            data.startsWith("error:") -> {
                // Error response
                val errorCode = data.substringAfter("error:").substringBefore(":").toIntOrNull() ?: 0
                val errorMsg = getErrorMessage(errorCode)
                _sendState.value = SendState.Error("Error $errorCode: $errorMsg")
                acknowledgeLine()
            }
            data.startsWith("<") && data.endsWith(">") -> {
                // Status report
                parseStatusReport(data)
            }
            data.startsWith("$") && data.contains("=") -> {
                // Setting response
                parseSetting(data)
            }
            data.startsWith("[") && data.endsWith("]") -> {
                // Feedback message
                parseFeedback(data)
            }
            data.startsWith("ALARM:") -> {
                // Alarm
                val alarmCode = data.substringAfter("ALARM:").toIntOrNull() ?: 0
                val alarmMsg = getAlarmMessage(alarmCode)
                _grblStatus.value = _grblStatus.value.copy(state = GrblMachineState.ALARM)
                _sendState.value = SendState.Error("ALARM $alarmCode: $alarmMsg")
            }
            data.startsWith("Grbl") -> {
                // Startup message
                val version = data.substringAfter("Grbl ").substringBefore(" ")
                _grblStatus.value = GrblStatus(GrblMachineState.IDLE)
            }
            data.startsWith("[PRB:") -> {
                // Probe result
                parseProbeResult(data)
            }
            data.startsWith("[WCO:") -> {
                // Work coordinate offset
                parseWorkCoordinateOffset(data)
            }
            data.startsWith("[TLO:") -> {
                // Tool length offset
                // Parse if needed
            }
        }
    }

    private fun acknowledgeLine() {
        // Find and remove the oldest pending line
        scope.launch {
            bufferMutex.withLock {
                pendingLines.keys.minOrNull()?.let { oldestLine ->
                    pendingLines.remove(oldestLine)?.let { line ->
                        charsInBuffer.addAndGet(-getLineCharCount(line))
                    }
                }
            }
            updateProgress()
        }
    }

    private fun parseStatusReport(data: String) {
        // Format: <State|MPos:x,y,z|WPos:x,y,z|Bf:15,128|Ln:999|F:1000|S:12000|Pn:XYZ|Ov:100,100,100|A:SF>
        try {
            val content = data.substring(1, data.length - 1)
            val parts = content.split("|")
            
            var state = GrblMachineState.UNKNOWN
            var mpos = Position()
            var wpos = Position()
            var feedRate = 0
            var spindleSpeed = 0
            var lineNumber = 0
            var bufferAvailable = 128
            var pinState = PinState()
            
            parts.forEach { part ->
                when {
                    part.contains(":") -> {
                        val key = part.substringBefore(":")
                        val value = part.substringAfter(":")
                        
                        when (key) {
                            "MPos" -> mpos = parsePosition(value)
                            "WPos" -> wpos = parsePosition(value)
                            "Bf" -> {
                                val bufferParts = value.split(",")
                                bufferAvailable = bufferParts.getOrNull(1)?.toIntOrNull() ?: 128
                            }
                            "Ln" -> lineNumber = value.toIntOrNull() ?: 0
                            "F" -> feedRate = value.toIntOrNull() ?: 0
                            "S" -> spindleSpeed = value.toIntOrNull() ?: 0
                            "Ov" -> {
                                // Override values: feed, rapid, spindle
                            }
                            "Pn" -> pinState = parsePinState(value)
                        }
                    }
                    else -> {
                        // Machine state
                        state = when (part) {
                            "Idle" -> GrblMachineState.IDLE
                            "Run" -> GrblMachineState.RUN
                            "Hold" -> GrblMachineState.HOLD
                            "Jog" -> GrblMachineState.JOG
                            "Alarm" -> GrblMachineState.ALARM
                            "Check" -> GrblMachineState.CHECK
                            "Door" -> GrblMachineState.DOOR
                            "Sleep" -> GrblMachineState.SLEEP
                            "Home" -> GrblMachineState.HOME
                            else -> GrblMachineState.UNKNOWN
                        }
                    }
                }
            }
            
            _grblStatus.value = GrblStatus(
                state = state,
                mpos = mpos,
                wpos = wpos,
                feedRate = feedRate,
                spindleSpeed = spindleSpeed,
                lineNumber = lineNumber,
                bufferAvailable = bufferAvailable,
                pinState = pinState
            )
        } catch (e: Exception) {
            // Parse error - ignore malformed status
        }
    }

    private fun parsePosition(value: String): Position {
        val coords = value.split(",")
        return Position(
            x = coords.getOrNull(0)?.toFloatOrNull() ?: 0f,
            y = coords.getOrNull(1)?.toFloatOrNull() ?: 0f,
            z = coords.getOrNull(2)?.toFloatOrNull() ?: 0f,
            a = coords.getOrNull(3)?.toFloatOrNull() ?: 0f
        )
    }

    private fun parsePinState(value: String): PinState {
        return PinState(
            limitX = value.contains("X"),
            limitY = value.contains("Y"),
            limitZ = value.contains("Z"),
            limitA = value.contains("A"),
            probe = value.contains("P"),
            door = value.contains("D"),
            hold = value.contains("H"),
            softReset = value.contains("R"),
            cycleStart = value.contains("S")
        )
    }

    private fun parseSetting(data: String) {
        // Format: $0=10.0 (step pulse, usec)
        try {
            val settingPart = data.substringBefore(" ")
            val id = settingPart.substringAfter("$").substringBefore("=").toInt()
            val value = settingPart.substringAfter("=").toDouble()
            val desc = data.substringAfter("(").substringBefore(")")
            
            _grblSettings.value = _grblSettings.value.toMutableMap().apply {
                put(id, value)
            }
        } catch (e: Exception) {
            // Parse error
        }
    }

    private fun parseFeedback(data: String) {
        // Handle various feedback messages
        when {
            data.contains("MSG:") -> {
                // Message feedback
            }
            data.contains("GC:") -> {
                // G-code parser state
            }
            data.contains("HLP:") -> {
                // Help message
            }
            data.contains("VER:") -> {
                // Version info
            }
            data.contains("OPT:") -> {
                // Compile options
            }
            data.contains("G54:") || data.contains("G55:") || 
            data.contains("G56:") || data.contains("G57:") ||
            data.contains("G58:") || data.contains("G59:") -> {
                // Work coordinate offsets
            }
        }
    }

    private fun parseProbeResult(data: String) {
        // Format: [PRB:0.000,0.000,0.000:1]
        try {
            val content = data.substring(5, data.length - 1) // Remove [PRB: and ]
            val parts = content.split(":")
            val pos = parsePosition(parts[0])
            val success = parts.getOrNull(1)?.toIntOrNull() == 1
            // Emit probe result if needed
        } catch (e: Exception) {
            // Parse error
        }
    }

    private fun parseWorkCoordinateOffset(data: String) {
        // Format: [WCO:0.000,0.000,0.000]
        try {
            val content = data.substring(5, data.length - 1)
            val offset = parsePosition(content)
            // Store WCO
        } catch (e: Exception) {
            // Parse error
        }
    }

    // ==================== Status Polling ====================

    private fun startStatusPolling() {
        statusJob?.cancel()
        statusJob = scope.launch {
            while (isActive) {
                if (connectionManager.isConnected()) {
                    connectionManager.send(CMD_STATUS_QUERY)
                }
                delay(200) // 5Hz status updates
            }
        }
    }

    private fun stopStatusPolling() {
        statusJob?.cancel()
        statusJob = null
    }

    // ==================== GRBL Settings ====================

    suspend fun loadSettings() {
        connectionManager.send("$$")
    }

    suspend fun setSetting(settingId: Int, value: Double): Result<Unit> {
        return connectionManager.send("$$settingId=$value")
    }

    // ==================== Jogging ====================

    /**
     * GRBL v1.1 Jogging (using $J=...)
     */
    suspend fun jog(axis: String, distance: Float, feedRate: Int = 1000): Result<Unit> {
        val jogCommand = "$J=G91 G21 ${axis}${distance} F$feedRate"
        return connectionManager.send(jogCommand)
    }

    suspend fun jogContinuous(axis: String, direction: Float, feedRate: Int = 1000): Result<Unit> {
        // For continuous jogging, use a large distance
        val jogCommand = "$J=G91 G21 ${axis}${direction * 1000} F$feedRate"
        return connectionManager.send(jogCommand)
    }

    fun cancelJog() {
        scope.launch {
            sendRealtimeCommand(CMD_JOG_CANCEL)
        }
    }

    // ==================== Overrides ====================

    fun setFeedOverride(percent: Int) {
        scope.launch {
            when {
                percent == 100 -> sendRealtimeCommand(CMD_FEED_OVERRIDE_100)
                percent > 100 -> repeat((percent - 100) / 10) { sendRealtimeCommand(CMD_FEED_OVERRIDE_PLUS_10) }
                percent < 100 -> repeat((100 - percent) / 10) { sendRealtimeCommand(CMD_FEED_OVERRIDE_MINUS_10) }
            }
        }
    }

    fun setSpindleOverride(percent: Int) {
        scope.launch {
            when {
                percent == 100 -> sendRealtimeCommand(CMD_SPINDLE_OVERRIDE_100)
                percent > 100 -> repeat((percent - 100) / 10) { sendRealtimeCommand(CMD_SPINDLE_OVERRIDE_PLUS_10) }
                percent < 100 -> repeat((100 - percent) / 10) { sendRealtimeCommand(CMD_SPINDLE_OVERRIDE_MINUS_10) }
            }
        }
    }

    // ==================== Homing & Probing ====================

    suspend fun home(): Result<Unit> {
        return connectionManager.send("$H")
    }

    suspend fun probe(direction: String, distance: Float, feedRate: Int = 100): Result<Unit> {
        return connectionManager.send("G38.2 ${direction}${distance} F$feedRate")
    }

    // ==================== Work Coordinates ====================

    suspend fun setWorkPosition(axis: String? = null) {
        if (axis == null) {
            connectionManager.send("G10 L20 P${currentWCS + 1} X0 Y0 Z0")
        } else {
            connectionManager.send("G10 L20 P${currentWCS + 1} ${axis}0")
        }
    }

    suspend fun setWorkCoordinateSystem(wcs: Int) {
        if (wcs in 0..5) {
            currentWCS = wcs
            val gCode = when (wcs) {
                0 -> "G54"
                1 -> "G55"
                2 -> "G56"
                3 -> "G57"
                4 -> "G58"
                5 -> "G59"
                else -> "G54"
            }
            connectionManager.send(gCode)
        }
    }

    // ==================== Utility ====================

    private fun getErrorMessage(code: Int): String {
        return when (code) {
            1 -> "G-code words consist of a letter and a value. Letter was not found."
            2 -> "Numeric value format is not valid or missing an expected value."
            3 -> "Grbl '$' system command was not recognized or supported."
            4 -> "Negative value received for an expected positive value."
            5 -> "Homing cycle is not enabled via settings."
            6 -> "Minimum step pulse time must be greater than 3usec"
            7 -> "EEPROM read failed. Reset and restored to default values."
            8 -> "Grbl '$' command cannot be used unless Grbl is IDLE."
            9 -> "G-code locked out during alarm or jog state."
            10 -> "Soft limits cannot be enabled without homing also enabled."
            11 -> "Max characters per line exceeded. Line was not processed."
            12 -> "Grbl '$' setting value cause the step rate to exceed the maximum."
            13 -> "Safety door detected as opened and door state initiated."
            14 -> "Build info or startup line exceeded EEPROM line length limit."
            15 -> "Jog target exceeds machine travel. Command ignored."
            16 -> "Jog command with no '=' or contains prohibited g-code."
            17 -> "Laser mode requires PWM output."
            20 -> "Unsupported or invalid g-code command found."
            21 -> "More than one g-code command from same modal group found."
            22 -> "Feed rate has not yet been set or is undefined."
            23 -> "G-code command requires an integer value."
            24 -> "Two G-code commands in same block require axis words."
            25 -> "G-code motion command target is invalid."
            26 -> "G-code motion command target exceeds machine travel."
            27 -> "G-code motion command has invalid target."
            28 -> "G-code motion command has no axis words."
            29 -> "G-code arc has invalid parameters."
            30 -> "G-code arc has invalid radius."
            31 -> "G-code arc has missing axis words."
            32 -> "G-code plane selection is invalid."
            33 -> "G-code motion command has invalid probe target."
            34 -> "G-code motion command has invalid probe parameters."
            35 -> "G-code tool change command is invalid."
            36 -> "G-code spindle speed command is invalid."
            37 -> "G-code coolant command is invalid."
            38 -> "G-code tool length offset command is invalid."
            39 -> "G-code coordinate system is invalid."
            40 -> "G-code scaling is invalid."
            41 -> "G-code rotation is invalid."
            42 -> "G-code mirror is invalid."
            43 -> "G-code canned cycle is invalid."
            44 -> "G-code compensation is invalid."
            45 -> "G-code macro is invalid."
            46 -> "G-code parameter is invalid."
            47 -> "G-code expression is invalid."
            48 -> "G-code variable is invalid."
            49 -> "G-code label is invalid."
            50 -> "G-code subroutine is invalid."
            51 -> "G-code condition is invalid."
            52 -> "G-code loop is invalid."
            53 -> "G-code branch is invalid."
            54 -> "G-code input is invalid."
            55 -> "G-code output is invalid."
            56 -> "G-code configuration is invalid."
            57 -> "G-code runtime error."
            58 -> "G-code stack overflow."
            59 -> "G-code memory error."
            60 -> "G-code divide by zero."
            61 -> "G-code domain error."
            62 -> "G-code range error."
            63 -> "G-code overflow error."
            64 -> "G-code underflow error."
            65 -> "G-code precision error."
            66 -> "G-code accuracy error."
            67 -> "G-code tolerance error."
            68 -> "G-code convergence error."
            69 -> "G-code iteration error."
            70 -> "G-code timeout error."
            else -> "Unknown error code"
        }
    }

    private fun getAlarmMessage(code: Int): String {
        return when (code) {
            1 -> "Hard limit triggered. Machine position is likely lost. Re-homing is recommended."
            2 -> "G-code motion target exceeds machine travel. Machine position safely retained."
            3 -> "Reset while in motion. Grbl cannot guarantee position. Re-homing recommended."
            4 -> "Probe fail. The probe is not in the expected initial state."
            5 -> "Probe fail. Probe did not contact the workpiece."
            6 -> "Homing fail. Reset during active homing cycle."
            7 -> "Homing fail. Safety door was opened during homing cycle."
            8 -> "Homing fail. Cycle failed to clear limit switch."
            9 -> "Homing fail. Could not find limit switch."
            10 -> "Homing fail. Limit switch not found within search distance."
            11 -> "Homing fail. Limit switch noise or debounce error."
            12 -> "Homing fail. Limit switch not found after pull-off."
            13 -> "Homing fail. Limit switch still engaged after pull-off."
            14 -> "Homing fail. Homing direction not set correctly."
            15 -> "Homing fail. Homing rate not set correctly."
            16 -> "Homing fail. Pull-off not set correctly."
            17 -> "Homing fail. Limit switch configuration error."
            18 -> "Homing fail. Homing cycle aborted."
            19 -> "Homing fail. Homing cycle failed."
            20 -> "Homing fail. Unknown homing error."
            else -> "Unknown alarm code"
        }
    }

    fun cleanup() {
        stopStatusPolling()
        scope.cancel()
    }
}
