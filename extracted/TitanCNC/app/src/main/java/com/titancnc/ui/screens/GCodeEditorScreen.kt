package com.titancnc.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.titancnc.ui.theme.*
import com.titancnc.ui.viewmodel.GCodeEditorViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GCodeEditorScreen(
    viewModel: GCodeEditorViewModel = hiltViewModel()
) {
    val gcodeContent by viewModel.gcodeContent.collectAsState()
    val currentFile by viewModel.currentFile.collectAsState()
    val hasChanges by viewModel.hasChanges.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val currentSearchIndex by viewModel.currentSearchIndex.collectAsState()
    
    var showSearchBar by remember { mutableStateOf(false) }
    var showFindReplace by remember { mutableStateOf(false) }
    var replaceText by remember { mutableStateOf("") }
    
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = currentFile?.name ?: "Untitled.gcode",
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (hasChanges) {
                            Text(
                                text = "Modified",
                                style = MaterialTheme.typography.bodySmall,
                                color = WarningOrange
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                    titleContentColor = TextPrimary
                ),
                actions = {
                    // Search
                    IconButton(onClick = { showSearchBar = !showSearchBar }) {
                        Icon(Icons.Rounded.Search, contentDescription = "Search", tint = CyanAccent)
                    }
                    
                    // Find & Replace
                    IconButton(onClick = { showFindReplace = !showFindReplace }) {
                        Icon(Icons.Rounded.FindReplace, contentDescription = "Find & Replace", tint = CyanAccent)
                    }
                    
                    // Save
                    IconButton(
                        onClick = { 
                            scope.launch {
                                viewModel.saveFile()
                                snackbarHostState.showSnackbar("File saved")
                            }
                        },
                        enabled = hasChanges
                    ) {
                        Icon(Icons.Rounded.Save, contentDescription = "Save", tint = if (hasChanges) CyanAccent else Color.Gray)
                    }
                    
                    // Send to CNC
                    IconButton(onClick = { viewModel.sendToCNC() }) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = "Send to CNC", tint = SuccessGreen)
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = DarkBackground,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.sendToCNC() },
                containerColor = SuccessGreen,
                contentColor = Color.White
            ) {
                Icon(Icons.Rounded.PlayArrow, "Send to CNC")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search Bar
            AnimatedVisibility(visible = showSearchBar) {
                SearchBar(
                    query = searchQuery,
                    onQueryChange = { viewModel.updateSearchQuery(it) },
                    results = searchResults,
                    currentIndex = currentSearchIndex,
                    onNext = { viewModel.nextSearchResult() },
                    onPrevious = { viewModel.previousSearchResult() },
                    onClose = { showSearchBar = false }
                )
            }
            
            // Find & Replace Bar
            AnimatedVisibility(visible = showFindReplace) {
                FindReplaceBar(
                    findQuery = searchQuery,
                    onFindQueryChange = { viewModel.updateSearchQuery(it) },
                    replaceQuery = replaceText,
                    onReplaceQueryChange = { replaceText = it },
                    onReplace = { viewModel.replaceCurrent(replaceText) },
                    onReplaceAll = { viewModel.replaceAll(searchQuery, replaceText) },
                    onClose = { showFindReplace = false }
                )
            }
            
            // Editor
            GCodeEditor(
                content = gcodeContent,
                onContentChange = { viewModel.updateContent(it) },
                searchResults = searchResults,
                currentSearchIndex = currentSearchIndex,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<Int>,
    currentIndex: Int,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        color = CardBackground,
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Rounded.Search, contentDescription = null, tint = TextSecondary)
            
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(
                    color = TextPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp
                ),
                cursorBrush = SolidColor(CyanAccent),
                decorationBox = { innerTextField ->
                    Box {
                        if (query.isEmpty()) {
                            Text(
                                text = "Search...",
                                color = TextSecondary,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp
                            )
                        }
                        innerTextField()
                    }
                }
            )
            
            if (results.isNotEmpty()) {
                Text(
                    text = "${currentIndex + 1}/${results.size}",
                    color = TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            }
            
            IconButton(onClick = onPrevious, enabled = results.isNotEmpty()) {
                Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Previous", tint = if (results.isNotEmpty()) CyanAccent else Color.Gray)
            }
            
            IconButton(onClick = onNext, enabled = results.isNotEmpty()) {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Next", tint = if (results.isNotEmpty()) CyanAccent else Color.Gray)
            }
            
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, contentDescription = "Close", tint = TextSecondary)
            }
        }
    }
}

@Composable
fun FindReplaceBar(
    findQuery: String,
    onFindQueryChange: (String) -> Unit,
    replaceQuery: String,
    onReplaceQueryChange: (String) -> Unit,
    onReplace: () -> Unit,
    onReplaceAll: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        color = CardBackground,
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Find field
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Find:",
                    color = TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier.width(50.dp)
                )
                
                BasicTextField(
                    value = findQuery,
                    onValueChange = onFindQueryChange,
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(
                        color = TextPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp
                    ),
                    cursorBrush = SolidColor(CyanAccent),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(ButtonBackground)
                                .padding(8.dp)
                        ) {
                            innerTextField()
                        }
                    }
                )
            }
            
            // Replace field
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Replace:",
                    color = TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier.width(50.dp)
                )
                
                BasicTextField(
                    value = replaceQuery,
                    onValueChange = onReplaceQueryChange,
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(
                        color = TextPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp
                    ),
                    cursorBrush = SolidColor(CyanAccent),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(ButtonBackground)
                                .padding(8.dp)
                        ) {
                            innerTextField()
                        }
                    }
                )
            }
            
            // Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onReplace) {
                    Text("Replace", color = CyanAccent)
                }
                TextButton(onClick = onReplaceAll) {
                    Text("Replace All", color = CyanAccent)
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = TextSecondary)
                }
            }
        }
    }
}

@Composable
fun GCodeEditor(
    content: String,
    onContentChange: (String) -> Unit,
    searchResults: List<Int>,
    currentSearchIndex: Int,
    modifier: Modifier = Modifier
) {
    val lines = remember(content) { content.lines() }
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    
    // Scroll to current search result
    LaunchedEffect(currentSearchIndex, searchResults) {
        if (searchResults.isNotEmpty() && currentSearchIndex in searchResults.indices) {
            val lineIndex = content.substring(0, searchResults[currentSearchIndex]).count { it == '\n' }
            listState.animateScrollToItem(lineIndex.coerceIn(0, lines.size - 1))
        }
    }
    
    Row(modifier = modifier) {
        // Line numbers
        Column(
            modifier = Modifier
                .width(56.dp)
                .fillMaxHeight()
                .background(DarkerBackground)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.End
        ) {
            lines.forEachIndexed { index, _ ->
                val isSearchResult = searchResults.any { resultIndex ->
                    val lineStart = lines.take(index).sumOf { it.length + 1 }
                    val lineEnd = lineStart + lines[index].length
                    resultIndex in lineStart..lineEnd
                }
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        text = "${index + 1}",
                        color = if (isSearchResult) CyanAccent else TextSecondary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        textAlign = TextAlign.End
                    )
                }
            }
        }
        
        // Editor content
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(DarkBackground)
        ) {
            itemsIndexed(lines) { index, line ->
                val isCurrentSearchResult = searchResults.getOrNull(currentSearchIndex)?.let { resultIndex ->
                    val lineStart = lines.take(index).sumOf { it.length + 1 }
                    val lineEnd = lineStart + lines[index].length
                    resultIndex in lineStart..lineEnd
                } ?: false
                
                GCodeLine(
                    line = line,
                    isHighlighted = isCurrentSearchResult,
                    onLineChange = { newLine ->
                        val newLines = lines.toMutableList()
                        newLines[index] = newLine
                        onContentChange(newLines.joinToString("\n"))
                    }
                )
            }
        }
    }
}

@Composable
fun GCodeLine(
    line: String,
    isHighlighted: Boolean,
    onLineChange: (String) -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    var editText by remember(line) { mutableStateOf(line) }
    
    val backgroundColor = when {
        isHighlighted -> CyanAccent.copy(alpha = 0.2f)
        else -> Color.Transparent
    }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clickable { isEditing = true }
    ) {
        if (isEditing) {
            BasicTextField(
                value = editText,
                onValueChange = { editText = it },
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(
                    color = TextPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp
                ),
                cursorBrush = SolidColor(CyanAccent),
                singleLine = true,
                decorationBox = { innerTextField ->
                    Box {
                        innerTextField()
                    }
                }
            )
            
            LaunchedEffect(isEditing) {
                if (!isEditing) {
                    onLineChange(editText)
                }
            }
        } else {
            Text(
                text = buildAnnotatedString {
                    append(highlightGCode(line))
                },
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Syntax highlighting for G-code
 */
fun highlightGCode(line: String): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < line.length) {
            val char = line[i]
            
            when {
                // Comments
                char == ';' || char == '(' -> {
                    val commentStart = i
                    while (i < line.length && line[i] != ')') i++
                    if (i < line.length && line[i] == ')') i++
                    withStyle(SpanStyle(color = TextSecondary)) {
                        append(line.substring(commentStart, i))
                    }
                }
                // G-codes (motion)
                char == 'G' || char == 'g' -> {
                    val codeStart = i
                    i++
                    while (i < line.length && (line[i].isDigit() || line[i] == '.')) i++
                    withStyle(SpanStyle(color = CyanAccent, fontWeight = FontWeight.Bold)) {
                        append(line.substring(codeStart, i))
                    }
                }
                // M-codes (miscellaneous)
                char == 'M' || char == 'm' -> {
                    val codeStart = i
                    i++
                    while (i < line.length && (line[i].isDigit() || line[i] == '.')) i++
                    withStyle(SpanStyle(color = PurpleAccent, fontWeight = FontWeight.Bold)) {
                        append(line.substring(codeStart, i))
                    }
                }
                // Axis letters
                char == 'X' || char == 'x' -> {
                    withStyle(SpanStyle(color = AxisXColor)) {
                        append(line[i])
                    }
                    i++
                    val numStart = i
                    while (i < line.length && (line[i].isDigit() || line[i] == '.' || line[i] == '-')) i++
                    withStyle(SpanStyle(color = TextPrimary)) {
                        append(line.substring(numStart, i))
                    }
                }
                char == 'Y' || char == 'y' -> {
                    withStyle(SpanStyle(color = AxisYColor)) {
                        append(line[i])
                    }
                    i++
                    val numStart = i
                    while (i < line.length && (line[i].isDigit() || line[i] == '.' || line[i] == '-')) i++
                    withStyle(SpanStyle(color = TextPrimary)) {
                        append(line.substring(numStart, i))
                    }
                }
                char == 'Z' || char == 'z' -> {
                    withStyle(SpanStyle(color = AxisZColor)) {
                        append(line[i])
                    }
                    i++
                    val numStart = i
                    while (i < line.length && (line[i].isDigit() || line[i] == '.' || line[i] == '-')) i++
                    withStyle(SpanStyle(color = TextPrimary)) {
                        append(line.substring(numStart, i))
                    }
                }
                char == 'A' || char == 'a' || char == 'B' || char == 'b' || char == 'C' || char == 'c' -> {
                    withStyle(SpanStyle(color = AxisAColor)) {
                        append(line[i])
                    }
                    i++
                    val numStart = i
                    while (i < line.length && (line[i].isDigit() || line[i] == '.' || line[i] == '-')) i++
                    withStyle(SpanStyle(color = TextPrimary)) {
                        append(line.substring(numStart, i))
                    }
                }
                // Feed rate
                char == 'F' || char == 'f' -> {
                    withStyle(SpanStyle(color = FeedColor)) {
                        append(line[i])
                    }
                    i++
                    val numStart = i
                    while (i < line.length && (line[i].isDigit() || line[i] == '.')) i++
                    withStyle(SpanStyle(color = TextPrimary)) {
                        append(line.substring(numStart, i))
                    }
                }
                // Spindle speed
                char == 'S' || char == 's' -> {
                    withStyle(SpanStyle(color = SpindleColor)) {
                        append(line[i])
                    }
                    i++
                    val numStart = i
                    while (i < line.length && (line[i].isDigit() || line[i] == '.')) i++
                    withStyle(SpanStyle(color = TextPrimary)) {
                        append(line.substring(numStart, i))
                    }
                }
                // Line number
                char == 'N' || char == 'n' -> {
                    withStyle(SpanStyle(color = WarningOrange)) {
                        append(line[i])
                    }
                    i++
                    val numStart = i
                    while (i < line.length && line[i].isDigit()) i++
                    withStyle(SpanStyle(color = TextPrimary)) {
                        append(line.substring(numStart, i))
                    }
                }
                // Tool number
                char == 'T' || char == 't' -> {
                    withStyle(SpanStyle(color = SuccessGreen)) {
                        append(line[i])
                    }
                    i++
                    val numStart = i
                    while (i < line.length && line[i].isDigit()) i++
                    withStyle(SpanStyle(color = TextPrimary)) {
                        append(line.substring(numStart, i))
                    }
                }
                // GRBL settings
                char == '$' -> {
                    withStyle(SpanStyle(color = CyanAccent, fontWeight = FontWeight.Bold)) {
                        append(line[i])
                    }
                    i++
                    val numStart = i
                    while (i < line.length && (line[i].isDigit() || line[i] == '=')) i++
                    withStyle(SpanStyle(color = TextPrimary)) {
                        append(line.substring(numStart, i))
                    }
                }
                // Default
                else -> {
                    append(line[i])
                    i++
                }
            }
        }
    }
}
