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

import com.noop.analytics.ChargeDriverLabel
import com.noop.analytics.ScoreConfidence

@Composable
internal fun RecoveryDetailScreen(vm: AppViewModel, you: () -> Unit, back: () -> Unit) {
    val days by vm.recentDays.collectAsStateWithLifecycle()
    val bases by vm.chargeBaselines.collectAsStateWithLifecycle()
    val today=LocalDate.now()
    val day=days.lastOrNull { it.day==today.toString() }
    val score=day?.recovery
    val yesterday=days.lastOrNull { it.day==today.minusDays(1).toString() }?.recovery
    val drivers=remember(bases,day){ recoveryChargeDrivers(bases,day).associateBy { it.label } }
    val c=designColors
    DPage(vm,stringResource(R.string.design_recovery),null,you,back,sectionGap=14) {
        Row(verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
            DText(number(score),72,c.chargeColor,true,height=72)
            Column {
                DText(if(score==null)missing else if(score>=67)stringResource(R.string.design_ready) else synthesisWord(score),20,medium=true)
                val delta = if(score!=null && yesterday!=null) (score-yesterday).roundToInt() else null
                val change = delta?.let { when { it==0 -> stringResource(R.string.design_same)
                    else -> stringResource(if(it>0)R.string.design_up else R.string.design_down,number(abs(it.toDouble()))) } }
                val tier=chargeConfidenceTier(bases,day)
                val confidence=when(tier) { ScoreConfidence.SOLID -> stringResource(R.string.design_confidence)
                    ScoreConfidence.BUILDING -> stringResource(R.string.today_confidence_building)
                    ScoreConfidence.CALIBRATING -> stringResource(R.string.today_confidence_calibrating)
                    else -> null }
                DText(listOfNotNull(change,confidence).joinToString(" · ").ifEmpty { missing },15,c.textSecondary)
            }
        }
        DCard(padding=16) {
            DSection(stringResource(R.string.design_what_set))
            Row(Modifier.align(Alignment.CenterHorizontally).width(240.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                DText(stringResource(R.string.design_lowers),13,c.textTertiary,true)
                DText(stringResource(R.string.design_raises),13,c.textTertiary,true)
            }
            val labels=listOf(R.string.design_hrv_short,R.string.design_rhr,R.string.nav_sleep,R.string.design_resp,R.string.design_temp)
            val units=listOf(R.string.design_unit_ms,R.string.design_unit_bpm,R.string.design_unit_pct,R.string.design_unit_resp,R.string.design_unit_temp)
            val maxPoints=drivers.values.maxOfOrNull { abs(it.deltaPoints) }?.coerceAtLeast(1) ?: 1
            ChargeDriverLabel.entries.forEachIndexed { i,key ->
                val driver=drivers[key]
                Column(Modifier.fillMaxWidth().padding(vertical=12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                        DText(stringResource(labels[i]),17,medium=true)
                        val unit=if(key == ChargeDriverLabel.SKIN_TEMPERATURE)metricUnit(DesignMetric.Temp) else stringResource(units[i])
                        DText(driver?.let { d -> if (key == ChargeDriverLabel.SLEEP_QUALITY)
                            stringResource(R.string.design_sleep_contribution,number(d.value))
                            else if (key == ChargeDriverLabel.SKIN_TEMPERATURE) signed(displayReading(DesignMetric.Temp,d.value),1)+" "+unit
                            else d.baseline?.let { stringResource(R.string.design_comparison,number(d.value,if(i>=3)1 else 0),number(it,if(i>=3)1 else 0),unit) }
                            ?: (number(d.value,if(i==4)1 else 0)+" "+unit) } ?: missing,15,c.textSecondary)
                    }
                    Canvas(Modifier.width(240.dp).height(16.dp).align(Alignment.CenterHorizontally)) {
                        val barH=10.dp.toPx()
                        val y=(size.height-barH)/2
                        val length=(driver?.deltaPoints ?: 0)*center.x/maxPoints
                        if(length!=0f) drawRoundRect(if(length>0)c.chargeColor else c.effortColor,
                            Offset(center.x+min(0f,length),y),Size(abs(length),barH),CornerRadius(5.dp.toPx()))
                        drawLine(c.textTertiary,Offset(center.x,0f),Offset(center.x,size.height),2.dp.toPx())
                    }
                }
                if(i<labels.lastIndex) HorizontalDivider(color=c.hairline)
            }
        }
        DCard(padding=16,gap=10) {
            DSection(stringResource(R.string.redesign_last_seven_days))
            Row(Modifier.fillMaxWidth().height(120.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.Bottom) {
                for(i in 6 downTo 0) {
                    val date=today.minusDays(i.toLong())
                    val value=days.lastOrNull { it.day==date.toString() }?.recovery
                    Column(horizontalAlignment=Alignment.CenterHorizontally) {
                        DText(number(value),13,medium=true)
                        Spacer(Modifier.width(26.dp).height(((value ?: 0.0).coerceIn(0.0,100.0)*0.84).dp)
                            .background(if((value ?: 0.0)>=67)c.chargeColor else c.recoveryMid,RoundedCornerShape(8.dp)))
                        DText(dateLabel(date,"EEE"),13,c.textSecondary)
                    }
                }
            }
        }
    }
}
