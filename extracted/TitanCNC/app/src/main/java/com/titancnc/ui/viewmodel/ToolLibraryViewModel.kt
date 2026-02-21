package com.titancnc.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.titancnc.data.database.ToolDao
import com.titancnc.data.model.Tool
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ToolLibraryViewModel @Inject constructor(
    private val toolDao: ToolDao
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val tools: StateFlow<List<Tool>> = _searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) {
                toolDao.getAllTools()
            } else {
                toolDao.searchTools(query)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedTool = MutableStateFlow<Tool?>(null)
    val selectedTool: StateFlow<Tool?> = _selectedTool.asStateFlow()

    private val _showCalculator = MutableStateFlow(false)
    val showCalculator: StateFlow<Boolean> = _showCalculator.asStateFlow()

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectTool(tool: Tool) {
        _selectedTool.value = tool
    }

    fun clearSelection() {
        _selectedTool.value = null
    }

    fun showCalculator() {
        _showCalculator.value = true
    }

    fun hideCalculator() {
        _showCalculator.value = false
    }

    fun toggleFavorite(tool: Tool) {
        viewModelScope.launch {
            toolDao.updateTool(tool.copy(isFavorite = !tool.isFavorite))
        }
    }

    fun addTool(tool: Tool) {
        viewModelScope.launch {
            toolDao.insertTool(tool)
        }
    }

    fun editTool(tool: Tool) {
        viewModelScope.launch {
            toolDao.updateTool(tool)
        }
    }

    fun deleteTool(tool: Tool) {
        viewModelScope.launch {
            toolDao.deleteTool(tool)
            _selectedTool.value = null
        }
    }
}
