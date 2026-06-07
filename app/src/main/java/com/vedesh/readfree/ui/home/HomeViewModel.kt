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

    sealed class SearchScope {
        object All : SearchScope()
        object Unsorted : SearchScope()
        object Offline : SearchScope()
        data class ListScope(val id: Long, val name: String) : SearchScope()
    }

    private val _filterState = MutableStateFlow(FilterState())
    val filterState: StateFlow<FilterState> = _filterState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchScope = MutableStateFlow<SearchScope>(SearchScope.All)
    val searchScope: StateFlow<SearchScope> = _searchScope.asStateFlow()

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
        combine(_filterState, _searchQuery, _searchScope) { filter, query, scope ->
            Triple(filter, query, scope)
        }.flatMapLatest { (filter, query, scope) ->
            when {
                query.isNotEmpty() -> {
                    when (scope) {
                        SearchScope.All -> articleRepo.search(query)
                        SearchScope.Unsorted -> articleRepo.searchUnsorted(query)
                        SearchScope.Offline -> articleRepo.searchOffline(query)
                        is SearchScope.ListScope -> articleRepo.searchInList(query, scope.id)
                    }
                }
                else -> {
                    when (filter.type) {
                        FilterType.ALL -> articleRepo.getAll()
                        FilterType.UNSORTED -> articleRepo.getUnsorted()
                        FilterType.OFFLINE -> articleRepo.getOffline()
                        FilterType.LIST -> filter.id?.let { articleRepo.getByList(it) } ?: articleRepo.getAll()
                        FilterType.TAG -> filter.tag?.let { articleRepo.getByTag(it) } ?: articleRepo.getAll()
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

    fun setSearchScope(scope: SearchScope) {
        _searchScope.value = scope
    }

    fun toggleReadState(articleUrl: String, currentState: ReadState) {
        val newState = if (currentState == ReadState.READ) ReadState.UNREAD else ReadState.READ
        viewModelScope.launch { articleRepo.updateReadState(articleUrl, newState) }
    }

    fun setReadState(articleUrl: String, state: ReadState) {
        viewModelScope.launch { articleRepo.updateReadState(articleUrl, state) }
    }

    fun deleteArticle(articleUrl: String) {
        viewModelScope.launch {
            articleRepo.getByUrl(articleUrl)?.let { article ->
                article.offlineFilePath?.let { path ->
                    try { java.io.File(path).delete() } catch (_: Exception) {}
                }
                articleRepo.delete(article)
            }
        }
    }

    // Re-inserts the article + its list/tag associations after accidental swipe delete
    fun restoreArticle(item: ArticleWithTags) {
        viewModelScope.launch {
            articleRepo.insert(item.article)
            item.lists.forEach { list ->
                listRepo.addArticleToList(item.article.url, list.id)
            }
            item.tags.forEach { tag ->
                tagRepo.addTagToArticle(item.article.url, tag.name)
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

    // Called from the Move to List dialog: syncs list assignments based on checkbox state
    fun updateArticleLists(articleUrl: String, listIds: List<Long>, checked: BooleanArray) {
        viewModelScope.launch {
            listIds.forEachIndexed { index, listId ->
                if (checked[index]) {
                    listRepo.addArticleToList(articleUrl, listId)
                } else {
                    listRepo.removeArticleFromList(articleUrl, listId)
                }
            }
        }
    }
}
