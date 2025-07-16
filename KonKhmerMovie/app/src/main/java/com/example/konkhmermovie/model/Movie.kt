package com.example.konkhmermovie.model

data class Movie(
    val title: String = "",
    val description: String = "No description",
    val imageUrl: String = "",
    val videoUrl: String = "",
    val thumbnailUrl: String = "",
    val userId: String? = null,
    var timestamp: Long? = null,
    var id: String? = null
)