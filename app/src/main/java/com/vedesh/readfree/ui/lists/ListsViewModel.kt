package com.vedesh.readfree.ui.lists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vedesh.readfree.data.model.ListWithCount
import com.vedesh.readfree.data.repository.ListRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ListsViewModel(
    private val listRepo: ListRepository
) : ViewModel() {

    val lists = listRepo.getAllWithCounts().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    fun deleteList(list: com.vedesh.readfree.data.db.entity.ArticleList) {
        viewModelScope.launch {
            listRepo.delete(list)
        }
    }

    fun updateList(list: com.vedesh.readfree.data.db.entity.ArticleList) {
        viewModelScope.launch {
            listRepo.update(list)
        }
    }

    fun updateSortOrder(items: List<ListWithCount>) {
        viewModelScope.launch {
            val ids = items.map { it.list.id }
            listRepo.batchUpdateSortOrder(ids)
        }
    }
}
