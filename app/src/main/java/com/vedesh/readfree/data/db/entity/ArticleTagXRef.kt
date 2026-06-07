package com.vedesh.readfree.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "article_tag_xref",
    primaryKeys = ["articleUrl", "tagName"],
    foreignKeys = [
        ForeignKey(
            entity = Article::class,
            parentColumns = ["url"],
            childColumns = ["articleUrl"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Tag::class,
            parentColumns = ["name"],
            childColumns = ["tagName"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("tagName")
    ]
)
data class ArticleTagXRef(
    val articleUrl: String,
    val tagName: String
)
