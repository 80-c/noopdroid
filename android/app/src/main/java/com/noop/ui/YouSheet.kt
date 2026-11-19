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

import androidx.compose.ui.graphics.vector.ImageVector

internal data class DesignDevice(val name:String,val connected:Boolean,val battery:Double?,val canSync:Boolean)

@Composable internal fun designDevice(vm:AppViewModel):DesignDevice {
    val name by vm.activeDeviceName.collectAsStateWithLifecycle()
    val live by vm.live.collectAsStateWithLifecycle()
    val whoop by vm.activeIsWhoop.collectAsStateWithLifecycle()
    val oura by vm.activeIsOura.collectAsStateWithLifecycle()
    val ring by vm.ouraLinkPhase.collectAsStateWithLifecycle()
    val ringBattery by vm.ouraBatteryPct.collectAsStateWithLifecycle()
    val band=whoop && live.connected && live.bonded
    val connected=band || (oura && ring==com.noop.ble.OuraLiveSource.LinkPhase.AUTHENTICATED)
    return DesignDevice(name ?: missing,connected,if(band)live.batteryPct else if(connected)ringBattery?.toDouble() else null,band && !live.backfilling)
}

@Composable internal fun syncLabel(ts:Long?)=stringResource(R.string.design_synced,
    ts?.let { ((System.currentTimeMillis()/1000-it).coerceAtLeast(0)/60).toString() } ?: missing)

private data class YouItem(val label:Int,val icon:Int,val route:String,val value:String?=null)

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun YouSheet(vm:AppViewModel, close:()->Unit, navigate:(String)->Unit) {
    val c=designColors
    val device=designDevice(vm)
    val live by vm.live.collectAsStateWithLifecycle()
    val ctx=LocalContext.current
    val hcSync by vm.hcAutoSync.collectAsStateWithLifecycle()
    val sourceCount by produceState<Int?>(null,vm,device.name){value=vm.deviceRegistry.all().size}
    val on=stringResource(R.string.design_on)
    val off=stringResource(R.string.design_off)
    val groups=listOf(
        R.string.design_tools to listOf(YouItem(R.string.nav_breathe,R.drawable.symbol_air,"breathe"),
            YouItem(R.string.design_timer,R.drawable.symbol_timer,"intervals"),YouItem(R.string.design_log,R.drawable.symbol_fitness_center,"strength"),
            YouItem(R.string.design_stress,R.drawable.symbol_bolt,"stress"),YouItem(R.string.design_rhythm,R.drawable.symbol_ecg_heart,"rhythm"),
            YouItem(R.string.design_labs,R.drawable.symbol_lab_profile,"lab_book")),
        R.string.design_insights to listOf(YouItem(R.string.design_coach,R.drawable.symbol_auto_awesome,"coach",if(NoopPrefs.coachEnabled(ctx))on else off),
            YouItem(R.string.design_effects,R.drawable.symbol_lightbulb,"insights"),YouItem(R.string.design_compare,R.drawable.symbol_compare_arrows,"compare")),
        R.string.design_data to listOf(YouItem(R.string.design_hc,R.drawable.symbol_favorite,"data_sources",if(hcSync)on else off),
            YouItem(R.string.design_sources,R.drawable.symbol_database,"data_sources",sourceCount?.toString() ?: missing),
            YouItem(R.string.design_backup,R.drawable.symbol_cloud_sync,"backup_sync",if(BackupSyncPrefs.autoEnabled(ctx))on else off),
            YouItem(R.string.design_export,R.drawable.symbol_upload,"export")),
        R.string.design_app to listOf(YouItem(R.string.nav_alarms,R.drawable.symbol_alarm,"smart_alarm"),
            YouItem(R.string.nav_notifications,R.drawable.symbol_notifications,"notifications"),YouItem(R.string.nav_settings,R.drawable.symbol_settings,"settings")))
    ModalBottomSheet(onDismissRequest=close,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),
        containerColor=c.surfaceBase,dragHandle=null) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=20.dp).padding(top=20.dp,bottom=32.dp),
            verticalArrangement=Arrangement.spacedBy(18.dp)) {
            Box(Modifier.fillMaxWidth().height(48.dp)) {
                Spacer(Modifier.align(Alignment.TopCenter).size(40.dp,5.dp).background(c.hairlineStrong,CircleShape))
                TextButton(onClick=close,modifier=Modifier.align(Alignment.CenterEnd)) {
                    DText(stringResource(R.string.l10n_steps_calibration_screen_done_e9b450d1),15,c.accent,true)
                }
            }
            DText(stringResource(R.string.redesign_you),32,medium=true)
            DCard(padding=16,gap=12) {
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                    Symbol(R.drawable.symbol_watch,28,if(device.connected)c.chargeColor else c.textTertiary)
                    Column {
                        DText(device.name,18,medium=true)
                        DText(listOf(stringResource(if(device.connected)R.string.design_link else R.string.design_offline),
                            stringResource(R.string.design_battery,number(device.battery)),syncLabel(live.lastSyncAt)).joinToString(" · "),15,c.textSecondary)
                    }
                }
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    DAction(stringResource(R.string.redesign_sync_now),device.canSync,Modifier.weight(1f)){vm.syncNow()}
                    DAction(stringResource(R.string.l10n_live_screen_manage_devices_e5c277ff),modifier=Modifier.weight(1f)){navigate("devices")}
                }
            }
            groups.forEach { (title,items)->
                Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    DSection(stringResource(title))
                    DCard(padding=0) {
                        items.forEachIndexed { i,item ->
                            DRow({navigate(item.route)}) {
                                Symbol(item.icon,22,c.textSecondary)
                                Spacer(Modifier.width(12.dp));DText(stringResource(item.label),17,modifier=Modifier.weight(1f))
                                item.value?.let { DText(it,15,c.textSecondary) }
                            }
                            if(i<items.lastIndex)HorizontalDivider(color=c.hairline)
                        }
                    }
                }
            }
        }
    }
}

