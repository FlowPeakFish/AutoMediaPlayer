package com.example.automediaplayer.service

import com.example.automediaplayer.model.MusicData

/**
 * 播放状态监听接口，Activity/Fragment 可通过它及时同步 UI 与提示文案。
 *
 * MusicPlayService 会在关键时刻回调这些方法，你只需要根据回调内容刷新界面即可。
 */
interface PlaybackStateListener {
    /**
     * 当播放/暂停发生切换时回调，适合更新播放按钮或动画。
     */
    fun onPlaybackStateChanged(isPlaying: Boolean)

    /**
     * 定时返回当前播放进度与总时长，用于 SeekBar 与时间文本刷新。
     */
    fun onPlaybackProgressChanged(progress: Int, duration: Int)

    /**
     * 当正在播放的歌曲发生变化时通知，例如切歌或列表重新初始化。
     */
    fun onMusicChanged(music: MusicData?)

    /**
     * 播放模式发生调整时通知（顺序/循环/随机），便于更新模式按钮。
     */
    fun onPlayModeChanged(mode: MusicPlayService.PlayMode)

    /**
     * 服务捕获到的播放错误消息，交给 UI 决定如何提示用户。
     */
    fun onPlaybackError(error: String)
}
