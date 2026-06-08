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
import com.vedesh.readfree.data.db.entity.ArticleList
import com.vedesh.readfree.ui.ViewModelFactory
import com.vedesh.readfree.util.tooltipFromContentDescription
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Collections

class ListsFragment : Fragment() {
    private val viewModel: ListsViewModel by viewModels {
        val app = requireContext().applicationContext as ReadFreeApp
        ViewModelFactory(app.articleRepository, app.listRepository, app.tagRepository, app.raindropRepository)
    }

    private lateinit var adapter: ListItemAdapter

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

        view.findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fabAddList)
            .tooltipFromContentDescription()

        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerViewLists)

        adapter =
            ListItemAdapter(
                onSystemRowClick = { systemRow ->
                    val bundle =
                        Bundle().apply {
                            putString("title", systemRow.name)
                            when (systemRow.id) {
                                "_offline" -> putBoolean("isOffline", true)
                                "_unsorted" -> putBoolean("isUnsorted", true)
                            }
                        }
                    findNavController().navigate(R.id.action_listsFragment_to_articleListFragment, bundle)
                },
                onUserListClick = { userListItem ->
                    val bundle =
                        Bundle().apply {
                            putLong("listId", userListItem.listWithCount.list.id)
                            putString("title", userListItem.listWithCount.list.name)
                        }
                    findNavController().navigate(R.id.action_listsFragment_to_articleListFragment, bundle)
                },
                onUserListLongClick = { userListItem ->
                    showEditListSheet(userListItem.listWithCount.list)
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

                    override fun isLongPressDragEnabled(): Boolean = false

                    override fun isItemViewSwipeEnabled(): Boolean = true

                    override fun getDragDirs(
                        recyclerView: RecyclerView,
                        viewHolder: RecyclerView.ViewHolder,
                    ): Int {
                        return if (adapter.currentList[viewHolder.adapterPosition] is ListItem.SystemRow)
                            0 else ItemTouchHelper.UP or ItemTouchHelper.DOWN
                    }

                    override fun getSwipeDirs(
                        recyclerView: RecyclerView,
                        viewHolder: RecyclerView.ViewHolder,
                    ): Int {
                        return if (adapter.currentList[viewHolder.adapterPosition] is ListItem.SystemRow)
                            0 else ItemTouchHelper.LEFT
                    }

                    override fun clearView(
                        recyclerView: RecyclerView,
                        viewHolder: RecyclerView.ViewHolder,
                    ) {
                        super.clearView(recyclerView, viewHolder)
                        viewModel.updateSortOrder(adapter.currentList)
                    }

                    override fun onSwiped(
                        viewHolder: RecyclerView.ViewHolder,
                        direction: Int,
                    ) {
                        val position = viewHolder.adapterPosition
                        val item = adapter.currentList[position] as ListItem.UserList
                        viewModel.deleteList(item.listWithCount.list)
                        Snackbar.make(view, "List deleted", Snackbar.LENGTH_LONG)
                            .setAction("Undo") {
                                viewModel.restoreList(item.listWithCount.list)
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

    private val VIEW_TYPE_SYSTEM = 0
    private val VIEW_TYPE_USER_LIST = 1

    inner class ListItemAdapter(
        private val onSystemRowClick: (ListItem.SystemRow) -> Unit,
        private val onUserListClick: (ListItem.UserList) -> Unit,
        private val onUserListLongClick: (ListItem.UserList) -> Unit,
    ) : ListAdapter<ListItem, RecyclerView.ViewHolder>(ListItemDiffCallback()) {
        override fun getItemViewType(position: Int): Int {
            return when (getItem(position)) {
                is ListItem.SystemRow -> VIEW_TYPE_SYSTEM
                is ListItem.UserList -> VIEW_TYPE_USER_LIST
            }
        }

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int,
        ): RecyclerView.ViewHolder {
            return when (viewType) {
                VIEW_TYPE_SYSTEM -> {
                    val view = LayoutInflater.from(parent.context).inflate(R.layout.item_list_row, parent, false)
                    SystemRowViewHolder(view, onSystemRowClick)
                }
                else -> {
                    val view = LayoutInflater.from(parent.context).inflate(R.layout.item_list_row, parent, false)
                    UserListViewHolder(view, onUserListClick, onUserListLongClick)
                }
            }
        }

        override fun onBindViewHolder(
            holder: RecyclerView.ViewHolder,
            position: Int,
        ) {
            when (holder) {
                is SystemRowViewHolder -> holder.bind(getItem(position) as ListItem.SystemRow)
                is UserListViewHolder -> holder.bind(getItem(position) as ListItem.UserList)
            }
        }
    }

    class SystemRowViewHolder(
        view: View,
        private val onSystemRowClick: (ListItem.SystemRow) -> Unit,
    ) : RecyclerView.ViewHolder(view) {
        private val tvEmoji: TextView = view.findViewById(R.id.tvEmoji)
        private val tvListName: TextView = view.findViewById(R.id.tvListName)
        private val tvCount: TextView = view.findViewById(R.id.tvCount)
        private val ivDragHandle: ImageView = view.findViewById(R.id.ivDragHandle)

        fun bind(item: ListItem.SystemRow) {
            itemView.setOnClickListener { onSystemRowClick(item) }
            tvEmoji.text = item.emoji
            tvListName.text = item.name
            tvListName.setTextColor(Color.parseColor(item.colorHex))
            tvCount.text = item.count.toString()
            ivDragHandle.visibility = View.GONE
        }
    }

    class UserListViewHolder(
        view: View,
        private val onUserListClick: (ListItem.UserList) -> Unit,
        private val onUserListLongClick: (ListItem.UserList) -> Unit,
    ) : RecyclerView.ViewHolder(view) {
        private val tvEmoji: TextView = view.findViewById(R.id.tvEmoji)
        private val tvListName: TextView = view.findViewById(R.id.tvListName)
        private val tvCount: TextView = view.findViewById(R.id.tvCount)
        private val ivDragHandle: ImageView = view.findViewById(R.id.ivDragHandle)

        fun bind(item: ListItem.UserList) {
            val listWithCount = item.listWithCount
            itemView.setOnClickListener { onUserListClick(item) }
            itemView.setOnLongClickListener {
                onUserListLongClick(item)
                true
            }
            tvEmoji.text = listWithCount.list.emoji
            tvListName.text = listWithCount.list.name
            tvListName.setTextColor(Color.parseColor(listWithCount.list.colorHex))
            tvCount.text = listWithCount.articleCount.toString()
            ivDragHandle.visibility = View.VISIBLE
        }
    }

    class ListItemDiffCallback : DiffUtil.ItemCallback<ListItem>() {
        override fun areItemsTheSame(
            oldItem: ListItem,
            newItem: ListItem,
        ): Boolean {
            return when {
                oldItem is ListItem.SystemRow && newItem is ListItem.SystemRow -> oldItem.id == newItem.id
                oldItem is ListItem.UserList && newItem is ListItem.UserList -> oldItem.listWithCount.list.id == newItem.listWithCount.list.id
                else -> false
            }
        }

        override fun areContentsTheSame(
            oldItem: ListItem,
            newItem: ListItem,
        ): Boolean {
            return oldItem == newItem
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

        val emojis = listOf("\uD83D\uDCC1", "\uD83D\uDCDA", "\uD83D\uDCBC", "\uD83E\uDD16", "\uD83C\uDFAF", "\u2B50", "\uD83D\uDD2C", "\uD83C\uDFA8", "\uD83C\uDF10", "\uD83D\uDCDD", "\uD83C\uDFE0", "\uD83D\uDCA1", "\uD83D\uDD25", "\uD83C\uDF31", "\uD83C\uDFB5", "\uD83C\uDFAE")
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
                val emoji = selectedEmojiChip?.text?.toString() ?: "\uD83D\uDCC1"

                val selectedColorRb = radioGroupColors.findViewById<android.widget.RadioButton>(radioGroupColors.checkedRadioButtonId)
                val color = selectedColorRb?.tag?.toString() ?: "#6C63FF"

                viewModel.createList(name, emoji, color)
                sheet.dismiss()
            }
        }

        sheet.show()
    }

    private fun showEditListSheet(list: ArticleList) {
        val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(requireContext())
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_create_list, null)
        sheet.setContentView(sheetView)

        val header = (sheetView as? ViewGroup)?.getChildAt(0) as? TextView
        header?.text = "Edit List"

        val etListName = sheetView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etListName)
        etListName.setText(list.name)

        val chipGroupEmojis = sheetView.findViewById<com.google.android.material.chip.ChipGroup>(R.id.chipGroupEmojis)
        val radioGroupColors = sheetView.findViewById<android.widget.RadioGroup>(R.id.radioGroupColors)

        val emojis = listOf("\uD83D\uDCC1", "\uD83D\uDCDA", "\uD83D\uDCBC", "\uD83E\uDD16", "\uD83C\uDFAF", "\u2B50", "\uD83D\uDD2C", "\uD83C\uDFA8", "\uD83C\uDF10", "\uD83D\uDCDD", "\uD83C\uDFE0", "\uD83D\uDCA1", "\uD83D\uDD25", "\uD83C\uDF31", "\uD83C\uDFB5", "\uD83C\uDFAE")
        val colors = listOf("#6C63FF", "#FF6584", "#4CAF50", "#FF9800", "#00BCD4", "#E91E63", "#9C27B0", "#3F51B5")

        chipGroupEmojis.removeAllViews()
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
            if (emoji == list.emoji) chip.isChecked = true
        }

        radioGroupColors.removeAllViews()
        colors.forEachIndexed { _, colorHex ->
            val rb =
                android.widget.RadioButton(requireContext()).apply {
                    buttonTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor(colorHex))
                    tag = colorHex
                }
            radioGroupColors.addView(rb)
            if (colorHex == list.colorHex) rb.isChecked = true
        }

        if (chipGroupEmojis.checkedChipId == View.NO_ID && chipGroupEmojis.childCount > 0)
            (chipGroupEmojis.getChildAt(0) as com.google.android.material.chip.Chip).isChecked = true
        if (radioGroupColors.checkedRadioButtonId == View.NO_ID && radioGroupColors.childCount > 0)
            (radioGroupColors.getChildAt(0) as android.widget.RadioButton).isChecked = true

        val btnCreateList = sheetView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCreateList)
        btnCreateList.text = "Save"
        btnCreateList.setOnClickListener {
            val name = etListName.text.toString().trim()
            if (name.isNotEmpty()) {
                val selectedEmojiChip = chipGroupEmojis.findViewById<com.google.android.material.chip.Chip>(chipGroupEmojis.checkedChipId)
                val emoji = selectedEmojiChip?.text?.toString() ?: "\uD83D\uDCC1"

                val selectedColorRb = radioGroupColors.findViewById<android.widget.RadioButton>(radioGroupColors.checkedRadioButtonId)
                val color = selectedColorRb?.tag?.toString() ?: "#6C63FF"

                viewModel.updateList(list.copy(name = name, emoji = emoji, colorHex = color))
                sheet.dismiss()
            }
        }

        sheet.show()
    }
}
