package `in`.lbscek.attendance.ui

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import `in`.lbscek.attendance.data.AttendancePrefs
import `in`.lbscek.attendance.data.AttendanceResult
import `in`.lbscek.attendance.data.EtlabRepository
import `in`.lbscek.attendance.data.InvalidCredentialsException
import `in`.lbscek.attendance.ui.theme.AttendanceTheme
import `in`.lbscek.attendance.widget.AttendanceWidget
import `in`.lbscek.attendance.work.AttendanceWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val UI_PREFS = "attendance_ui_prefs"
private const val KEY_USE_SHORTHAND = "use_shorthand"
private const val TAG = "AttendanceUI"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AttendanceTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AttendanceSetupScreen()
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Reliable widget refresh
//
//  updateAll() only queues a broadcast to the launcher. We:
//    1. wait 400 ms so the EncryptedSharedPreferences commit is fully settled
//       and so we don't trip the launcher's ~1-update/sec rate limiter
//    2. enumerate widget IDs and force per-instance updates (this tears down
//       the existing Glance session so provideGlance() re-reads prefs)
//    3. swallow-and-log errors so a single bad ID doesn't kill the loop
// ─────────────────────────────────────────────────────────────────────────────
suspend fun refreshAttendanceWidgets(context: Context) {
    delay(400L)
    try {
        // CRITICAL FIX: Always use applicationContext for Glance operations
        // to avoid the "empty IDs" bug when called from an Activity context.
        val appContext = context.applicationContext
        val manager = GlanceAppWidgetManager(appContext)
        val ids = manager.getGlanceIds(AttendanceWidget::class.java)

        Log.d(TAG, "refreshAttendanceWidgets: Found ${ids.size} widget(s)")

        if (ids.isEmpty()) {
            Log.w(TAG, "No widget IDs found. The widget might not be placed, or this is a Glance context bug.")
            return
        }

        ids.forEach { id ->
            runCatching {
                // Pass appContext here as well for consistency
                AttendanceWidget().update(appContext, id)
                Log.d(TAG, "Successfully updated widget $id")
            }.onFailure { e ->
                // Changed to Log.e so it stands out in Logcat if it fails
                Log.e(TAG, "Widget update failed for $id", e)
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "refreshAttendanceWidgets failed completely", e)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Root screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AttendanceSetupScreen() {
    val context = LocalContext.current
    val prefs = remember { AttendancePrefs(context) }
    val uiPrefs = remember {
        context.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE)
    }
    val scope = rememberCoroutineScope()

    var username by remember { mutableStateOf(prefs.getUsername() ?: "") }
    var password by remember { mutableStateOf(prefs.getPassword() ?: "") }
    var status by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    var result by remember { mutableStateOf(prefs.getLastResult()) }
    var nameOverrides by remember { mutableStateOf(prefs.getSubjectNames()) }
    var useCustomNames by remember { mutableStateOf(prefs.getUseCustomNames()) }
    var useShorthand by remember {
        mutableStateOf(uiPrefs.getBoolean(KEY_USE_SHORTHAND, false))
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
    ) {
        // ── Header ────────────────────────────────────────────────────────────
        item {
            Text(
                text = "LBSCEK Attendance",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Your Etlab credentials are stored encrypted, only on this " +
                        "device, and are only ever sent to lbscek.etlab.app. This widget " +
                        "always shows your current semester's attendance automatically.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ── Credentials card ──────────────────────────────────────────────────
        item {
            CredentialsCard(
                username = username,
                onUsernameChange = { username = it },
                password = password,
                onPasswordChange = { password = it },
                loading = loading,
                onSave = {
                    loading = true
                    status = null
                    scope.launch {
                        try {
                            val repo = EtlabRepository()
                            val fetched: AttendanceResult =
                                repo.fetchAttendance(username.trim(), password).attendance

                            withContext(Dispatchers.IO) {
                                prefs.saveCredentials(username.trim(), password)
                                prefs.saveLastResult(fetched)
                            }

                            refreshAttendanceWidgets(context)
                            AttendanceWorker.schedulePeriodic(context)

                            result = fetched
                            status = "Success — overall attendance is %.1f%%. ".format(
                                fetched.overallPercent
                            ) + "Now add the widget from your home screen's widget picker."
                        } catch (e: InvalidCredentialsException) {
                            status = "Invalid username or password."
                        } catch (e: Exception) {
                            status = "Couldn't fetch attendance: ${e.message}"
                        } finally {
                            loading = false
                        }
                    }
                },
            )
        }

        // ── Status banner ─────────────────────────────────────────────────────
        status?.let { msg ->
            item {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = msg,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }

        // ── Subject overrides ─────────────────────────────────────────────────
        val currentResult = result
        if (currentResult != null && currentResult.subjects.isNotEmpty()) {

            item {
                Divider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                Text(
                    text = "Subject names",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Etlab already gave us the real names below. Turn on " +
                            "custom names if you want to shorten or rename one on " +
                            "the widget.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))

                ToggleRow(
                    label = "Use my custom names",
                    checked = useCustomNames,
                    onCheckedChange = { checked ->
                        useCustomNames = checked
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                prefs.saveUseCustomNames(checked)
                            }
                            refreshAttendanceWidgets(context)
                        }
                    },
                )

                Spacer(Modifier.height(8.dp))

                ToggleRow(
                    label = "Use shorthand version",
                    checked = useShorthand,
                    enabled = useCustomNames,
                    onCheckedChange = { checked ->
                        useShorthand = checked
                        uiPrefs.edit()
                            .putBoolean(KEY_USE_SHORTHAND, checked)
                            .commit()

                        val updated = nameOverrides.toMutableMap()
                        currentResult.subjects.forEach { subject ->
                            val original = subject.name.ifBlank { subject.code }
                            val shorthand = toShorthand(original)
                            val existing = updated[subject.code]

                            if (checked) {
                                if (existing.isNullOrBlank()) {
                                    updated[subject.code] = shorthand
                                }
                            } else {
                                if (existing == shorthand) {
                                    updated.remove(subject.code)
                                }
                            }
                        }
                        nameOverrides = updated

                        scope.launch {
                            withContext(Dispatchers.IO) {
                                prefs.saveSubjectNames(updated)
                            }
                            refreshAttendanceWidgets(context)
                        }
                    },
                )

                Spacer(Modifier.height(16.dp))
            }

            items(currentResult.subjects) { subject ->
                val original = subject.name.ifBlank { subject.code }
                val override = nameOverrides[subject.code].orEmpty()

                SubjectOverrideItem(
                    subjectCode = subject.code,
                    originalName = original,
                    overrideValue = override,
                    enabled = useCustomNames,
                    onValueChange = { input ->
                        nameOverrides = nameOverrides.toMutableMap()
                            .apply { put(subject.code, input) }
                    },
                )
            }

            item {
                Button(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                prefs.saveSubjectNames(nameOverrides)
                            }
                            refreshAttendanceWidgets(context)
                        }
                    },
                    enabled = useCustomNames,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Text(
                        text = "Save name overrides",
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
        }

        // ── About ─────────────────────────────────────────────────────────────
        item {
            Spacer(Modifier.height(8.dp))
            AboutCard()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Toggle row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Credentials card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CredentialsCard(
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    loading: Boolean,
    onSave: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Etlab credentials",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = username,
                onValueChange = onUsernameChange,
                label = { Text("Etlab username") },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text("Etlab password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))

            Button(
                onClick = onSave,
                enabled = !loading && username.isNotBlank() && password.isNotBlank(),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text(
                    text = if (loading) "Checking…" else "Save & fetch attendance",
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Subject override item
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SubjectOverrideItem(
    subjectCode: String,
    originalName: String,
    overrideValue: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val hasOverride = overrideValue.isNotBlank()

    val displayValue: String = when {
        !enabled -> originalName
        hasOverride -> overrideValue
        focused -> ""
        else -> originalName
    }

    val container = if (enabled) {
        MaterialTheme.colorScheme.surfaceContainerLow
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f)
    }

    val nameColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    val codeColor = if (enabled) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.outlinedCardColors(containerColor = container),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = originalName,
                style = MaterialTheme.typography.titleMedium,
                color = nameColor,
            )
            Text(
                text = subjectCode,
                style = MaterialTheme.typography.bodySmall,
                color = codeColor,
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = displayValue,
                onValueChange = onValueChange,
                enabled = enabled,
                label = { Text("Custom display name") },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focused = it.isFocused },
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  About card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AboutCard() {
    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0L),
                ).versionName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager
                    .getPackageInfo(context.packageName, 0)
                    .versionName
            }
        }.getOrNull() ?: "1.0.0"
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "About",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(12.dp))

            AboutRow("Version", versionName)
            AboutRow("Developer", "LBSCEK Attendance")
            AboutRow("Endpoint", "lbscek.etlab.app")
            AboutRow("Data policy", "On-device only")

            Spacer(Modifier.height(16.dp))
            Divider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(16.dp))

            Text(
                text = "This widget fetches your current semester's attendance " +
                        "automatically. Credentials are encrypted at rest and are " +
                        "only ever transmitted to lbscek.etlab.app. Subject names " +
                        "come straight from Etlab — override them only if you want " +
                        "shorter labels on the widget.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Shorthand generator
// ─────────────────────────────────────────────────────────────────────────────

private val SHORTHAND_STOPWORDS = setOf(
    "and", "of", "the", "for", "in", "to", "a", "an", "&",
)

private val LAB_REGEX = Regex("(?i)\\b(lab|laboratory)\\b")

internal fun toShorthand(rawName: String): String {
    if (rawName.isBlank()) return ""

    val isLab = LAB_REGEX.containsMatchIn(rawName)
    val cleaned = LAB_REGEX.replace(rawName, " ").trim()

    val words = cleaned
        .split(Regex("\\s+"))
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .filterIndexed { index, word ->
            index == 0 || word.lowercase() !in SHORTHAND_STOPWORDS
        }

    if (words.isEmpty()) return rawName.uppercase()

    val acronym: String = if (words.size == 1) {
        words.first().take(3).uppercase()
    } else {
        words.mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("")
            .take(5)
    }

    return if (isLab) "$acronym LAB" else acronym
}