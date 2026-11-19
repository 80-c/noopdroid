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
internal fun TrendsScreen(vm: AppViewModel, you: () -> Unit, history: (DesignMetric) -> Unit) {
    val today=LocalDate.now()
    val c=designColors
    var days by rememberSaveable { mutableIntStateOf(30) }
    val scores=listOf(DesignMetric.Recovery,DesignMetric.Strain,DesignMetric.Sleep)
    val body=listOf(DesignMetric.Hrv,DesignMetric.Rhr,DesignMetric.Asleep,DesignMetric.Resp)
    val data=metricData(vm,scores+body,today,days*2)
    DPage(vm,stringResource(R.string.nav_trends),stringResource(R.string.design_last_days,days),you) {
        DRange(days){days=it}
        DCard(gap=10) {
            val recovery=window(data[DesignMetric.Recovery].orEmpty(),today,days)
            val strain=window(data[DesignMetric.Strain].orEmpty(),today,days)
            Row(horizontalArrangement=Arrangement.spacedBy(18.dp)) {
                listOf(DesignMetric.Recovery to recovery, DesignMetric.Strain to strain).forEach { (metric,series) ->
                    Column {
                        Row(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically) {
                            val line=metric==DesignMetric.Recovery
                            Spacer(Modifier.size(if(line)14.dp else 10.dp,if(line)3.dp else 10.dp).background(metric.tint))
                            DText(stringResource(if(line)R.string.design_rec_avg else R.string.design_strain_avg),13,c.textSecondary)
                        }
                        DText(number(series.meanValue()),26,metric.tint,true)
                    }
                }
            }
            Canvas(Modifier.fillMaxWidth().height(150.dp)) {
                for(i in 1..3) drawLine(c.hairline,Offset(0f,size.height*i/4),Offset(size.width,size.height*i/4),1.dp.toPx())
                val slot=size.width/days
                strain.forEachIndexed { i,value -> if(value!=null) {
                    val h=(value/100).coerceIn(0.0,1.0).toFloat()*size.height
                    drawRoundRect(c.effortColor.copy(alpha=.55f),Offset(slot*i,size.height-h),Size(slot*.72f,h),CornerRadius(2.dp.toPx()))
                } }
                seriesLine(recovery,c.chargeColor,0.0,100.0,3.dp.toPx())
            }
            DAxis(today,days)
        }
        listOf(R.string.design_daily to scores,R.string.design_body to body).forEach { (title,metrics) ->
            DGroup(stringResource(title)) {
                metrics.forEachIndexed { i,metric ->
                    val series=data[metric].orEmpty()
                    val current=window(series,today,days)
                    val mean=current.meanValue()
                    val prior=window(series,today.minusDays(days.toLong()),days).meanValue()
                    val change=if(mean!=null && prior!=null)mean-prior else null
                    DRow({history(metric)},60,12) {
                        Column(Modifier.weight(1f)) {
                            DText(stringResource(metric.label),17,medium=true)
                            val delta = if (metric == DesignMetric.Asleep && change != null)
                                signed(change * 60) + " " + stringResource(R.string.design_unit_min) else signed(change,metric.decimals)
                            DText(stringResource(R.string.design_prior,delta,rangeLabel(days)),14,
                                if(change==null)c.textTertiary else if(metric.favorable(change))c.chargeColor else c.effortColor,true)
                        }
                        Canvas(Modifier.size(72.dp,28.dp)) {
                            val values=current.filterNotNull()
                            val min=values.minOrNull() ?: 0.0
                            val max=values.maxOrNull() ?: 1.0
                            seriesLine(current,metric.tint,min-0.1,max+0.1,2.dp.toPx())
                        }
                        Box(Modifier.width(86.dp),contentAlignment=Alignment.CenterEnd) {
                            DValue(metricReading(metric,mean),metricUnit(metric),20,13)
                        }
                    }
                    if(i<metrics.lastIndex)HorizontalDivider(color=c.hairline)
                }
            }
        }
    }
}
