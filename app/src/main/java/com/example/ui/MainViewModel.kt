package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.converter.ConverterManager
import com.example.data.AppDatabase
import com.example.data.DocumentEntity
import com.example.data.PageEntity
import com.example.util.AppThemeMode
import com.example.util.CacheManager
import com.example.util.SettingsManager
import com.example.util.StorageUsageStats
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class ConversionUiState {
    object Idle : ConversionUiState()
    data class Converting(val message: String, val progress: Float) : ConversionUiState()
    data class Success(val documentId: String) : ConversionUiState()
    data class Error(val errorMessage: String) : ConversionUiState()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val docDao = db.documentDao()
    private val converterManager = ConverterManager(application)
    private val cacheManager = CacheManager(application)
    private val settingsManager = SettingsManager(application)

    private val _conversionState = MutableStateFlow<ConversionUiState>(ConversionUiState.Idle)
    val conversionState: StateFlow<ConversionUiState> = _conversionState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow("ALL") // ALL, PDF, DOCX, PPTX, XLSX, TEXT
    val selectedFilter: StateFlow<String> = _selectedFilter.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val documentsList: StateFlow<List<DocumentEntity>> = combine(_searchQuery, _selectedFilter) { query, filter ->
        Pair(query, filter)
    }.flatMapLatest { (query, filter) ->
        if (query.isBlank()) {
            docDao.getAllDocuments()
        } else {
            docDao.searchDocumentsByContent(query)
        }
    }.combine(_selectedFilter) { docs, filter ->
        if (filter == "ALL") docs
        else docs.filter { it.fileType.equals(filter, ignoreCase = true) }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _storageStats = MutableStateFlow<StorageUsageStats?>(null)
    val storageStats: StateFlow<StorageUsageStats?> = _storageStats.asStateFlow()

    val themeMode: StateFlow<AppThemeMode> = settingsManager.themeModeFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppThemeMode.SYSTEM
    )

    init {
        refreshStorageStats()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilter(filter: String) {
        _selectedFilter.value = filter
    }

    fun convertUri(uri: Uri) {
        viewModelScope.launch {
            _conversionState.value = ConversionUiState.Converting("Analyzing document...", 0.2f)
            try {
                _conversionState.value = ConversionUiState.Converting("Extracting structure & rendering Markdown...", 0.6f)
                val result = converterManager.convertDocument(uri)
                _conversionState.value = ConversionUiState.Success(result.documentId)
                refreshStorageStats()
            } catch (e: Exception) {
                e.printStackTrace()
                _conversionState.value = ConversionUiState.Error(e.localizedMessage ?: "Failed to convert document")
            }
        }
    }

    fun resetConversionState() {
        _conversionState.value = ConversionUiState.Idle
    }

    fun deleteDocument(id: String) {
        viewModelScope.launch {
            docDao.deleteDocumentById(id)
            refreshStorageStats()
        }
    }

    fun refreshStorageStats() {
        viewModelScope.launch {
            _storageStats.value = cacheManager.getStorageUsage()
        }
    }

    fun clearPreviews() {
        viewModelScope.launch {
            cacheManager.clearPreviews()
            refreshStorageStats()
        }
    }

    fun clearConvertedFiles() {
        viewModelScope.launch {
            cacheManager.clearConvertedFiles()
            refreshStorageStats()
        }
    }

    fun clearTempFiles() {
        viewModelScope.launch {
            cacheManager.clearTempFiles()
            refreshStorageStats()
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch {
            settingsManager.setThemeMode(mode)
        }
    }

    fun getPagesForDocument(documentId: String) = docDao.getPagesForDocument(documentId)

    suspend fun getDocumentById(id: String): DocumentEntity? = docDao.getDocumentById(id)

    fun updateReadingPosition(documentId: String, pageIndex: Int, position: Int) {
        viewModelScope.launch {
            docDao.updateReadingPosition(documentId, pageIndex, position)
        }
    }
}
