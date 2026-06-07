package com.vedesh.readfree.ui.lists

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.vedesh.readfree.R
import com.vedesh.readfree.ReadFreeApp
import com.vedesh.readfree.data.model.ListWithCount
import com.vedesh.readfree.ui.ViewModelFactory
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Collections

class ListsFragment : Fragment() {
    private val viewModel: ListsViewModel by viewModels {
        val app = requireContext().applicationContext as ReadFreeApp
        ViewModelFactory(app.articleRepository, app.listRepository, app.tagRepository, app.raindropRepository)
    }

    private lateinit var adapter: ListAdapterImpl

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_lists, container, false)
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

        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerViewLists)

        adapter =
            ListAdapterImpl(
                onItemClick = { listWithCount ->
                    val bundle =
                        android.os.Bundle().apply {
                            putLong("listId", listWithCount.list.id)
                            putString("title", listWithCount.list.name)
                        }
                    findNavController().navigate(R.id.action_listsFragment_to_articleListFragment, bundle)
                },
            )
        view.findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fabAddList).setOnClickListener {
            showCreateListSheet()
        }

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        val itemTouchHelper =
            ItemTouchHelper(
                object : ItemTouchHelper.SimpleCallback(
                    ItemTouchHelper.UP or ItemTouchHelper.DOWN,
                    ItemTouchHelper.LEFT,
                ) {
                    override fun onMove(
                        recyclerView: RecyclerView,
                        viewHolder: RecyclerView.ViewHolder,
                        target: RecyclerView.ViewHolder,
                    ): Boolean {
                        val fromPos = viewHolder.adapterPosition
                        val toPos = target.adapterPosition
                        val currentList = adapter.currentList.toMutableList()
                        Collections.swap(currentList, fromPos, toPos)
                        adapter.submitList(currentList)
                        return true
                    }

                    override fun clearView(
                        recyclerView: RecyclerView,
                        viewHolder: RecyclerView.ViewHolder,
                    ) {
                        super.clearView(recyclerView, viewHolder)
                        // When drag ends, update order in DB
                        viewModel.updateSortOrder(adapter.currentList)
                    }

                    override fun onSwiped(
                        viewHolder: RecyclerView.ViewHolder,
                        direction: Int,
                    ) {
                        val position = viewHolder.adapterPosition
                        val item = adapter.currentList[position]
                        viewModel.deleteList(item.list)
                        Snackbar.make(view, "List deleted", Snackbar.LENGTH_LONG)
                            .setAction("Undo") {
                                // Undo logic would re-insert the list. For now just show toast or omit.
                            }.show()
                    }
                },
            )
        itemTouchHelper.attachToRecyclerView(recyclerView)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.lists.collectLatest { lists ->
                    adapter.submitList(lists)
                }
            }
        }
    }

    class ListAdapterImpl(
        private val onItemClick: (ListWithCount) -> Unit,
    ) : ListAdapter<ListWithCount, ListAdapterImpl.ViewHolder>(ListDiffCallback()) {
        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int,
        ): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_list_row, parent, false)
            return ViewHolder(view, onItemClick)
        }

        override fun onBindViewHolder(
            holder: ViewHolder,
            position: Int,
        ) {
            holder.bind(getItem(position))
        }

        class ViewHolder(
            view: View,
            private val onItemClick: (ListWithCount) -> Unit,
        ) : RecyclerView.ViewHolder(view) {
            private val tvEmoji: TextView = view.findViewById(R.id.tvEmoji)
            private val tvListName: TextView = view.findViewById(R.id.tvListName)
            private val tvCount: TextView = view.findViewById(R.id.tvCount)
            private val ivDragHandle: ImageView = view.findViewById(R.id.ivDragHandle)

            fun bind(item: ListWithCount) {
                itemView.setOnClickListener { onItemClick(item) }
                tvEmoji.text = item.list.emoji
                tvListName.text = item.list.name
                tvListName.setTextColor(Color.parseColor(item.list.colorHex))
                tvCount.text = item.articleCount.toString()
                ivDragHandle.visibility = View.VISIBLE
            }
        }

        class ListDiffCallback : DiffUtil.ItemCallback<ListWithCount>() {
            override fun areItemsTheSame(
                oldItem: ListWithCount,
                newItem: ListWithCount,
            ): Boolean {
                return oldItem.list.id == newItem.list.id
            }

            override fun areContentsTheSame(
                oldItem: ListWithCount,
                newItem: ListWithCount,
            ): Boolean {
                return oldItem == newItem
            }
        }
    }

    private fun showCreateListSheet() {
        val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(requireContext())
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_create_list, null)
        sheet.setContentView(sheetView)

        val etListName = sheetView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etListName)
        val chipGroupEmojis = sheetView.findViewById<com.google.android.material.chip.ChipGroup>(R.id.chipGroupEmojis)
        val radioGroupColors = sheetView.findViewById<android.widget.RadioGroup>(R.id.radioGroupColors)
        val btnCreateList = sheetView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCreateList)

        val emojis = listOf("📁", "📚", "💼", "🤖", "🎯", "⭐", "🔬", "🎨", "🌐", "📝", "🏠", "💡", "🔥", "🌱", "🎵", "🎮")
        val colors = listOf("#6C63FF", "#FF6584", "#4CAF50", "#FF9800", "#00BCD4", "#E91E63", "#9C27B0", "#3F51B5")

        emojis.forEach { emoji ->
            val chip =
                com.google.android.material.chip.Chip(requireContext()).apply {
                    text = emoji
                    isCheckable = true
                    setChipDrawable(
                        com.google.android.material.chip.ChipDrawable.createFromAttributes(
                            requireContext(),
                            null,
                            0,
                            com.google.android.material.R.style.Widget_MaterialComponents_Chip_Choice,
                        ),
                    )
                }
            chipGroupEmojis.addView(chip)
        }

        colors.forEach { colorHex ->
            val rb =
                android.widget.RadioButton(requireContext()).apply {
                    buttonTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor(colorHex))
                    tag = colorHex
                }
            radioGroupColors.addView(rb)
        }

        if (chipGroupEmojis.childCount > 0) (chipGroupEmojis.getChildAt(0) as com.google.android.material.chip.Chip).isChecked = true
        if (radioGroupColors.childCount > 0) (radioGroupColors.getChildAt(0) as android.widget.RadioButton).isChecked = true

        btnCreateList.setOnClickListener {
            val name = etListName.text.toString().trim()
            if (name.isNotEmpty()) {
                val selectedEmojiChip = chipGroupEmojis.findViewById<com.google.android.material.chip.Chip>(chipGroupEmojis.checkedChipId)
                val emoji = selectedEmojiChip?.text?.toString() ?: "📁"

                val selectedColorRb = radioGroupColors.findViewById<android.widget.RadioButton>(radioGroupColors.checkedRadioButtonId)
                val color = selectedColorRb?.tag?.toString() ?: "#6C63FF"

                viewModel.createList(name, emoji, color)
                sheet.dismiss()
            }
        }

        sheet.show()
    }
}
