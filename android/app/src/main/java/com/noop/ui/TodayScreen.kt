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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noop.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.*

@Composable
internal fun TodayScreen(vm: AppViewModel, you: () -> Unit, recovery: () -> Unit, sleep: () -> Unit,
    activity: () -> Unit, history: (DesignMetric) -> Unit) {
    val c = designColors
    val today = LocalDate.now()
    val days by vm.recentDays.collectAsStateWithLifecycle()
    val current = days.lastOrNull { it.day == today.toString() }
    val data = metricData(vm, DesignMetric.entries, today,31)
    val goal = optimalFractionRange(current?.recovery)
    val live by vm.live.collectAsStateWithLifecycle()
    val bpm by vm.bpm.collectAsStateWithLifecycle()
    val device = designDevice(vm)
    DPage(vm,stringResource(R.string.nav_today),dateLabel(today,"EEE, MMM d"),you) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            listOf(DesignMetric.Recovery,DesignMetric.Strain,DesignMetric.Sleep).forEach { metric ->
                val value = data[metric]?.get(today.toString())
                val caption = when(metric) {
                    DesignMetric.Recovery -> when { value == null -> missing
                        value >= 67 -> stringResource(R.string.design_ready_train)
                        else -> synthesisWord(value) }
                    DesignMetric.Strain -> goal?.let { stringResource(R.string.redesign_training_goal,
                        number(it.start*100.0),number(it.endInclusive*100.0)) } ?: missing
                    else -> sleepDurationLabel(current?.totalSleepMin)
                }
                val action = when(metric) { DesignMetric.Recovery -> Modifier.clickable(onClick=recovery)
                    DesignMetric.Sleep -> Modifier.clickable(onClick=sleep)
                    else -> Modifier }
                Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(16.dp))
                    .background(c.surfaceRaised).then(action).padding(horizontal=12.dp,vertical=14.dp),
                    verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    DText(stringResource(if(metric==DesignMetric.Sleep) R.string.nav_sleep else metric.label),15,c.textSecondary,true)
                    DText(number(value),44,metric.tint,true,height=48)
                    DTrack(value?.div(100),metric.tint,6)
                    DText(caption,13,c.textSecondary)
                }
            }
        }
        DCard { Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.AutoAwesome,null,Modifier.size(20.dp),c.chargeColor)
            DText(synthesisDetail(current),17,height=23)
        } }
        DGroup(stringResource(R.string.design_vitals)) {
            vitals.forEachIndexed { index, metric ->
                val series=data[metric].orEmpty()
                val value=series[today.toString()]
                val baseline=window(series,today.minusDays(1),30).meanValue()
                val delta=if(value!=null && baseline!=null) value-baseline else null
                DRow({history(metric)}) {
                    DText(stringResource(metric.label),17,modifier=Modifier.weight(1f))
                    DValue(if(metric==DesignMetric.Temp)signed(value,1) else number(value,metric.decimals),metricUnit(metric),20,14)
                    Row(Modifier.width(92.dp).padding(end=6.dp),horizontalArrangement=Arrangement.End,verticalAlignment=Alignment.CenterVertically) {
                        val color=if(delta==null)c.textTertiary else if(metric.favorable(delta,baseline,value))c.chargeColor else c.effortColor
                        if(delta!=null) Icon(if(delta<0)Icons.Rounded.ArrowDownward else Icons.Rounded.ArrowUpward,null,Modifier.size(14.dp),color)
                        DText(signed(delta,metric.decimals),14,color,true)
                    }
                }
                if(index < vitals.lastIndex) HorizontalDivider(color=c.hairline)
            }
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surfaceRaised)
            .clickable(onClick=activity).heightIn(min=64.dp).padding(horizontal=14.dp,vertical=12.dp),
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.FavoriteBorder,null,Modifier.size(24.dp),c.metricRose)
            Column(Modifier.weight(1f)) {
                DText(stringResource(R.string.design_heart_now),13,c.textSecondary,true)
                DValue(number(bpm?.toDouble()),stringResource(R.string.design_unit_bpm),22,14)
            }
            Column(horizontalAlignment=Alignment.End) {
                DText(stringResource(R.string.design_device_stats,device.name,number(device.battery)),13,c.textSecondary)
                DText(syncLabel(live.lastSyncAt),13,c.textSecondary)
            }
        }
    }
}

