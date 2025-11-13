package com.example.automediaplayer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
import android.media.MediaPlayer
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.automediaplayer.manager.PlayListManager
import com.example.automediaplayer.model.MusicData
import com.example.automediaplayer.service.MusicPlayService.PlayMode.LOOP
import com.example.automediaplayer.service.MusicPlayService.PlayMode.ORDER
import com.example.automediaplayer.service.MusicPlayService.PlayMode.SHUFFLE

/**
 * 音乐播放服务类，用于在后台管理音乐播放逻辑。
 *
 * 此服务支持播放、暂停、停止、上一首、下一首等基本功能，
 * 并提供播放进度更新、播放模式切换等功能。
 */
class MusicPlayService : Service() {
    /**
     * 播放模式枚举定义了三种不同的播放方式：
     * - [ORDER]：顺序播放
     * - [LOOP]：列表循环
     * - [SHUFFLE]：随机播放
     */
    enum class PlayMode {
        ORDER,    // 顺序播放
        LOOP,     // 列表循环
        SHUFFLE   // 随机播放
    }

    /**
     * 自定义Binder类，用于将服务实例暴露给绑定组件。
     */
    inner class MusicBinder : Binder() {
        /**
         * 获取当前服务实例。
         *
         * @return 当前的 [MusicPlayService] 实例。
         */
        fun getService(): MusicPlayService = this@MusicPlayService
    }

    /**
     * 常量对象，包含服务相关的静态常量。
     */
    companion object {
        /** 通知ID，用于标识前台服务的通知。 */
        const val NOTIFICATION_ID = 1001

        /** 通知渠道ID，用于创建通知渠道（适用于 Android 8.0 及以上）。 */
        const val CHANNEL_ID = "music_playback_channel"
    }

    private val mTag = "MusicPlayService"

    // 播放器相关变量：MediaPlayer 是 Android 自带的音频播放引擎。
    // isPrepared 用来记录“播放器是否已经加载好音频，可以立即播放”。
    private var mediaPlayer: MediaPlayer? = null
    private var isPrepared = false

    // 绑定相关：
    // - binder 让 Activity 能通过 bindService 拿到 Service 实例。
    private val binder = MusicBinder()

    // - playlistManager 负责算“下一首/上一首是谁”。
    private val playlistManager = PlayListManager()

    // - playbackListeners 用来存放所有想要收到播放状态的对象（Activity、通知等）。
    private val playbackListeners = mutableListOf<PlaybackStateListener>()

    // - progressHandler/edgeHintHandler 都关联到主线程 Looper：
    //   Handler(Looper.getMainLooper()) 表示“在主线程消息队列中排定任务”，
    //   这样即便代码运行在 Service 背景线程，也能安全地更新 UI/Toast。
    private val progressHandler = Handler(Looper.getMainLooper())
    private val edgeHintHandler = Handler(Looper.getMainLooper())

    /**
     * 定义进度更新任务。
     *
     * 这里声明了一个 Runnable（可执行的代码块），它会每隔 1 秒被 Handler 调度一次。
     * 每次运行时就去读取 MediaPlayer 的当前位置和总时长，再通过监听器丢给 UI。
     * 这样 Activity 就不用自己 while(true) 轮询，既省电又安全。
     */
    private val progressUpdateRunnable = object : Runnable {
        override fun run() {
            if (isPrepared && isPlaying()) {
                val progress = mediaPlayer?.currentPosition ?: 0
                val duration = mediaPlayer?.duration ?: 0
                playbackListeners.forEach { listener ->
                    listener.onPlaybackProgressChanged(progress, duration)
                }
            }
            // 每秒钟更新一次进度
            progressHandler.postDelayed(this, 1000)
        }
    }

    /**
     * ==================== 服务生命周期回调 ====================
     * 以实际的生命周期触发顺序排列，方便查阅。
     */

    /**
     * 服务创建时调用的方法。
     * 初始化媒体播放器并创建通知渠道。
     */
    override fun onCreate() {
        super.onCreate()
        Log.d(mTag, "onCreate")
        initializeMediaPlayer()
        createNotificationChannel()
    }

    /**
     * 当服务被启动时调用。
     *
     * @param intent 启动服务的意图。
     * @param flags 特殊标志位。
     * @param startId 请求ID。
     * @return 表示如何重新启动服务的标志。
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(mTag, "onStartCommand")
        // 1. 构建一个“常驻通知”。Android 要求所有后台播放类型的 Service 必须前台化，
        //    否则系统会认为它是“静默占资源”的应用，可能随时被杀掉。
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
        // 2. 调用 startForeground 把服务真正“绑”到通知上：
        //    - 系统接到这行代码后，会立即显示上面的通知，并把当前进程设为前台优先级，
        //      这样即使屏幕熄灭或切换应用，音乐也不会因为内存回收而被杀掉。
        //    - 用户层面能看到通知栏里常驻一个播放器提示，知道后台正在播放，点击还能回到应用。
        //    - Manifest 中也要同步声明 android:foregroundServiceType="mediaPlayback"，否则系统会拒绝这个类型。
        //    - 本项目以 API 30+ 为基础，因此直接使用带 ServiceInfo 参数的新重载。
        //    - startForeground 只能在 Service 已经通过 startService/bindService 创建后调用，
        //      否则系统会抛 ForegroundServiceStartNotAllowedException。
        startForeground(
            NOTIFICATION_ID,
            notification,
            FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )
        return START_STICKY
    }

    /**
     * 当其他组件通过bindService绑定此服务时调用。
     *
     * @param intent 启动该服务的意图。
     * @return 返回一个IBinder对象以供客户端与服务通信。
     */
    override fun onBind(intent: Intent): IBinder {
        Log.d(mTag, "onBind")
        return binder
    }

    /**
     * 服务销毁时调用的方法。
     * 释放媒体播放器资源并移除进度更新任务。
     */
    override fun onDestroy() {
        super.onDestroy()
        Log.d(mTag, "onDestroy")
        releaseMediaPlayer()
    }

    /**
     * ==================== 播放入口与控制 ====================
     */

    /**
     * 初始化播放列表并尝试从指定音乐开始播放。
     *
     * @param playlist 新的播放列表。
     * @param musicId 可选参数，表示要播放的初始音乐ID，默认为0。
     */
    fun initPlaylist(playlist: List<MusicData>, musicId: Long = 0) {
        Log.d(mTag, "initPlaylist: musicId: $musicId")
        playlistManager.setPlaylist(playlist, musicId)
        if (playlist.isNotEmpty()) {
            playMusicAt(playlistManager.getCurrentPosition())
        }
    }

    /**
     * 根据指定的位置播放对应的音乐。
     *
     * @param position 要播放的音乐在播放列表中的索引。
     */
    fun playMusicAt(position: Int) {
        Log.d(mTag, "playMusicAt: position: $position")
        val music = playlistManager.moveToPosition(position)
        if (music == null) {
            Log.e(mTag, "playMusicAt: music is null")
            return
        }
        try {
            mediaPlayer?.reset()
            // MediaPlayer 需要一个 Context 来解析 Uri，这里使用 applicationContext：
            // - 每个 Service 都继承自 Context，因此内部可以直接访问 applicationContext（全局应用级别的 Context）。
            // - 相比传 Activity，applicationContext 生命周期更长，Activity 销毁后仍能安全读取文件。
            mediaPlayer?.setDataSource(applicationContext, music.uri)
            mediaPlayer?.prepareAsync()
            notifyPlaybackStateChanged()
        } catch (e: Exception) {
            e.printStackTrace()
            Log.d(mTag, "playMusicAt: error: ${e.message}")
            notifyPlaybackError("播放失败: ${e.message}")
        }
    }

    /**
     * 开始播放当前准备好的音频文件。
     */
    fun play() {
        if (isPrepared) {
            mediaPlayer?.start()
            startProgressUpdates()
            notifyPlaybackStateChanged()
        }
    }

    /**
     * 暂停当前正在播放的音频。
     */
    fun pause() {
        mediaPlayer?.pause()
        notifyPlaybackStateChanged()
    }

    /**
     * 播放下一首歌曲。
     */
    fun playNext() {
        if (playlistManager.moveToNext()) {
            playMusicAt(playlistManager.getCurrentPosition())
        } else {
            // 播放结束
            stopPlayback()
            showEdgeHint("已经是最后一首")
        }
    }

    /**
     * 播放上一首歌曲。
     */
    fun playPrevious() {
        if (playlistManager.moveToPrevious()) {
            playMusicAt(playlistManager.getCurrentPosition())
        } else {
            showEdgeHint("已经是第一首")
        }
    }

    /**
     * 停止当前播放并重置播放器状态。
     */
    fun stopPlayback() {
        mediaPlayer?.stop()
        isPrepared = false
        notifyPlaybackStateChanged()
    }

    /**
     * 将播放位置跳转到指定时间点。
     *
     * @param position 目标播放位置（毫秒单位）。
     */
    fun seekTo(position: Int) {
        if (isPrepared) {
            mediaPlayer?.seekTo(position)
        }
    }

    /**
     * 查询当前是否处于播放状态。
     *
     * @return 如果正在播放则返回true，否则返回false。
     */
    fun isPlaying(): Boolean {
        return mediaPlayer?.isPlaying ?: false
    }

    /**
     * 获取当前正在播放的音乐数据。
     *
     * @return 当前音乐数据，如果没有则返回null。
     */
    fun getCurrentMusic(): MusicData? {
        return playlistManager.getCurrentMusic()
    }

    /**
     * 设置新的播放模式。
     *
     * @param mode 新的播放模式。
     */
    fun setPlayMode(mode: PlayMode) {
        playlistManager.setPlayMode(mode)
        notifyPlaybackStateChanged()
    }

    /**
     * 获取当前的播放模式。
     *
     * @return 当前播放模式。
     */
    fun getPlayMode(): PlayMode = playlistManager.getPlayMode()

    /**
     * ==================== 监听器管理 ====================
     */

    /**
     * 添加一个新的播放状态监听器。
     *
     * @param listener 要添加的监听器。
     */
    fun addPlaybackListener(listener: PlaybackStateListener) {
        if (!playbackListeners.contains(listener)) {
            playbackListeners.add(listener)
        }
    }

    /**
     * 移除一个已有的播放状态监听器。
     *
     * @param listener 要移除的监听器。
     */
    fun removePlaybackListener(listener: PlaybackStateListener) {
        playbackListeners.remove(listener)
    }

    /**
     * ==================== 内部工具方法 ====================
     */

    /**
     * 初始化MediaPlayer，并设置各种事件监听器。
     */
    private fun initializeMediaPlayer() {
        mediaPlayer = MediaPlayer().apply {
            setOnPreparedListener {
                Log.i(mTag, "MediaPlayer prepared")
                isPrepared = true
                start()
                startProgressUpdates()
                notifyPlaybackStateChanged()
            }
            setOnCompletionListener {
                Log.d(mTag, "MediaPlayer completed")
                playNext()
            }
            setOnErrorListener { _, what, extra ->
                Log.e(mTag, "MediaPlayer error: $what, $extra")
                onPlaybackError(what, extra)
                true
            }
        }
    }

    /**
     * 创建通知渠道（仅限 Android O 及更高版本）。
     *
     * onCreate() 中会优先调用该方法，确保 startForeground 时通道已经存在。
     */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "音乐播放",
            NotificationManager.IMPORTANCE_LOW
        )
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * 释放MediaPlayer资源并清理相关状态。
     */
    private fun releaseMediaPlayer() {
        mediaPlayer?.let { player ->
            player.stop()
            player.release()
        }
        mediaPlayer = null
        isPrepared = false
        progressHandler.removeCallbacks(progressUpdateRunnable)
        // removeCallbacks 会把之前排队的进度任务全部清掉，避免服务销毁后仍旧执行。
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    /**
     * 开始或重启进度更新任务。
     */
    private fun startProgressUpdates() {
        progressHandler.removeCallbacks(progressUpdateRunnable)
        // 立即执行一次 runnable，然后 runnable 自己每秒再 postDelayed，形成循环。
        progressHandler.post(progressUpdateRunnable)
    }

    /**
     * 处理播放过程中发生的错误。
     *
     * @param what 错误类型码。
     * @param extra 具体错误信息码。
     */
    private fun onPlaybackError(what: Int, extra: Int) {
        // 播放错误处理
        stopPlayback()
        notifyPlaybackError("播放错误: $what, $extra")
    }

    /**
     * 通知所有注册的监听器播放状态已更改。
     */
    private fun notifyPlaybackStateChanged() {
        Log.d(mTag, "notifyPlaybackStateChanged isplaying ${isPlaying()}")
        // 通知所有监听器播放状态改变
        playbackListeners.forEach { listener ->
            listener.onPlaybackStateChanged(isPlaying())
            listener.onMusicChanged(getCurrentMusic())
            listener.onPlayModeChanged(getPlayMode())
        }
    }

    /**
     * 通知所有注册的监听器发生播放错误。
     *
     * @param error 错误描述字符串。
     */
    private fun notifyPlaybackError(error: String) {
        // 通知所有监听器播放错误
        playbackListeners.forEach { listener ->
            listener.onPlaybackError(error)
        }
    }

    /**
     * 在主线程短暂提示用户已经到达播放边界。
     */
    private fun showEdgeHint(message: String) {
        edgeHintHandler.post {
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }
}
