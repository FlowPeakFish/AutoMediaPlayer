package com.example.automediaplayer.util

import android.content.ContentResolver
import android.content.Context
import android.provider.MediaStore
import com.example.automediaplayer.model.MusicData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MusicScanner {
    suspend fun scanMusicFiles(context: Context): List<MusicData> = withContext(Dispatchers.IO) {
        val musicList = mutableListOf<MusicData>()
        val contentResolver: ContentResolver = context.contentResolver

        // 定义要查询的列
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA
        )

        // 查询条件：是音乐文件，且时长大于45秒（避免识别短音频）
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 45000"

        // 排序：按歌曲名称排序
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val title = cursor.getString(titleColumn) ?: "未知标题"
                val artist = cursor.getString(artistColumn) ?: "未知艺术家"
                val album = cursor.getString(albumColumn) ?: "未知专辑"
                val duration = cursor.getLong(durationColumn)
                val filePath = cursor.getString(dataColumn)

                val contentUri = android.net.Uri.withAppendedPath(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id.toString()
                )

                val musicData = MusicData(
                    id = id,
                    title = title,
                    artist = artist,
                    album = album,
                    duration = duration,
                    uri = contentUri,
                    filePath = filePath ?: ""
                )

                musicList.add(musicData)
            }
        }

        return@withContext musicList
    }
}