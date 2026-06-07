package com.vedesh.readfree.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lists")
data class ArticleList(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String = "📁",
    val colorHex: String = "#6C63FF",
    val sortOrder: Int = 0
)
