package com.vedesh.readfree.data.db.dao

import androidx.room.*
import com.vedesh.readfree.data.db.entity.ArticleTagXRef
import com.vedesh.readfree.data.db.entity.Tag
import com.vedesh.readfree.data.model.TagWithCount
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: Tag)

    @Delete
    suspend fun delete(tag: Tag)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addTagToArticle(xref: ArticleTagXRef)

    @Query("DELETE FROM article_tag_xref WHERE articleUrl = :url AND tagName = :tag")
    suspend fun removeTagFromArticle(url: String, tag: String)

    @Query("DELETE FROM article_tag_xref WHERE tagName = :tag")
    suspend fun removeTagFromAllArticles(tag: String)

    @Query("SELECT DISTINCT name FROM tags ORDER BY name ASC")
    fun getAll(): Flow<List<Tag>>

    @Query("""
        SELECT t.name, COUNT(x.articleUrl) as articleCount
        FROM tags t LEFT JOIN article_tag_xref x ON t.name = x.tagName
        GROUP BY t.name ORDER BY articleCount DESC
    """)
    fun getAllWithCounts(): Flow<List<TagWithCount>>

    @Query("SELECT tagName FROM article_tag_xref WHERE articleUrl = :url")
    suspend fun getTagsForArticle(url: String): List<String>

    @Query("UPDATE article_tag_xref SET tagName = :newName WHERE tagName = :oldName")
    suspend fun renameTagInXRef(oldName: String, newName: String)

    @Query("UPDATE tags SET name = :newName WHERE name = :oldName")
    suspend fun renameTag(oldName: String, newName: String)
}
