package hime.mp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import hime.mp.ui.MainScreen
import hime.mp.ui.screens.CrashScreen
import hime.mp.ui.theme.HimeTheme
import hime.mp.util.CrashHandler

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val report = CrashHandler.consume()
        super.onCreate(savedInstanceState)
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
}
