package hime.mp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import hime.mp.discord.DiscordRpcManager
import hime.mp.ui.MainScreen
import hime.mp.ui.screens.CrashScreen
import hime.mp.ui.theme.HimeTheme
import hime.mp.util.CrashHandler

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val report = CrashHandler.consume()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        DiscordRpcManager.handleOAuthIntent(intent)
        setContent {
            HimeTheme {
                if (report != null) {
                    CrashScreen(report)
                } else {
                    MainScreen()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        DiscordRpcManager.handleOAuthIntent(intent)
    }
}
