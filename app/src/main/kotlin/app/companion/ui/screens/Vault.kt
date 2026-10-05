package app.companion.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.companion.core.DocExtract
import app.companion.core.DocKind
import app.companion.core.DocState
import app.companion.core.Due
import app.companion.data.DocRow
import app.companion.data.Revealed
import app.companion.sl
import app.companion.system.Ocr
import app.companion.ui.Secure
import app.companion.ui.Ty
import app.companion.ui.copy
import app.companion.ui.dateOf
import app.companion.ui.daysTo
import app.companion.ui.kit.Btn
import app.companion.ui.kit.Chip
import app.companion.ui.kit.Field
import app.companion.ui.kit.Group
import app.companion.ui.kit.Ic
import app.companion.ui.kit.Lead
import app.companion.ui.kit.LocalSnack
import app.companion.ui.kit.Rule
import app.companion.ui.kit.Screen
import app.companion.ui.kit.Section
import app.companion.ui.kit.TextBtn
import app.companion.ui.kit.ToolButton
import app.companion.ui.kit.Tone
import app.companion.ui.kit.empty
import app.companion.ui.kit.part
import app.companion.ui.pal
import app.companion.ui.shortDay
import app.companion.ui.today
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun DocRow.date() = expires?.let(::dateOf)

private fun DocRow.days(now: LocalDate) = date()?.let { daysTo(it, now) }

@Composable
fun VaultScreen(back: () -> Unit, go: (String) -> Unit) {
    val p = pal
    val c = LocalContext.current
    val docs by remember { c.sl.repo.docs.list() }.collectAsStateWithLifecycle(emptyList())
    val now = today()
    var add by remember { mutableStateOf(false) }
    val groups = remember(docs) { DocKind.entries.mapNotNull { k -> docs.filter { it.kind == k.name }.takeIf { it.isNotEmpty() }?.let { k to it } } }
    Secure()
    Screen(
        "Vault", if (docs.isEmpty()) "Nothing saved yet" else "${docs.size} ${if (docs.size == 1) "document" else "documents"} · numbers stay masked",
        back = back, nav = false, tall = false, tools = { ToolButton(Ic.Add, "Add a document") { add = true } },
    ) {
        if (docs.isEmpty()) empty(Ic.Vault, "Nothing saved yet", "Add insurance, registration, warranties and more. Numbers stay masked until you reveal them.") {
            Btn("Add a document", go = true, icon = Ic.Add) { add = true }
        }
        groups.forEach { (kind, list) ->
            item(key = "h${kind.name}") { Section(kind.label) }
            itemsIndexed(list, key = { _, d -> "d${d.id}" }) { k, d ->
                val days = d.days(now)
                Box(Modifier.animateItem().part(p, k == 0, k == list.lastIndex)) {
                    app.companion.ui.kit.PassLine(
                        d.title, d.mask.orEmpty(), lead = kindIcon(d.kind), tone = Tone.Plain, lines = 1, onClick = { go("vault/${d.id}") },
                        trailing = { ExpiryChip(days, d.date()) },
                    )
                }
            }
        }
    }
    if (add) AddDoc { add = false }
}

@Composable
fun VaultDocScreen(id: Long, back: () -> Unit) {
    val p = pal
    val c = LocalContext.current
    val repo = c.sl.repo
    val scope = rememberCoroutineScope()
    val reveal = rememberReveal()
    val docs by remember { repo.docs.list() }.collectAsStateWithLifecycle(emptyList())
    val d = docs.firstOrNull { it.id == id }
    var shown by remember { mutableStateOf<Revealed?>(null) }
    var failed by remember { mutableStateOf(false) }
    var ask by remember { mutableStateOf(false) }
    val photo by produceState<Bitmap?>(null, shown) {
        value = if (shown != null && d?.photo != null) withContext(Dispatchers.IO) { runCatching { repo.docs.photo(id)?.let { BitmapFactory.decodeByteArray(it, 0, it.size) } }.getOrNull() } else null
    }
    AutoHide(shown != null) { shown = null }
    Secure()
    val days = d?.days(today())
    Screen(
        d?.title ?: "Document", d?.let { listOfNotNull(kindLabel(it.kind), it.date()?.let { e -> "expires ${shortDay(e)} ${e.year}" }).joinToString(" · ") }.orEmpty(),
        back = back, nav = false, tall = false, tools = { if (d != null) ToolButton(Ic.Trash, "Delete") { ask = true } },
    ) {
        if (d != null) {
            item(key = "fields") {
                Group(Modifier.padding(top = 8.dp)) {
                    val s = shown
                    DocField(d, "Number", s?.number ?: d.mask ?: "None", s != null, copy = s?.number)
                    s?.extra?.forEach { (k, v) ->
                        Rule(72.dp)
                        DocField(d, k.replace('_', ' ').cap(), v, true, copy = v)
                    }
                    if (d.expires != null) {
                        Rule(72.dp)
                        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Lead(Ic.Calendar, Tone.Plain)
                            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                                Text("Expires", style = Ty.ui(13, FontWeight.Normal).copy(color = p.ink2))
                                Text(d.date()!!.let { "${shortDay(it)} ${it.year}" }, style = Ty.mono(16, FontWeight.Medium).copy(color = p.ink))
                            }
                            ExpiryChip(days, d.date())
                        }
                    }
                }
            }
            item(key = "reveal") {
                Row(Modifier.padding(start = 16.dp, top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (shown == null) {
                        Btn("Reveal", go = true, icon = Ic.Eye) { reveal(id) { r -> if (r != null) { shown = r; failed = false } else failed = true } }
                    } else {
                        Btn("Hide", icon = Ic.Lock) { shown = null }
                    }
                }
                Text(
                    if (failed) "Couldn't unlock that. Try again." else if (shown != null) "Hides again in 30 seconds" else "Asks for your fingerprint or screen lock",
                    Modifier.padding(start = 28.dp, top = 8.dp), style = Ty.ui(13, FontWeight.Normal).copy(color = if (failed) p.red else p.ink2),
                )
            }
            photo?.let { b ->
                item(key = "photo") {
                    Image(b.asImageBitmap(), "Saved photo", Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)), contentScale = ContentScale.FillWidth)
                }
            }
        }
    }
    if (ask && d != null) {
        PassConfirm("Delete ${d.title}?", "This removes it and its photo from this phone.", "Delete", { ask = false; scope.launch { repo.docs.remove(d.id); back() } }, { ask = false })
    }
}

@Composable
private fun DocField(d: DocRow, label: String, value: String, open: Boolean, copy: String?) {
    val p = pal
    val c = LocalContext.current
    val snack = LocalSnack.current
    Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Lead(kindIcon(d.kind), Tone.Plain)
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(label, style = Ty.ui(13, FontWeight.Normal).copy(color = p.ink2))
            Text(value, style = Ty.mono(if (open) 18 else 16, FontWeight.Medium).copy(color = p.ink), maxLines = 2)
        }
        if (copy != null) TextBtn("Copy") {
            copy(c, copy, "vault")
            snack.say("Copied")
        }
    }
}

private suspend fun readPhoto(c: android.content.Context, uri: Uri): Pair<Bitmap, ByteArray>? = withContext(Dispatchers.IO) {
    runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var s = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / s > 2000) s *= 2
        val b = c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s }) } ?: return@runCatching null
        b to ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
    }.getOrNull()
}

@Composable
private fun AddDoc(onClose: () -> Unit) {
    val p = pal
    val c = LocalContext.current
    val repo = c.sl.repo
    val snack = LocalSnack.current
    val scope = rememberCoroutineScope()
    var kind by remember { mutableStateOf(DocKind.Insurance) }
    var title by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("") }
    var exp by remember { mutableStateOf<LocalDate?>(null) }
    var extra by remember { mutableStateOf(emptyMap<String, String>()) }
    var photo by remember { mutableStateOf<ByteArray?>(null) }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    val take = { uri: Uri ->
        busy = true
        scope.launch {
            val r = readPhoto(c, uri)
            if (r == null) {
                note = "Couldn't open that photo."
            } else {
                photo = r.second
                val text = Ocr.read(r.first)
                val k = text?.let(DocExtract::kind)
                if (text == null || k == null) {
                    note = "Couldn't read details from that photo. Fill them in below."
                } else {
                    val f = DocExtract.fields(k, text, today())
                    kind = f.kind
                    title = f.title
                    number = f.number.orEmpty()
                    exp = f.expires
                    extra = f.extra
                    note = "Check what was read, then save."
                }
            }
            busy = false
        }
        Unit
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u -> if (u != null) take(u) }
    val shot = remember { File(File(c.cacheDir, "capture").also { it.mkdirs() }, "doc.jpg") }
    val cam = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) take(Uri.fromFile(shot)) else shot.delete()
    }
    val ok = (title.isNotBlank() || number.isNotBlank()) && !busy
    val save = {
        val t = title.trim().ifEmpty { kind.label }
        val n = number.trim().ifEmpty { null }
        val e = exp
        val x = extra
        val ph = photo
        onClose()
        snack.go {
            repo.docs.add(kind, t, n, e, x, if (ph != null) "photo" else "manual", ph)
            shot.delete()
            snack.say("Saved $t")
        }
    }
    Sheet(onClose) {
        SheetTitle("Add a document", "Photos are encrypted on this phone")
        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn("Take a photo", icon = Ic.Camera, enabled = !busy) { cam.launch(FileProvider.getUriForFile(c, "${c.packageName}.files", shot)) }
            Btn("Choose a photo", icon = Ic.Image, enabled = !busy) { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
        }
        if (busy) Text("Reading the photo on this phone", Modifier.padding(top = 10.dp), style = Ty.ui(13, FontWeight.Normal).copy(color = p.ink2))
        note?.let { Text(it, Modifier.padding(top = 10.dp), style = Ty.ui(13, FontWeight.Normal).copy(color = p.ink2)) }
        FlowRow(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DocKind.entries.forEach { k -> Chip(k.label, kind == k) { kind = k } }
        }
        Field("Title", title, { title = it.take(60) }, Modifier.padding(top = 14.dp))
        Field("Number", number, { number = it.take(40) }, Modifier.padding(top = 10.dp), mono = true)
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val e = exp
            if (e != null) {
                DateField("Expires", e, Modifier.weight(1f)) { exp = it }
                TextBtn("No expiry", color = p.ink2) { exp = null }
            } else {
                Btn("Add expiry date", icon = Ic.Calendar) { exp = today().plusYears(1) }
            }
        }
        Btn("Save", Modifier.padding(top = 20.dp), go = true, enabled = ok) { save() }
    }
}
