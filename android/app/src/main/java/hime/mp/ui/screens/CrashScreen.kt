package hime.mp.ui.screens

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import hime.mp.MainActivity

@Composable
fun CrashScreen(report: String) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text("HimeMP crashed", style = MaterialTheme.typography.headlineSmall)
            Text(
                "The error was saved. You can copy it or restart the app.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Button(onClick = { copyReport(context, report) }) { Text("Copy report") }
                Button(
                    onClick = { restart(context) },
                    modifier = Modifier.padding(start = 8.dp),
                ) { Text("Restart") }
                Button(
                    onClick = { finish(context) },
                    modifier = Modifier.padding(start = 8.dp),
                ) { Text("Close") }
            }
            Text(
                report,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 16.dp)
                    .verticalScroll(rememberScrollState()),
            )
        }
    }
}

private fun copyReport(context: Context, report: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("HimeMP crash", report))
}

private fun restart(context: Context) {
    val intent = Intent(context, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
    context.startActivity(intent)
    finish(context)
}

private fun finish(context: Context) {
    (context as? android.app.Activity)?.finish()
}
