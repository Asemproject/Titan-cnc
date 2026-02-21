package com.titancnc.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.titancnc.service.GCodeSender
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GCodeEditorViewModel @Inject constructor(
    private val gCodeSender: GCodeSender
) : ViewModel() {

    private val _gcodeContent = MutableStateFlow("")
    val gcodeContent: StateFlow<String> = _gcodeContent.asStateFlow()

    private val _originalContent = MutableStateFlow("")
    
    private val _currentFile = MutableStateFlow<GCodeFile?>(null)
    val currentFile: StateFlow<GCodeFile?> = _currentFile.asStateFlow()

    val hasChanges: StateFlow<Boolean> = combine(_gcodeContent, _originalContent) { current, original ->
        current != original
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    // Search functionality
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Int>>(emptyList())
    val searchResults: StateFlow<List<Int>> = _searchResults.asStateFlow()

    private val _currentSearchIndex = MutableStateFlow(-1)
    val currentSearchIndex: StateFlow<Int> = _currentSearchIndex.asStateFlow()

    data class GCodeFile(
        val uri: Uri,
        val name: String,
        val path: String
    )

    init {
        // Listen for search query changes
        viewModelScope.launch {
            _searchQuery.collect { query ->
                if (query.isNotEmpty()) {
                    performSearch(query)
                } else {
                    _searchResults.value = emptyList()
                    _currentSearchIndex.value = -1
                }
            }
        }
    }

    fun loadFile(uri: Uri, name: String, path: String) {
        viewModelScope.launch {
            _currentFile.value = GCodeFile(uri, name, path)
            // Load file content would go here
        }
    }

    fun updateContent(newContent: String) {
        _gcodeContent.value = newContent
    }

    fun newFile() {
        _gcodeContent.value = ""
        _originalContent.value = ""
        _currentFile.value = null
    }

    fun saveFile() {
        viewModelScope.launch {
            _originalContent.value = _gcodeContent.value
            // Save to file system would go here
        }
    }

    fun sendToCNC() {
        viewModelScope.launch {
            val lines = _gcodeContent.value.lines()
            gCodeSender.sendJob(lines)
        }
    }

    // ==================== Search ====================

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    private fun performSearch(query: String) {
        val content = _gcodeContent.value
        val results = mutableListOf<Int>()
        var index = content.indexOf(query, ignoreCase = true)
        
        while (index != -1) {
            results.add(index)
            index = content.indexOf(query, index + 1, ignoreCase = true)
        }
        
        _searchResults.value = results
        _currentSearchIndex.value = if (results.isNotEmpty()) 0 else -1
    }

    fun nextSearchResult() {
        val results = _searchResults.value
        if (results.isNotEmpty()) {
            _currentSearchIndex.value = (_currentSearchIndex.value + 1) % results.size
        }
    }

    fun previousSearchResult() {
        val results = _searchResults.value
        if (results.isNotEmpty()) {
            _currentSearchIndex.value = (_currentSearchIndex.value - 1 + results.size) % results.size
        }
    }

    // ==================== Find & Replace ====================

    fun replaceCurrent(replacement: String) {
        val currentIndex = _currentSearchIndex.value
        val results = _searchResults.value
        
        if (currentIndex in results.indices) {
            val searchStart = results[currentIndex]
            val searchEnd = searchStart + _searchQuery.value.length
            
            val content = _gcodeContent.value
            val newContent = content.substring(0, searchStart) + 
                           replacement + 
                           content.substring(searchEnd)
            
            _gcodeContent.value = newContent
            
            // Update search results
            performSearch(_searchQuery.value)
        }
    }

    fun replaceAll(find: String, replacement: String) {
        if (find.isEmpty()) return
        
        _gcodeContent.value = _gcodeContent.value.replace(find, replacement, ignoreCase = true)
        performSearch(_searchQuery.value)
    }

    // ==================== Utility ====================

    fun insertAtCursor(text: String, cursorPosition: Int): Int {
        val content = _gcodeContent.value
        val newContent = content.substring(0, cursorPosition) + text + content.substring(cursorPosition)
        _gcodeContent.value = newContent
        return cursorPosition + text.length
    }

    fun getLineCount(): Int {
        return _gcodeContent.value.lines().size
    }

    fun getCharacterCount(): Int {
        return _gcodeContent.value.length
    }

    fun getEstimatedMachiningTime(feedRate: Int = 1000): Float {
        // Rough estimate based on line count
        val lineCount = getLineCount()
        return lineCount * 0.5f / 60f // minutes
    }
}
