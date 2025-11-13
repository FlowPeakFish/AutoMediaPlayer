package com.example.automediaplayer.model

import android.graphics.Bitmap
import android.net.Uri
import java.util.Locale

/**
 * 描述一首本地音乐的完整元数据，便于在 UI、播放服务与通知之间共享。
 *
 * 你可以把它看作“音乐身份证”，里面塞满了播放所需的全部信息。
 * 每个字段都由 `MusicScanner` 在扫描时填充，后续界面或 Service 只需要读取即可。
 *
 * @property id 媒体库中的唯一 ID，可用于重新查询或构建 Uri。
 * @property title 歌曲标题，供列表和播放器界面展示。
 * @property artist 艺术家信息，缺失时由扫描器填入"未知艺术家"。
 * @property album 所属专辑名称，配合封面信息一起显示。
 * @property duration 音频时长（毫秒），在 SeekBar/列表中展示进度。
 * @property uri 可直接交给 MediaPlayer 的内容 Uri（比文件路径更安全可靠）。
 * @property filePath 物理文件路径，方便后续扩展如分享/删除。
 * @property cover 专辑封面 Bitmap，没有封面时为 null。
 */
data class MusicData(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val uri: Uri,
    val filePath: String,
    val cover: Bitmap? = null
) {
    /**
     * 将毫秒级时长格式化为 mm:ss，方便列表与播放条复用。
     *
     * 具体做法：
     * 1. 全部转成秒（duration 是毫秒）。
     * 2. `分钟 = 秒数 / 60`，`秒 = 秒数 % 60`。
     * 3. 用 `%02d` 补零，保证 1:5 也会显示成 01:05。
     */
    fun getFormattedDuration(): String {
        val minutes = (duration / 1000) / 60
        val seconds = (duration / 1000) % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}
