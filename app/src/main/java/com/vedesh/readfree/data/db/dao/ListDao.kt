package com.vedesh.readfree.data.db.dao

import androidx.room.*
import com.vedesh.readfree.data.db.entity.ArticleList
import com.vedesh.readfree.data.db.entity.ArticleListXRef
import com.vedesh.readfree.data.model.ListWithCount
import kotlinx.coroutines.flow.Flow

@Dao
interface ListDao {
    @Insert
    suspend fun insert(list: ArticleList): Long

    @Update
    suspend fun update(list: ArticleList)

    @Delete
    suspend fun delete(list: ArticleList)

    @Query(
        """
        SELECT l.*, COUNT(x.articleUrl) as articleCount
        FROM lists l LEFT JOIN article_list_xref x ON l.id = x.listId
        GROUP BY l.id ORDER BY l.sortOrder ASC
    """,
    )
    fun getAllWithCounts(): Flow<List<ListWithCount>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addArticleToList(xref: ArticleListXRef)

    @Query("DELETE FROM article_list_xref WHERE articleUrl = :url AND listId = :listId")
    suspend fun removeArticleFromList(
        url: String,
        listId: Long,
    )

    @Query("SELECT listId FROM article_list_xref WHERE articleUrl = :url")
    suspend fun getListIdsForArticle(url: String): List<Long>

    @Query("UPDATE lists SET sortOrder = :order WHERE id = :id")
    suspend fun updateSortOrder(
        id: Long,
        order: Int,
    )
}
