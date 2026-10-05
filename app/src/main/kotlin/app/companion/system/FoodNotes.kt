package app.companion.system

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import app.companion.R
import app.companion.core.Face
import app.companion.ui.has

object FoodNotes {
    const val CHAN = "food"
    const val KEY = "reply"
    const val KIND = "kind"
    const val SLOT = "slot"
    const val REF = "ref"
    const val ID = "id"
    private const val TAG = "food"

    private fun nm(c: Context) = c.getSystemService(NotificationManager::class.java)

    private fun post(c: Context, id: Int, face: Face, kind: String, slot: String?, ref: Long, label: String) {
        if (!c.has(Manifest.permission.POST_NOTIFICATIONS)) return
        val intent = Intent(c, FoodReply::class.java).putExtra(KIND, kind).putExtra(SLOT, slot).putExtra(REF, ref).putExtra(ID, id)
        val pi = PendingIntent.getBroadcast(c, id, intent, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val reply = Notification.Action.Builder(Icon.createWithResource(c, R.drawable.ic_tile), "Reply", pi)
            .addRemoteInput(RemoteInput.Builder(KEY).setLabel(label).build())
            .setSemanticAction(Notification.Action.SEMANTIC_ACTION_REPLY)
            .setAllowGeneratedReplies(false)
            .build()
        val n = Ping.base(c, CHAN, face, face.copy(text = null, title = "Food reminder"), Prefs.get(c))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(Notes.to(c, "today"))
            .addAction(reply)
            .build()
        nm(c).notify(TAG, id, n)
    }

    fun slotId(slot: String?) = 100 + (slot.orEmpty().hashCode() and 0xF)

    fun orderId(meal: Long) = 200 + (meal.toInt() and 0xFFFF)

    fun slot(c: Context, slot: String, text: String = "What did you have for ${slot.lowercase()}?") = post(c, slotId(slot), Face("Food", text), "meal", slot, 0, "Your meal")

    fun order(c: Context, meal: Long, text: String) = post(c, orderId(meal), Face("Food", text), "order", null, meal, "What you had")

    fun weigh(c: Context) = post(c, 300, Face("Weekly weight", "What do you weigh today?"), "weight", null, 0, "Weight in kg")

    fun done(c: Context, id: Int, text: String) {
        if (!c.has(Manifest.permission.POST_NOTIFICATIONS)) return
        val n = Ping.base(c, CHAN, Face("Food", text), Face("Food"), Prefs.get(c))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setTimeoutAfter(6 * 3_600_000L)
            .setContentIntent(Notes.to(c, "today"))
            .build()
        nm(c).notify(TAG, id, n)
    }
}
