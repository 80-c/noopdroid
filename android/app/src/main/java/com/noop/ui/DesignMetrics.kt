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

import com.noop.analytics.SkinTempDisplay

internal enum class DesignMetric(val key: String, val label: Int, val unit: Int? = null, val decimals: Int = 0) {
    Recovery("recovery",R.string.design_recovery), Strain("strain",R.string.design_strain),
    Sleep("sleep_performance",R.string.redesign_sleep_score), Hrv("hrv",R.string.design_hrv,R.string.design_unit_ms),
    Rhr("rhr",R.string.design_rhr,R.string.design_unit_bpm), Asleep("asleep_min",R.string.design_asleep,R.string.design_unit_hours,1),
    Resp("resp_rate",R.string.design_resp,R.string.design_unit_resp,1), Temp("skin_temp",R.string.design_temp,R.string.design_unit_temp,1),
    Oxygen("spo2",R.string.design_oxygen,R.string.design_unit_pct);

    val tint: Color get() = when(this) {
        Recovery -> designColors.chargeColor
        Strain -> designColors.effortColor
        Sleep -> designColors.restColor
        else -> designColors.textPrimary
    }
    fun favorable(delta: Double, baseline: Double? = null, value: Double? = null): Boolean = when(this) {
        Rhr -> delta <= 0
        Resp,Temp -> if(baseline != null && value != null) abs(value - baseline) <= abs(value - delta - baseline) else abs(delta) < 0.05
        else -> delta >= 0
    }
}

internal val vitals = listOf(DesignMetric.Hrv, DesignMetric.Rhr, DesignMetric.Resp, DesignMetric.Temp, DesignMetric.Oxygen)

@Composable
internal fun dailyEffort(vm: AppViewModel, day: com.noop.data.DailyMetric?): Double? {
    val context = LocalContext.current
    val cycle by vm.activeDayCycle.collectAsStateWithLifecycle()
    val strap by vm.activeStrapIdFlow.collectAsStateWithLifecycle()
    val lastSync by vm.lastHistorySyncAt.collectAsStateWithLifecycle()
    val date = LocalDate.now()
    val start = activeDayCycleStart(NoopPrefs.dayCycleMode(context), cycle?.onsetTs,
        date.atStartOfDay(java.time.ZoneId.systemDefault()).toEpochSecond())
    return produceState(day?.strain, listOf(day, strap, start, lastSync)) {
        val profile = ProfileStore.from(context)
        val samples = vm.repo.hrSamplesUnion(vm.activeStrapId, start, System.currentTimeMillis() / 1000)
        val live = if (samples.isEmpty()) null else com.noop.analytics.StrainScorer.strain(
            hr = samples, maxHR = profile.hrMaxOverride.takeIf { it > 0 }?.toDouble()
                ?: com.noop.analytics.StrainScorer.tanakaHRmax(profile.age.toDouble()),
            restingHR = day?.restingHr?.toDouble() ?: com.noop.analytics.StrainScorer.defaultRestingHR,
            method = NoopPrefs.effortMethod(context), sex = profile.sex)
        value = com.noop.analytics.StrainScorer.effectiveEffort(live, day?.strain)
    }.value
}

@Composable
internal fun metricData(vm: AppViewModel, metrics: List<DesignMetric>, end: LocalDate, length: Int):
    Map<DesignMetric,Map<String,Double>> {
    val recent by vm.recentDays.collectAsStateWithLifecycle()
    val strap by vm.activeStrapIdFlow.collectAsStateWithLifecycle()
    return produceState<Map<DesignMetric,Map<String,Double>>>(emptyMap(),vm,strap,listOf(metrics,recent,end,length)) {
        val from = end.minusDays(length.toLong()-1).toString()
        value = metrics.associateWith { metric ->
            vm.repo.resolvedSeries(metric.key,"my-whoop",from,end.toString(),strapDeviceId=vm.activeStrapId).values
                .mapNotNull { (day,v) -> when {
                    !v.isFinite() -> null
                    metric == DesignMetric.Temp && SkinTempDisplay.kind(v) != SkinTempDisplay.Kind.DEVIATION -> null
                    metric == DesignMetric.Asleep -> day to v / 60.0
                    else -> day to v
                } }.toMap()
        }
    }.value
}

internal fun window(series: Map<String,Double>, end: LocalDate, days: Int) =
    (days-1 downTo 0).map { series[end.minusDays(it.toLong()).toString()] }

internal fun List<Double?>.meanValue() = filterNotNull().takeIf { it.isNotEmpty() }?.average()

@Composable internal fun metricUnit(metric: DesignMetric) =
    when(metric) {
        DesignMetric.Asleep -> ""
        DesignMetric.Temp -> UnitFormatter.temperatureUnit(UnitPrefs.temperature(LocalContext.current))
        else -> metric.unit?.let { stringResource(it) }.orEmpty()
    }

@Composable internal fun displayReading(metric: DesignMetric, value: Double?): Double? =
    if (metric == DesignMetric.Temp && UnitPrefs.temperature(LocalContext.current) == TemperatureUnit.FAHRENHEIT)
        value?.times(1.8) else value

@Composable internal fun metricReading(metric: DesignMetric, value: Double?): String =
    if (metric == DesignMetric.Asleep) sleepDurationLabel(value?.times(60)) else number(displayReading(metric,value), metric.decimals)

@Composable internal fun DValue(value: String, unit: String, size: Int, unitSize: Int, color: Color = designColors.textPrimary) {
    Row(verticalAlignment=Alignment.Bottom) {
        DText(value,size,color,true,modifier=Modifier.alignByBaseline(),height=if(size==64)64 else size+4)
        if(unit.isNotEmpty()) { Spacer(Modifier.width(3.dp)); DText(unit,unitSize,designColors.textSecondary,medium=size==64,modifier=Modifier.alignByBaseline()) }
    }
}

internal fun DrawScope.seriesLine(values: List<Double?>, color: Color, min: Double, max: Double, width: Float, dots: Boolean = false) {
    var previous: Offset? = null
    values.forEachIndexed { index, value ->
        val point = value?.let {
            Offset(size.width * index / (values.size-1).coerceAtLeast(1),
                size.height * (1.0-(it-min)/(max-min).coerceAtLeast(0.001)).toFloat())
        }
        if (point != null) {
            previous?.let { drawLine(color,it,point,width,StrokeCap.Round) }
            if(dots || previous == null) drawCircle(color, if(dots) 2.6.dp.toPx() else width/2,point)
        }
        previous = point
    }
}

@Composable internal fun DAxis(end: LocalDate, days: Int) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
        listOf(dateLabel(end.minusDays(days-1L),"MMM d"),dateLabel(end.minusDays(days/2L),"MMM d"),
            stringResource(R.string.nav_today)).forEach { DText(it,13,designColors.textTertiary) }
    }
}

