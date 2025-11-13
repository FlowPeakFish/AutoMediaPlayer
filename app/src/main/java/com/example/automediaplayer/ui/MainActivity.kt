package com.example.automediaplayer.ui

import android.os.Bundle
import android.util.Log
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.automediaplayer.R
import com.example.automediaplayer.adapter.MusicAdapter
import com.example.automediaplayer.databinding.ActivityMainBinding
import com.example.automediaplayer.model.MusicData
import com.example.automediaplayer.service.MusicPlayService
import com.example.automediaplayer.service.MusicPlayService.PlayMode
import com.example.automediaplayer.service.PlaybackStateListener
import com.example.automediaplayer.service.ServiceConnectionHelper
import com.example.automediaplayer.util.MusicScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * 主界面：负责展示音乐列表、响应用户控制，并通过绑定 Service 与播放逻辑解耦。
 */
class MainActivity : AppCompatActivity() {

    private val mTag = "MainActivity"

    private lateinit var mBinding: ActivityMainBinding
    private lateinit var musicAdapter: MusicAdapter
    private lateinit var serviceConnectionHelper: ServiceConnectionHelper
    private var musicService: MusicPlayService? = null

    /** 标记用户是否正在拖动进度条，避免刷新时出现跳动。 */
    private var mIsTrackingTouch = false

    /**
     * Activity 生命周期入口：初始化视图、列表、数据以及服务连接。
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ActivityMainBinding.inflate 会读取 XML 并生成类型安全的视图绑定对象，替代手写 findViewById。
        mBinding = ActivityMainBinding.inflate(layoutInflater)
        // setContentView 需要一个根 View，ViewBinding 通过 root 字段提供。
        setContentView(mBinding.root)

        initViews()
        setupRecyclerView()
        loadMusicFiles()
        setupServiceConnection()
        updateCurrentMusicInfo(musicService?.getCurrentMusic())
    }

    /**
     * 设置播放控制按钮与进度条的交互逻辑。
     *
     * 可以把这里当作“遥控器的按键绑定”，全部写在一个函数里，方便后续维护。
     */
    private fun initViews() {
        mBinding.btnPlayPause.setOnClickListener {
            Log.i(mTag, "Play/Pause button clicked")
            musicService?.let { service ->
                if (service.isPlaying()) {
                    service.pause()
                } else {
                    service.play()
                }
            }
        }


        mBinding.btnPrev.setOnClickListener {
            Log.i(mTag, "Previous button clicked")
            musicService?.playPrevious()
        }
        mBinding.btnNext.setOnClickListener {
            Log.i(mTag, "Next button clicked")
            musicService?.playNext()
        }
        mBinding.btnMode.setOnClickListener {
            Log.i(mTag, "Mode button clicked")
            musicService?.let {
                when (musicService!!.getPlayMode()) {
                    PlayMode.ORDER -> musicService!!.setPlayMode(PlayMode.LOOP)
                    PlayMode.LOOP -> musicService!!.setPlayMode(PlayMode.SHUFFLE)
                    PlayMode.SHUFFLE -> musicService!!.setPlayMode(PlayMode.ORDER)
                }
            }
        }
        // SeekBar 交互：
        // - 用户拖动时我们打个标记 mIsTrackingTouch，暂停 UI 自动刷新，避免进度条来回跳动。
        // - 拖动结束后，把用户松手的位置通过 service.seekTo() 通知 Service。
        mBinding.playerProgressBar.setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(
                p0: SeekBar?,
                p1: Int,
                p2: Boolean
            ) {
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {
                mIsTrackingTouch = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                mIsTrackingTouch = false
                musicService?.seekTo(seekBar!!.progress)
            }

        })

    }

    /**
     * 初始化音乐列表，点击歌曲时交给 Service 处理播放。
     *
     * 这里的点击回调只是“告诉服务我要听哪首歌”，真正的播放逻辑仍在 Service 里，
     * 避免 Activity 因为生命周期切换而中断音乐。
     */
    private fun setupRecyclerView() {
        musicAdapter = MusicAdapter(emptyList()) { music ->
            // 如果服务已连接，使用服务播放
            musicService?.initPlaylist(musicAdapter.getMusicList(), music.id) ?: run {
                // 服务未连接时的备用方案
                showErrorMessage("服务未就绪")
            }
        }

        mBinding.rvMusic.apply {
            // LinearLayoutManager 让 RecyclerView 以垂直方式排列每一行。
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = musicAdapter
        }
    }

    /**
     * 从媒体库扫描音乐并刷新列表，包含异常处理与空数据提示。
     *
     * 这里使用 `CoroutineScope(Dispatchers.Main)`：
     * - 由于 scanMusicFiles 是挂起函数，必须在协程里调用。
     * - 启动在 Main dispatcher 上，意味着 `launch {}` 里的代码一开始运行在主线程，
     *   但 scanMusicFiles 自己会切到 IO 线程，等结果出来再回主线程更新 UI。
     */
    private fun loadMusicFiles() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val musicList = MusicScanner.scanMusicFiles(this@MainActivity)
                Log.d(mTag, "Loaded ${musicList.size} music files")
                musicAdapter.updateData(musicList)

                if (musicList.isEmpty()) {
                    // Toast.makeText 会在屏幕底部弹出一个短消息，提示用户当前状态。
                    Toast.makeText(this@MainActivity, "未扫描到音乐", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // 显示错误信息
                showErrorMessage("扫描音乐文件失败: ${e.message}")
            }
        }
    }

    /**
     * 绑定后台播放服务，注册监听器并在连接断开时清理引用。
     *
     * 绑定成功后才能拿到 MusicPlayService 的实例，所以所有播放控制都应该写在监听回调里。
     */
    private fun setupServiceConnection() {
        serviceConnectionHelper = ServiceConnectionHelper()
        serviceConnectionHelper.bindService(
            this,
            object : ServiceConnectionHelper.OnServiceConnectedListener {
                override fun onConnected(service: MusicPlayService) {
                    musicService = service
                    // 注册播放状态监听器
                    service.addPlaybackListener(playbackListener)
                    // 更新UI状态
                    updateCurrentMusicInfo(service.getCurrentMusic())
                }

                override fun onDisconnected() {
                    musicService = null
                }
            })
    }

    /**
     * 播放状态监听器：同步播放按钮、进度条、当前歌曲和模式按钮。
     *
     * Service 会在关键节点调用这些回调，我们在 UI 层只负责“听消息并更新控件”，
     * 这样可以保证数据源统一。
     */
    private val playbackListener = object : PlaybackStateListener {
        override fun onPlaybackStateChanged(isPlaying: Boolean) {
            Log.i(mTag, "Playback state changed: $isPlaying")
            // runOnUiThread 保证即使回调发生在后台线程，也能安全更新 UI。
            runOnUiThread {
                updatePlayButton()
            }
        }

        override fun onPlaybackProgressChanged(progress: Int, duration: Int) {
            Log.i(mTag, "Playback progress changed: $progress / $duration")
            if (!mIsTrackingTouch) {
                mBinding.playerProgressBar.progress = progress
                mBinding.playerProgressBar.max = duration
                mBinding.tvProgressCurrent.text = getFormattedDuration(progress.toLong())
                mBinding.tvProgressMax.text = getFormattedDuration(duration.toLong())
            }
        }

        override fun onMusicChanged(music: MusicData?) {
            Log.i(mTag, "Current music changed: $music")
            runOnUiThread {
                updateCurrentMusicInfo(music)
            }
        }

        override fun onPlayModeChanged(mode: PlayMode) {
            Log.i(mTag, "Play mode changed: $mode")
            when (mode) {
                PlayMode.ORDER -> mBinding.btnMode.background = AppCompatResources.getDrawable(
                    this@MainActivity,
                    R.drawable.icon_controls_order
                )

                PlayMode.LOOP -> mBinding.btnMode.background =
                    AppCompatResources.getDrawable(this@MainActivity, R.drawable.icon_controls_loop)

                PlayMode.SHUFFLE -> mBinding.btnMode.background = AppCompatResources.getDrawable(
                    this@MainActivity,
                    R.drawable.icon_controls_shuffle
                )
            }
        }

        override fun onPlaybackError(error: String) {
            Log.e(mTag, "Playback error: $error")
            runOnUiThread {
                showErrorMessage(error)
            }
        }
    }

    /**
     * 将毫秒级时间格式化为 mm:ss，供进度条文本使用。
     */
    fun getFormattedDuration(time: Long): String {
        val minutes = (time / 1000) / 60
        val seconds = (time / 1000) % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    /**
     * 根据当前歌曲刷新标题/艺术家/封面。
     */
    private fun updateCurrentMusicInfo(music: MusicData?) {
        if (music != null) {
            mBinding.tvTitle.text = music.title
            mBinding.tvArtist.text = music.artist
            mBinding.imgCover.setImageBitmap(music.cover)
        } else {
            Log.d(mTag, "No music currently playing")
        }
    }

    /**
     * 根据 Service 的播放状态切换播放/暂停按钮的图标。
     *
     * 注意：由于播放逻辑在 Service 端，Activity 只依据 `service.isPlaying()` 来判断，
     * 避免和 Service 状态不一致。
     */
    private fun updatePlayButton() {
        musicService?.let { service ->
            if (service.isPlaying()) {
                // AppCompatResources.getDrawable 会根据主题加载矢量图，兼容不同 API 版本。
                mBinding.btnPlayPause.background =
                    AppCompatResources.getDrawable(this, R.drawable.icon_controls_play)
            } else {
                mBinding.btnPlayPause.background =
                    AppCompatResources.getDrawable(this, R.drawable.icon_controls_pause)
            }
        }
    }

    /**
     * 统一处理错误输出，后续可替换为 Snackbar/Toast。
     *
     * 目前仅打印日志，防止 App 因为 Toast 频繁弹出影响体验。
     */
    private fun showErrorMessage(message: String) {
        // 可以在UI上显示错误信息
        Log.e(mTag, message)
    }

    /**
     * 生命周期销毁回调：注销监听并解绑服务，防止内存泄露。
     */
    override fun onDestroy() {
        super.onDestroy()
        musicService?.removePlaybackListener(playbackListener)
        serviceConnectionHelper.unbindService(this)
    }
}

/**
 * 便捷扩展：通过反射拿到当前列表数据，供 Service 初始化播放队列。
 *
 * 之所以使用反射，是因为 Adapter 默认没有暴露内部列表；
 * 更好的做法是给 Adapter 添加公开方法，这里为了快速完成 MVP 暂时使用 workaround。
 */
fun MusicAdapter.getMusicList(): List<MusicData> {
    return (this::class.java.getDeclaredField("musicList").apply {
        isAccessible = true
    }.get(this) as List<MusicData>)
}
