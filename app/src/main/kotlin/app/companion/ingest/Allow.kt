package app.companion.ingest

import app.companion.core.Source

object Allow {
    private val apps = mapOf(
        "com.snapwork.hdfc" to Source.Notif,
        "com.csam.icici.bank.imobile" to Source.Notif,
        "com.sbi.lotusintouch" to Source.Notif,
        "com.axis.mobile" to Source.Notif,
        "com.msf.kbank.mobile" to Source.Notif,
        "com.dreamplug.androidapp" to Source.Notif,
        "com.indwealth.android" to Source.Notif,
        "in.indwealth" to Source.Notif,
        "in.amazon.mShop.android.shopping" to Source.Notif,
        "com.google.android.apps.nbu.paisa.user" to Source.Notif,
        "com.phonepe.app" to Source.Notif,
        "net.one97.paytm" to Source.Notif,
        "com.whatsapp" to Source.Wa,
        "com.whatsapp.w4b" to Source.Wa,
        "com.instagram.android" to Source.Ig,
        "com.instagram.lite" to Source.Ig,
    )

    fun of(pkg: String) = apps[pkg]
}
