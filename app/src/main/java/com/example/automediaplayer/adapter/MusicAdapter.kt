package com.example.automediaplayer.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.automediaplayer.R
import com.example.automediaplayer.model.MusicData

class MusicAdapter(
    private var musicList: List<MusicData> = emptyList(),
    private val onItemClick: (MusicData) -> Unit
) : RecyclerView.Adapter<MusicAdapter.MusicViewHolder>(){

    class MusicViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val songTitle: TextView = itemView.findViewById(R.id.tvSongTitle)
        val artist: TextView = itemView.findViewById(R.id.tvArtist)
        val album: TextView = itemView.findViewById(R.id.tvAlbum)
        val duration: TextView = itemView.findViewById(R.id.tvDuration)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MusicViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_music, parent, false)
        return MusicViewHolder(view)
    }

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

    override fun getItemCount(): Int = musicList.size

    fun updateData(newMusicList: List<MusicData>) {
        musicList = newMusicList
        notifyDataSetChanged()
    }
}