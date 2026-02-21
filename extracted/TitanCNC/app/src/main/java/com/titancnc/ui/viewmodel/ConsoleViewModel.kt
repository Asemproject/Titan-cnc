package com.titancnc.ui.viewmodel

import android.hardware.usb.UsbDevice
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.titancnc.service.ConnectionManager
import com.titancnc.service.ConnectionState
import com.titancnc.ui.screens.ConsoleLineItem
import com.titancnc.ui.screens.ConsoleLineType
import com.titancnc.ui.screens.DeviceInfo
import com.titancnc.ui.screens.DeviceType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConsoleViewModel @Inject constructor(
    private val connectionManager: ConnectionManager
) : ViewModel() {

    private val _consoleOutput = MutableStateFlow<List<ConsoleLineItem>>(emptyList())
    val consoleOutput: StateFlow<List<ConsoleLineItem>> = _consoleOutput.asStateFlow()

    val connectionState: StateFlow<ConnectionState> = connectionManager.connectionState

    private val _availableDevices = MutableStateFlow<List<DeviceInfo>>(emptyList())
    val availableDevices: StateFlow<List<DeviceInfo>> = _availableDevices.asStateFlow()

    private val _commandHistory = MutableStateFlow<List<String>>(emptyList())
    val commandHistory: StateFlow<List<String>> = _commandHistory.asStateFlow()

    private var historyIndex = -1

    init {
        // Listen for received data
        viewModelScope.launch {
            connectionManager.receivedData.collect { data ->
                addConsoleLine(data, ConsoleLineType.OUTPUT)
            }
        }

        // Listen for connection state changes
        viewModelScope.launch {
            connectionState.collect { state ->
                when (state) {
                    is ConnectionState.Connected -> {
                        addConsoleLine("Connected to ${state.deviceName}", ConsoleLineType.SUCCESS)
                    }
                    is ConnectionState.Error -> {
                        addConsoleLine("Error: ${state.message}", ConsoleLineType.ERROR)
                    }
                    ConnectionState.Disconnected -> {
                        addConsoleLine("Disconnected", ConsoleLineType.SYSTEM)
                    }
                    else -> {}
                }
            }
        }
    }

    fun sendCommand(command: String) {
        viewModelScope.launch {
            addConsoleLine("> $command", ConsoleLineType.INPUT)
            
            // Add to history
            _commandHistory.value = (_commandHistory.value + command).takeLast(50)
            historyIndex = -1
            
            connectionManager.send(command).onFailure { error ->
                addConsoleLine("Error: ${error.message}", ConsoleLineType.ERROR)
            }
        }
    }

    fun clearConsole() {
        _consoleOutput.value = emptyList()
    }

    fun connectToDevice(device: DeviceInfo) {
        viewModelScope.launch {
            when (device.type) {
                DeviceType.USB -> {
                    // USB connection would need the actual UsbDevice
                    addConsoleLine("USB connection not implemented in this view", ConsoleLineType.WARNING)
                }
                DeviceType.BLUETOOTH -> {
                    // Bluetooth connection
                    addConsoleLine("Connecting to ${device.name}...", ConsoleLineType.SYSTEM)
                }
                DeviceType.WIFI -> {
                    val parts = device.address.split(":")
                    if (parts.size == 2) {
                        connectionManager.connectWiFi(parts[0], parts[1].toIntOrNull() ?: 23)
                    }
                }
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            connectionManager.disconnect()
        }
    }

    fun scanForDevices() {
        viewModelScope.launch {
            // Scan for USB devices
            val usbDevices = connectionManager.getAvailableUsbDevices().map {
                DeviceInfo(
                    name = it.productName ?: "USB Device",
                    address = "${it.vendorId}:${it.productId}",
                    type = DeviceType.USB
                )
            }

            // Scan for Bluetooth devices
            val btDevices = connectionManager.getPairedBluetoothDevices().map {
                DeviceInfo(
                    name = it.name ?: "Bluetooth Device",
                    address = it.address,
                    type = DeviceType.BLUETOOTH
                )
            }

            _availableDevices.value = usbDevices + btDevices
        }
    }

    fun getPreviousCommand(): String? {
        val history = _commandHistory.value
        if (history.isEmpty()) return null
        
        historyIndex = (historyIndex + 1).coerceAtMost(history.size - 1)
        return history[history.size - 1 - historyIndex]
    }

    fun getNextCommand(): String? {
        val history = _commandHistory.value
        if (history.isEmpty()) return null
        
        historyIndex = (historyIndex - 1).coerceAtLeast(0)
        return if (historyIndex == 0) "" else history[history.size - 1 - historyIndex]
    }

    private fun addConsoleLine(text: String, type: ConsoleLineType) {
        _consoleOutput.value = (_consoleOutput.value + ConsoleLineItem(text, type)).takeLast(1000)
    }
}
