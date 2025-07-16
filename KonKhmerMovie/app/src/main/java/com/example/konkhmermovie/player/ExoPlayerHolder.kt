package com.example.konkhmermovie.player

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer

object ExoPlayerHolder {
    private var exoPlayer: ExoPlayer? = null

    fun getPlayer(context: Context): ExoPlayer {
        if (exoPlayer == null) {
            exoPlayer = ExoPlayer.Builder(context.applicationContext).build()
        }
        return exoPlayer!!
    }

    fun releasePlayer() {
        exoPlayer?.release()
        exoPlayer = null
    }
}