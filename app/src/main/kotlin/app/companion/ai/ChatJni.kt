package app.companion.ai

import java.util.function.Predicate

object ChatJni {
    init {
        System.loadLibrary("llm_jni")
    }

    external fun load(fd: Int, threads: Int): Long

    external fun warm(h: Long, pin: String, pre: Int): Boolean

    external fun run(h: Long, prompt: String, pin: String, grammar: String, maxTokens: Int, threads: Int, pre: Int, temp: Float, sink: Predicate<String>): Boolean

    external fun stats(h: Long): LongArray

    external fun cancel(h: Long)

    external fun free(h: Long)
}
