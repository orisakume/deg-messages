package com.example.messages.feature.recyclebin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.messages.core.database.DeletedItemEntity
import com.example.messages.data.recyclebin.RecycleBinRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class RecycleBinViewModel @Inject constructor(private val repository: RecycleBinRepository) : ViewModel() {
    val items: StateFlow<List<DeletedItemEntity>> = repository.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun restore(ids: List<Long>) = viewModelScope.launch { repository.restore(ids) }
    fun delete(ids: List<Long>) = viewModelScope.launch { repository.permanentlyDelete(ids) }
    fun empty() = viewModelScope.launch { repository.empty() }
}
