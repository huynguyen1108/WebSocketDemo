package com.example.wschat

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.TableRows
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.core.security.TokenStore
import com.example.feature.auth.presentation.ui.LoginScreen
import com.example.feature.chat.presentation.ui.ChatScreen
import com.example.feature.order.presentation.ui.OrderScreen
import com.example.feature.stock.presentation.ui.StockScreen
import com.example.feature.video.presentation.ui.VideoScreen
import com.example.wschat.ui.theme.WSChatTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

private const val ROUTE_LOGIN = "login"
private const val ROUTE_STOCK = "stock"
private const val ROUTE_CHAT = "chat"
private const val ROUTE_ORDERS = "orders"
private const val ROUTE_ORDERS_WITH_SYMBOL = "orders?symbol={symbol}"
private const val ROUTE_VIDEO = "video"

private const val ACTION_OPEN_INCOMING_CALL = "com.example.wschat.OPEN_INCOMING_CALL"

private val bottomNavRoutes = setOf(ROUTE_STOCK, ROUTE_CHAT, ROUTE_ORDERS, ROUTE_VIDEO)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var tokenStore: TokenStore

    /** Set by onCreate / onNewIntent so Compose can react to "open incoming call". */
    private var pendingIncomingCallTrigger by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (intent.shouldOpenIncomingCall()) pendingIncomingCallTrigger++
        setContent {
            WSChatTheme {
                val navController = rememberNavController()
                val backStack by navController.currentBackStackEntryAsState()
                val currentRoute = backStack?.destination?.route
                val context = LocalContext.current

                val startDest = remember { if (tokenStore.isLoggedIn()) ROUTE_STOCK else ROUTE_LOGIN }

                // Whenever an "incoming call" intent fires, jump to the video screen.
                LaunchedEffect(pendingIncomingCallTrigger) {
                    if (pendingIncomingCallTrigger > 0 && tokenStore.isLoggedIn()) {
                        navController.navigateToVideo()
                    }
                }

                Scaffold(
                    bottomBar = {
                        if (currentRoute in bottomNavRoutes) {
                            NavigationBar {
                                NavigationBarItem(
                                    selected = currentRoute == ROUTE_STOCK,
                                    onClick = { navController.navigate(ROUTE_STOCK) { launchSingleTop = true } },
                                    icon = { Icon(Icons.Default.BarChart, null) },
                                    label = { Text("Thị trường") },
                                )
                                NavigationBarItem(
                                    selected = currentRoute == ROUTE_ORDERS || currentRoute?.startsWith("orders") == true,
                                    onClick = { navController.navigate(ROUTE_ORDERS) { launchSingleTop = true } },
                                    icon = { Icon(Icons.Default.Receipt, null) },
                                    label = { Text("Lệnh") },
                                )
                                NavigationBarItem(
                                    selected = currentRoute == ROUTE_CHAT,
                                    onClick = { navController.navigate(ROUTE_CHAT) { launchSingleTop = true } },
                                    icon = { Icon(Icons.Default.Chat, null) },
                                    label = { Text("Chat") },
                                )
                                NavigationBarItem(
                                    selected = currentRoute == ROUTE_VIDEO,
                                    onClick = { navController.navigate(ROUTE_VIDEO) { launchSingleTop = true } },
                                    icon = { Icon(Icons.Default.Videocam, null) },
                                    label = { Text("Video") },
                                )
                                NavigationBarItem(
                                    selected = false,
                                    onClick = { context.startActivity(Intent(context, StockXmlActivity::class.java)) },
                                    icon = { Icon(Icons.Default.TableRows, null) },
                                    label = { Text("XML") },
                                )
                            }
                        }
                    },
                ) { innerPadding ->
                    Surface(
                        modifier = Modifier.fillMaxSize().padding(innerPadding),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        NavHost(navController = navController, startDestination = startDest) {
                            composable(ROUTE_LOGIN) {
                                LoginScreen(
                                    onLoginSuccess = {
                                        navController.navigate(ROUTE_STOCK) {
                                            popUpTo(ROUTE_LOGIN) { inclusive = true }
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            composable(ROUTE_STOCK) {
                                StockScreen(
                                    modifier = Modifier.fillMaxSize(),
                                    onTradeClick = { symbol ->
                                        navController.navigate("orders?symbol=$symbol")
                                    },
                                    onLogout = {
                                        navController.navigate(ROUTE_LOGIN) {
                                            popUpTo(0) { inclusive = true }
                                        }
                                    },
                                )
                            }
                            composable(ROUTE_ORDERS) {
                                OrderScreen(modifier = Modifier.fillMaxSize())
                            }
                            composable(
                                route = ROUTE_ORDERS_WITH_SYMBOL,
                                arguments = listOf(navArgument("symbol") {
                                    type = NavType.StringType
                                    defaultValue = ""
                                }),
                            ) { backStackEntry ->
                                OrderScreen(
                                    prefilledSymbol = backStackEntry.arguments?.getString("symbol"),
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            composable(ROUTE_CHAT) {
                                ChatScreen(modifier = Modifier.fillMaxSize())
                            }
                            composable(ROUTE_VIDEO) {
                                VideoScreen(modifier = Modifier.fillMaxSize())
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.shouldOpenIncomingCall()) pendingIncomingCallTrigger++
    }

    private fun Intent.shouldOpenIncomingCall(): Boolean = action == ACTION_OPEN_INCOMING_CALL

    private fun NavHostController.navigateToVideo() {
        if (currentDestination?.route == ROUTE_VIDEO) return
        navigate(ROUTE_VIDEO) { launchSingleTop = true }
    }
}

private fun TokenStore.isLoggedIn(): Boolean = hasSession()
