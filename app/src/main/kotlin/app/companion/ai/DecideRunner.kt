package app.companion.ai

import android.content.Context
import app.companion.core.DecideInput
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.BuiltinNpuAcceleratorProvider
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.Environment

class DecideRunner(private val model: CompiledModel, private val env: Environment, override val accel: String) : Runner {
    fun logits(x: DecideInput): FloatArray {
        val ins = model.createInputBuffers()
        val outs = model.createOutputBuffers()
        ins[0].writeInt(x.ids)
        if (ins.size > 1) ins[1].writeInt(x.mask)
        model.run(ins, outs)
        return outs[0].readFloat().also {
            ins.forEach(AutoCloseable::close)
            outs.forEach(AutoCloseable::close)
        }
    }

    override fun close() {
        model.close()
        env.close()
    }

    companion object {
        fun open(c: Context, path: String): DecideRunner {
            var last: Throwable? = null
            for (a in listOf(Accelerator.NPU, Accelerator.GPU, Accelerator.CPU)) {
                try {
                    val env = if (a == Accelerator.NPU) Environment.create(c, BuiltinNpuAcceleratorProvider(c)) else Environment.create(c)
                    return DecideRunner(CompiledModel.create(path, CompiledModel.Options(a), env), env, a.name)
                } catch (e: Throwable) {
                    last = e
                }
            }
            throw last ?: IllegalStateException()
        }
    }
}
