package com.example.automediaplayer.model

import android.net.Uri

data class MusicData(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val uri: Uri,
    val filePath: String
) {
    // 格式化时长显示 (毫秒 -> 分:秒)
    fun getFormattedDuration(): String {
        val minutes = (duration / 1000) / 60
        val seconds = (duration / 1000) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
}
