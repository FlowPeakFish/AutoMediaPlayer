package com.example.automediaplayer.util

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.MediaStore
import android.util.Log
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
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.ALBUM_ID
        )

        // 查询条件：是音乐文件，且时长大于45秒（避免识别短音频）
        val selection =
            "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 45000"

        // 排序：按歌曲名称排序
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, null, sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val title = cursor.getString(titleColumn) ?: "未知标题"
                val artist = cursor.getString(artistColumn) ?: "未知艺术家"
                val album = cursor.getString(albumColumn) ?: "未知专辑"
                val duration = cursor.getLong(durationColumn)
                val filePath = cursor.getString(dataColumn)
                val albumId = cursor.getLong(albumIdColumn)
                val cover = getCoverBitmap(contentResolver, albumId)

                val contentUri = android.net.Uri.withAppendedPath(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id.toString()
                )

                val musicData = MusicData(
                    id = id,
                    title = title,
                    artist = artist,
                    album = album,
                    duration = duration,
                    uri = contentUri,
                    filePath = filePath ?: "",
                    cover = cover
                )

                musicList.add(musicData)
            }
        }

        return@withContext musicList
    }

    private fun getCoverBitmap(
        contentResolver: ContentResolver, albumId: Long
    ): Bitmap? {
        val albumArtUri =
            android.net.Uri.parse("content://media/external/audio/albumart").buildUpon()
                .appendPath(albumId.toString()).build()
        return try {
            contentResolver.openInputStream(albumArtUri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: Exception) {
            null
        }
    }
}