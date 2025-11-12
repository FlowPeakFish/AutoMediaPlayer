package com.example.automediaplayer.ui

import android.Manifest
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.automediaplayer.R
import com.example.automediaplayer.adapter.MusicAdapter
import com.example.automediaplayer.databinding.ActivityMainBinding
import com.example.automediaplayer.model.MusicData
import com.example.automediaplayer.util.MusicScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val mTag = "MainActivity"

    private lateinit var mBinding: ActivityMainBinding
    private lateinit var musicAdapter: MusicAdapter
    private var mediaPlayer: MediaPlayer? = null
    private var currentMusic: MusicData? = null
    private var isPlaying = false

    private lateinit var permissionLauncher: ActivityResultLauncher<String>

    private val requiredAudioPermission: String = Manifest.permission.READ_EXTERNAL_STORAGE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(mBinding.root)

        initViews()
        setupRecyclerView()
        setupPermissionLauncher()
        requestPermissionAndLoad()
    }

    private fun initViews() {
        mBinding.btnPlayPause.setOnClickListener { togglePlayPause() }
    }

    private fun setupRecyclerView() {
        musicAdapter = MusicAdapter(emptyList()) { music ->
            playMusic(music)
        }

        mBinding.rvMusic.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = musicAdapter
        }
    }

    private fun setupPermissionLauncher() {
        permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                loadMusicFiles()
            } else {
                Toast.makeText(this, "需要读取媒体库权限才能加载音乐", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun requestPermissionAndLoad() {
        val granted = ContextCompat.checkSelfPermission(
            this, requiredAudioPermission
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (granted) {
            loadMusicFiles()
        } else {
            permissionLauncher.launch(requiredAudioPermission)
        }
    }

    private fun loadMusicFiles() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val musicList = MusicScanner.scanMusicFiles(this@MainActivity)
                Log.d(mTag, "Loaded ${musicList.size} music files")
                musicAdapter.updateData(musicList)

                if (musicList.isEmpty()) {
                    // 显示无音乐的提示
                    Toast.makeText(this@MainActivity, "未扫描到音乐", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // 显示错误信息
                showErrorMessage("扫描音乐文件失败: ${e.message}")
            }
        }
    }

    private fun playMusic(music: MusicData) {
        try {
            // 停止当前播放
            stopPlayback()
            // 创建新的MediaPlayer
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@MainActivity, music.uri)
                setOnPreparedListener {
                    start()
                    this@MainActivity.isPlaying = true
                    updateCurrentMusicInfo(music)
                }
                setOnCompletionListener {
                    this@MainActivity.isPlaying = false
                    updatePlayButton()
                }
                prepareAsync() // 使用异步准备避免ANR
            }

            currentMusic = music

        } catch (e: Exception) {
            e.printStackTrace()
            showErrorMessage("播放失败: ${e.message}")
        }
    }

    private fun togglePlayPause() {
        mediaPlayer?.let { player ->
            if (isPlaying) {
                player.pause()
                isPlaying = false
            } else {
                player.start()
                isPlaying = true
            }
            updatePlayButton()
        } ?: run {
            // 如果没有当前音乐，播放列表中的第一首
            if (musicAdapter.itemCount > 0) {
                playMusic(musicAdapter.getItemAt(0))
            }
        }
    }

    private fun stopPlayback() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.stop()
            }
            player.release()
        }
        mediaPlayer = null
        isPlaying = false
        currentMusic = null
        updateCurrentMusicInfo(null)
    }

    private fun updateCurrentMusicInfo(music: MusicData?) {
        if (music != null) {
            mBinding.tvTitle.text = music.title
            mBinding.tvArtist.text = music.artist
            mBinding.imgCover.setImageBitmap(music.cover)
            updatePlayButton()
        } else {
            Log.d(mTag, "No music currently playing")
        }
    }

    private fun updatePlayButton() {
        if (isPlaying) {
            mBinding.btnPlayPause.background =
                AppCompatResources.getDrawable(this, R.drawable.icon_controls_play)
        } else {
            mBinding.btnPlayPause.background =
                AppCompatResources.getDrawable(this, R.drawable.icon_controls_pause)
        }
    }

    private fun showErrorMessage(message: String) {
        // 可以在UI上显示错误信息
        Log.e(mTag, message)
    }


    override fun onDestroy() {
        super.onDestroy()
        stopPlayback()
    }
}


// 为适配器添加扩展函数来获取指定位置的音乐
private fun MusicAdapter.getItemAt(position: Int): MusicData {
    return (this.itemCount.takeIf { it > position }?.let {
        (this::class.java.getDeclaredField("musicList").apply {
            isAccessible = true
        }.get(this) as List<MusicData>)[position]
    } ?: throw IndexOutOfBoundsException("Position $position out of bounds"))
}