package com.lonnnnnng.biucar.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class LikedMediaRepositoryTest {
    @Test
    fun `喜欢的媒体可以添加并取消`() = runBlocking {
        val dao = FakeLikedMediaDao()
        val repository = LikedMediaRepository(dao, nowEpochMs = { 42L })
        val item = LikedMediaEntity(
            mediaId = "BV1TEST:101",
            bvid = "BV1TEST",
            cid = 101L,
            title = "测试合辑",
            pageTitle = "第一首",
            artist = "测试UP",
            artworkUrl = "https://i.test/cover.jpg",
            likedAtEpochMs = 0L,
        )

        repository.add(item)

        assertEquals(listOf(item.copy(likedAtEpochMs = 42L)), repository.liked.first())

        repository.remove(item.mediaId)

        assertEquals(emptyList<LikedMediaEntity>(), repository.liked.first())
    }
}

private class FakeLikedMediaDao : LikedMediaDao {
    private val state = MutableStateFlow<List<LikedMediaEntity>>(emptyList())

    override fun observeAll(): Flow<List<LikedMediaEntity>> = state

    override suspend fun upsert(item: LikedMediaEntity) {
        state.value = (state.value.filterNot { it.mediaId == item.mediaId } + item)
            .sortedByDescending(LikedMediaEntity::likedAtEpochMs)
    }

    override suspend fun delete(mediaId: String) {
        state.value = state.value.filterNot { it.mediaId == mediaId }
    }
}
