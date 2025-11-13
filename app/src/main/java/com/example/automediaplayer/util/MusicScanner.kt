package com.example.automediaplayer.util

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.MediaStore
import androidx.core.net.toUri
import com.example.automediaplayer.model.MusicData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 媒体库扫描工具。
 *
 * 你可以把它想成“去系统图书馆借 CD 的人”：它负责跑到 Android 系统提供的
 * MediaStore 数据库里，把可播放的音乐一首一首查询出来，并包装成 [MusicData] 对象。
 */
object MusicScanner {
    /**
     * 扫描本地音乐的“主入口”函数。
     *
     * - 这是一个 `suspend` 函数：意味着它必须运行在协程里，不能阻塞主线程。
     * - `withContext(Dispatchers.IO)`：显式切到 IO 线程池执行，避免在主线程做耗时的
     *   磁盘/数据库操作导致界面卡顿。可以把它理解成“把重活交给后台线程去干”。
     */
    suspend fun scanMusicFiles(context: Context): List<MusicData> = withContext(Dispatchers.IO) {
        val musicList = mutableListOf<MusicData>()
        // ContentResolver 是系统提供的“数据访问管家”，通过它才能访问 MediaStore 里的多媒体数据。
        val contentResolver: ContentResolver = context.contentResolver

        // 1. 告诉系统：我们需要哪些“字段”（就像 SQL 的 SELECT 列表）。
        //    这些列涵盖了歌曲的 ID、标题、歌手等基础信息。
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.ALBUM_ID
        )

        // 2. 设定查询条件：
        //    - `IS_MUSIC != 0` 表示只挑真正的音乐文件，排除通知音/铃声。
        //    - `DURATION >= 45000` 过滤掉长度小于 45 秒的内容，避免扫描到无意义的片段。
        val selection =
            "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 45000"

        // 3. 指定排序方式，这里按标题升序排，列表显示会更有序。
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, null, sortOrder
        )?.use { cursor ->
            // Cursor 类似数据库查询结果的“指针”，use { } 会在块执行完毕后自动关闭它，避免资源泄露。
            // 4. 把我们想要的每一列对应的“下标”找出来，方便后续读取。
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

            // 5. 逐行遍历查询结果，把每首歌的元数据取出来拼成 MusicData。
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val title = cursor.getString(titleColumn) ?: "未知标题"    // 有些字段可能为空，给默认值
                val artist = cursor.getString(artistColumn) ?: "未知艺术家"
                val album = cursor.getString(albumColumn) ?: "未知专辑"
                val duration = cursor.getLong(durationColumn)
                val filePath = cursor.getString(dataColumn)
                val albumId = cursor.getLong(albumIdColumn)
                val cover = getCoverBitmap(contentResolver, albumId)

                // 6. Content Uri 是 MediaPlayer 真正能识别的地址，相当于“这首歌在系统目录里的坐标”。
                // withAppendedPath 会把 id 拼到基础 Uri 后面，最终得到 content://.../id 这样的地址。
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

        // return@withContext 语法表示“从 withContext 这段协程块返回值”，等价于最终函数返回 musicList。
        return@withContext musicList
    }

    /**
     * 根据专辑 ID 读取专辑封面，如果媒体库没有封面就返回 null。
     *
     * 这里同样使用 ContentResolver 去访问专辑封面提供者，得到一个输入流后交给
     * BitmapFactory 解码即可。try-catch 是为了防止因为封面缺失导致程序崩溃。
     */
    private fun getCoverBitmap(
        contentResolver: ContentResolver, albumId: Long
    ): Bitmap? {
        val albumArtUri =
        // toUri()/buildUpon()/appendPath
            // 像搭积木一样逐段拼出 content://media/.../albumId。
            "content://media/external/audio/albumart".toUri().buildUpon()
                .appendPath(albumId.toString()).build()
        return try {
            // openInputStream 打开一个“输入流”，BitmapFactory.decodeStream 则负责把二进制内容转成图片对象。
            contentResolver.openInputStream(albumArtUri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (_: Exception) {
            null
        }
    }
}
