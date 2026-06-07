package com.vedesh.readfree.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vedesh.readfree.data.db.entity.ReadState
import com.vedesh.readfree.data.model.ArticleWithTags
import com.vedesh.readfree.data.repository.ArticleRepository
import com.vedesh.readfree.data.repository.ListRepository
import com.vedesh.readfree.data.repository.TagRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HomeViewModel(
    private val articleRepo: ArticleRepository,
    private val listRepo: ListRepository,
    private val tagRepo: TagRepository,
) : ViewModel() {
    enum class FilterType {
        ALL,
        UNSORTED,
        OFFLINE,
        LIST,
        TAG,
    }

    data class FilterState(
        val type: FilterType = FilterType.ALL,
        val id: Long? = null,
        val tag: String? = null,
    )

    private val _filterState = MutableStateFlow(FilterState())
    val filterState: StateFlow<FilterState> = _filterState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val lists =
        listRepo.getAllWithCounts().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList(),
        )

    val tags =
        tagRepo.getAllWithCounts().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList(),
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val articles: StateFlow<List<ArticleWithTags>> =
        combine(_filterState, _searchQuery) { filter, query ->
            filter to query
        }.flatMapLatest { (filter, query) ->
            when {
                query.isNotEmpty() -> {
                    if (filter.type == FilterType.LIST && filter.id != null) {
                        articleRepo.searchInList(query, filter.id)
                    } else {
                        articleRepo.search(query)
                    }
                }
                else -> {
                    when (filter.type) {
                        FilterType.ALL -> articleRepo.getAll()
                        FilterType.UNSORTED -> articleRepo.getUnsorted()
                        FilterType.OFFLINE -> articleRepo.getOffline()
                        FilterType.LIST -> filter.id?.let { articleRepo.getByList(it) } ?: articleRepo.getAll()
                        FilterType.TAG -> {
                            // For Phase 3, we haven't implemented getByTag in DAO, but we can search for it via search query
                            // The search query searches tag matching anyway
                            articleRepo.search(filter.tag ?: "")
                        }
                    }
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setFilter(
        type: FilterType,
        listId: Long? = null,
        tag: String? = null,
    ) {
        _filterState.value = FilterState(type, listId, tag)
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleReadState(
        articleUrl: String,
        currentState: ReadState,
    ) {
        val newState = if (currentState == ReadState.READ) ReadState.UNREAD else ReadState.READ
        viewModelScope.launch {
            articleRepo.updateReadState(articleUrl, newState)
        }
    }

    fun deleteArticle(articleUrl: String) {
        viewModelScope.launch {
            articleRepo.getByUrl(articleUrl)?.let {
                articleRepo.delete(it)
            }
        }
    }

    fun createList(
        name: String,
        emoji: String,
        colorHex: String,
    ) {
        viewModelScope.launch {
            listRepo.insert(
                com.vedesh.readfree.data.db.entity.ArticleList(
                    name = name,
                    emoji = emoji,
                    colorHex = colorHex,
                ),
            )
        }
    }
}
