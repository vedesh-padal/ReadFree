package com.vedesh.readfree.ui.home

import android.graphics.Color
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.vedesh.readfree.R
import com.vedesh.readfree.UrlUtils
import com.vedesh.readfree.data.db.entity.ReadState
import com.vedesh.readfree.data.model.ArticleWithTags
import com.vedesh.readfree.databinding.ItemArticleCardBinding
import java.util.concurrent.TimeUnit

class ArticleAdapter(
    private val onClick: (ArticleWithTags) -> Unit,
    private val onLongClick: (ArticleWithTags) -> Unit,
) : ListAdapter<ArticleWithTags, ArticleAdapter.ViewHolder>(ArticleDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemArticleCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false,
        )
        return ViewHolder(binding, onClick, onLongClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemArticleCardBinding,
        private val onClick: (ArticleWithTags) -> Unit,
        private val onLongClick: (ArticleWithTags) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ArticleWithTags) {
            binding.root.setOnClickListener { onClick(item) }
            binding.root.setOnLongClickListener { onLongClick(item); true }

            // --- Title ---
            val title = item.article.title.ifEmpty { "Untitled Article" }
            binding.tvTitle.text = title

            // --- Read state visual ---
            when (item.article.readState) {
                ReadState.UNREAD -> {
                    binding.tvTitle.setTypeface(null, Typeface.BOLD)
                    binding.tvTitle.alpha = 1f
                    binding.readStateIndicator.visibility = View.VISIBLE
                    binding.ivReadCheck.visibility = View.GONE
                    binding.readStateIndicator.backgroundTintList =
                        ContextCompat.getColorStateList(binding.root.context, R.color.read_state_unread)
                }
                ReadState.READING -> {
                    binding.tvTitle.setTypeface(null, Typeface.NORMAL)
                    binding.tvTitle.alpha = 1f
                    binding.readStateIndicator.visibility = View.VISIBLE
                    binding.ivReadCheck.visibility = View.GONE
                    binding.readStateIndicator.backgroundTintList =
                        ContextCompat.getColorStateList(binding.root.context, R.color.read_state_reading)
                }
                ReadState.READ -> {
                    binding.tvTitle.setTypeface(null, Typeface.NORMAL)
                    binding.tvTitle.alpha = 0.45f
                    binding.readStateIndicator.visibility = View.GONE
                    binding.ivReadCheck.visibility = View.VISIBLE
                }
            }

            // --- Meta: domain · time ago ---
            val domain = try {
                android.net.Uri.parse(item.article.url).host?.removePrefix("www.") ?: ""
            } catch (e: Exception) {
                UrlUtils.formatUrlForDisplay(item.article.url)
            }
            val timeAgo = formatTimeAgo(System.currentTimeMillis() - item.article.savedAt)
            binding.tvMeta.text = if (domain.isNotEmpty()) "$domain · $timeAgo" else timeAgo

            // --- Offline badge ---
            binding.ivOffline.visibility =
                if (item.article.offlineFilePath != null) View.VISIBLE else View.GONE

            // --- List chips ---
            binding.chipGroupLists.removeAllViews()
            if (item.lists.isNotEmpty()) {
                item.lists.forEach { list ->
                    val chip = Chip(binding.root.context).apply {
                        text = "${list.emoji} ${list.name}"
                        textSize = 9f
                        isClickable = false
                        chipMinHeight = 20f
                        chipStartPadding = 4f
                        chipEndPadding = 4f
                        try {
                            val color = Color.parseColor(list.colorHex)
                            setChipBackgroundColorResource(android.R.color.transparent)
                            chipStrokeWidth = 1f
                            setChipStrokeColor(android.content.res.ColorStateList.valueOf(color))
                            setTextColor(color)
                        } catch (e: Exception) { /* fallback to default */ }
                    }
                    binding.chipGroupLists.addView(chip)
                }
            }

            // --- Tag chips ---
            binding.chipGroupTags.removeAllViews()
            if (item.tags.isEmpty()) {
                binding.tagsScrollView.visibility = View.GONE
            } else {
                binding.tagsScrollView.visibility = View.VISIBLE
                item.tags.forEach { tag ->
                    val chip = Chip(binding.root.context).apply {
                        text = tag.name
                        textSize = 11f
                        isClickable = false
                        chipMinHeight = 24f
                        chipStartPadding = 8f
                        chipEndPadding = 8f
                        setChipBackgroundColorResource(android.R.color.transparent)
                        chipStrokeWidth = 1f
                        setChipStrokeColorResource(R.color.text_tertiary)
                        setTextColor(ContextCompat.getColor(binding.root.context, R.color.text_tertiary))
                    }
                    binding.chipGroupTags.addView(chip)
                }
            }
        }

        private fun formatTimeAgo(diffMs: Long): String {
            val mins = TimeUnit.MILLISECONDS.toMinutes(diffMs)
            val hours = TimeUnit.MILLISECONDS.toHours(diffMs)
            val days = TimeUnit.MILLISECONDS.toDays(diffMs)
            return when {
                mins < 1 -> "just now"
                hours < 1 -> "${mins}m ago"
                hours < 24 -> "${hours}h ago"
                days < 7 -> "${days}d ago"
                days < 30 -> "${days / 7}w ago"
                else -> "${days / 30}mo ago"
            }
        }
    }

    class ArticleDiffCallback : DiffUtil.ItemCallback<ArticleWithTags>() {
        override fun areItemsTheSame(oldItem: ArticleWithTags, newItem: ArticleWithTags) =
            oldItem.article.url == newItem.article.url

        override fun areContentsTheSame(oldItem: ArticleWithTags, newItem: ArticleWithTags) =
            oldItem == newItem
    }
}
