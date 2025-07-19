package com.example.konkhmermovie.model

data class DownloadItem(
    var id: String = "",
    var title: String = "",
    var imageUrl: String = "",
    var videoUrl: String = "",
    var downloadId: Long = -1L,
    var downloadedAt: Long = 0L
)