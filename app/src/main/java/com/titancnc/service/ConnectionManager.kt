package com.titancnc.service

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import com.hoho.android.usbserial.driver.*
import com.hoho.android.usbserial.util.SerialInputOutputManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data object Connecting : ConnectionState()
    data class Connected(val deviceName: String, val connectionType: ConnectionType) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

sealed class ConnectionType {
    data class USB(val vendorId: Int, val productId: Int) : ConnectionType()
    data class Bluetooth(val address: String) : ConnectionType()
    data class WiFi(val host: String, val port: Int) : ConnectionType()
    data class WebSocket(val url: String) : ConnectionType()
}

sealed class CNCResponse {
    data class Data(val data: String) : CNCResponse()
    data class Error(val error: Throwable) : CNCResponse()
    data object Disconnected : CNCResponse()
}

interface CNCConnection {
    suspend fun connect(): Result<Unit>
    suspend fun disconnect()
    suspend fun send(data: String): Result<Unit>
    fun receive(): Flow<CNCResponse>
    fun isConnected(): Boolean
}

@Singleton
class ConnectionManager @Inject constructor(
    private val context: Context
) {
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var currentConnection: CNCConnection? = null
    private val _receivedData = MutableSharedFlow<String>(extraBufferCapacity = 100)
    val receivedData: SharedFlow<String> = _receivedData.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val usbManager: UsbManager by lazy {
        context.getSystemService(Context.USB_SERVICE) as UsbManager
    }
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        BluetoothAdapter.getDefaultAdapter()
    }

    companion object {
        private const val ACTION_USB_PERMISSION = "com.titancnc.USB_PERMISSION"
        private const val BAUD_RATE = 115200
        private const val DATA_BITS = 8
        private const val STOP_BITS = UsbSerialPort.STOPBITS_1
        private const val PARITY = UsbSerialPort.PARITY_NONE
        
        // Common USB-to-Serial chip Vendor IDs
        private val SUPPORTED_VENDORS = mapOf(
            0x1A86 to "CH340/CH341",    // QinHeng
            0x10C4 to "CP2102",         // Silicon Labs
            0x0403 to "FTDI",           // FTDI
            0x067B to "PL2303",         // Prolific
            0x0483 to "STM32",          // STMicroelectronics
            0x2341 to "Arduino",        // Arduino
            0x2A03 to "Arduino",        // Arduino (alternate)
            0x16C0 to "Teensy",         // PJRC
            0x03EB to "Atmel"           // Atmel
        )
    }

    private val usbPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_USB_PERMISSION) {
                synchronized(this) {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    
                    if (granted && device != null) {
                        scope.launch {
                            connectToUsbDevice(device)
                        }
                    } else {
                        _connectionState.value = ConnectionState.Error("USB permission denied")
                    }
                }
            }
        }
    }

    init {
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        ContextCompat.registerReceiver(
            context,
            usbPermissionReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    // ==================== USB Connection ====================
    
    fun getAvailableUsbDevices(): List<UsbDevice> {
        return usbManager.deviceList.values.filter { device ->
            SUPPORTED_VENDORS.containsKey(device.vendorId) ||
            isCncDevice(device)
        }
    }

    private fun isCncDevice(device: UsbDevice): Boolean {
        // Check for common CNC controller interfaces
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            if (intf.interfaceClass == 0x02 && intf.interfaceSubclass == 0x02) {
                return true // CDC-ACM (Communication Device Class)
            }
        }
        return false
    }

    fun requestUsbPermission(device: UsbDevice) {
        val permissionIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(ACTION_USB_PERMISSION),
            PendingIntent.FLAG_MUTABLE
        )
        usbManager.requestPermission(device, permissionIntent)
    }

    suspend fun connectUsb(device: UsbDevice): Result<Unit> = withContext(Dispatchers.IO) {
        if (!usbManager.hasPermission(device)) {
            requestUsbPermission(device)
            return@withContext Result.failure(IllegalStateException("USB permission not granted"))
        }
        connectToUsbDevice(device)
    }

    private suspend fun connectToUsbDevice(device: UsbDevice): Result<Unit> {
        _connectionState.value = ConnectionState.Connecting
        
        return try {
            val driver = UsbSerialProber.getDefaultProber().probeDevice(device)
                ?: return Result.failure(IllegalStateException("No driver for device"))
            
            val connection = usbManager.openDevice(device)
                ?: return Result.failure(IllegalStateException("Failed to open USB device"))
            
            val port = driver.ports.firstOrNull()
                ?: return Result.failure(IllegalStateException("No serial port available"))
            
            port.open(connection)
            port.setParameters(BAUD_RATE, DATA_BITS, STOP_BITS, PARITY)
            
            val usbConnection = UsbCncConnection(port)
            currentConnection = usbConnection
            
            // Start listening for data
            scope.launch {
                usbConnection.receive().collect { response ->
                    when (response) {
                        is CNCResponse.Data -> _receivedData.emit(response.data)
                        is CNCResponse.Error -> {
                            _connectionState.value = ConnectionState.Error(response.error.message ?: "Connection error")
                        }
                        CNCResponse.Disconnected -> disconnect()
                    }
                }
            }
            
            _connectionState.value = ConnectionState.Connected(
                deviceName = device.productName ?: "USB Device",
                connectionType = ConnectionType.USB(device.vendorId, device.productId)
            )
            
            Result.success(Unit)
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.Error(e.message ?: "USB connection failed")
            Result.failure(e)
        }
    }

    // ==================== Bluetooth Connection ====================
    
    fun getPairedBluetoothDevices(): List<BluetoothDevice> {
        return bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
    }

    @Suppress("MissingPermission")
    suspend fun connectBluetooth(device: BluetoothDevice): Result<Unit> = withContext(Dispatchers.IO) {
        _connectionState.value = ConnectionState.Connecting
        
        try {
            val btConnection = BluetoothCncConnection(device, bluetoothAdapter!!)
            btConnection.connect().getOrThrow()
            currentConnection = btConnection
            
            // Start listening for data
            scope.launch {
                btConnection.receive().collect { response ->
                    when (response) {
                        is CNCResponse.Data -> _receivedData.emit(response.data)
                        is CNCResponse.Error -> {
                            _connectionState.value = ConnectionState.Error(response.error.message ?: "BT error")
                        }
                        CNCResponse.Disconnected -> disconnect()
                    }
                }
            }
            
            _connectionState.value = ConnectionState.Connected(
                deviceName = device.name ?: "Bluetooth Device",
                connectionType = ConnectionType.Bluetooth(device.address)
            )
            
            Result.success(Unit)
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.Error(e.message ?: "Bluetooth connection failed")
            Result.failure(e)
        }
    }

    // ==================== WiFi Connection ====================
    
    suspend fun connectWiFi(host: String, port: Int): Result<Unit> = withContext(Dispatchers.IO) {
        _connectionState.value = ConnectionState.Connecting
        
        try {
            val wifiConnection = WiFiCncConnection(host, port)
            wifiConnection.connect().getOrThrow()
            currentConnection = wifiConnection
            
            scope.launch {
                wifiConnection.receive().collect { response ->
                    when (response) {
                        is CNCResponse.Data -> _receivedData.emit(response.data)
                        is CNCResponse.Error -> {
                            _connectionState.value = ConnectionState.Error(response.error.message ?: "WiFi error")
                        }
                        CNCResponse.Disconnected -> disconnect()
                    }
                }
            }
            
            _connectionState.value = ConnectionState.Connected(
                deviceName = "$host:$port",
                connectionType = ConnectionType.WiFi(host, port)
            )
            
            Result.success(Unit)
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.Error(e.message ?: "WiFi connection failed")
            Result.failure(e)
        }
    }

    // ==================== WebSocket Connection (FluidNC/grblHAL) ====================
    
    suspend fun connectWebSocket(url: String): Result<Unit> = withContext(Dispatchers.IO) {
        _connectionState.value = ConnectionState.Connecting
        
        try {
            val wsConnection = WebSocketCncConnection(url)
            wsConnection.connect().getOrThrow()
            currentConnection = wsConnection
            
            scope.launch {
                wsConnection.receive().collect { response ->
                    when (response) {
                        is CNCResponse.Data -> _receivedData.emit(response.data)
                        is CNCResponse.Error -> {
                            _connectionState.value = ConnectionState.Error(response.error.message ?: "WebSocket error")
                        }
                        CNCResponse.Disconnected -> disconnect()
                    }
                }
            }
            
            _connectionState.value = ConnectionState.Connected(
                deviceName = url,
                connectionType = ConnectionType.WebSocket(url)
            )
            
            Result.success(Unit)
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.Error(e.message ?: "WebSocket connection failed")
            Result.failure(e)
        }
    }

    // ==================== General Operations ====================
    
    suspend fun send(data: String): Result<Unit> {
        return currentConnection?.send(data) 
            ?: Result.failure(IllegalStateException("Not connected"))
    }

    suspend fun disconnect() {
        currentConnection?.disconnect()
        currentConnection = null
        _connectionState.value = ConnectionState.Disconnected
    }

    fun isConnected(): Boolean = currentConnection?.isConnected() ?: false

    fun cleanup() {
        scope.cancel()
        try {
            context.unregisterReceiver(usbPermissionReceiver)
        } catch (_: IllegalArgumentException) { }
        runBlocking {
            disconnect()
        }
    }
}

// ==================== USB Serial Connection Implementation ====================

class UsbCncConnection(
    private val port: UsbSerialPort
) : CNCConnection, SerialInputOutputManager.Listener {
    
    private val _dataFlow = MutableSharedFlow<CNCResponse>(extraBufferCapacity = 100)
    private var ioManager: SerialInputOutputManager? = null
    private val isConnected = AtomicBoolean(false)
    private val buffer = StringBuilder()
    
    override suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            ioManager = SerialInputOutputManager(port, this@UsbCncConnection)
            ioManager?.start()
            isConnected.set(true)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        isConnected.set(false)
        ioManager?.stop()
        ioManager = null
        try {
            port.close()
        } catch (_: Exception) { }
    }

    override suspend fun send(data: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val bytes = (data + "\n").toByteArray(Charsets.UTF_8)
            port.write(bytes, 1000)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun receive(): Flow<CNCResponse> = _dataFlow.asSharedFlow()

    override fun isConnected(): Boolean = isConnected.get()

    override fun onNewData(data: ByteArray?) {
        data?.let { bytes ->
            val received = String(bytes, Charsets.UTF_8)
            buffer.append(received)
            
            // Process complete lines
            var newlineIndex: Int
            while (buffer.indexOf('\n').also { newlineIndex = it } != -1) {
                val line = buffer.substring(0, newlineIndex).trim()
                buffer.delete(0, newlineIndex + 1)
                if (line.isNotEmpty()) {
                    _dataFlow.tryEmit(CNCResponse.Data(line))
                }
            }
        }
    }

    override fun onRunError(e: Exception) {
        _dataFlow.tryEmit(CNCResponse.Error(e))
        isConnected.set(false)
    }
}

// ==================== Bluetooth Connection Implementation ====================

class BluetoothCncConnection(
    private val device: BluetoothDevice,
    private val adapter: BluetoothAdapter
) : CNCConnection {
    
    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private val _dataFlow = MutableSharedFlow<CNCResponse>(extraBufferCapacity = 100)
    private val isConnected = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    companion object {
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    @Suppress("MissingPermission")
    override suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            adapter.cancelDiscovery()
            
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            socket?.connect()
            
            inputStream = socket?.inputStream
            outputStream = socket?.outputStream
            isConnected.set(true)
            
            // Start read loop
            scope.launch {
                readLoop()
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        isConnected.set(false)
        scope.cancel()
        try {
            inputStream?.close()
            outputStream?.close()
            socket?.close()
        } catch (_: Exception) { }
    }

    override suspend fun send(data: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            outputStream?.write((data + "\n").toByteArray(Charsets.UTF_8))
            outputStream?.flush()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun receive(): Flow<CNCResponse> = _dataFlow.asSharedFlow()

    override fun isConnected(): Boolean = isConnected.get()

    private suspend fun readLoop() {
        val buffer = ByteArray(1024)
        val lineBuffer = StringBuilder()
        
        while (isConnected.get()) {
            try {
                val bytesRead = inputStream?.read(buffer) ?: break
                if (bytesRead > 0) {
                    val data = String(buffer, 0, bytesRead, Charsets.UTF_8)
                    lineBuffer.append(data)
                    
                    // Process complete lines
                    var newlineIndex: Int
                    while (lineBuffer.indexOf('\n').also { newlineIndex = it } != -1) {
                        val line = lineBuffer.substring(0, newlineIndex).trim()
                        lineBuffer.delete(0, newlineIndex + 1)
                        if (line.isNotEmpty()) {
                            _dataFlow.emit(CNCResponse.Data(line))
                        }
                    }
                }
            } catch (e: Exception) {
                if (isConnected.get()) {
                    _dataFlow.emit(CNCResponse.Error(e))
                }
                break
            }
        }
        
        isConnected.set(false)
        _dataFlow.emit(CNCResponse.Disconnected)
    }
}

// ==================== WiFi/Telnet Connection Implementation ====================

class WiFiCncConnection(
    private val host: String,
    private val port: Int
) : CNCConnection {
    
    private var socket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private val _dataFlow = MutableSharedFlow<CNCResponse>(extraBufferCapacity = 100)
    private val isConnected = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            socket = Socket()
            socket?.connect(InetSocketAddress(host, port), 10000)
            
            inputStream = socket?.getInputStream()
            outputStream = socket?.getOutputStream()
            isConnected.set(true)
            
            scope.launch {
                readLoop()
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        isConnected.set(false)
        scope.cancel()
        try {
            inputStream?.close()
            outputStream?.close()
            socket?.close()
        } catch (_: Exception) { }
    }

    override suspend fun send(data: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            outputStream?.write((data + "\n").toByteArray(Charsets.UTF_8))
            outputStream?.flush()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun receive(): Flow<CNCResponse> = _dataFlow.asSharedFlow()

    override fun isConnected(): Boolean = isConnected.get()

    private suspend fun readLoop() {
        val buffer = ByteArray(1024)
        val lineBuffer = StringBuilder()
        
        while (isConnected.get()) {
            try {
                val bytesRead = inputStream?.read(buffer) ?: break
                if (bytesRead > 0) {
                    val data = String(buffer, 0, bytesRead, Charsets.UTF_8)
                    lineBuffer.append(data)
                    
                    // Process complete lines
                    var newlineIndex: Int
                    while (lineBuffer.indexOf('\n').also { newlineIndex = it } != -1) {
                        val line = lineBuffer.substring(0, newlineIndex).trim()
                        lineBuffer.delete(0, newlineIndex + 1)
                        if (line.isNotEmpty()) {
                            _dataFlow.emit(CNCResponse.Data(line))
                        }
                    }
                }
            } catch (e: Exception) {
                if (isConnected.get()) {
                    _dataFlow.emit(CNCResponse.Error(e))
                }
                break
            }
        }
        
        isConnected.set(false)
        _dataFlow.emit(CNCResponse.Disconnected)
    }
}

// ==================== WebSocket Connection Implementation ====================

class WebSocketCncConnection(
    private val url: String
) : CNCConnection {
    
    private var webSocket: WebSocket? = null
    private val _dataFlow = MutableSharedFlow<CNCResponse>(extraBufferCapacity = 100)
    private val isConnected = AtomicBoolean(false)
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .pingInterval(30, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun connect(): Result<Unit> = suspendCancellableCoroutine { continuation ->
        val request = Request.Builder()
            .url(url)
            .build()
        
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected.set(true)
                if (continuation.isActive) {
                    continuation.resume(Result.success(Unit)) { }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                _dataFlow.tryEmit(CNCResponse.Data(text.trim()))
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isConnected.set(false)
                _dataFlow.tryEmit(CNCResponse.Disconnected)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected.set(false)
                if (continuation.isActive) {
                    continuation.resume(Result.failure(t)) { }
                } else {
                    _dataFlow.tryEmit(CNCResponse.Error(t))
                }
            }
        })
    }

    override suspend fun disconnect() {
        isConnected.set(false)
        webSocket?.close(1000, "User disconnected")
        client.dispatcher.executorService.shutdown()
    }

    override suspend fun send(data: String): Result<Unit> {
        return if (webSocket?.send(data) == true) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("Failed to send WebSocket message"))
        }
    }

    override fun receive(): Flow<CNCResponse> = _dataFlow.asSharedFlow()

    override fun isConnected(): Boolean = isConnected.get()
}
