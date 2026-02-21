package com.titancnc.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.titancnc.service.ConnectionState
import com.titancnc.ui.theme.*
import com.titancnc.ui.viewmodel.ConsoleViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsoleScreen(
    viewModel: ConsoleViewModel = hiltViewModel()
) {
    val consoleOutput by viewModel.consoleOutput.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val availableDevices by viewModel.availableDevices.collectAsState()
    val commandHistory by viewModel.commandHistory.collectAsState()
    
    var commandInput by remember { mutableStateOf("") }
    var showDeviceDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }
    
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    
    // Auto-scroll to bottom
    LaunchedEffect(consoleOutput.size) {
        if (consoleOutput.isNotEmpty()) {
            listState.animateScrollToItem(consoleOutput.size - 1)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "CONSOLE",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                    titleContentColor = CyanAccent
                ),
                actions = {
                    // Connection button
                    IconButton(onClick = { showDeviceDialog = true }) {
                        val iconColor = when (connectionState) {
                            is ConnectionState.Connected -> SuccessGreen
                            is ConnectionState.Connecting -> WarningOrange
                            else -> TextSecondary
                        }
                        Icon(
                            Icons.Rounded.Usb,
                            contentDescription = "Connect",
                            tint = iconColor
                        )
                    }
                    
                    // Clear console
                    IconButton(onClick = { viewModel.clearConsole() }) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Clear", tint = TextSecondary)
                    }
                    
                    // Settings
                    IconButton(onClick = { /* Open settings */ }) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = TextSecondary)
                    }
                }
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Connection status bar
            ConnectionStatusBar(
                state = connectionState,
                onConnectClick = { showDeviceDialog = true },
                onDisconnectClick = { viewModel.disconnect() }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Console output
            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkerBackground),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(consoleOutput) { line ->
                        ConsoleLine(line)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Command input
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = commandInput,
                    onValueChange = { commandInput = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Enter command...", color = TextSecondary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanAccent,
                        unfocusedBorderColor = BorderColor,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Rounded.ChevronRight, null, tint = CyanAccent)
                    }
                )
                
                IconButton(
                    onClick = {
                        if (commandInput.isNotBlank()) {
                            viewModel.sendCommand(commandInput)
                            commandInput = ""
                        }
                    },
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CyanAccent),
                    enabled = connectionState is ConnectionState.Connected
                ) {
                    Icon(
                        Icons.Rounded.Send,
                        contentDescription = "Send",
                        tint = DarkBackground
                    )
                }
            }
            
            // Quick commands
            QuickCommandsRow(
                onHome = { viewModel.sendCommand("$H") },
                onUnlock = { viewModel.sendCommand("$X") },
                onStatus = { viewModel.sendCommand("?") },
                onSettings = { viewModel.sendCommand("$$") },
                onReset = { viewModel.sendCommand("\u0018") },
                isEnabled = connectionState is ConnectionState.Connected
            )
        }
    }
    
    // Device selection dialog
    if (showDeviceDialog) {
        DeviceSelectionDialog(
            devices = availableDevices,
            onConnect = { device ->
                viewModel.connectToDevice(device)
                showDeviceDialog = false
            },
            onDismiss = { showDeviceDialog = false }
        )
    }
}

@Composable
fun ConnectionStatusBar(
    state: ConnectionState,
    onConnectClick: () -> Unit,
    onDisconnectClick: () -> Unit
) {
    val (backgroundColor, textColor, statusText) = when (state) {
        is ConnectionState.Connected -> Triple(
            SuccessGreen.copy(alpha = 0.2f),
            SuccessGreen,
            "Connected: ${(state as ConnectionState.Connected).deviceName}"
        )
        is ConnectionState.Connecting -> Triple(
            WarningOrange.copy(alpha = 0.2f),
            WarningOrange,
            "Connecting..."
        )
        is ConnectionState.Error -> Triple(
            ErrorRed.copy(alpha = 0.2f),
            ErrorRed,
            "Error: ${(state as ConnectionState.Error).message}"
        )
        ConnectionState.Disconnected -> Triple(
            ButtonBackground,
            TextSecondary,
            "Disconnected"
        )
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(textColor)
            )
            Text(
                text = statusText,
                color = textColor,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        
        if (state is ConnectionState.Connected) {
            TextButton(onClick = onDisconnectClick) {
                Text("DISCONNECT", color = ErrorRed, fontSize = 12.sp)
            }
        } else {
            TextButton(onClick = onConnectClick) {
                Text("CONNECT", color = CyanAccent, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun ConsoleLine(line: ConsoleLineItem) {
    val color = when (line.type) {
        ConsoleLineType.INPUT -> CyanAccent
        ConsoleLineType.OUTPUT -> TextPrimary
        ConsoleLineType.ERROR -> ErrorRed
        ConsoleLineType.WARNING -> WarningOrange
        ConsoleLineType.SUCCESS -> SuccessGreen
        ConsoleLineType.SYSTEM -> TextSecondary
    }
    
    val prefix = when (line.type) {
        ConsoleLineType.INPUT -> "> "
        ConsoleLineType.ERROR -> "! "
        ConsoleLineType.WARNING -> "? "
        else -> ""
    }
    
    Text(
        text = prefix + line.text,
        color = color,
        fontSize = 13.sp,
        fontFamily = FontFamily.Monospace,
        lineHeight = 18.sp
    )
}

@Composable
fun QuickCommandsRow(
    onHome: () -> Unit,
    onUnlock: () -> Unit,
    onStatus: () -> Unit,
    onSettings: () -> Unit,
    onReset: () -> Unit,
    isEnabled: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        QuickCommandButton("$H", "Home", onHome, isEnabled, Modifier.weight(1f))
        QuickCommandButton("$X", "Unlock", onUnlock, isEnabled, Modifier.weight(1f))
        QuickCommandButton("?", "Status", onStatus, isEnabled, Modifier.weight(1f))
        QuickCommandButton("$$", "Settings", onSettings, isEnabled, Modifier.weight(1f))
        QuickCommandButton("RST", "Reset", onReset, isEnabled, Modifier.weight(1f), ErrorRed)
    }
}

@Composable
fun QuickCommandButton(
    label: String,
    description: String,
    onClick: () -> Unit,
    isEnabled: Boolean,
    modifier: Modifier = Modifier,
    color: Color = CyanAccent
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Button(
            onClick = onClick,
            enabled = isEnabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = color.copy(alpha = 0.2f),
                contentColor = color,
                disabledContainerColor = Color.Gray.copy(alpha = 0.1f),
                disabledContentColor = Color.Gray
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = description,
            color = TextSecondary,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun DeviceSelectionDialog(
    devices: List<DeviceInfo>,
    onConnect: (DeviceInfo) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBackground,
        title = {
            Text(
                text = "SELECT DEVICE",
                color = CyanAccent,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                if (devices.isEmpty()) {
                    Text(
                        text = "No devices found. Make sure your CNC controller is connected.",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                } else {
                    devices.forEach { device ->
                        DeviceItem(device = device, onClick = { onConnect(device) })
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

@Composable
fun DeviceItem(device: DeviceInfo, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = ButtonBackground),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = when (device.type) {
                    DeviceType.USB -> Icons.Rounded.Usb
                    DeviceType.BLUETOOTH -> Icons.Rounded.Bluetooth
                    DeviceType.WIFI -> Icons.Rounded.Wifi
                },
                contentDescription = null,
                tint = CyanAccent
            )
            Column {
                Text(
                    text = device.name,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = device.address,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

// Data classes
data class ConsoleLineItem(
    val text: String,
    val type: ConsoleLineType,
    val timestamp: Long = System.currentTimeMillis()
)

enum class ConsoleLineType {
    INPUT,      // User input
    OUTPUT,     // Normal output from CNC
    ERROR,      // Error messages
    WARNING,    // Warning messages
    SUCCESS,    // Success messages
    SYSTEM      // System messages
}

data class DeviceInfo(
    val name: String,
    val address: String,
    val type: DeviceType
)

enum class DeviceType {
    USB,
    BLUETOOTH,
    WIFI
}
