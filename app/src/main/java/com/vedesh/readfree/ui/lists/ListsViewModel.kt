package com.vedesh.readfree.ui.lists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vedesh.readfree.data.model.ListWithCount
import com.vedesh.readfree.data.repository.ArticleRepository
import com.vedesh.readfree.data.repository.ListRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed class ListItem {
    data class SystemRow(
        val id: String,
        val emoji: String,
        val name: String,
        val count: Int,
        val colorHex: String,
    ) : ListItem()

    data class UserList(
        val listWithCount: ListWithCount,
    ) : ListItem()
}

class ListsViewModel(
    private val listRepo: ListRepository,
    private val articleRepo: ArticleRepository,
) : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    val lists: StateFlow<List<ListItem>> =
        combine(
            listRepo.getAllWithCounts(),
            articleRepo.observeArticleCount(),
            articleRepo.observeOfflineCount(),
            articleRepo.observeUnsortedCount(),
        ) { userLists, all, offline, unsorted ->
            buildList {
                add(ListItem.SystemRow("_all", "\uD83D\uDCDA", "All Articles", all, "#9E9E9E"))
                add(ListItem.SystemRow("_offline", "\uD83D\uDCE5", "Offline", offline, "#4CAF50"))
                add(ListItem.SystemRow("_unsorted", "", "Unsorted", unsorted, "#9E9E9E"))
                addAll(userLists.map { ListItem.UserList(it) })
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    fun updateSortOrder(items: List<ListItem>) {
        viewModelScope.launch {
            val ids = items.filterIsInstance<ListItem.UserList>().map { it.listWithCount.list.id }
            listRepo.batchUpdateSortOrder(ids)
        }
    }

    fun createList(
        name: String,
        emoji: String,
        colorHex: String,
    ) {
        viewModelScope.launch {
            listRepo.insert(com.vedesh.readfree.data.db.entity.ArticleList(name = name, emoji = emoji, colorHex = colorHex))
        }
    }

    fun restoreList(list: com.vedesh.readfree.data.db.entity.ArticleList) {
        viewModelScope.launch {
            listRepo.insert(list)
        }
    }
}
