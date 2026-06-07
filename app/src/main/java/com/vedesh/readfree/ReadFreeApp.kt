package com.vedesh.readfree

import android.app.Application
import android.content.Context
import android.content.Intent
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

    fun saveToRaindrop(
        context: Context,
        url: String,
        title: String,
        onResult: ((Boolean) -> Unit)? = null,
    ) {
        val mode = settingsRepository.getRaindropSaveMode()
        if (mode == "INTENT") {
            val intent =
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, url)
                    putExtra(Intent.EXTRA_SUBJECT, title)
                }
            try {
                context.startActivity(Intent.createChooser(intent, "Share to Raindrop"))
                onResult?.invoke(true)
            } catch (_: Exception) {
                onResult?.invoke(false)
            }
        } else {
            raindropRepository.syncArticle(url, title, onResult, requireSyncEnabled = false)
        }
    }
}
