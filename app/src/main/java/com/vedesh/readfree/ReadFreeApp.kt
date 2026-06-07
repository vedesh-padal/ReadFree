package com.vedesh.readfree

import android.app.Application
import com.vedesh.readfree.data.db.AppDatabase
import com.vedesh.readfree.data.repository.ArticleRepository
import com.vedesh.readfree.data.repository.ListRepository
import com.vedesh.readfree.data.repository.RaindropRepository
import com.vedesh.readfree.data.repository.SettingsRepository
import com.vedesh.readfree.data.repository.TagRepository

class ReadFreeApp : Application() {
    val database by lazy { AppDatabase.getInstance(this) }
    val articleRepository by lazy { ArticleRepository(database.articleDao()) }
    val listRepository by lazy { ListRepository(database.listDao(), database) }
    val tagRepository by lazy { TagRepository(database.tagDao(), database) }
    val settingsRepository by lazy { SettingsRepository(this) }
    val raindropRepository by lazy { RaindropRepository(settingsRepository, articleRepository) }
}
