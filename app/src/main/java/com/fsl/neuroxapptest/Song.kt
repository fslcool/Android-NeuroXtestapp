package com.fsl.neuroxapptest

/**
 * 一首歌的数据模型。
 *
 * @param title  歌名
 * @param artist 作者
 * @param assetPath 在 assets 里的路径，如 "songs/Like The Rain.mp3"
 */
data class Song(
    val title: String,
    val artist: String,
    val assetPath: String,
    val lrcPath: String?
)

/**
 * 歌单（唯一配置处）。
 *
 * 加新歌：在列表里加一行即可，无需改其它代码。
 */
object SongLibrary {

    val SONGS: List<Song> = listOf(
        Song("Like The Rain", "佚名", "songs/Like The Rain.mp3", "songs/Like The Rain.lrc"),
        Song("The Sound Of Silence", "Simon & Garfunkel", "songs/The Sound Of Silence.mp3", "songs/The Sound Of Silence.lrc"),
        Song("在你身上流动的河", "佚名", "songs/在你身上流动的河.mp3", "songs/在你身上流动的河.lrc")
    )
}