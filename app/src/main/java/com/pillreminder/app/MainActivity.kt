package com.pillreminder.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.pillreminder.app.ui.EditScreen
import com.pillreminder.app.ui.MedsScreen
import com.pillreminder.app.ui.PillTheme
import com.pillreminder.app.ui.SettingsScreen
import com.pillreminder.app.ui.TodayScreen
import com.pillreminder.app.ui.findSetupIssues
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    /** Bumped whenever the app comes back to the front, so setup checks and "now" refresh. */
    private var resumeTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.init(this)
        Notifier.ensureChannel(this)
        Scheduler.reschedule(this)
        Photos.cleanUp(this, Repo.get(this).meds.mapNotNull { it.photoPath }.toSet())
        askForNotificationsOnce()

        setContent {
            PillTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    App(resumeTick)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
    }

    private fun askForNotificationsOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        val prefs = getSharedPreferences("setup", Context.MODE_PRIVATE)
        if (prefs.getBoolean("asked_notif", false)) return
        prefs.edit().putBoolean("asked_notif", true).apply()
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        resumeTick++
    }
}

private sealed interface Screen {
    data object Today : Screen
    data object Meds : Screen
    data object Settings : Screen
    data class Edit(val medId: String?, val back: Screen) : Screen
}

@Composable
private fun App(resumeTick: Int) {
    val context = LocalContext.current
    val data by Repo.state.collectAsState()
    var screen by remember { mutableStateOf<Screen>(Screen.Today) }
    var dateEpochDay by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    val issues = remember(resumeTick, data) { findSetupIssues(context) }

    BackHandler(enabled = screen != Screen.Today) {
        screen = when (val s = screen) {
            is Screen.Edit -> s.back
            else -> Screen.Today
        }
    }

    when (val s = screen) {
        Screen.Today -> TodayScreen(
            data = data,
            date = LocalDate.ofEpochDay(dateEpochDay),
            issues = issues,
            onDateChange = { dateEpochDay = it.toEpochDay() },
            onAdd = { screen = Screen.Edit(null, Screen.Today) },
            onMyPills = { screen = Screen.Meds },
            onSettings = { screen = Screen.Settings },
        )
        Screen.Meds -> MedsScreen(
            data = data,
            onBack = { screen = Screen.Today },
            onEdit = { id -> screen = Screen.Edit(id, Screen.Meds) },
            onAdd = { screen = Screen.Edit(null, Screen.Meds) },
        )
        Screen.Settings -> SettingsScreen(data = data, issues = issues, onBack = { screen = Screen.Today })
        is Screen.Edit -> EditScreen(data = data, medId = s.medId, onDone = { screen = s.back })
    }
}
