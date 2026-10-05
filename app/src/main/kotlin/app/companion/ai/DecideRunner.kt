package app.companion.ai

import android.content.Context
import android.util.Log
import app.companion.core.BertSpec
import app.companion.core.DecideInput
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.BuiltinNpuAcceleratorProvider
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.Environment

class DecideRunner(private val model: CompiledModel, private val env: Environment, override val accel: String) : Runner {
    fun logits(x: DecideInput, sig: String? = null): FloatArray {
        val ins = if (sig == null) model.createInputBuffers() else model.createInputBuffers(sig)
        val outs = if (sig == null) model.createOutputBuffers() else model.createOutputBuffers(sig)
        ins[0].writeInt(x.ids)
        if (ins.size > 1) ins[1].writeInt(x.mask)
        if (sig == null) model.run(ins, outs) else model.run(ins, outs, sig)
        return outs[0].readFloat().also {
            ins.forEach(AutoCloseable::close)
            outs.forEach(AutoCloseable::close)
        }
    }

    fun named(x: DecideInput, s: BertSpec.Sig): FloatArray {
        val i = checkNotNull(s.ids)
        val m = checkNotNull(s.mask)
        val o = checkNotNull(s.out)
        val a = model.createInputBuffer(i, s.name)
        val b = model.createInputBuffer(m, s.name)
        val y = model.createOutputBuffer(o, s.name)
        try {
            a.writeInt(x.ids)
            b.writeInt(x.mask)
            model.run(mapOf(i to a, m to b), mapOf(o to y), s.name)
            return y.readFloat()
        } finally {
            listOf(a, b, y).forEach(AutoCloseable::close)
        }
    }

    override fun close() {
        model.close()
        env.close()
    }

    companion object {
        fun open(c: Context, path: String, cache: String? = null, threads: Int? = null, gpu: Boolean = true): DecideRunner {
            var last: Throwable? = null
            for (a in listOf(Accelerator.NPU, Accelerator.GPU, Accelerator.CPU).filter { gpu || it != Accelerator.GPU }) {
                try {
                    val env = if (a == Accelerator.NPU) Environment.create(c, BuiltinNpuAcceleratorProvider(c)) else Environment.create(c)
                    val o = CompiledModel.Options(a)
                    if (cache != null || threads != null) o.cpuOptions = CompiledModel.CpuOptions(threads, null, cache)
                    return DecideRunner(CompiledModel.create(path, o, env), env, a.name).also { Log.i("Companion", "runner ${a.name}") }
                } catch (e: Throwable) {
                    Log.w("Companion", "runner ${a.name} ${e.javaClass.simpleName}: ${e.message?.take(200)}")
                    last = e
                }
            }
            throw last ?: IllegalStateException()
        }
    }
}
