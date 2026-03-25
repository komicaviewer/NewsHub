package tw.kevinzhang.newshub

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.core.util.Consumer
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import tw.kevinzhang.newshub.ui.bindAppScreen

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val navController = rememberNavController()
            bindAppScreen(navController = navController)
            bindThreadListener(navController)
        }
    }

    @Composable
    private fun bindThreadListener(navController: NavHostController) {
        DisposableEffect(Unit) {
            intent.dataString?.let { uri ->
                navController.navigate("thread/${uri.encode()}")
            }
            val listener = Consumer<Intent> {
                it.dataString?.let { uri ->
                    navController.navigate("thread/${uri.encode()}")
                }
            }
            addOnNewIntentListener(listener)
            onDispose { removeOnNewIntentListener(listener) }
        }
    }
}
