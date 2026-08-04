package com.lonnnnnng.biucar.data.model

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

data class Account(
    val isLoggedIn: Boolean = false,
    val mid: Long = 0L,
    val name: String = "",
    val faceUrl: String = "",
)

data class Creator(
    val mid: Long,
    val name: String,
    val faceUrl: String = "",
)

data class Video(
    val bvid: String,
    val aid: Long? = null,
    val cid: Long? = null,
    val title: String,
    val author: String,
    val coverUrl: String = "",
    val durationSeconds: Int? = null,
    val publishedAtEpochSeconds: Long? = null,
    val progressSeconds: Int? = null,
)

data class Page<T>(
    val items: List<T>,
    val page: Int,
    val hasMore: Boolean,
)

data class HistoryCursor(
    val max: Long,
    val viewAtEpochSeconds: Long,
    val business: String,
)

data class HistoryPage(
    val items: List<Video>,
    val nextCursor: HistoryCursor?,
)

data class VideoPage(
    val cid: Long,
    val page: Int,
    val title: String,
    val durationSeconds: Int,
)

data class VideoDetail(
    val bvid: String,
    val aid: Long?,
    val title: String,
    val author: String,
    val coverUrl: String,
    val pages: List<VideoPage>,
)

enum class FavoriteGroup { CREATED, COLLECTED }

enum class FavoriteType(val apiValue: Int) {
    VIDEO_FOLDER(11),
    VIDEO_COLLECTION(21),
    UNKNOWN(-1),
    ;

    companion object {
        fun fromApiValue(value: Int): FavoriteType = entries.firstOrNull { it.apiValue == value } ?: UNKNOWN
    }
}

data class FavoriteFolder(
    val id: Long,
    val title: String,
    val mediaCount: Int,
    val group: FavoriteGroup,
    val type: FavoriteType,
)

data class AudioTrack(
    val bvid: String,
    val cid: Long,
    val title: String,
    val pageTitle: String?,
    val artist: String,
    val artworkUrl: String,
    val streamUrl: String,
    val qualityLabel: String,
) {
    val mediaId: String = "$bvid:$cid"

    fun toMediaItem(): MediaItem {
        val extras = Bundle().apply {
            putString(EXTRA_BVID, bvid)
            putLong(EXTRA_CID, cid)
            putString(EXTRA_STREAM_URL, streamUrl)
            putString(EXTRA_RESOURCE_TITLE, title)
            putString(EXTRA_PAGE_TITLE, pageTitle)
        }
        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri(Uri.parse(streamUrl))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(pageTitle?.takeIf(String::isNotBlank) ?: title)
                    .setAlbumTitle(title)
                    .setArtist(artist)
                    .setArtworkUri(artworkUrl.takeIf(String::isNotBlank)?.let(Uri::parse))
                    .setDescription(qualityLabel)
                    .setExtras(extras)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .build(),
            )
            .build()
    }
}

data class LoginCredentials(
    val cookieHeader: String,
    val accessToken: String,
    val refreshToken: String,
    val mid: Long,
    val expiresAtEpochSeconds: Long,
)

data class QrChallenge(
    val url: String,
    val authCode: String,
)

sealed interface QrPollResult {
    data object Waiting : QrPollResult
    data object Scanned : QrPollResult
    data object Expired : QrPollResult
    data class Success(val credentials: LoginCredentials) : QrPollResult
    data class Error(val message: String) : QrPollResult
}

const val EXTRA_BVID = "biucar.bvid"
const val EXTRA_CID = "biucar.cid"
const val EXTRA_STREAM_URL = "biucar.stream_url"
const val EXTRA_RESOURCE_TITLE = "biucar.resource_title"
const val EXTRA_PAGE_TITLE = "biucar.page_title"
