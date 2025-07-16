package com.example.konkhmermovie.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.example.konkhmermovie.R
import com.example.konkhmermovie.databinding.ItemBannerBinding
import com.example.konkhmermovie.model.Banner

class BannerAdapter : RecyclerView.Adapter<BannerAdapter.BannerViewHolder>() {

    private val banners = mutableListOf<Banner>()

    fun submitList(newBanners: List<Banner>) {
        banners.clear()
        banners.addAll(newBanners)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BannerViewHolder {
        val binding = ItemBannerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BannerViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BannerViewHolder, position: Int) {
        val banner = banners[position % banners.size]
        holder.bind(banner)
    }

    override fun getItemCount(): Int = banners.size

    inner class BannerViewHolder(private val binding: ItemBannerBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(banner: Banner) {
            Glide.with(binding.root.context)
                .load(banner.imageUrl)
                .centerCrop()
                .placeholder(R.drawable.placeholder)
                .transition(DrawableTransitionOptions.withCrossFade(500))
                .into(binding.bannerImage)
        }
    }
}