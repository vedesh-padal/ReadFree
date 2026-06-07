package com.vedesh.readfree.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.vedesh.readfree.data.repository.ArticleRepository
import com.vedesh.readfree.data.repository.ListRepository
import com.vedesh.readfree.data.repository.TagRepository
import com.vedesh.readfree.ui.home.HomeViewModel

class ViewModelFactory(
    private val articleRepository: ArticleRepository,
    private val listRepository: ListRepository,
    private val tagRepository: TagRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            return HomeViewModel(articleRepository, listRepository, tagRepository) as T
        }
        if (modelClass.isAssignableFrom(com.vedesh.readfree.ui.reader.ReaderViewModel::class.java)) {
            return com.vedesh.readfree.ui.reader.ReaderViewModel(articleRepository, listRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
