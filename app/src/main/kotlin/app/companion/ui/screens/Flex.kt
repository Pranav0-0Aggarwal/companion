package app.companion.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.window.layout.FoldingFeature
import app.companion.data.dueDate
import app.companion.sl
import app.companion.ui.Ty
import app.companion.ui.daysTo
import app.companion.ui.inDays
import app.companion.ui.kit.CardShape
import app.companion.ui.kit.CodeCard
import app.companion.ui.kit.Ic
import app.companion.ui.kit.LocalSnack
import app.companion.ui.kit.Motion
import app.companion.ui.kit.PassLine
import app.companion.ui.kit.Amount
import app.companion.ui.kit.Section
import app.companion.ui.kit.Tone
import app.companion.ui.kit.lift
import app.companion.ui.kit.motion
import app.companion.ui.kit.part
import app.companion.ui.pal
import app.companion.ui.rememberNow
import app.companion.core.Timeline
import app.companion.ui.clock
import kotlinx.coroutines.launch
import app.companion.ui.money as amt

@Composable
private fun Modifier.slide(from: Float): Modifier {
    val on = motion()
    val k = remember { Animatable(if (on) 0f else 1f) }
    LaunchedEffect(Unit) { k.animateTo(1f, Motion.soft()) }
    return graphicsLayer {
        alpha = k.value
        translationY = (1f - k.value) * from * density
    }
}

@Composable
fun FlexScreen(go: (String) -> Unit, fold: FoldingFeature) {
    val p = pal
    val d = rememberDay()
    val now by rememberNow(60_000)
    val tl = rememberTl(d.today, d.today, now)
    val goal = rememberGoal()
    val density = LocalDensity.current
    var y0 by remember { mutableFloatStateOf(0f) }
    BoxWithConstraints(Modifier.fillMaxSize().background(p.bg).onGloballyPositioned { y0 = it.positionInWindow().y }) {
        val h = constraints.maxHeight.toFloat()
        val top = with(density) { (fold.bounds.top - y0).coerceIn(0f, h).toDp() }
        val bottom = with(density) { (h - (fold.bounds.bottom - y0)).coerceIn(0f, h).toDp() }
        Column {
            Glance(d, tl, goal, Modifier.height(top).slide(-48f))
            Box(Modifier.weight(1f))
            Controls(tl, now, go, Modifier.height(bottom).slide(48f))
        }
    }
}

@Composable
private fun Glance(d: Day, tl: Tl, goal: Int?, modifier: Modifier) {
    val p = pal
    val repo = LocalContext.current.sl.repo
    val snack = LocalSnack.current
    val now = rememberNow(1000, active = d.otps.isNotEmpty())
    val live by remember(d.otps) { derivedStateOf(structuralEqualityPolicy()) { d.otps.filter { (it.expires ?: 0) > now.value } } }
    val o = live.firstOrNull()
    val size = if (o == null) 40 else 28
    Column(
        modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(d.hello, Modifier.padding(start = 8.dp), style = Ty.ui(15, FontWeight.Medium).copy(color = p.ink2))
        if (o != null) CodeCard(o, { snack.teach(repo) { repo.notOtp(o.id) } }, big = true)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val safe = tl.safe
            Tile(if (safe != null) "Safe to spend" else "Spent today", Modifier.weight(1f)) {
                Amount(amt(safe ?: tl.spent, "INR"), Ty.mono(size, FontWeight.Bold).copy(color = p.ink))
                Text(if (safe != null) "a day" else "${tl.n} payments", style = Ty.ui(13, FontWeight.Normal).copy(color = p.ink2))
            }
            if (goal != null) {
                Tile("Calories", Modifier.weight(1f)) {
                    Text(app.companion.ui.num(kotlin.math.abs(goal - tl.kcal)), style = Ty.mono(size, FontWeight.Bold).copy(color = p.ink), maxLines = 1)
                    Text(if (goal >= tl.kcal) "kcal left" else "kcal over", style = Ty.ui(13, FontWeight.Normal).copy(color = p.ink2))
                }
            }
        }
    }
}

@Composable
internal fun Tile(label: String, modifier: Modifier, content: @Composable () -> Unit) {
    val p = pal
    Column(modifier.lift(p).clip(CardShape).background(p.card).padding(horizontal = 18.dp, vertical = 16.dp)) {
        Text(label, Modifier.padding(bottom = 4.dp), style = Ty.ui(13, FontWeight.Medium).copy(color = p.ink2))
        content()
    }
}

@Composable
private fun Controls(tl: Tl, now: Long, go: (String) -> Unit, modifier: Modifier) {
    val p = pal
    val rows = Timeline.around(tl.items, { it.at }, now, 3)
    Column(modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(top = 20.dp, bottom = 12.dp)) {
        AskPill(hint = "Ask or log anything")
        Box(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(top = 12.dp)) {
            Column {
                if (rows.isEmpty()) {
                    Box(Modifier.part(p, true, true)) { PassLine("All clear", "Nothing planned right now", lead = Ic.Check, tone = Tone.Green, lines = 1) }
                }
                rows.forEachIndexed { k, e ->
                    val b = e.beat()
                    Box(Modifier.part(p, k == 0, k == rows.lastIndex)) {
                        PassLine(
                            b.title, listOfNotNull(e.at?.let(::clock), b.sub.takeIf { it.isNotEmpty() }).joinToString(" · "), lines = 1,
                            lead = when (e) { is Ev.Bill -> Ic.Bolt; is Ev.Meet -> Ic.Calendar; is Ev.Meal -> Ic.Food; is Ev.Review -> Ic.Inbox; else -> Ic.Ledger }, tone = Tone.Plain,
                            trailing = b.amount?.let { a -> { Text(a, style = Ty.mono(16, FontWeight.SemiBold).copy(color = p.ink)) } },
                        )
                    }
                }
            }
        }
        Box(Modifier.weight(0.001f))
        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp).clip(CardShape).background(p.card).padding(4.dp)) {
            Dest(Ic.Money, "Money", Modifier.weight(1f)) { go("money") }
            Dest(Ic.Food, "Food", Modifier.weight(1f)) { go("food") }
            Dest(Ic.Inbox, "Inbox", Modifier.weight(1f)) { go("inbox") }
            Dest(Ic.User, "You", Modifier.weight(1f)) { go("you") }
        }
    }
}

@Composable
internal fun Dest(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    val p = pal
    Column(
        modifier.heightIn(min = 56.dp).clip(CardShape).clickable(role = Role.Button, onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = p.ink)
        Text(label, Modifier.padding(top = 4.dp), style = Ty.ui(12).copy(color = p.ink2), maxLines = 1, softWrap = false)
    }
}
