package com.vedesh.readfree.ui.tags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vedesh.readfree.data.repository.TagRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TagsViewModel(
    private val tagRepo: TagRepository,
) : ViewModel() {
    val tags =
        tagRepo.getAllWithCounts().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList(),
        )

    fun deleteTag(tag: com.vedesh.readfree.data.db.entity.Tag) {
        viewModelScope.launch {
            tagRepo.delete(tag)
        }
    }

    fun renameTag(
        oldName: String,
        newName: String,
    ) {
        viewModelScope.launch {
            tagRepo.rename(oldName, newName)
        }
    }

    fun createTag(name: String) {
        viewModelScope.launch {
            tagRepo.insert(com.vedesh.readfree.data.db.entity.Tag(name))
        }
    }
}
