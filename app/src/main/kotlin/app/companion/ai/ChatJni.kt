package app.companion.ai

import java.util.function.Predicate

object ChatJni {
    init {
        System.loadLibrary("llm_jni")
    }

    external fun load(fd: Int, threads: Int): Long

    external fun run(h: Long, prompt: String, grammar: String, maxTokens: Int, threads: Int, temp: Float, sink: Predicate<String>): Boolean

    external fun cancel(h: Long)

    external fun free(h: Long)
}
