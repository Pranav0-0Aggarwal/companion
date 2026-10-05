package app.companion.ai

object NuxJni {
    init {
        System.loadLibrary("llm_jni")
    }

    external fun load(fd: Int, threads: Int): Long

    external fun run(h: Long, prefix: String, text: String, suffix: String, grammar: String, maxTokens: Int): String?

    external fun free(h: Long)
}
