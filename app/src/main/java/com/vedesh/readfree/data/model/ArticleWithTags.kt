package com.vedesh.readfree.data.model

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import com.vedesh.readfree.data.db.entity.Article
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
)
