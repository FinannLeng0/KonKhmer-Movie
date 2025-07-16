package com.example.konkhmermovie.adapter

import android.content.Context
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.example.konkhmermovie.R
import com.example.konkhmermovie.model.Movie

class UserMovieAdapter(
    private val context: Context,
    private val movieList: MutableList<Movie>,
    private val onItemClick: (Movie) -> Unit,
    private val onDeleteClick: (Movie) -> Unit
) : RecyclerView.Adapter<UserMovieAdapter.MovieViewHolder>() {

    inner class MovieViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val thumbnailImageView: ImageView = itemView.findViewById(R.id.thumbnailImageView)
        val titleTextView: TextView = itemView.findViewById(R.id.titleTextView)
        val postStatusTextView: TextView = itemView.findViewById(R.id.postStatusTextView)
        val deleteTextView: TextView = itemView.findViewById(R.id.deleteTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MovieViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_user_video, parent, false)
        return MovieViewHolder(view)
    }

    override fun onBindViewHolder(holder: MovieViewHolder, position: Int) {
        val movie = movieList[position]

        holder.titleTextView.text = movie.title
        holder.postStatusTextView.text = "Your video was posted"
        holder.deleteTextView.paintFlags = holder.deleteTextView.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        holder.deleteTextView.setTextColor(context.getColor(android.R.color.holo_red_dark))

        Glide.with(context)
            .load(movie.thumbnailUrl)
            .transform(CenterCrop(), RoundedCorners(20))
            .into(holder.thumbnailImageView)

        holder.itemView.setOnClickListener { onItemClick(movie) }
        holder.deleteTextView.setOnClickListener { onDeleteClick(movie) }
    }

    override fun getItemCount(): Int = movieList.size
}