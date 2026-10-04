package app.companion.ai

object NeedleJni {
    init {
        System.loadLibrary("needle_jni")
    }

    external fun load(fd: Int): Long

    private external fun exec(h: Long, query: ByteArray, tools: ByteArray, facts: ByteArray): ByteArray?

    external fun free(h: Long)

    fun run(h: Long, query: String, tools: String, facts: String) =
        exec(h, query.toByteArray(), tools.toByteArray(), facts.toByteArray())?.toString(Charsets.UTF_8)
}
