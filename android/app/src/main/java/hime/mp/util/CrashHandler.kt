package hime.mp.util

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import hime.mp.MainActivity
import java.io.File
import java.io.StringWriter
import java.io.PrintWriter

object CrashHandler {
    private const val PREFS = "himemp_crash"
    private const val KEY_LAST = "last_crash_ms"
    private const val KEY_COUNT = "crash_count"
    private const val REPORT_FILE = "crash-report.txt"

    private lateinit var app: Context

    fun install(context: Context) {
        app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { save(buildReport(throwable, thread)) }
            runCatching { previous?.uncaughtException(thread, throwable) }
            Runtime.getRuntime().exit(2)
        }
    }

    fun reportFatal(message: String) {
        runCatching { save("Native fatal error\n\n$message") }
    }

    private fun save(report: String) {
        File(app.filesDir, REPORT_FILE).writeText(report)
        scheduleRestart()
    }

    private fun scheduleRestart() {
        val prefs: SharedPreferences = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val last = prefs.getLong(KEY_LAST, 0L)
        val count = if (now - last < 60_000L) prefs.getInt(KEY_COUNT, 0) + 1 else 1
        prefs.edit()
            .putLong(KEY_LAST, now)
            .putInt(KEY_COUNT, count)
            .apply()
        if (count > 2) return

        val intent = Intent(app, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        val pending = PendingIntent.getActivity(
            app,
            99,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        pending.send()
    }

    fun consume(): String? {
        val file = File(app.filesDir, REPORT_FILE)
        if (!file.exists()) return null
        val report = runCatching { file.readText() }.getOrNull()
        file.delete()
        return report
    }

    fun buildReport(throwable: Throwable, thread: Thread): String {
        val writer = StringWriter()
        throwable.printStackTrace(PrintWriter(writer))
        return buildString {
            append("HimeMP crash report\n")
            append("Time: ").append(java.util.Date()).append('\n')
            append("Thread: ").append(thread.name).append('\n')
            append("Device: ").append(android.os.Build.MODEL).append('\n')
            append("Android: ").append(android.os.Build.VERSION.RELEASE).append('\n')
            append('\n').append(writer.toString())
        }
    }
}
