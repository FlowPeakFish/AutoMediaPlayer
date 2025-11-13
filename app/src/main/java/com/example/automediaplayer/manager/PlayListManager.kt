package com.example.automediaplayer.manager

import com.example.automediaplayer.model.MusicData
import com.example.automediaplayer.service.MusicPlayService.PlayMode
import kotlin.random.Random

/**
 * 播放队列管理器，负责维护原始列表、当前列表以及顺序/循环/随机等模式下的游标。
 *
 * 该类只关注列表与位置的运算，不直接操作 MediaPlayer，便于在 Service 中复用。
 */
class PlayListManager {
    private var originalPlaylist: List<MusicData> = emptyList()
    private var currentPlaylist: List<MusicData> = emptyList()
    private var currentPosition = -1
    private var currentPlayMode = PlayMode.ORDER

    // playedIndices 作为“播放历史栈”，只在随机模式下使用，便于实现“随机下一首，同时还能返回上一次”。
    private val playedIndices = mutableListOf<Int>()

    /**
     * 初始化或更新播放列表，并可选指定一个起始歌曲。
     * 会按照当前播放模式决定是否需要洗牌，并重置已播放记录。
     */
    fun setPlaylist(playlist: List<MusicData>, musicId: Long = 0) {
        originalPlaylist = playlist.toList()
        currentPlaylist = when (currentPlayMode) {
            PlayMode.SHUFFLE -> shufflePlaylist(originalPlaylist)
            else -> originalPlaylist
        }
        currentPosition = if (currentPlaylist.isNotEmpty()) {
            currentPlaylist.indexOfFirst { it.id == musicId }
        } else {
            -1
        }
        playedIndices.clear()
    }

    /**
     * 切换播放模式，确保正在播放的歌曲在新列表中位置正确。
     */
    fun setPlayMode(mode: PlayMode) {
        if (currentPlayMode != mode) {
            currentPlayMode = mode
            val currentMusic = getCurrentMusic()

            // 如果切换到随机模式，重新洗牌
            if (mode == PlayMode.SHUFFLE) {
                currentPlaylist = shufflePlaylist(originalPlaylist)
                currentPosition = if (currentPosition != -1) {
                    // 在新列表中查找相同ID的歌曲
                    val newIndex = currentPlaylist.indexOfFirst { it.id == currentMusic?.id }
                    if (newIndex != -1) newIndex else 0
                } else {
                    0
                }
            } else {
                currentPlaylist = originalPlaylist
                // 更新 currentPosition 以匹配新列表中的相同歌曲
                currentPosition = if (currentPosition != -1) {
                    val newIndex = currentPlaylist.indexOfFirst { it.id == currentMusic?.id }
                    if (newIndex != -1) newIndex else 0
                } else {
                    0
                }
            }
            playedIndices.clear()
        }
    }

    /** 当前使用的播放模式。 */
    fun getPlayMode(): PlayMode = currentPlayMode

    /**
     * 返回当前游标对应的音乐，没有可播放内容时返回 null。
     */
    fun getCurrentMusic(): MusicData? {
        return if (currentPosition in 0 until currentPlaylist.size) {
            currentPlaylist[currentPosition]
        } else {
            null
        }
    }

    /** 获取当前播放位置索引。 */
    fun getCurrentPosition(): Int = currentPosition

    /**
     * 计算下一首歌曲的索引，兼容多种播放模式。
     */
    private fun getNextPosition(): Int {
        if (currentPlaylist.isEmpty()) return -1

        return when (currentPlayMode) {
            PlayMode.ORDER -> {
                if (currentPosition < currentPlaylist.size - 1) {
                    currentPosition + 1
                } else {
                    -1 // 播放结束
                }
            }

            PlayMode.LOOP -> {
                if (currentPosition < currentPlaylist.size - 1) {
                    currentPosition + 1
                } else {
                    0 // 循环到开头
                }
            }

            PlayMode.SHUFFLE -> {
                if (playedIndices.size >= currentPlaylist.size) {
                    // 所有歌曲都播放过了，重新开始：清空历史，重新随机。
                    playedIndices.clear()
                }

                val availableIndices = currentPlaylist.indices.filter {
                    it != currentPosition && !playedIndices.contains(it)
                }

                if (availableIndices.isEmpty()) {
                    -1
                } else {
                    // 随机挑一个“从未播放过的索引”。
                    availableIndices[Random.nextInt(availableIndices.size)]
                }
            }
        }
    }

    /**
     * 计算上一首歌曲的索引，兼容多种播放模式。
     */
    private fun getPreviousPosition(): Int {
        if (currentPlaylist.isEmpty()) return -1

        return when (currentPlayMode) {
            PlayMode.ORDER -> {
                (currentPosition - 1).coerceAtLeast(0)
            }

            PlayMode.LOOP -> {
                if (currentPosition > 0) {
                    currentPosition - 1
                } else {
                    currentPlaylist.size - 1 // 循环到末尾
                }
            }

            PlayMode.SHUFFLE -> {
                if (playedIndices.isNotEmpty()) {
                    // 栈结构：回退到最后一次 push 的索引，实现“随机模式下也能上一首”。
                    playedIndices.removeAt(playedIndices.size - 1)
                } else if (currentPlaylist.isNotEmpty()) {
                    currentPlaylist.size - 1
                } else {
                    -1
                }
            }
        }
    }

    /**
     * 将游标移动到下一首，如果成功则返回 true。
     */
    fun moveToNext(): Boolean {
        val nextPos = getNextPosition()
        if (nextPos != -1) {
            // 记录当前播放的索引（随机模式下使用）
            if (currentPosition != -1) {
                playedIndices.add(currentPosition)
            }
            currentPosition = nextPos
            return true
        }
        return false
    }

    /**
     * 将游标移动到上一首，如果成功则返回 true。
     */
    fun moveToPrevious(): Boolean {
        val prevPos = getPreviousPosition()
        if (prevPos != -1) {
            currentPosition = prevPos
            return true
        }
        return false
    }

    /**
     * 根据当前列表复制一份并打乱顺序，供随机模式使用。
     */
    private fun shufflePlaylist(playlist: List<MusicData>): List<MusicData> {
        return playlist.shuffled()
    }

    /** 获取当前正在使用的列表（可能是洗牌后的结果）。 */
    fun getPlaylist(): List<MusicData> = currentPlaylist

    /**
     * 直接跳转到指定位置并返回对应歌曲，供 Service 播放指定条目。
     */
    fun moveToPosition(position: Int): MusicData? {
        return if (position in 0 until currentPlaylist.size) {
            currentPosition = position
            currentPlaylist[position]
        } else {
            null
        }
    }
}
