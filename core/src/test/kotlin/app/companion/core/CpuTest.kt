package app.companion.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CpuTest {
    private fun core(f: String) = "processor\t: 0\nBogoMIPS\t: 38.40\nFeatures\t: $f\nCPU implementer\t: 0x41\n\n"

    @Test
    fun `dot product and i8mm together are required`() {
        assertTrue(Cpu.nux(core("fp asimd evtstrm aes asimddp i8mm bf16")))
        assertFalse(Cpu.nux(core("fp asimd asimddp")))
        assertFalse(Cpu.nux(core("fp asimd i8mm")))
        assertFalse(Cpu.nux(core("fp asimd asimdhp")))
    }

    @Test
    fun `names match whole words only`() {
        assertFalse(Cpu.nux(core("fp asimddpx i8mmx")))
        assertFalse(Cpu.nux(core("asimddp_i8mm")))
    }

    @Test
    fun `every core must have both`() {
        assertTrue(Cpu.nux(core("asimddp i8mm") + core("i8mm asimddp sve")))
        assertFalse(Cpu.nux(core("asimddp i8mm") + core("asimddp")))
    }

    @Test
    fun `no features line or no text is unsupported`() {
        assertFalse(Cpu.nux(""))
        assertFalse(Cpu.nux("flags\t: sse4_2 avx2 asimddp i8mm\n"))
        assertFalse(Cpu.nux("processor : 0\n"))
    }
}
