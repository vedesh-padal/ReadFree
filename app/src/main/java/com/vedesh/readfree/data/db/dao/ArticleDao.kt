package com.vedesh.readfree.data.db.dao

import androidx.room.*
import com.vedesh.readfree.data.db.entity.Article
import com.vedesh.readfree.data.db.entity.ReadState
import com.vedesh.readfree.data.model.ArticleWithTags
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(article: Article)

    @Update
    suspend fun update(article: Article)

    @Delete
    suspend fun delete(article: Article)

    @Query("SELECT * FROM articles WHERE url = :url")
    suspend fun getByUrl(url: String): Article?

    @Query("SELECT EXISTS(SELECT 1 FROM articles WHERE url = :url)")
    suspend fun exists(url: String): Boolean

    @Transaction
    @Query("SELECT * FROM articles ORDER BY savedAt DESC")
    fun getAll(): Flow<List<ArticleWithTags>>

    @Transaction
    @Query(
        """
        SELECT a.* FROM articles a
        INNER JOIN article_list_xref x ON a.url = x.articleUrl
        WHERE x.listId = :listId ORDER BY x.addedAt DESC
    """,
    )
    fun getByList(listId: Long): Flow<List<ArticleWithTags>>

    @Transaction
    @Query(
        """
        SELECT * FROM articles
        WHERE url NOT IN (SELECT articleUrl FROM article_list_xref)
        ORDER BY savedAt DESC
    """,
    )
    fun getUnsorted(): Flow<List<ArticleWithTags>>

    @Transaction
    @Query("SELECT * FROM articles WHERE offlineFilePath IS NOT NULL ORDER BY savedAt DESC")
    fun getOffline(): Flow<List<ArticleWithTags>>

    @Transaction
    @Query(
        """
        SELECT DISTINCT a.* FROM articles a
        INNER JOIN article_tag_xref x ON a.url = x.articleUrl
        WHERE x.tagName = :tagName
        ORDER BY a.savedAt DESC
    """,
    )
    fun getByTag(tagName: String): Flow<List<ArticleWithTags>>

    // Metadata search: title LIKE query OR has matching tag
    @Transaction
    @Query(
        """
        SELECT DISTINCT a.* FROM articles a
        LEFT JOIN article_tag_xref t ON a.url = t.articleUrl
        WHERE a.title LIKE '%' || :query || '%'
           OR t.tagName LIKE '%' || :query || '%'
        ORDER BY a.savedAt DESC
    """,
    )
    fun search(query: String): Flow<List<ArticleWithTags>>

    // Scoped search within a list
    @Transaction
    @Query(
        """
        SELECT DISTINCT a.* FROM articles a
        INNER JOIN article_list_xref x ON a.url = x.articleUrl
        LEFT JOIN article_tag_xref t ON a.url = t.articleUrl
        WHERE x.listId = :listId
          AND (a.title LIKE '%' || :query || '%' OR t.tagName LIKE '%' || :query || '%')
        ORDER BY x.addedAt DESC
    """,
    )
    fun searchInList(
        query: String,
        listId: Long,
    ): Flow<List<ArticleWithTags>>

    @Query("UPDATE articles SET readState = :state WHERE url = :url")
    suspend fun updateReadState(
        url: String,
        state: ReadState,
    )

    @Query("UPDATE articles SET scrollProgress = :progress WHERE url = :url")
    suspend fun updateProgress(
        url: String,
        progress: Int,
    )

    @Query("UPDATE articles SET offlineFilePath = :path WHERE url = :url")
    suspend fun updateOfflinePath(
        url: String,
        path: String?,
    )

    @Query("UPDATE articles SET raindropSavedAt = :ts WHERE url = :url")
    suspend fun updateRaindropTs(
        url: String,
        ts: Long?,
    )

    @Query("UPDATE articles SET offlineFilePath = NULL")
    suspend fun clearAllOfflinePaths()

    @Query("SELECT COUNT(*) FROM articles")
    fun observeArticleCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM articles WHERE offlineFilePath IS NOT NULL")
    fun observeOfflineCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM articles WHERE url NOT IN (SELECT articleUrl FROM article_list_xref)")
    fun observeUnsortedCount(): Flow<Int>
}
