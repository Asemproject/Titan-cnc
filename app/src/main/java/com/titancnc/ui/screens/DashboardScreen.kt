package com.titancnc.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.titancnc.service.*
import com.titancnc.ui.theme.*
import com.titancnc.ui.viewmodel.DashboardViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val grblStatus by viewModel.grblStatus.collectAsState()
    val sendState by viewModel.sendState.collectAsState()
    val progress by viewModel.progress.collectAsState()
    
    val scrollState = rememberScrollState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "TITAN CNC",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                    titleContentColor = CyanAccent
                ),
                actions = {
                    ConnectionStatusIndicator(connectionState)
                }
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Emergency Stop - Always visible at top
            EmergencyStopButton(
                onClick = { viewModel.emergencyStop() },
                modifier = Modifier.fillMaxWidth()
            )
            
            // Digital Readout Display
            DRODisplay(
                mpos = grblStatus.mpos,
                wpos = grblStatus.wpos,
                feedRate = grblStatus.feedRate,
                spindleSpeed = grblStatus.spindleSpeed,
                machineState = grblStatus.state
            )
            
            // Jogging Controls
            JoggingControls(
                onJog = { axis, distance -> viewModel.jog(axis, distance) },
                onJogContinuous = { axis, direction -> viewModel.jogContinuous(axis, direction) },
                onCancelJog = { viewModel.cancelJog() },
                onHome = { viewModel.home() },
                isEnabled = grblStatus.state == GrblMachineState.IDLE || 
                           grblStatus.state == GrblMachineState.JOG
            )
            
            // Work Position Controls
            WorkPositionControls(
                onZeroX = { viewModel.setWorkPosition("X") },
                onZeroY = { viewModel.setWorkPosition("Y") },
                onZeroZ = { viewModel.setWorkPosition("Z") },
                onZeroAll = { viewModel.setWorkPosition(null) },
                onProbeZ = { viewModel.probeZ() },
                isEnabled = grblStatus.state == GrblMachineState.IDLE
            )
            
            // Override Controls
            OverrideControls(
                feedOverride = viewModel.feedOverride.collectAsState().value,
                spindleOverride = viewModel.spindleOverride.collectAsState().value,
                onFeedOverrideChange = { viewModel.setFeedOverride(it) },
                onSpindleOverrideChange = { viewModel.setSpindleOverride(it) },
                isEnabled = connectionState is ConnectionState.Connected
            )
            
            // Job Progress (if running)
            AnimatedVisibility(
                visible = sendState is SendState.Sending || sendState is SendState.Paused,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                JobProgressCard(
                    progress = progress,
                    isPaused = sendState is SendState.Paused,
                    onPause = { viewModel.pauseJob() },
                    onResume = { viewModel.resumeJob() },
                    onStop = { viewModel.stopJob() }
                )
            }
            
            // Pin State Indicators
            PinStateIndicators(pinState = grblStatus.pinState)
        }
    }
}

@Composable
fun ConnectionStatusIndicator(state: ConnectionState) {
    val (color, text) = when (state) {
        is ConnectionState.Connected -> SuccessGreen to "CONNECTED"
        is ConnectionState.Connecting -> WarningOrange to "CONNECTING..."
        is ConnectionState.Error -> ErrorRed to "ERROR"
        ConnectionState.Disconnected -> Color.Gray to "OFFLINE"
    }
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(end = 16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
                .animateContentSize()
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            color = color,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun EmergencyStopButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "scale"
    )
    
    val haptic = LocalHapticFeedback.current
    
    Box(
        modifier = modifier
            .scale(scale)
            .height(70.dp)
            .shadow(
                elevation = if (isPressed) 4.dp else 12.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = ErrorRed.copy(alpha = 0.5f)
            )
            .clip(RoundedCornerShape(16.dp))
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ErrorRed,
                        ErrorRedDark
                    )
                )
            )
            .border(
                width = 3.dp,
                color = ErrorRed.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp)
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = { onClick() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
            Text(
                text = "EMERGENCY STOP",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 3.sp
            )
        }
    }
}

@Composable
fun DRODisplay(
    mpos: Position,
    wpos: Position,
    feedRate: Int,
    spindleSpeed: Int,
    machineState: GrblMachineState
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Machine State
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val stateColor = when (machineState) {
                    GrblMachineState.IDLE -> SuccessGreen
                    GrblMachineState.RUN -> CyanAccent
                    GrblMachineState.HOLD -> WarningOrange
                    GrblMachineState.JOG -> PurpleAccent
                    GrblMachineState.ALARM -> ErrorRed
                    GrblMachineState.HOME -> WarningOrange
                    else -> Color.Gray
                }
                
                Text(
                    text = "STATE: ${machineState.name}",
                    color = stateColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    DROValue(label = "F", value = feedRate, unit = "mm/min", color = FeedColor)
                    DROValue(label = "S", value = spindleSpeed, unit = "RPM", color = SpindleColor)
                }
            }
            
            Divider(color = BorderColor)
            
            // Position Display
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Machine Position
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "MACHINE (MPos)",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    AxisDisplay(label = "X", value = mpos.x, color = AxisXColor)
                    AxisDisplay(label = "Y", value = mpos.y, color = AxisYColor)
                    AxisDisplay(label = "Z", value = mpos.z, color = AxisZColor)
                    AxisDisplay(label = "A", value = mpos.a, color = AxisAColor)
                }
                
                Divider(
                    modifier = Modifier
                        .width(1.dp)
                        .height(140.dp),
                    color = BorderColor
                )
                
                // Work Position
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "WORK (WPos)",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    AxisDisplay(label = "X", value = wpos.x, color = AxisXColor)
                    AxisDisplay(label = "Y", value = wpos.y, color = AxisYColor)
                    AxisDisplay(label = "Z", value = wpos.z, color = AxisZColor)
                    AxisDisplay(label = "A", value = wpos.a, color = AxisAColor)
                }
            }
        }
    }
}

@Composable
fun AxisDisplay(label: String, value: Float, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = color,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = String.format("%8.3f", value),
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(110.dp)
        )
    }
}

@Composable
fun DROValue(label: String, value: Int, unit: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$label:",
            color = color,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "$value",
            color = Color.White,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = unit,
            color = TextSecondary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun JoggingControls(
    onJog: (String, Float) -> Unit,
    onJogContinuous: (String, Float) -> Unit,
    onCancelJog: () -> Unit,
    onHome: () -> Unit,
    isEnabled: Boolean
) {
    var selectedIncrement by remember { mutableStateOf(1.0f) }
    val increments = listOf(0.01f, 0.1f, 1f, 10f, 100f)
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "JOGGING",
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            
            // Increment Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                increments.forEach { increment ->
                    val isSelected = selectedIncrement == increment
                    val label = when {
                        increment >= 1 -> "${increment.toInt()}mm"
                        else -> "${(increment * 100).toInt()}0μm"
                    }
                    
                    Button(
                        onClick = { selectedIncrement = increment },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) CyanAccent else ButtonBackground,
                            contentColor = if (isSelected) DarkBackground else TextPrimary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f),
                        enabled = isEnabled
                    ) {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
            
            // D-Pad Layout
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // XY Pad
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    JogButton(
                        icon = Icons.Rounded.KeyboardArrowUp,
                        onPress = { onJogContinuous("Y", 1f) },
                        onRelease = onCancelJog,
                        onClick = { onJog("Y", selectedIncrement) },
                        color = AxisYColor,
                        enabled = isEnabled
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        JogButton(
                            icon = Icons.Rounded.KeyboardArrowLeft,
                            onPress = { onJogContinuous("X", -1f) },
                            onRelease = onCancelJog,
                            onClick = { onJog("X", -selectedIncrement) },
                            color = AxisXColor,
                            enabled = isEnabled
                        )
                        JogButton(
                            icon = Icons.Rounded.Home,
                            onPress = {},
                            onRelease = {},
                            onClick = onHome,
                            color = WarningOrange,
                            enabled = isEnabled,
                            isHome = true
                        )
                        JogButton(
                            icon = Icons.Rounded.KeyboardArrowRight,
                            onPress = { onJogContinuous("X", 1f) },
                            onRelease = onCancelJog,
                            onClick = { onJog("X", selectedIncrement) },
                            color = AxisXColor,
                            enabled = isEnabled
                        )
                    }
                    JogButton(
                        icon = Icons.Rounded.KeyboardArrowDown,
                        onPress = { onJogContinuous("Y", -1f) },
                        onRelease = onCancelJog,
                        onClick = { onJog("Y", -selectedIncrement) },
                        color = AxisYColor,
                        enabled = isEnabled
                    )
                }
                
                // Z Controls
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Z AXIS",
                        color = AxisZColor,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    JogButton(
                        icon = Icons.Rounded.KeyboardArrowUp,
                        onPress = { onJogContinuous("Z", 1f) },
                        onRelease = onCancelJog,
                        onClick = { onJog("Z", selectedIncrement) },
                        color = AxisZColor,
                        enabled = isEnabled
                    )
                    JogButton(
                        icon = Icons.Rounded.KeyboardArrowDown,
                        onPress = { onJogContinuous("Z", -1f) },
                        onRelease = onCancelJog,
                        onClick = { onJog("Z", -selectedIncrement) },
                        color = AxisZColor,
                        enabled = isEnabled
                    )
                }
            }
        }
    }
}

@Composable
fun JogButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    onClick: () -> Unit,
    color: Color,
    enabled: Boolean,
    isHome: Boolean = false
) {
    var isPressed by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "jogScale"
    )
    
    Box(
        modifier = Modifier
            .scale(scale)
            .size(if (isHome) 56.dp else 64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (enabled) color.copy(alpha = if (isPressed) 0.4f else 0.2f)
                else Color.Gray.copy(alpha = 0.1f)
            )
            .border(
                width = 2.dp,
                color = if (enabled) color else Color.Gray,
                shape = RoundedCornerShape(12.dp)
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        if (enabled) {
                            isPressed = true
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onPress()
                            tryAwaitRelease()
                            isPressed = false
                            onRelease()
                        }
                    },
                    onTap = { if (enabled) onClick() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) color else Color.Gray,
            modifier = Modifier.size(if (isHome) 28.dp else 32.dp)
        )
    }
}

@Composable
fun WorkPositionControls(
    onZeroX: () -> Unit,
    onZeroY: () -> Unit,
    onZeroZ: () -> Unit,
    onZeroAll: () -> Unit,
    onProbeZ: () -> Unit,
    isEnabled: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "WORK POSITION",
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ZeroButton("X0", AxisXColor, onZeroX, isEnabled, Modifier.weight(1f))
                ZeroButton("Y0", AxisYColor, onZeroY, isEnabled, Modifier.weight(1f))
                ZeroButton("Z0", AxisZColor, onZeroZ, isEnabled, Modifier.weight(1f))
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onZeroAll,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WarningOrange.copy(alpha = 0.2f),
                        contentColor = WarningOrange
                    ),
                    shape = RoundedCornerShape(8.dp),
                    enabled = isEnabled,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "ZERO ALL",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Button(
                    onClick = onProbeZ,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanAccent.copy(alpha = 0.2f),
                        contentColor = CyanAccent
                    ),
                    shape = RoundedCornerShape(8.dp),
                    enabled = isEnabled,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "PROBE Z",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun ZeroButton(
    text: String,
    color: Color,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = color.copy(alpha = 0.2f),
            contentColor = color,
            disabledContainerColor = Color.Gray.copy(alpha = 0.1f),
            disabledContentColor = Color.Gray
        ),
        shape = RoundedCornerShape(8.dp),
        enabled = enabled,
        modifier = modifier.height(44.dp)
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun OverrideControls(
    feedOverride: Int,
    spindleOverride: Int,
    onFeedOverrideChange: (Int) -> Unit,
    onSpindleOverrideChange: (Int) -> Unit,
    isEnabled: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "OVERRIDES",
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            
            // Feed Override
            OverrideSlider(
                label = "FEED",
                value = feedOverride,
                onValueChange = onFeedOverrideChange,
                color = FeedColor,
                isEnabled = isEnabled,
                valueRange = 10..200
            )
            
            // Spindle Override
            OverrideSlider(
                label = "SPINDLE",
                value = spindleOverride,
                onValueChange = onSpindleOverrideChange,
                color = SpindleColor,
                isEnabled = isEnabled,
                valueRange = 10..200
            )
        }
    }
}

@Composable
fun OverrideSlider(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    color: Color,
    isEnabled: Boolean,
    valueRange: IntRange
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = color,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "$value%",
                color = Color.White,
                fontSize = 16.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
            steps = (valueRange.last - valueRange.first) / 10 - 1,
            enabled = isEnabled,
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color,
                inactiveTrackColor = color.copy(alpha = 0.2f),
                disabledThumbColor = Color.Gray,
                disabledActiveTrackColor = Color.Gray,
                disabledInactiveTrackColor = Color.Gray.copy(alpha = 0.2f)
            )
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${valueRange.first}%",
                color = TextSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "100%",
                color = TextSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "${valueRange.last}%",
                color = TextSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun JobProgressCard(
    progress: JobProgress,
    isPaused: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(2.dp, if (isPaused) WarningOrange else CyanAccent)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isPaused) "JOB PAUSED" else "JOB RUNNING",
                    color = if (isPaused) WarningOrange else CyanAccent,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "${progress.percentComplete.toInt()}%",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
            
            // Progress Bar
            LinearProgressIndicator(
                progress = { progress.percentComplete / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = if (isPaused) WarningOrange else CyanAccent,
                trackColor = ProgressTrackColor,
            )
            
            // Stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem("SENT", "${progress.sentLines}")
                StatItem("DONE", "${progress.completedLines}")
                StatItem("TOTAL", "${progress.totalLines}")
                StatItem("BUFFER", "${progress.bufferFill}/128")
            }
            
            // Control Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isPaused) {
                    Button(
                        onClick = onResume,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SuccessGreen,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("RESUME")
                    }
                } else {
                    Button(
                        onClick = onPause,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WarningOrange,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Pause, null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("PAUSE")
                    }
                }
                
                Button(
                    onClick = onStop,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ErrorRed,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Stop, null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("STOP")
                }
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun PinStateIndicators(pinState: PinState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "PIN STATE",
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PinIndicator("X", pinState.limitX, ErrorRed)
                PinIndicator("Y", pinState.limitY, ErrorRed)
                PinIndicator("Z", pinState.limitZ, ErrorRed)
                PinIndicator("A", pinState.limitA, ErrorRed)
                PinIndicator("PROBE", pinState.probe, CyanAccent)
                PinIndicator("DOOR", pinState.door, WarningOrange)
                PinIndicator("HOLD", pinState.hold, WarningOrange)
                PinIndicator("RESET", pinState.softReset, ErrorRed)
            }
        }
    }
}

@Composable
fun PinIndicator(label: String, active: Boolean, activeColor: Color) {
    val backgroundColor = if (active) activeColor else Color.Gray.copy(alpha = 0.2f)
    val textColor = if (active) Color.White else TextSecondary
    
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(backgroundColor)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
        )
    }
}

// FlowRow implementation for pin indicators
@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit
) {
    Layout(
        content = content,
        modifier = modifier
    ) { measurables, constraints ->
        val hGapPx = 8.dp.roundToPx()
        val vGapPx = 8.dp.roundToPx()
        
        val rows = mutableListOf<List<androidx.compose.ui.layout.Placeable>>()
        val rowWidths = mutableListOf<Int>()
        val rowHeights = mutableListOf<Int>()
        
        var currentRow = mutableListOf<androidx.compose.ui.layout.Placeable>()
        var currentRowWidth = 0
        var currentRowHeight = 0
        
        measurables.forEach { measurable ->
            val placeable = measurable.measure(constraints)
            
            if (currentRow.isNotEmpty() && currentRowWidth + hGapPx + placeable.width > constraints.maxWidth) {
                rows.add(currentRow)
                rowWidths.add(currentRowWidth)
                rowHeights.add(currentRowHeight)
                currentRow = mutableListOf()
                currentRowWidth = 0
                currentRowHeight = 0
            }
            
            currentRow.add(placeable)
            currentRowWidth += if (currentRow.size > 1) hGapPx else 0
            currentRowWidth += placeable.width
            currentRowHeight = maxOf(currentRowHeight, placeable.height)
        }
        
        if (currentRow.isNotEmpty()) {
            rows.add(currentRow)
            rowWidths.add(currentRowWidth)
            rowHeights.add(currentRowHeight)
        }
        
        val totalHeight = rowHeights.sum() + (rowHeights.size - 1).coerceAtLeast(0) * vGapPx
        
        layout(constraints.maxWidth, totalHeight) {
            var y = 0
            rows.forEachIndexed { rowIndex, row ->
                var x = when (horizontalArrangement) {
                    Arrangement.Center -> (constraints.maxWidth - rowWidths[rowIndex]) / 2
                    Arrangement.End -> constraints.maxWidth - rowWidths[rowIndex]
                    else -> 0
                }
                
                row.forEach { placeable ->
                    placeable.placeRelative(x, y)
                    x += placeable.width + hGapPx
                }
                
                y += rowHeights[rowIndex] + vGapPx
            }
        }
    }
}
