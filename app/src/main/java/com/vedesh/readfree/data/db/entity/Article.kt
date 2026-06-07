package com.vedesh.readfree.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "articles")
data class Article(
    @PrimaryKey val url: String,
    val title: String,
    val savedAt: Long = System.currentTimeMillis(),
    val readState: ReadState = ReadState.UNREAD,
    val scrollProgress: Int = 0,
    val offlineFilePath: String? = null,
    val raindropSavedAt: Long? = null,
    val isMediumUrl: Boolean = false
)
