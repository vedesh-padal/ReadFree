package com.vedesh.readfree.data.model

import androidx.room.Embedded
import com.vedesh.readfree.data.db.entity.ArticleList

data class ListWithCount(
    @Embedded val list: ArticleList,
    val articleCount: Int
)
