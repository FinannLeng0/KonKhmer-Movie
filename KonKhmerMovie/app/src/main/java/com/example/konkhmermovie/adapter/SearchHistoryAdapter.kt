package com.example.konkhmermovie.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.konkhmermovie.databinding.ItemSearchHistoryBinding
import com.example.konkhmermovie.model.SearchHistoryItem

class SearchHistoryAdapter(
    private val items: MutableList<SearchHistoryItem>,
    private val onItemClick: (SearchHistoryItem) -> Unit
) : RecyclerView.Adapter<SearchHistoryAdapter.ViewHolder>() {

    inner class ViewHolder(private val binding: ItemSearchHistoryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: SearchHistoryItem) {
            binding.titleTextView.text = item.title
            Glide.with(binding.imageView.context)
                .load(item.thumbnailUrl)
                .into(binding.imageView)
            binding.root.setOnClickListener {
                onItemClick(item)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSearchHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }


}
