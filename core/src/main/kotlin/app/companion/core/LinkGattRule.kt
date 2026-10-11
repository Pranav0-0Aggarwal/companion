package app.companion.core

enum class Cccd { Accept, Restart, Reject, Drop, Ignore }

object LinkGattRule {
    fun <T> cccd(active: T?, d: T, enable: Boolean) = when {
        enable -> if (active == null) Cccd.Accept else if (active == d) Cccd.Restart else Cccd.Reject
        active == d -> Cccd.Drop
        else -> Cccd.Ignore
    }
}
