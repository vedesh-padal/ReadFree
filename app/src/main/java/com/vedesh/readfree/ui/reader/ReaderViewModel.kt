package com.vedesh.readfree.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vedesh.readfree.data.db.entity.Article
import com.vedesh.readfree.data.db.entity.ReadState
import com.vedesh.readfree.data.repository.ArticleRepository
import com.vedesh.readfree.data.repository.ListRepository
import com.vedesh.readfree.data.repository.RaindropRepository
import com.vedesh.readfree.data.repository.TagRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ReaderViewModel(
    private val articleRepo: ArticleRepository,
    private val listRepo: ListRepository,
    private val tagRepo: TagRepository,
    private val raindropRepo: RaindropRepository,
) : ViewModel() {
    val lists =
        listRepo.getAllWithCounts().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList(),
        )

    val allTags =
        tagRepo.getAll().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList(),
        )

    private val _progressPercentage = MutableStateFlow(0)
    val progressPercentage: StateFlow<Int> = _progressPercentage

    fun checkIfExists(
        url: String,
        callback: (Boolean) -> Unit,
    ) {
        viewModelScope.launch {
            callback(articleRepo.exists(url))
        }
    }

    fun saveArticle(
        url: String,
        title: String,
        listId: Long?,
        tags: List<String>,
        isMediumUrl: Boolean,
    ) = viewModelScope.launch {
        if (!articleRepo.exists(url)) {
            articleRepo.insert(
                Article(
                    url = url,
                    title = title.ifEmpty { "Loading..." },
                    isMediumUrl = isMediumUrl,
                ),
            )
        } else {
            // Update title if it was loading before
            articleRepo.getByUrl(url)?.let {
                if (it.title == "Loading..." || it.title == url) {
                    articleRepo.update(it.copy(title = title))
                }
            }
        }

        if (listId != null) {
            listRepo.addArticleToList(url, listId)
        }

        tags.forEach { tagName ->
            tagRepo.insert(com.vedesh.readfree.data.db.entity.Tag(tagName))
            tagRepo.addTagToArticle(url, tagName)
        }
    }

    fun updateTitle(
        url: String,
        newTitle: String,
    ) {
        viewModelScope.launch {
            articleRepo.getByUrl(url)?.let {
                if (it.title == "Loading..." || it.title == url || it.title.isEmpty()) {
                    articleRepo.update(it.copy(title = newTitle))
                    // Sync to Raindrop now that we have the real title
                    raindropRepo.syncArticle(url, newTitle, requireSyncEnabled = true)
                }
            }
        }
    }

    fun getArticle(
        url: String,
        callback: (Article?) -> Unit,
    ) {
        viewModelScope.launch {
            callback(articleRepo.getByUrl(url))
        }
    }

    fun getArticleDetails(
        url: String,
        callback: (Article?, List<Long>, List<String>) -> Unit,
    ) {
        viewModelScope.launch {
            val article = articleRepo.getByUrl(url)
            val listIds = listRepo.getListIdsForArticle(url)
            val tags = tagRepo.getTagsForArticle(url)
            callback(article, listIds, tags)
        }
    }

    fun setReadState(
        url: String,
        state: ReadState,
    ) {
        viewModelScope.launch { articleRepo.updateReadState(url, state) }
    }

    fun toggleReadState(url: String) {
        viewModelScope.launch {
            articleRepo.getByUrl(url)?.let { article ->
                val newState = if (article.readState == ReadState.READ) ReadState.UNREAD else ReadState.READ
                articleRepo.update(article.copy(readState = newState))
            }
        }
    }

    fun updateScrollProgress(
        url: String,
        scrollY: Int,
        percentage: Float,
    ) {
        val pctInt = percentage.toInt().coerceIn(0, 100)
        _progressPercentage.value = pctInt
        viewModelScope.launch {
            articleRepo.getByUrl(url)?.let { article ->
                val newProgress = scrollY
                val newState = if (pctInt >= 90) com.vedesh.readfree.data.db.entity.ReadState.READ else article.readState

                if (article.scrollProgress != newProgress || article.readState != newState) {
                    articleRepo.update(article.copy(scrollProgress = newProgress, readState = newState))
                }
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
                com.vedesh.readfree.data.db.entity.ArticleList(name = name, emoji = emoji, colorHex = colorHex),
            )
        }
    }

    fun updateOfflinePath(
        url: String,
        path: String,
    ) {
        viewModelScope.launch {
            articleRepo.getByUrl(url)?.let { article ->
                articleRepo.update(article.copy(offlineFilePath = path))
            }
        }
    }
}
