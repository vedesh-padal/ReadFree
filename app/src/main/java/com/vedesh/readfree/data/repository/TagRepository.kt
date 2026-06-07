package com.vedesh.readfree.data.repository

import androidx.room.withTransaction
import com.vedesh.readfree.data.db.AppDatabase
import com.vedesh.readfree.data.db.dao.TagDao
import com.vedesh.readfree.data.db.entity.ArticleTagXRef
import com.vedesh.readfree.data.db.entity.Tag
import com.vedesh.readfree.data.model.TagWithCount
import kotlinx.coroutines.flow.Flow

class TagRepository(
    private val tagDao: TagDao,
    private val database: AppDatabase,
) {
    suspend fun insert(tag: Tag) = tagDao.insert(tag)

    suspend fun delete(tag: Tag) = tagDao.delete(tag)

    suspend fun addTagToArticle(
        url: String,
        tagName: String,
    ) = tagDao.addTagToArticle(ArticleTagXRef(url, tagName))

    suspend fun removeTagFromArticle(
        url: String,
        tagName: String,
    ) = tagDao.removeTagFromArticle(url, tagName)

    fun getAll(): Flow<List<Tag>> = tagDao.getAll()

    fun getAllWithCounts(): Flow<List<TagWithCount>> = tagDao.getAllWithCounts()

    suspend fun getTagsForArticle(url: String): List<String> = tagDao.getTagsForArticle(url)

    suspend fun rename(
        oldName: String,
        newName: String,
    ) {
        database.withTransaction {
            // First create the new tag if it doesn't exist
            tagDao.insert(Tag(newName))
            // Then update references
            tagDao.renameTagInXRef(oldName, newName)
            // Finally delete the old tag completely (remove it from tags table and all remaining xrefs)
            tagDao.removeTagFromAllArticles(oldName)
            tagDao.delete(Tag(oldName))
        }
    }
}
