package com.vedesh.readfree.ui.home

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

class ArticleAdapter(
    private val onClick: (ArticleWithTags) -> Unit,
    private val onLongClick: (ArticleWithTags) -> Unit
) : ListAdapter<ArticleWithTags, ArticleAdapter.ViewHolder>(ArticleDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemArticleCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding, onClick, onLongClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemArticleCardBinding,
        private val onClick: (ArticleWithTags) -> Unit,
        private val onLongClick: (ArticleWithTags) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ArticleWithTags) {
            binding.root.setOnClickListener { onClick(item) }
            binding.root.setOnLongClickListener { 
                onLongClick(item)
                true
            }

            binding.tvTitle.text = item.article.title.ifEmpty { "Untitled Article" }
            binding.tvUrl.text = UrlUtils.formatUrlForDisplay(item.article.url)
            
            // Set Read State color
            val colorRes = when (item.article.readState) {
                ReadState.UNREAD -> R.color.read_state_unread
                ReadState.READING -> R.color.read_state_reading
                ReadState.READ -> R.color.read_state_read
            }
            binding.readStateIndicator.backgroundTintList = 
                ContextCompat.getColorStateList(binding.root.context, colorRes)

            // Formatted Date
            val diff = System.currentTimeMillis() - item.article.savedAt
            val days = (diff / (1000 * 60 * 60 * 24)).coerceAtLeast(0)
            binding.tvDate.text = if (days == 0L) "Saved today" else "Saved $days days ago"

            // Offline Indicator
            binding.ivOffline.visibility = if (item.article.offlineFilePath != null) View.VISIBLE else View.GONE

            // Tags
            binding.chipGroupTags.removeAllViews()
            if (item.tags.isEmpty()) {
                binding.tagsScrollView.visibility = View.GONE
            } else {
                binding.tagsScrollView.visibility = View.VISIBLE
                item.tags.forEach { tag ->
                    val chip = Chip(binding.root.context).apply {
                        text = tag.name
                        textSize = 10f
                        isClickable = false
                        chipMinHeight = 24f
                        ensureAccessibleTouchTarget(24)
                    }
                    binding.chipGroupTags.addView(chip)
                }
            }
        }
    }

    class ArticleDiffCallback : DiffUtil.ItemCallback<ArticleWithTags>() {
        override fun areItemsTheSame(oldItem: ArticleWithTags, newItem: ArticleWithTags): Boolean {
            return oldItem.article.url == newItem.article.url
        }

        override fun areContentsTheSame(oldItem: ArticleWithTags, newItem: ArticleWithTags): Boolean {
            return oldItem == newItem
        }
    }
}
