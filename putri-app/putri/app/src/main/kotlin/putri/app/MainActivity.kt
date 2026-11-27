package putri.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import putri.app.ui.*
import putri.app.ui.screens.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as PutriApp
        setContent { PutriTheme { Root(app) } }
    }
}

@Composable
private fun Root(app: PutriApp) {
    val ready by app.ready.collectAsState()
    if (!ready) {
        Box(Modifier.fillMaxSize().background(heroBrush), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.material3.Text("🌲", fontSize = 56.sp)
                androidx.compose.material3.Text("PassGo Banyumas", color = Color.White, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))
                CircularProgressIndicator(color = Color.White)
            }
        }
        return
    }

    val nav = rememberNavController()
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val env = remember { AppEnv(app.container, app.session, snack, scope, nav) }
    val s = app.session

    // rapikan pesanan kedaluwarsa & pastikan akun tersimpan masih ada
    LaunchedEffect(Unit) {
        app.container.orders.expireOverdue()
        val id = s.userId
        if (id != null && app.container.database.userDao().byId(id) == null) s.signOut()
    }

    val start = when {
        !s.onboarded -> "onboarding"
        s.loggedIn -> homeRoute(s.role)
        else -> "login"
    }
    val longArg = { name: String -> navArgument(name) { type = NavType.LongType } }

    CompositionLocalProvider(LocalApp provides env) {
        Scaffold(
            snackbarHost = { SnackbarHost(snack) },
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0)
        ) { pad ->
            NavHost(nav, startDestination = start, modifier = Modifier.padding(pad)) {
                composable("onboarding") { OnboardingScreen() }
                composable("login") { LoginScreen() }
                composable("register") { RegisterScreen() }
                composable("home") { HomeScreen() }
                composable("partner") { PartnerScreen() }
                composable("officer") { OfficerScreen() }
                composable("budget") { BudgetScreen() }
                composable("cart") { CartScreen() }
                composable("orders") { OrdersScreen() }
                composable("wallet") { WalletScreen() }
                composable("destination/{id}", listOf(longArg("id"))) { DestinationScreen(it.arguments!!.getLong("id")) }
                composable("checkout/{id}", listOf(longArg("id"))) { CheckoutScreen(it.arguments!!.getLong("id")) }
                composable("order/{id}", listOf(longArg("id"))) { OrderDetailScreen(it.arguments!!.getLong("id")) }
                composable("trip/{id}", listOf(longArg("id"))) { TripScreen(it.arguments!!.getLong("id")) }
                composable("expense/{tripId}/{expenseId}", listOf(longArg("tripId"), longArg("expenseId"))) {
                    ExpenseScreen(it.arguments!!.getLong("tripId"), it.arguments!!.getLong("expenseId"))
                }
            }
        }
    }
}
