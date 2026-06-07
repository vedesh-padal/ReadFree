package com.vedesh.readfree.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vedesh.readfree.data.db.entity.Article
import com.vedesh.readfree.data.db.entity.ArticleListXRef
import com.vedesh.readfree.data.repository.ArticleRepository
import com.vedesh.readfree.data.repository.ListRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ReaderViewModel(
    private val articleRepo: ArticleRepository,
    private val listRepo: ListRepository
) : ViewModel() {

    val lists = listRepo.getAllWithCounts().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    fun checkIfExists(url: String, callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            callback(articleRepo.exists(url))
        }
    }

    fun saveArticle(url: String, title: String, listId: Long?, isMediumUrl: Boolean) {
        viewModelScope.launch {
            if (!articleRepo.exists(url)) {
                articleRepo.insert(
                    Article(
                        url = url,
                        title = title.ifEmpty { "Loading..." },
                        isMediumUrl = isMediumUrl
                    )
                )
                if (listId != null) {
                    listRepo.addArticleToList(url, listId)
                }
            }
        }
    }

    fun updateTitle(url: String, newTitle: String) {
        viewModelScope.launch {
            articleRepo.getByUrl(url)?.let {
                if (it.title == "Loading..." || it.title == url || it.title.isEmpty()) {
                    articleRepo.update(it.copy(title = newTitle))
                }
            }
        }
    }
}
