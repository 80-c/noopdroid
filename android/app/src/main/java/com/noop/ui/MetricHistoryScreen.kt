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

import com.noop.analytics.Baselines
import com.noop.analytics.EffectRanker
import com.noop.analytics.VitalBands

@Composable
internal fun MetricHistoryScreen(vm: AppViewModel, metric: DesignMetric, you: () -> Unit, back: () -> Unit) {
    val c=designColors
    val end=LocalDate.now()
    var days by rememberSaveable(metric){mutableIntStateOf(30)}
    val series=metricData(vm,listOf(metric),end,max(days,32))[metric].orEmpty()
    val values=window(series,end,days)
    val prior=window(series,end.minusDays(1),30)
    val cfg=when(metric) { DesignMetric.Hrv -> Baselines.hrvCfg
        DesignMetric.Rhr -> Baselines.restingHRCfg
        DesignMetric.Resp -> Baselines.respCfg
        DesignMetric.Temp -> VitalBands.skinTempDeviationCfg
        else -> null }
    val baseline=remember(prior,cfg){cfg?.let { Baselines.foldHistory(prior,it) }?.takeIf { it.trusted }}
    val normal=baseline?.let { (it.baseline-it.spread*VitalBands.sigmaK)..(it.baseline+it.spread*VitalBands.sigmaK) }
    val last=values.lastOrNull()
    val actual=values.filterNotNull()
    val hi=actual.maxOrNull()
    val lo=actual.minOrNull()
    val unit=metricUnit(metric)
    val name=stringResource(metric.label)
    val journal by produceState(initialValue=emptyList<com.noop.data.JournalEntry>(),vm,days,series) {
        val from=end.minusDays(days.toLong()).toString()
        value=(vm.repo.journal("my-whoop",from,end.toString())+vm.repo.journal(JOURNAL_DEVICE_ID,from,end.toString()))
            .associateBy { it.day to it.question }.values.toList()
    }
    DPage(vm,name,null,you,back) {
        DRange(days){days=it}
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            listOf(stringResource(R.string.design_avg,days) to values.meanValue(),
                stringResource(R.string.design_last_night) to last).forEachIndexed { i,(title,value) ->
                DCard(Modifier.weight(1f),12) {
                    DText(title,14,c.textSecondary,true)
                    val color=if(i==0 || normal==null || value==null)c.textPrimary else
                        if(value in normal || metric.favorable(value-baseline!!.baseline,baseline.baseline,value))c.chargeColor else c.effortColor
                    DValue(metricReading(metric,value),unit,30,15,color)
                }
            }
        }
        DCard(gap=10) {
            Row(horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                listOf(stringResource(R.string.design_normal,normal?.let {
                    metricReading(metric,it.start)+"–"+metricReading(metric,it.endInclusive) } ?: missing),
                    stringResource(R.string.design_high,metricReading(metric,hi)),stringResource(R.string.design_low,metricReading(metric,lo)))
                    .forEachIndexed { i,label ->
                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(3.dp)) {
                            val tint=if(i==2)c.effortColor else c.chargeColor
                            val shape=if(i==0)RoundedCornerShape(1.dp) else CircleShape
                            Spacer(Modifier.size(if(i==0)14.dp else 10.dp,10.dp).background(if(i==0)tint.copy(alpha=.2f) else Color.Transparent,shape)
                                .border(if(i==0)1.dp else 2.dp,tint,shape))
                            DText(label,13,c.textSecondary,true)
                        }
                    }
            }
            Canvas(Modifier.fillMaxWidth().height(170.dp).padding(6.dp)) {
                val bottom=minOf(lo ?: 0.0,normal?.start ?: lo ?: 0.0)
                val top=maxOf(hi ?: 1.0,normal?.endInclusive ?: hi ?: 1.0).coerceAtLeast(bottom+.01)
                fun y(v: Double)=size.height*(1-(v-bottom)/(top-bottom)).toFloat()
                if(normal!=null && baseline!=null) {
                    drawRect(c.chargeColor.copy(alpha=.13f),Offset(0f,y(normal.endInclusive)),Size(size.width,y(normal.start)-y(normal.endInclusive)))
                    val at=y(baseline.baseline)
                    drawLine(c.chargeColor,Offset(0f,at),Offset(size.width,at),1.5.dp.toPx(),
                        pathEffect=PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(),4.dp.toPx())))
                }
                seriesLine(values,c.textPrimary,bottom,top,2.dp.toPx(),true)
                listOf(hi to c.chargeColor,lo to c.effortColor).forEach { (v,tint) ->
                    if(v!=null)drawCircle(tint,6.dp.toPx(),Offset(size.width*values.indexOf(v)/(days-1),y(v)),style=Stroke(2.dp.toPx()))
                }
            }
            DAxis(end,days)
        }
        DCard {
            DSection(stringResource(R.string.design_weekly))
            val monday=end.minusDays(end.dayOfWeek.value-1L)
            val averages=(0..3).map { i -> val start=monday.minusWeeks(i.toLong())
                (0..6).map { series[start.plusDays(it.toLong()).toString()] }.meanValue() }
            averages.forEachIndexed { i,value ->
                Row(Modifier.fillMaxWidth().heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically) {
                    DText(when(i){0->stringResource(R.string.design_this_week);1->stringResource(R.string.design_last_week)
                        else->stringResource(R.string.design_weeks_ago,i)},16,modifier=Modifier.width(104.dp))
                    Box(Modifier.weight(1f)) { DTrack(value?.let { abs(it)/(averages.filterNotNull().maxOfOrNull { n -> abs(n) } ?: 1.0).coerceAtLeast(.01) },c.textSecondary,8) }
                    Box(Modifier.widthIn(min=64.dp),contentAlignment=Alignment.CenterEnd) { DValue(metricReading(metric,value),unit,18,13) }
                }
                if(i<3)HorizontalDivider(color=c.hairline)
            }
        }
        if(journal.isNotEmpty()) DCard {
            val habitMetric = if(metric == DesignMetric.Hrv) stringResource(R.string.design_hrv_short) else name.lowercase(Locale.getDefault())
            DSection(stringResource(R.string.design_habits,habitMetric))
            DText(stringResource(R.string.design_habit_note),14,c.textTertiary)
            val effects=remember(journal,series) {
                fun shifted(yes:Boolean)=journal.filter { it.answeredYes==yes }.groupBy { it.question }.mapValues { (_,rows) ->
                    rows.map { LocalDate.parse(it.day).plusDays(1).toString() }.toSet() }
                EffectRanker.rankNoLag(shifted(true),shifted(false),series,metric.key).associateBy { it.behavior }
            }
            val habits=journal.map{it.question}.distinct().sorted()
            habits.forEachIndexed { i,habit ->
                val delta=effects[habit]?.delta
                Row(Modifier.heightIn(min=44.dp),verticalAlignment=Alignment.CenterVertically) {
                    DText(habit,16,modifier=Modifier.weight(1f))
                    val change = when {
                        delta == null -> missing
                        metric == DesignMetric.Asleep -> signed(delta * 60)+" "+stringResource(R.string.design_unit_min)
                        else -> listOf(signed(displayReading(metric,delta),metric.decimals),unit).filter { it.isNotEmpty() }.joinToString(" ")
                    }
                    DText(change,16,if(delta==null)c.textTertiary else if(metric.favorable(delta))c.chargeColor else c.effortColor,true)
                }
                if(i<habits.lastIndex)HorizontalDivider(color=c.hairline)
            }
        }
    }
}
