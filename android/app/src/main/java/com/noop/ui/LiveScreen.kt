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

import com.noop.data.HrBucket
import com.noop.data.WorkoutRow
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.delay
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
internal fun LiveScreen(vm: AppViewModel, you: () -> Unit, navigate: (String)->Unit) {
    val c=designColors
    val live by vm.live.collectAsStateWithLifecycle()
    val bpm by vm.bpm.collectAsStateWithLifecycle()
    val strap by vm.activeStrapIdFlow.collectAsStateWithLifecycle()
    val profile=ProfileStore.from(LocalContext.current)
    val units=UnitPrefs.distanceSystem(LocalContext.current)
    val active by vm.activeWorkout.collectAsStateWithLifecycle()
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    val finished by vm.lastWorkout.collectAsStateWithLifecycle()
    val days by vm.recentDays.collectAsStateWithLifecycle()
    val today=LocalDate.now()
    val day=days.lastOrNull { it.day==today.toString() }
    val effort=dailyEffort(vm,day)
    val goal=optimalFractionRange(day?.recovery)
    var picker by rememberSaveable {mutableStateOf(false)}
    var recording by rememberSaveable {mutableStateOf(false)}
    var detail by remember {mutableStateOf<WorkoutRow?>(null)}
    DisposableEffect(vm){vm.requestRealtimeHr(); onDispose {vm.releaseRealtimeHr()}}
    LaunchedEffect(strap,finished?.endTs){vm.loadWorkouts()}
    LaunchedEffect(vm){if(vm.consumeActiveWorkoutRequest())recording=true}
    val buckets by produceState<List<HrBucket>>(emptyList(),strap) {
        val id=strap ?: return@produceState
        while(true) {
            val now=System.currentTimeMillis()/1000
            value=vm.repo.hrBucketsForDevice(id,now-1200,now,30)
            delay(30_000)
        }
    }
    if(picker)StartWorkoutSheet(vm=vm,onDismiss={picker=false})
    if(recording && active!=null) Dialog({recording=false},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        LiveWorkoutScreen(vm=vm,onClose={recording=false})
    }
    detail?.let { WorkoutDetailSheet(vm,it){detail=null} }
    DPage(vm,stringResource(R.string.redesign_activity),stringResource(R.string.redesign_activity_subtitle),you) {
        DCard(padding=16,gap=10) {
            val streaming=live.connected && live.streamingLiveHR && bpm!=null
            val value=if(streaming)bpm?.toDouble() else buckets.lastOrNull()?.avgBpm
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Spacer(Modifier.size(8.dp).background(c.metricRose,CircleShape))
                DText(stringResource(R.string.redesign_live_heart_rate),15,c.textSecondary,true,Modifier.weight(1f))
                DText(if(streaming)stringResource(R.string.redesign_zone,profile.hrZoneSet.zoneNumber(value!!))
                    else buckets.lastOrNull()?.let {stringResource(R.string.design_saved,relativeAgo(it.bucket))} ?: stringResource(R.string.design_offline),14,c.textSecondary)
            }
            DValue(number(value),stringResource(R.string.design_unit_bpm),64,20)
            Canvas(Modifier.fillMaxWidth().height(56.dp)) {
                val now=System.currentTimeMillis()/1000
                val values=(39 downTo 0).map { i->buckets.lastOrNull { it.bucket/30==now/30-i }?.avgBpm }
                seriesLine(values,c.metricRose,(values.filterNotNull().minOrNull() ?: 40.0)-2,(values.filterNotNull().maxOrNull() ?: 160.0)+2,2.5.dp.toPx())
            }
            DText(stringResource(R.string.redesign_last_twenty_minutes),13,c.textTertiary)
        }
        Button(onClick={if(active!=null)recording=true else picker=true},modifier=Modifier.fillMaxWidth().heightIn(min=56.dp),
            shape=RoundedCornerShape(16.dp),colors=ButtonDefaults.buttonColors(containerColor=c.primaryContainer),
            elevation=ButtonDefaults.buttonElevation(defaultElevation=1.dp)) {
            Symbol(R.drawable.symbol_play_arrow_filled,24,c.onPrimaryContainer)
            DText(stringResource(if(active==null)R.string.design_start else R.string.l10n_workout_start_open_cf9b7706),16,c.onPrimaryContainer,true)
        }
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            listOf(Triple(R.string.nav_breathe,R.drawable.symbol_air,Destination.Breathe.route),
                Triple(R.string.design_intervals,R.drawable.symbol_timer,Destination.Intervals.route),
                Triple(R.string.design_strength,R.drawable.symbol_fitness_center,"strength")).forEach { (label,icon,route)->
                Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(c.secondaryContainer)
                    .clickable{navigate(route)}.heightIn(min=72.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) {
                    Symbol(icon,24,c.onPrimaryContainer);DText(stringResource(label),14,c.onPrimaryContainer,true)
                }
            }
        }
        DCard(gap=10) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                DSection(stringResource(R.string.design_strain_today)); DText(stringResource(R.string.design_goal_recovery,number(day?.recovery)),14,c.textSecondary)
            }
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                DText(number(effort),36,c.effortColor,true)
                val range=goal?.let { stringResource(R.string.design_goal,number(it.start*100.0),number(it.endInclusive*100.0)) } ?: missing
                DText(stringResource(R.string.design_more_goal,number(effort?.let { goal?.start?.times(100.0)?.minus(it)?.coerceAtLeast(0.0) }),range),15,c.textSecondary)
            }
            Canvas(Modifier.fillMaxWidth().height(12.dp).clip(CircleShape).background(c.surfaceInset)) {
                goal?.let { val left=it.start*size.width; val right=it.endInclusive*size.width
                    drawRect(c.strainGoalBand,Offset(left,0f),Size(right-left,size.height))
                    for(x in listOf(left,right))drawLine(c.effortColor,Offset(x,0f),Offset(x,size.height),2.dp.toPx())
                }
                effort?.let {drawRoundRect(c.effortColor,size=Size(size.width*(it/100).coerceIn(0.0,1.0).toFloat(),size.height),cornerRadius=CornerRadius(6.dp.toPx()))}
            }
        }
        Column {
            DSection(stringResource(R.string.redesign_recent_workouts))
            if(workouts.isEmpty())DText(missing,17,modifier=Modifier.padding(vertical=14.dp))
            workouts.sortedByDescending { it.startTs }.take(5).forEach { row ->
                DRow({detail=row},56,0) {
                    Column(Modifier.weight(1f)) {
                        DText(row.sport,17,medium=true)
                        val date=Instant.ofEpochSecond(row.startTs).atZone(ZoneId.systemDefault()).toLocalDate()
                        val whenLabel=when(date){today->stringResource(R.string.nav_today);today.minusDays(1)->stringResource(R.string.design_yesterday);else->dateLabel(date,"MMM d")}
                        DText(listOfNotNull(whenLabel,stringResource(R.string.redesign_minutes,((row.durationS ?: (row.endTs-row.startTs).toDouble())/60).roundToInt()),
                            row.distanceM?.let{liveDistance(it,units)}).joinToString(" · "),14,c.textSecondary)
                    }
                    DText(number(row.strain),20,c.effortColor,true)
                }
                HorizontalDivider(color=c.hairline)
            }
        }
    }
}
