package app.companion.core

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RangesTest {
    private val dir: File = Files.createTempDirectory("ranges").toFile()
    private val sha = "a".repeat(64)

    @AfterTest
    fun clean() {
        dir.deleteRecursively()
    }

    private fun s(a: Long, b: Long) = Seg(a, b)

    @Test
    fun `merge joins overlapping, touching and unsorted ranges`() {
        assertEquals(listOf(s(0, 30), s(40, 50)), Ranges.merge(listOf(s(40, 50), s(10, 20), s(0, 12), s(20, 30))))
        assertEquals(listOf(s(5, 9)), Ranges.merge(listOf(s(5, 9), s(6, 7), s(8, 8), s(3, 3))))
        assertEquals(emptyList(), Ranges.merge(emptyList()))
    }

    @Test
    fun `missing returns the gaps inside the file`() {
        assertEquals(listOf(s(0, 100)), Ranges.missing(100, emptyList()))
        assertEquals(listOf(s(10, 40), s(60, 100)), Ranges.missing(100, listOf(s(0, 10), s(40, 60))))
        assertEquals(emptyList(), Ranges.missing(100, listOf(s(0, 60), s(50, 100))))
        assertEquals(listOf(s(0, 10)), Ranges.missing(100, listOf(s(10, 100), s(95, 130))))
    }

    @Test
    fun `chunks tile the gaps exactly within the size bounds`() {
        val gaps = listOf(s(0, 25_000_000), s(40_000_000, 50_000_000))
        val c = Ranges.chunks(gaps, 4, 8_000_000, 1_000_000)
        assertTrue(c.all { it.len in 1..8_000_000 })
        assertEquals(35_000_000L, c.sumOf { it.len })
        assertEquals(gaps, Ranges.merge(c))
    }

    @Test
    fun `few bytes left still split over the connections but never below the minimum`() {
        val c = Ranges.chunks(listOf(s(0, 3_000_000)), 4, 8_000_000, 1_000_000)
        assertEquals(listOf(s(0, 1_000_000), s(1_000_000, 2_000_000), s(2_000_000, 3_000_000)), c)
        assertEquals(listOf(s(10, 20)), Ranges.chunks(listOf(s(10, 20)), 4, 8_000_000, 1_000_000))
        assertEquals(emptyList(), Ranges.chunks(emptyList(), 4, 8, 1))
    }

    @Test
    fun `sidecar text round trips and merges`() {
        val t = Ranges.encode(500, sha, listOf(s(100, 200), s(0, 50), s(50, 60)))
        assertEquals(listOf(s(0, 60), s(100, 200)), Ranges.decode(t, 500, sha))
    }

    @Test
    fun `sidecar for another size, checksum or with bad lines is ignored`() {
        val t = Ranges.encode(500, sha, listOf(s(0, 60)))
        assertEquals(emptyList(), Ranges.decode(t, 501, sha))
        assertEquals(emptyList(), Ranges.decode(t, 500, "b".repeat(64)))
        assertEquals(emptyList(), Ranges.decode("500 $sha\n0 60\nx y", 500, sha))
        assertEquals(emptyList(), Ranges.decode("500 $sha\n10 5", 500, sha))
        assertEquals(emptyList(), Ranges.decode("500 $sha\n0 501", 500, sha))
        assertEquals(emptyList(), Ranges.decode("500 $sha\n-1 5", 500, sha))
        assertEquals(emptyList(), Ranges.decode("", 500, sha))
    }

    @Test
    fun `book persists completed ranges and reports what is left`() {
        val f = File(dir, "x.part.ranges")
        val b = Book(f, 1000, sha)
        assertFalse(b.known())
        b.add(Seg(0, 300))
        b.add(Seg(600, 700))
        b.add(Seg(300, 450))
        val again = Book(f, 1000, sha)
        assertEquals(550L, again.done())
        assertEquals(listOf(s(450, 600), s(700, 1000)), again.missing())
        assertFalse(File(f.path + ".tmp").exists())
    }

    @Test
    fun `book starts empty for another file or a damaged sidecar and reset clears it`() {
        val f = File(dir, "y.part.ranges")
        Book(f, 1000, sha).add(Seg(0, 10))
        assertFalse(Book(f, 2000, sha).known())
        f.writeText("garbage")
        assertFalse(Book(f, 1000, sha).known())
        val b = Book(f, 1000, sha)
        b.add(Seg(0, 10))
        b.reset()
        assertFalse(f.exists())
        assertEquals(listOf(s(0, 1000)), b.missing())
    }

    @Test
    fun `resume math counts only what is missing`() {
        val size = 555_000_000L
        val done = listOf(s(0, 200_000_000), s(300_000_000, 400_000_000))
        val left = Ranges.missing(size, done).sumOf { it.len }
        assertEquals(size - 300_000_000, left)
        assertEquals(300_000_000L, Ranges.total(done))
        assertEquals(54, Show.pct(Ranges.total(done), size))
    }
}
