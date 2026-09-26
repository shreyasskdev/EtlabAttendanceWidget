package `in`.lbscek.attendance.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.updateAll
import `in`.lbscek.attendance.data.AttendancePrefs
import `in`.lbscek.attendance.data.AttendanceResult
import `in`.lbscek.attendance.data.EtlabRepository
import `in`.lbscek.attendance.data.InvalidCredentialsException
import `in`.lbscek.attendance.widget.AttendanceWidget
import `in`.lbscek.attendance.work.AttendanceWorker
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    AttendanceSetupScreen()
                }
            }
        }
    }
}

@Composable
fun AttendanceSetupScreen() {
    val context = LocalContext.current
    val prefs = remember { AttendancePrefs(context) }
    val scope = rememberCoroutineScope()

    var username by remember { mutableStateOf(prefs.getUsername() ?: "") }
    var password by remember { mutableStateOf(prefs.getPassword() ?: "") }
    var status by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    var result by remember { mutableStateOf(prefs.getLastResult()) }
    var nameOverrides by remember { mutableStateOf(prefs.getSubjectNames()) }
    var useCustomNames by remember { mutableStateOf(prefs.getUseCustomNames()) }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        item {
            Text("LBSCEK Attendance Widget", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "Your Etlab credentials are stored encrypted, only on this device, " +
                        "and are only ever sent to lbscek.etlab.app. This always shows your " +
                        "current semester's attendance automatically.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Etlab username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Etlab password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(20.dp))

            Button(
                enabled = !loading && username.isNotBlank() && password.isNotBlank(),
                onClick = {
                    loading = true
                    status = null
                    scope.launch {
                        try {
                            val repo = EtlabRepository()
                            val fetchResult = repo.fetchAttendance(username.trim(), password)
                            val fetched: AttendanceResult = fetchResult.attendance

                            prefs.saveCredentials(username.trim(), password)
                            prefs.saveLastResult(fetched)
                            AttendanceWidget().updateAll(context)
                            AttendanceWorker.schedulePeriodic(context)
                            result = fetched
                            status = "Success — overall attendance is %.1f%%. ".format(fetched.overallPercent) +
                                    "Now add the widget from your home screen's widget picker."
                        } catch (e: InvalidCredentialsException) {
                            status = "Invalid username or password."
                        } catch (e: Exception) {
                            status = "Couldn't fetch attendance: ${e.message}"
                        } finally {
                            loading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (loading) "Checking..." else "Save & fetch attendance")
            }

            status?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        }

        val currentResult = result
        if (currentResult != null && currentResult.subjects.isNotEmpty()) {
            item {
                Spacer(Modifier.height(28.dp))
                Divider()
                Spacer(Modifier.height(16.dp))
                Text("Subject names", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Etlab already gave us the real names below. Only fill these in if " +
                            "you want to shorten or rename one on the widget.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(
                        "Use my custom names",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = useCustomNames,
                        onCheckedChange = { checked ->
                            useCustomNames = checked
                            prefs.saveUseCustomNames(checked)
                            scope.launch { AttendanceWidget().updateAll(context) }
                        }
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            items(currentResult.subjects) { subject ->
                OutlinedTextField(
                    value = nameOverrides[subject.code] ?: "",
                    onValueChange = { input ->
                        nameOverrides = nameOverrides.toMutableMap().apply { put(subject.code, input) }
                    },
                    label = { Text(subject.code) },
                    placeholder = { Text(subject.name.ifBlank { subject.code }) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )
            }

            item {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        prefs.saveSubjectNames(nameOverrides)
                        scope.launch { AttendanceWidget().updateAll(context) }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save name overrides")
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}