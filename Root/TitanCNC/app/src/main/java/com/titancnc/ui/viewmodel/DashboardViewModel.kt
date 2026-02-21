package com.titancnc.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.titancnc.service.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val connectionManager: ConnectionManager,
    private val gCodeSender: GCodeSender
) : ViewModel() {

    // Connection State
    val connectionState: StateFlow<ConnectionState> = connectionManager.connectionState

    // GRBL Status
    val grblStatus: StateFlow<GrblStatus> = gCodeSender.grblStatus

    // Job State
    val sendState: StateFlow<SendState> = gCodeSender.sendState
    val progress: StateFlow<JobProgress> = gCodeSender.progress

    // Overrides
    private val _feedOverride = MutableStateFlow(100)
    val feedOverride: StateFlow<Int> = _feedOverride.asStateFlow()

    private val _spindleOverride = MutableStateFlow(100)
    val spindleOverride: StateFlow<Int> = _spindleOverride.asStateFlow()

    // Jogging State
    private var isJogging = false

    init {
        // Initialize with default state
    }

    // ==================== Emergency Stop ====================

    fun emergencyStop() {
        viewModelScope.launch {
            // Send soft reset
            gCodeSender.sendRealtimeCommand(GCodeSender.CMD_SOFT_RESET)
            // Stop any running job
            gCodeSender.stopJob()
        }
    }

    // ==================== Jogging ====================

    fun jog(axis: String, distance: Float) {
        viewModelScope.launch {
            if (grblStatus.value.state == GrblMachineState.IDLE ||
                grblStatus.value.state == GrblMachineState.JOG) {
                gCodeSender.jog(axis, distance)
            }
        }
    }

    fun jogContinuous(axis: String, direction: Float) {
        viewModelScope.launch {
            if (!isJogging && (grblStatus.value.state == GrblMachineState.IDLE ||
                             grblStatus.value.state == GrblMachineState.JOG)) {
                isJogging = true
                gCodeSender.jogContinuous(axis, direction)
            }
        }
    }

    fun cancelJog() {
        viewModelScope.launch {
            if (isJogging) {
                isJogging = false
                gCodeSender.cancelJog()
            }
        }
    }

    // ==================== Homing ====================

    fun home() {
        viewModelScope.launch {
            if (grblStatus.value.state == GrblMachineState.IDLE) {
                gCodeSender.home()
            }
        }
    }

    // ==================== Work Position ====================

    fun setWorkPosition(axis: String?) {
        viewModelScope.launch {
            if (grblStatus.value.state == GrblMachineState.IDLE) {
                gCodeSender.setWorkPosition(axis)
            }
        }
    }

    fun probeZ() {
        viewModelScope.launch {
            if (grblStatus.value.state == GrblMachineState.IDLE) {
                // Probe in negative Z direction
                gCodeSender.probe("Z", -50f, 100)
            }
        }
    }

    // ==================== Overrides ====================

    fun setFeedOverride(percent: Int) {
        _feedOverride.value = percent.coerceIn(10, 200)
        gCodeSender.setFeedOverride(_feedOverride.value)
    }

    fun setSpindleOverride(percent: Int) {
        _spindleOverride.value = percent.coerceIn(10, 200)
        gCodeSender.setSpindleOverride(_spindleOverride.value)
    }

    // ==================== Job Control ====================

    fun pauseJob() {
        gCodeSender.pauseJob()
    }

    fun resumeJob() {
        gCodeSender.resumeJob()
    }

    fun stopJob() {
        gCodeSender.stopJob()
    }

    // ==================== Connection ====================

    fun connectUsb(device: android.hardware.usb.UsbDevice) {
        viewModelScope.launch {
            connectionManager.connectUsb(device)
        }
    }

    fun connectBluetooth(device: android.bluetooth.BluetoothDevice) {
        viewModelScope.launch {
            connectionManager.connectBluetooth(device)
        }
    }

    fun connectWiFi(host: String, port: Int) {
        viewModelScope.launch {
            connectionManager.connectWiFi(host, port)
        }
    }

    fun connectWebSocket(url: String) {
        viewModelScope.launch {
            connectionManager.connectWebSocket(url)
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            connectionManager.disconnect()
        }
    }

    // ==================== Cleanup ====================

    override fun onCleared() {
        super.onCleared()
        connectionManager.cleanup()
        gCodeSender.cleanup()
    }
}
