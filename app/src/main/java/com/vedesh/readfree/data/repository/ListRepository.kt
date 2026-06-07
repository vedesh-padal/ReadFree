package com.vedesh.readfree.data.repository

import androidx.room.withTransaction
import com.vedesh.readfree.data.db.AppDatabase
import com.vedesh.readfree.data.db.dao.ListDao
import com.vedesh.readfree.data.db.entity.ArticleList
import com.vedesh.readfree.data.db.entity.ArticleListXRef
import com.vedesh.readfree.data.model.ListWithCount
import kotlinx.coroutines.flow.Flow

class ListRepository(
    private val listDao: ListDao,
    private val database: AppDatabase,
) {
    suspend fun insert(list: ArticleList): Long = listDao.insert(list)

    suspend fun update(list: ArticleList) = listDao.update(list)

    suspend fun delete(list: ArticleList) = listDao.delete(list)

    fun getAllWithCounts(): Flow<List<ListWithCount>> = listDao.getAllWithCounts()

    suspend fun addArticleToList(
        url: String,
        listId: Long,
    ) = listDao.addArticleToList(ArticleListXRef(url, listId))

    suspend fun removeArticleFromList(
        url: String,
        listId: Long,
    ) = listDao.removeArticleFromList(url, listId)

    suspend fun getListIdsForArticle(url: String): List<Long> = listDao.getListIdsForArticle(url)

    suspend fun batchUpdateSortOrder(idsInOrder: List<Long>) {
        database.withTransaction {
            idsInOrder.forEachIndexed { index, id ->
                listDao.updateSortOrder(id, index)
            }
        }
    }
}
