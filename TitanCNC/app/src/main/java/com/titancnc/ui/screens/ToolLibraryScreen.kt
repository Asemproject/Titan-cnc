package com.titancnc.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.titancnc.data.model.*
import com.titancnc.ui.theme.*
import com.titancnc.ui.viewmodel.ToolLibraryViewModel
import com.titancnc.utils.ChiploadCalculator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolLibraryScreen(
    viewModel: ToolLibraryViewModel = hiltViewModel()
) {
    val tools by viewModel.tools.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedTool by viewModel.selectedTool.collectAsState()
    val showCalculator by viewModel.showCalculator.collectAsState()
    
    var showAddDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "TOOL LIBRARY",
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
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Rounded.Add, contentDescription = "Add Tool", tint = CyanAccent)
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
            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search tools...", color = TextSecondary) },
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = TextSecondary) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyanAccent,
                    unfocusedBorderColor = BorderColor,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Tool list
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tools) { tool ->
                    ToolCard(
                        tool = tool,
                        onClick = { viewModel.selectTool(tool) },
                        onFavoriteClick = { viewModel.toggleFavorite(tool) }
                    )
                }
            }
        }
    }
    
    // Tool Detail Dialog
    selectedTool?.let { tool ->
        ToolDetailDialog(
            tool = tool,
            onDismiss = { viewModel.clearSelection() },
            onEdit = { viewModel.editTool(it) },
            onDelete = { viewModel.deleteTool(it) },
            onCalculate = { viewModel.showCalculator() }
        )
    }
    
    // Calculator Dialog
    if (showCalculator) {
        CalculatorDialog(
            tool = selectedTool,
            onDismiss = { viewModel.hideCalculator() }
        )
    }
    
    // Add Tool Dialog
    if (showAddDialog) {
        AddToolDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { viewModel.addTool(it) }
        )
    }
}

@Composable
fun ToolCard(
    tool: Tool,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tool.name,
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "${tool.diameter}mm ${tool.toolType.name.replace("_", " ")}",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    StatBadge("F${tool.recommendedFeedRate}", FeedColor)
                    StatBadge("S${tool.recommendedSpindleSpeed}", SpindleColor)
                    StatBadge("${tool.numberOfFlutes}F", CyanAccent)
                }
            }
            
            IconButton(onClick = onFavoriteClick) {
                Icon(
                    if (tool.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (tool.isFavorite) ErrorRed else TextSecondary
                )
            }
        }
    }
}

@Composable
fun StatBadge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.2f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun ToolDetailDialog(
    tool: Tool,
    onDismiss: () -> Unit,
    onEdit: (Tool) -> Unit,
    onDelete: (Tool) -> Unit,
    onCalculate: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBackground,
        title = {
            Text(
                text = tool.name,
                color = TextPrimary,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DetailRow("Type", tool.toolType.name.replace("_", " "))
                DetailRow("Material", tool.material.name)
                DetailRow("Diameter", "${tool.diameter} mm")
                DetailRow("Flutes", "${tool.numberOfFlutes}")
                DetailRow("Flute Length", "${tool.fluteLength} mm")
                DetailRow("Max Depth", "${tool.maxDepthOfCut} mm")
                DetailRow("Feed Rate", "${tool.recommendedFeedRate} mm/min")
                DetailRow("Plunge Rate", "${tool.recommendedPlungeRate} mm/min")
                DetailRow("Spindle Speed", "${tool.recommendedSpindleSpeed} RPM")
                DetailRow("Chip Load", "${tool.recommendedChipLoad} mm/tooth")
                if (tool.notes.isNotEmpty()) {
                    DetailRow("Notes", tool.notes)
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = onCalculate) {
                    Text("Calculate", color = CyanAccent)
                }
                TextButton(onClick = { onEdit(tool) }) {
                    Text("Edit", color = CyanAccent)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onDelete(tool) },
                colors = ButtonDefaults.textButtonColors(contentColor = ErrorRed)
            ) {
                Text("Delete")
            }
        }
    )
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun CalculatorDialog(
    tool: Tool?,
    onDismiss: () -> Unit
) {
    if (tool == null) return
    
    var selectedMaterial by remember { mutableStateOf(MaterialDatabase.materials.keys.first()) }
    var selectedOperation by remember { mutableStateOf(OperationType.SLOTTING) }
    
    val calculator = remember { ChiploadCalculator() }
    val params = remember(selectedMaterial, selectedOperation) {
        calculator.getRecommendedParameters(tool, selectedMaterial, selectedOperation)
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBackground,
        title = {
            Text(
                text = "CHIPLOAD CALCULATOR",
                color = CyanAccent,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Material selector
                Text("Material", color = TextSecondary, fontSize = 12.sp)
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedMaterial,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.menuAnchor(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = BorderColor
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        MaterialDatabase.materials.keys.forEach { material ->
                            DropdownMenuItem(
                                text = { Text(material) },
                                onClick = {
                                    selectedMaterial = material
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                
                // Results
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkerBackground),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ResultRow("Feed Rate", "${params.feedRate} mm/min", FeedColor)
                        ResultRow("Plunge Rate", "${params.plungeRate} mm/min", FeedColor)
                        ResultRow("Spindle Speed", "${params.spindleSpeed} RPM", SpindleColor)
                        ResultRow("Stepdown", "${"%.2f".format(params.stepdown)} mm", AxisZColor)
                        ResultRow("Stepover", "${"%.0f".format(params.stepover * 100)}%", AxisXColor)
                        ResultRow("Chip Load", "${"%.4f".format(params.chipLoad)} mm/tooth", CyanAccent)
                        ResultRow("MRR", "${"%.2f".format(params.materialRemovalRate)} cm³/min", SuccessGreen)
                    }
                }
                
                // Warnings
                val warnings = calculator.validateParameters(params, tool)
                warnings.forEach { warning ->
                    Text(
                        text = "⚠ $warning",
                        color = WarningOrange,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = CyanAccent)
            }
        }
    )
}

@Composable
fun ResultRow(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = value,
            color = color,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun AddToolDialog(
    onDismiss: () -> Unit,
    onAdd: (Tool) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var diameter by remember { mutableStateOf("3.175") }
    var flutes by remember { mutableStateOf("2") }
    var toolType by remember { mutableStateOf(ToolType.END_MILL) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBackground,
        title = { Text("Add New Tool", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanAccent,
                        unfocusedBorderColor = BorderColor
                    )
                )
                OutlinedTextField(
                    value = diameter,
                    onValueChange = { diameter = it },
                    label = { Text("Diameter (mm)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanAccent,
                        unfocusedBorderColor = BorderColor
                    )
                )
                OutlinedTextField(
                    value = flutes,
                    onValueChange = { flutes = it },
                    label = { Text("Number of Flutes") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanAccent,
                        unfocusedBorderColor = BorderColor
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val tool = Tool(
                        name = name,
                        toolType = toolType,
                        diameter = diameter.toFloatOrNull() ?: 3.175f,
                        numberOfFlutes = flutes.toIntOrNull() ?: 2,
                        material = Material.CARBIDE,
                        fluteLength = 12f,
                        overallLength = 38f,
                        shankDiameter = diameter.toFloatOrNull() ?: 3.175f,
                        maxDepthOfCut = 6f,
                        maxStepdown = 1.5f,
                        recommendedFeedRate = 800,
                        recommendedPlungeRate = 400,
                        recommendedSpindleSpeed = 12000,
                        recommendedChipLoad = 0.033f
                    )
                    onAdd(tool)
                    onDismiss()
                },
                enabled = name.isNotBlank()
            ) {
                Text("Add", color = CyanAccent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}
