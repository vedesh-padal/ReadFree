package com.vedesh.readfree.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "article_list_xref",
    primaryKeys = ["articleUrl", "listId"],
    foreignKeys = [
        ForeignKey(
            entity = Article::class,
            parentColumns = ["url"],
            childColumns = ["articleUrl"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ArticleList::class,
            parentColumns = ["id"],
            childColumns = ["listId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("listId")
    ]
)
data class ArticleListXRef(
    val articleUrl: String,
    val listId: Long,
    val addedAt: Long = System.currentTimeMillis()
)
