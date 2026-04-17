package com.tapweb.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.tapweb.R
import com.tapweb.data.db.WebSiteEntity
import com.tapweb.data.model.IconType
import com.tapweb.data.model.OpenMode
import com.tapweb.databinding.ItemWebsiteCardBinding
import com.tapweb.util.FaviconManager
import java.io.File

class WebSiteAdapter(
    private val onClick: (WebSiteEntity) -> Unit,
    private val onLongClick: (WebSiteEntity, View) -> Unit,
    private val onViewUrl: (WebSiteEntity) -> Unit
) : ListAdapter<WebSiteEntity, WebSiteAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(val binding: ItemWebsiteCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: WebSiteEntity) {
            binding.tvTitle.text = item.title
            binding.tvUrl.text = item.url

            val showExternal = item.openMode == OpenMode.BROWSER
            binding.ivOpenMode.alpha = if (showExternal) 0.7f else 0.3f

            loadIcon(item)

            binding.root.setOnClickListener { onClick(item) }
            binding.root.setOnLongClickListener {
                onLongClick(item, binding.root)
                true
            }
            binding.tvViewUrl.setOnClickListener { onViewUrl(item) }
        }

        private fun loadIcon(item: WebSiteEntity) {
            val context = binding.root.context
            when (item.iconType) {
                IconType.CUSTOM -> {
                    if (!item.customIconPath.isNullOrBlank()) {
                        val file = File(item.customIconPath)
                        if (file.exists()) {
                            Glide.with(context)
                                .load(file)
                                .placeholder(R.drawable.ic_globe)
                                .error(R.drawable.ic_globe)
                                .circleCrop()
                                .into(binding.ivFavicon)
                            return
                        }
                    }
                    // Fallback to auto
                    loadAutoIcon(item)
                }
                IconType.DEFAULT_FAVICON -> {
                    val defaultUrl = FaviconManager.buildDefaultFaviconUrl(item.url)
                    Glide.with(context)
                        .load(defaultUrl)
                        .placeholder(R.drawable.ic_globe)
                        .error(R.drawable.ic_globe)
                        .circleCrop()
                        .into(binding.ivFavicon)
                }
                IconType.AUTO -> loadAutoIcon(item)
            }
        }

        private fun loadAutoIcon(item: WebSiteEntity) {
            val context = binding.root.context
            if (item.faviconUrl.isNullOrBlank()) {
                Glide.with(context).clear(binding.ivFavicon)
                binding.ivFavicon.setImageResource(R.drawable.ic_globe)
            } else {
                Glide.with(context)
                    .load(item.faviconUrl)
                    .placeholder(R.drawable.ic_globe)
                    .error(R.drawable.ic_globe)
                    .circleCrop()
                    .into(binding.ivFavicon)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(
            ItemWebsiteCardBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<WebSiteEntity>() {
            override fun areItemsTheSame(old: WebSiteEntity, new: WebSiteEntity): Boolean =
                old.id == new.id

            override fun areContentsTheSame(old: WebSiteEntity, new: WebSiteEntity): Boolean =
                old == new
        }
    }
}
