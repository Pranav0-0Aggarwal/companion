package app.companion.core

import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class VideoFrameTest {
    @Test
    fun `pack puts flags and big endian pts before the data`() {
        val b = VideoFrame.pack(VideoFrame.KEY, 0x0102030405060708, ByteBuffer.wrap(byteArrayOf(9, 8)))
        assertContentEquals(byteArrayOf(2, 1, 2, 3, 4, 5, 6, 7, 8, 9, 8), b)
    }

    @Test
    fun `small frames stay whole`() {
        val b = VideoFrame.pack(0, 1, ByteBuffer.wrap(ByteArray(20)))
        assertContentEquals(b, VideoFrame.split(b, 64).single())
    }

    @Test
    fun `big frames split under the limit and rejoin byte for byte`() {
        val data = ByteArray(1000) { it.toByte() }
        val b = VideoFrame.pack(VideoFrame.KEY, 7, ByteBuffer.wrap(data))
        val parts = VideoFrame.split(b, 109)
        assertEquals(10, parts.size)
        assertEquals(true, parts.all { it.size <= 109 })
        assertEquals(true, parts.dropLast(1).all { it[0].toInt() == (VideoFrame.KEY or VideoFrame.MORE) })
        assertEquals(VideoFrame.KEY, parts.last()[0].toInt())
        assertEquals(true, parts.all { p -> p.copyOfRange(1, 9).contentEquals(b.copyOfRange(1, 9)) })
        assertContentEquals(data, parts.map { it.copyOfRange(VideoFrame.HEAD, it.size) }.reduce(ByteArray::plus))
    }

    @Test
    fun `a frame at the bulk limit goes out as one piece`() {
        assertEquals(1, VideoFrame.split(ByteArray(BulkFrame.MAX - 1)).size)
        assertEquals(2, VideoFrame.split(ByteArray(BulkFrame.MAX)).size)
    }

    @Test
    fun `fit caps the long side and rounds both sides down to 16`() {
        assertEquals(544 to 1280, VideoFrame.fit(1080, 2520, 1280))
        assertEquals(1280 to 544, VideoFrame.fit(2520, 1080, 1280))
        assertEquals(1072 to 1072, VideoFrame.fit(1080, 1080, 4096))
        assertEquals(16 to 16, VideoFrame.fit(3, 4, 4096))
    }
}
