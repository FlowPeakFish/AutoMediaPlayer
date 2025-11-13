package com.example.automediaplayer.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.automediaplayer.R
import com.example.automediaplayer.model.MusicData

/**
 * 音乐列表适配器：展示扫描结果，并将点击事件回传给外部 (通常是 Activity)。
 */
class MusicAdapter(
    private var musicList: List<MusicData> = emptyList(),
    private val onItemClick: (MusicData) -> Unit
) : RecyclerView.Adapter<MusicAdapter.MusicViewHolder>() {

    /**
     * 列表项持有者，缓存标题/歌手/专辑/时长文本，避免重复查找。
     */
    class MusicViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // findViewById 会在当前 itemView 中寻找对应 id 的控件，相当于“把 XML 里的按钮实例化出来”。
        val songTitle: TextView = itemView.findViewById(R.id.tvSongTitle)
        val artist: TextView = itemView.findViewById(R.id.tvArtist)
        val album: TextView = itemView.findViewById(R.id.tvAlbum)
        val duration: TextView = itemView.findViewById(R.id.tvDuration)
    }

    /**
     * RecyclerView 需要一个新的行视图时会调用该方法，我们在此解析布局并包装成 ViewHolder。
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MusicViewHolder {
        // LayoutInflater 会把 XML 布局文件解析成真正的 View；from(parent.context) 传入当前列表所在的 Context。
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_music, parent, false)
        return MusicViewHolder(view)
    }

    /**
     * 当某个位置需要显示内容时调用该方法，把 musicList[position] 的数据填充到对应控件上。
     */
    override fun onBindViewHolder(holder: MusicViewHolder, position: Int) {
        val music = musicList[position]

        holder.songTitle.text = music.title
        holder.artist.text = music.artist
        holder.album.text = music.album
        holder.duration.text = music.getFormattedDuration()

        holder.itemView.setOnClickListener {
            onItemClick(music)
        }
    }

    /**
     * 告诉 RecyclerView 一共有多少条数据，这里直接返回内存中的列表大小。
     */
    override fun getItemCount(): Int = musicList.size

    /**
     * 用新的音乐列表刷新 UI，后续可根据需求替换为 DiffUtil 提升效率。
     *
     * 这里选择最直接的 `notifyDataSetChanged()`：对小型 MVP 来说够用。
     */
    fun updateData(newMusicList: List<MusicData>) {
        musicList = newMusicList
        // notifyDataSetChanged 会通知 RecyclerView “数据整表发生变化”，从而重新绘制列表。
        notifyDataSetChanged()
    }
}
