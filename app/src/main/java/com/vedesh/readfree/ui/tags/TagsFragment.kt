package com.vedesh.readfree.ui.tags

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.vedesh.readfree.R
import com.vedesh.readfree.ReadFreeApp
import com.vedesh.readfree.ui.ViewModelFactory
import com.vedesh.readfree.util.tooltipFromContentDescription
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class TagsFragment : Fragment() {
    private val viewModel: TagsViewModel by viewModels {
        val app = requireContext().applicationContext as ReadFreeApp
        ViewModelFactory(app.articleRepository, app.listRepository, app.tagRepository, app.raindropRepository)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_tags, container, false)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        val chipGroup = view.findViewById<ChipGroup>(R.id.chipGroupTags)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.tags.collectLatest { tags ->
                    chipGroup.removeAllViews()
                    tags.forEach { tagWithCount ->
                        val chip =
                            Chip(requireContext()).apply {
                                text = "${tagWithCount.name} (${tagWithCount.articleCount})"
                                isClickable = true
                                setOnClickListener {
                                    val bundle =
                                        android.os.Bundle().apply {
                                            putString("tagName", tagWithCount.name)
                                            putString("title", "#${tagWithCount.name}")
                                        }
                                    findNavController().navigate(R.id.action_tagsFragment_to_articleListFragment, bundle)
                                }
                                setOnLongClickListener {
                                    showTagOptionsDialog(tagWithCount.name)
                                    true
                                }
                            }
                        chipGroup.addView(chip)
                    }
                }
            }
        }

        view.findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fabAddTag).apply {
            setOnClickListener { showCreateTagDialog() }
            tooltipFromContentDescription()
        }
    }

    private fun showTagOptionsDialog(tagName: String) {
        val options = arrayOf("✏️ Rename", "🗑 Delete")
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("#$tagName")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showRenameTagDialog(tagName)
                    1 -> showDeleteTagConfirmation(tagName)
                }
            }
            .show()
    }

    private fun showRenameTagDialog(tagName: String) {
        val input = android.widget.EditText(requireContext()).apply {
            setText(tagName)
            selectAll()
        }
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Rename Tag")
            .setView(input)
            .setPositiveButton("Rename") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty() && newName != tagName) {
                    viewModel.renameTag(tagName, newName)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteTagConfirmation(tagName: String) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Delete Tag")
            .setMessage("Delete \"#$tagName\"? It will be removed from all articles.")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteTag(com.vedesh.readfree.data.db.entity.Tag(tagName))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCreateTagDialog() {
        val input = android.widget.EditText(requireContext()).apply { hint = "Tag name" }
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Create New Tag")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) viewModel.createTag(name)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
