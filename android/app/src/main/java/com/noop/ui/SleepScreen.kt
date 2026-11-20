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

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.launch
import com.noop.data.SleepSession
import com.noop.data.WhoopRepository

@Composable
internal fun SleepScreen(vm: AppViewModel,you: () -> Unit) {
    val c=designColors
    val days by vm.recentDays.collectAsStateWithLifecycle()
    val strap by vm.activeStrapIdFlow.collectAsStateWithLifecycle()
    val scope=rememberCoroutineScope()
    var revision by remember { mutableIntStateOf(0) }
    var offset by rememberSaveable(strap) { mutableIntStateOf(0) }
    val nights by produceState(emptyList<SleepSession>(),days,strap,revision) {
        val now=System.currentTimeMillis()/1000
        value=WhoopRepository.mergeSleepRichness(vm.repo.sleepSessionsUnion(vm.activeStrapId,0,now),
            vm.repo.computedSleepSessionsUnion(vm.activeStrapId,0,now)) { localDayString(it.endTs) }.sortedBy { it.effectiveStartTs }
    }
    val imported by produceState(ImportedSleepSeries(),days,strap) {
        suspend fun load(key:String)=vm.repo.metricSeries("my-whoop",key,"0000-01-01","9999-12-31").associate { it.day to it.value }
        value=ImportedSleepSeries(load("sleep_performance"),load("sleep_consistency"),load("sleep_need_min"),load("sleep_debt_min"))
    }
    val habitual by produceState<Long?>(null,nights,strap){value=vm.repo.habitualMidsleepSec(vm.activeStrapId)}
    val grouped=remember(nights){nights.groupBy { localDayString(it.endTs) }.toSortedMap(reverseOrder()).values.toList()}
    val night=remember(grouped,days,offset,habitual){selectNight(grouped,days,offset,habitual)}
    val key=night?.dayKey ?: LocalDate.now().toString()
    val date=LocalDate.parse(key)
    val day=days.lastOrNull { it.day==key }
    val model=remember(days,night,imported,nights,habitual) {
        buildSleepModel(days.filter { it.day<=key },night?.session,imported,selectedDay=key,heroStages=night?.groupStages,
            heroSegments=night?.groupSegments,napSleepMinByDay=napSleepMinutesByDay(nights,habitual),sessions=nights,todayKey=key)
    }
    val display=heroDisplay(model,night)
    val stages=display?.stages
    val asleep=stages?.asleep ?: day?.totalSleepMin
    val need=imported.needMin[key] ?: model?.trendDates?.indexOf(key)?.takeIf{it>=0}?.let { model.trendNeedHours.getOrNull(it)?.times(60) }
    val percent=if(need!=null && need>0 && asleep!=null) asleep/need else null
    val onset=night?.heroOnsetTs ?: night?.session?.effectiveStartTs
    val wake=night?.heroWakeTs ?: night?.session?.endTs
    val zone=ZoneId.systemDefault()
    fun at(ts:Long)=Instant.ofEpochSecond(ts).atZone(zone)
    val overline=stringResource(R.string.design_sleep_night,dateLabel(onset?.let {at(it).toLocalDate()} ?: date.minusDays(1),"EEE"),dateLabel(date,"EEE"))
    val swipe=Modifier.pointerInput(grouped.size) {
        var dx=0f
        detectHorizontalDragGestures(onDragStart={dx=0f},onDragEnd={
            if(abs(dx)>=48.dp.toPx())offset=(offset+if(dx>0)1 else -1).coerceIn(0,grouped.lastIndex.coerceAtLeast(0))
        }) { change,amount -> change.consume(); dx+=amount }
    }
    DPage(vm,stringResource(R.string.design_last_night),overline,you,modifier=swipe) {
        DCard(padding=16) {
            Row(horizontalArrangement=Arrangement.spacedBy(16.dp),verticalAlignment=Alignment.CenterVertically) {
                Column {
                    DText(stringResource(R.string.redesign_sleep_score),15,c.textSecondary,true)
                    val score=night?.let { heroPerformanceScore(it,days,imported) } ?: imported.performance[key]
                    DText(number(score),56,c.restColor,true,height=58)
                }
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    DText(stringResource(R.string.design_asleep_amount,sleepDurationLabel(asleep)),22,medium=true)
                    DTrack(percent,c.restColor,8)
                    DText(stringResource(R.string.design_need,number(percent?.times(100)),sleepDurationLabel(need)),14,c.textSecondary)
                }
            }
        }
        val names=listOf(R.string.redesign_stage_awake,R.string.redesign_stage_light,R.string.redesign_stage_deep,R.string.redesign_stage_rem)
        val colors=listOf(c.sleepAwake,c.sleepLight,c.sleepDeep,c.sleepREM)
        val minutes=listOf(stages?.awake,stages?.light,stages?.deep,stages?.rem)
        DCard(padding=16,gap=12) {
            DSection(stringResource(R.string.design_stages))
            Row(Modifier.fillMaxWidth().height(14.dp).clip(CircleShape).background(c.surfaceInset)) {
                minutes.forEachIndexed { i,v -> if(v!=null && v>0)Spacer(Modifier.fillMaxHeight().weight(v.toFloat()).background(colors[i])) }
            }
            Row { names.forEachIndexed { i,label ->
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(3.dp)) {
                        Spacer(Modifier.size(10.dp).background(colors[i],RoundedCornerShape(3.dp)))
                        DText(stringResource(label),14,c.textSecondary,true)
                    }
                    DText(sleepDurationLabel(minutes[i]),18,medium=true)
                }
            } }
        }
        DCard(gap=10) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                DSection(stringResource(R.string.design_through))
                val fmt=DateTimeFormatter.ofPattern(if(ClockPrefs.uses24Hour(LocalContext.current))"HH:mm" else "h:mm a",Locale.getDefault())
                DText(if(onset==null || wake==null)missing else stringResource(R.string.design_bounds,at(onset).format(fmt),at(wake).format(fmt)),14,c.textSecondary)
            }
            Canvas(Modifier.fillMaxWidth().height(100.dp)) {
                if(onset!=null && wake!=null && wake>onset) display?.hypnogramSegments.orEmpty().forEach { segment ->
                    val stage=when(segment.stage.lowercase()) {
                        "wake", "awake" -> 0
                        "light" -> 1
                        "deep" -> 2
                        "rem" -> 3
                        else -> -1
                    }
                    if(stage>=0) {
                        val lane=listOf(0,2,3,1)[stage]
                        fun x(ts:Long)=size.width*(ts-onset).toFloat()/(wake-onset)
                        val start=x(segment.start).coerceIn(0f,size.width)
                        val stop=x(segment.end).coerceIn(start,size.width)
                        drawRoundRect(colors[stage],Offset(start,(lane+.5f)*size.height/4-8.dp.toPx()),Size(stop-start,16.dp.toPx()),CornerRadius(3.dp.toPx()))
                    }
                }
            }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            val efficiency=if(asleep!=null && (night?.groupInBedMin ?: stages?.total ?: 0.0)>0) asleep*100/(night?.groupInBedMin ?: stages!!.total) else null
            val consistency=imported.consistency[key] ?: consistencySeries(nights.filter {localDayString(it.endTs)<=key}).latest
            listOf(Triple(R.string.design_eff,number(efficiency)+if(efficiency==null)"" else "%",R.string.design_eff_note),
                Triple(R.string.design_cons,number(consistency),R.string.design_cons_note)).forEach { (label,value,note)->
                DCard(Modifier.weight(1f),padding=12,radius=12) {
                    DText(stringResource(label),14,c.textSecondary,true); DText(value,22,medium=true); DText(stringResource(note),13,c.textTertiary)
                }
            }
        }
        NightNavHeader(offset,"",grouped.lastIndex,null,{offset=it},night?.session,night?.heroGroup.orEmpty(),
            onUpdateTimes={s,start,end ->scope.launch { vm.updateSleepGroupTimes(night?.heroGroup.orEmpty().ifEmpty{listOf(s)},start,end);revision++ }},
            editButtonOnly=true)
    }
}
