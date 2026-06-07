package com.vedesh.readfree.data.repository

import com.vedesh.readfree.data.db.dao.ArticleDao
import com.vedesh.readfree.data.db.entity.Article
import com.vedesh.readfree.data.db.entity.ReadState
import com.vedesh.readfree.data.model.ArticleWithTags
import kotlinx.coroutines.flow.Flow

class ArticleRepository(private val articleDao: ArticleDao) {
    suspend fun insert(article: Article) = articleDao.insert(article)

    suspend fun update(article: Article) = articleDao.update(article)

    suspend fun delete(article: Article) = articleDao.delete(article)

    suspend fun getByUrl(url: String): Article? = articleDao.getByUrl(url)

    suspend fun exists(url: String): Boolean = articleDao.exists(url)

    fun getAll(): Flow<List<ArticleWithTags>> = articleDao.getAll()

    fun getByList(listId: Long): Flow<List<ArticleWithTags>> = articleDao.getByList(listId)

    fun getUnsorted(): Flow<List<ArticleWithTags>> = articleDao.getUnsorted()

    fun getOffline(): Flow<List<ArticleWithTags>> = articleDao.getOffline()

    fun getByTag(tagName: String): Flow<List<ArticleWithTags>> = articleDao.getByTag(tagName)

    fun search(query: String): Flow<List<ArticleWithTags>> = articleDao.search(query)

    fun searchInList(
        query: String,
        listId: Long,
    ): Flow<List<ArticleWithTags>> = articleDao.searchInList(query, listId)

    suspend fun updateReadState(
        url: String,
        state: ReadState,
    ) = articleDao.updateReadState(url, state)

    suspend fun updateProgress(
        url: String,
        progress: Int,
    ) = articleDao.updateProgress(url, progress)

    suspend fun updateOfflinePath(
        url: String,
        path: String?,
    ) = articleDao.updateOfflinePath(url, path)

    suspend fun updateRaindropTs(
        url: String,
        ts: Long?,
    ) = articleDao.updateRaindropTs(url, ts)

    suspend fun clearAllOfflinePaths() = articleDao.clearAllOfflinePaths()
}
