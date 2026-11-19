package com.noop.ui

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.noop.data.DemoSeeder
import com.noop.data.WhoopRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Opt-in visual acceptance check. Run on a fresh, disposable 412dp emulator. */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class RedesignScreenshots {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun captureSevenScreens() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("screenshots") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val recoveryAverage = runBlocking {
            val repo = WhoopRepository.from(context)
            check(repo.daysMerged("my-whoop").isEmpty()) { "Use a fresh emulator install" }
            DemoSeeder.seedIfEmpty(repo)
            val now = System.currentTimeMillis() / 1000
            val strap = "my-whoop"
            // Give the disposable demo night timed periods, preserving its stage order and proportions.
            val night = repo.sleepSessionsUnion(strap,0,now).maxBy { it.endTs }
            val stages = org.json.JSONArray(night.stagesJSON)
            val total = (0 until stages.length()).sumOf { stages.getJSONObject(it).getDouble("min") }
            var minutes = 0.0
            var start = night.startTs
            for (index in 0 until stages.length()) {
                val stage = stages.getJSONObject(index)
                minutes += stage.getDouble("min")
                val end = night.startTs + ((night.endTs - night.startTs) * minutes / total).toLong()
                stage.put("start",start).put("end",end)
                start = end
            }
            repo.upsertSleepSessions(listOf(night.copy(stagesJSON = stages.toString())))
            val samples = (0 until 40).map { index ->
                val bpm = 66 + (5 * kotlin.math.sin(index * 0.37) + 2 * kotlin.math.sin(index * 1.4)).toInt()
                com.noop.data.HrSample(strap, now - (39-index)*30L, bpm)
            }
            com.noop.data.WhoopDatabase.get(context).whoopDao().insertHr(samples)
            val end = java.time.LocalDate.now()
            val from = end.minusDays(29)
            val recovery = repo.resolvedSeries("recovery",strap,from.toString(),end.toString(),strapDeviceId=strap)
            number(recovery.values.map { it.second }.average())
        }
        NoopPrefs.of(context).edit()
            .putBoolean(NoopPrefs.KEY_ONBOARDED, true)
            .putString(NoopPrefs.KEY_ACCEPTED_TERMS_VERSION, Terms.CURRENT_VERSION)
            .putString(NoopPrefs.KEY_LAST_SEEN_CHANGELOG, AppChangelog.CURRENT_VERSION).commit()
        val photos = File(context.filesDir, "design-review").also { it.mkdirs() }
        File(photos, "scrolled").mkdirs()

        fun shot(name: String, marker: String) {
            compose.waitUntil(30000) { compose.onAllNodesWithText(marker).fetchSemanticsNodes().isNotEmpty() }
            compose.waitForIdle()
            SystemClock.sleep(800)
            val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            val density = context.resources.displayMetrics.density
            android.util.Log.i("ScreenReview", "$name captured at ${bitmap.width}x${bitmap.height}, density $density")
            File(photos, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        fun tap(label: String) {
            compose.onAllNodesWithText(label).onLast().performClick()
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            shot("01-today", "Vitals vs your 30-day normal")
            tap("Recovery")
            shot("02-recovery", "What set today's recovery")
            compose.onNodeWithContentDescription("Back").performClick()
            tap("Sleep")
            shot("03-sleep", "Through the night")
            tap("Activity")
            shot("04-activity", "Strain today")
            tap("Trends")
            compose.waitUntil(60000) { compose.onAllNodesWithText(recoveryAverage).fetchSemanticsNodes().isNotEmpty() }
            shot("05-trends", "Daily scores")
            compose.onNodeWithText("Respiration").performScrollTo()
            shot("scrolled/05-trends-body", "Respiration")
            compose.onNodeWithText("Heart rate variability").performScrollTo().performClick()
            shot("06-metric-history", "Weekly averages")
            compose.onNodeWithText("3 weeks ago").performScrollTo()
            shot("scrolled/06-metric-weeks", "3 weeks ago")
            compose.onNodeWithContentDescription("Back").performScrollTo()
            compose.onNodeWithContentDescription("You: devices, tools and settings").performClick()
            shot("07-you", "Manage devices")
            compose.onNodeWithText("Settings").performScrollTo()
            shot("scrolled/07-you-settings", "Settings")
        }
    }
}
