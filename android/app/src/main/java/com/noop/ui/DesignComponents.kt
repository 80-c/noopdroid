package com.noop.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noop.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.*

internal val designColors get() = if (Palette.isLight) LightTokens else DarkTokens
internal const val missing = "–"

@Composable
internal fun Symbol(id: Int, size: Int, color: Color, description: String? = null) {
    Icon(androidx.compose.ui.res.painterResource(id), description, Modifier.size(size.dp), color)
}

@Composable
internal fun DText(text: String, size: Int, color: Color = designColors.textPrimary,
    medium: Boolean = false, modifier: Modifier = Modifier, height: Int = size + 4,
    spacing: Double = 0.0, bold: Boolean = false) {
    Text(text, modifier, color = color, style = TextStyle(fontSize = size.sp,
        lineHeight = height.sp, letterSpacing = spacing.sp, fontFeatureSettings = "tnum",
        fontWeight = if (bold) FontWeight.Bold else if (medium) FontWeight.Medium else FontWeight.Normal))
}

@Composable
internal fun DSection(title: String) = DText(title, 14, designColors.accent, true, height = 20, spacing = 0.1)

@Composable
internal fun DCard(modifier: Modifier = Modifier, padding: Int = 14, gap: Int = 0, radius: Int = 16,
    content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(radius.dp)).background(designColors.surfaceRaised)
        .padding(padding.dp), verticalArrangement = Arrangement.spacedBy(gap.dp), content = content)
}

@Composable
internal fun DPage(vm: AppViewModel, title: String, overline: String?, you: () -> Unit,
    back: (() -> Unit)? = null, modifier: Modifier = Modifier, sectionGap: Int = 12, content: @Composable ColumnScope.() -> Unit) {
    val c = designColors
    Column(modifier.fillMaxSize().background(c.surfaceBase).verticalScroll(rememberScrollState())
        .padding(start = 20.dp, end = 20.dp, top = 40.dp, bottom = 14.dp)) {
        if (back != null) IconButton(onClick = back, modifier = Modifier.offset(x = (-12).dp).size(48.dp)) {
            Symbol(R.drawable.symbol_arrow_back,24,c.textPrimary,stringResource(R.string.redesign_back))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                if (overline != null) DText(overline, 15, c.textSecondary)
                DText(title, 28, height = 36)
            }
            val device = designDevice(vm)
            val desc = stringResource(R.string.design_avatar)
            val context = LocalContext.current
            val initial = remember(context) {
                runCatching { context.getSystemService(android.os.UserManager::class.java)?.userName }
                    .getOrNull()?.trim()?.firstOrNull()?.uppercase() ?: missing
            }
            Box(Modifier.size(48.dp).clickable(onClick=you).semantics { contentDescription = desc },contentAlignment=Alignment.Center) {
                Box(Modifier.size(44.dp)) {
                    Box(Modifier.fillMaxSize().background(c.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                        DText(initial,17,c.onPrimaryContainer,true)
                    }
                    Box(Modifier.align(Alignment.BottomEnd).size(11.dp)
                        .background(if (device.connected) c.statusPositive else c.hairlineStrong, CircleShape)
                        .border(2.dp, c.surfaceBase, CircleShape))
                }
            }
        }
        Spacer(Modifier.height(if (back == null) 14.dp else 12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(sectionGap.dp), content = content)
    }
}

@Composable
internal fun DGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        DSection(title)
        DCard(padding = 0, content = content)
    }
}

@Composable
internal fun DRow(onClick: () -> Unit, height: Int = 52, right: Int = 14, left: Int = 16,
    content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = height.dp)
        .padding(start = left.dp, end = right.dp), verticalAlignment = Alignment.CenterVertically) {
        content()
        Symbol(R.drawable.symbol_chevron_right,18,designColors.textTertiary)
    }
}

@Composable
internal fun DTrack(fraction: Double?, tint: Color, height: Int) {
    Box(Modifier.fillMaxWidth().height(height.dp).clip(CircleShape).background(designColors.surfaceInset)) {
        if (fraction != null) Spacer(Modifier.fillMaxWidth(fraction.toFloat().coerceIn(0f, 1f)).fillMaxHeight().background(tint,CircleShape))
    }
}

@Composable
internal fun DRange(selected: Int, onSelect: (Int) -> Unit) {
    val c = designColors
    Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
        Spacer(Modifier.fillMaxWidth().height(40.dp).border(1.dp,c.hairlineStrong,CircleShape))
        Row(Modifier.fillMaxWidth().height(48.dp)) {
            listOf(7,30,90,365).forEach { days ->
                val active = days == selected
                val shape = when(days) {
                    7 -> RoundedCornerShape(topStart=20.dp,bottomStart=20.dp)
                    365 -> RoundedCornerShape(topEnd=20.dp,bottomEnd=20.dp)
                    else -> RoundedCornerShape(0.dp)
                }
                Row(Modifier.weight(1f).fillMaxHeight().clickable { onSelect(days) }.padding(vertical=4.dp)
                    .background(if(active)c.secondaryContainer else Color.Transparent,shape)
                    .border(1.dp,c.hairlineStrong,shape), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    if(active) Symbol(R.drawable.symbol_check,18,c.onPrimaryContainer)
                    DText(rangeLabel(days),14,if(active)c.onPrimaryContainer else c.textSecondary,true)
                }
            }
        }
    }
}

@Composable
internal fun rangeLabel(days: Int) = stringResource(when(days) {
    7 -> R.string.design_range_week
    90 -> R.string.design_range_quarter
    365 -> R.string.design_range_year
    else -> R.string.design_range_month
})

@Composable
internal fun DBottom(current: Destination, select: (Destination) -> Unit) {
    val c = designColors
    val systemBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Row(Modifier.fillMaxWidth().height(96.dp + (systemBottom - 32.dp).coerceAtLeast(0.dp)).background(c.surfaceRaised).padding(horizontal=8.dp)
        .padding(bottom=16.dp)) {
        primaryBarTabs.forEach { tab ->
            val active = tab.dest == current
            val icon = when(tab.dest) {
                Destination.Today -> if(active) R.drawable.symbol_today_filled else R.drawable.symbol_today
                Destination.Sleep -> if(active) R.drawable.symbol_bedtime_filled else R.drawable.symbol_bedtime
                Destination.Live -> if(active) R.drawable.symbol_directions_run_filled else R.drawable.symbol_directions_run
                else -> if(active) R.drawable.symbol_insights_filled else R.drawable.symbol_insights
            }
            Column(Modifier.weight(1f).fillMaxHeight().clickable { select(tab.dest) }.padding(top=12.dp),
                horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(64.dp,32.dp).background(if(active)c.secondaryContainer else Color.Transparent,CircleShape),
                    contentAlignment=Alignment.Center) { Symbol(icon,24,if(active)c.onPrimaryContainer else c.textSecondary) }
                DText(stringResource(tab.labelRes),12,if(active)c.textPrimary else c.textSecondary,true,spacing=0.5,bold=active)
            }
        }
    }
}

internal fun number(value: Double?, decimals: Int = 0): String = value?.takeIf { it.isFinite() }?.let {
    String.format(Locale.getDefault(), "%."+decimals+"f", it).replace('-', '−')
} ?: missing

internal fun signed(value: Double?, decimals: Int = 0) = if(value == null) missing else
    (if(value > 0) "+" else "") + number(value,decimals)

@Composable
internal fun sleepDurationLabel(minutes: Double?): String = minutes?.roundToInt()?.let {
    stringResource(R.string.design_hour_min,it / 60,it % 60)
} ?: missing

internal fun dateLabel(day: LocalDate, pattern: String) =
    day.format(DateTimeFormatter.ofPattern(pattern,Locale.getDefault()))

@Composable
internal fun DAction(label: String, enabled: Boolean = true, modifier: Modifier = Modifier,
    icon: Int? = null, click: () -> Unit) {
    OutlinedButton(onClick=click,enabled=enabled,modifier=modifier.heightIn(min=48.dp),
        border=BorderStroke(1.dp,designColors.hairlineStrong),contentPadding=PaddingValues(horizontal=8.dp)) {
        if(icon != null) { Symbol(icon,20,designColors.accent); Spacer(Modifier.width(8.dp)) }
        DText(label,15,designColors.accent,true)
    }
}

