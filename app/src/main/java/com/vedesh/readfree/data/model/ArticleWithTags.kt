package com.vedesh.readfree.data.model

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import com.vedesh.readfree.data.db.entity.Article
import com.vedesh.readfree.data.db.entity.ArticleList
import com.vedesh.readfree.data.db.entity.ArticleListXRef
import com.vedesh.readfree.data.db.entity.ArticleTagXRef
import com.vedesh.readfree.data.db.entity.Tag

data class ArticleWithTags(
    @Embedded val article: Article,
    @Relation(
        parentColumn = "url",
        entityColumn = "name",
        associateBy =
            Junction(
                ArticleTagXRef::class,
                parentColumn = "articleUrl",
                entityColumn = "tagName",
            ),
    )
    val tags: List<Tag>,
    @Relation(
        parentColumn = "url",
        entityColumn = "id",
        associateBy =
            Junction(
                ArticleListXRef::class,
                parentColumn = "articleUrl",
                entityColumn = "listId",
            ),
    )
    val lists: List<ArticleList>,
)
