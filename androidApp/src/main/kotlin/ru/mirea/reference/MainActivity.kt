package ru.mirea.reference

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.flow.first
import ru.mirea.reference.ui.AuthViewModel
import ru.mirea.reference.ui.DetailViewModel
import ru.mirea.reference.ui.LoginScreen
import ru.mirea.reference.ui.ReferenceTheme
import ru.mirea.reference.ui.RegisterScreen
import ru.mirea.reference.ui.SearchScreen
import ru.mirea.reference.ui.SearchViewModel
import ru.mirea.reference.ui.SettingsScreen
import ru.mirea.reference.ui.SettingsViewModel
import ru.mirea.reference.ui.TermDetailScreen
import ru.mirea.reference.ui.ViewModelFactory
import ru.mirea.reference.ui.savedStateHandle

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as ReferenceApplication).container
        setContent {
            val darkTheme by container.preferences.darkTheme.collectAsState(initial = false)
            val authorized by produceState<Boolean?>(initialValue = null) {
                value = container.authRepository.isAuthorized.first()
            }
            ReferenceTheme(darkTheme) {
                if (authorized == null) return@ReferenceTheme
                val navController = rememberNavController()
                NavHost(navController, startDestination = if (authorized == true) Routes.search else Routes.login) {
                    composable(Routes.login) {
                        val model: AuthViewModel = viewModel(factory = ViewModelFactory { AuthViewModel(container.authRepository) })
                        LoginScreen(
                            viewModel = model,
                            onLoggedIn = { navController.navigate(Routes.search) { popUpTo(Routes.login) { inclusive = true } } },
                            onRegister = { navController.navigate(Routes.register) },
                        )
                    }
                    composable(Routes.register) {
                        val model: AuthViewModel = viewModel(factory = ViewModelFactory { AuthViewModel(container.authRepository) })
                        RegisterScreen(
                            viewModel = model,
                            onRegistered = { navController.navigate(Routes.search) { popUpTo(Routes.login) { inclusive = true } } },
                            onBack = navController::popBackStack,
                        )
                    }
                    composable(Routes.search) {
                        val model: SearchViewModel = viewModel(
                            factory = ViewModelFactory { extras ->
                                SearchViewModel(extras.savedStateHandle(), container.termsRepository, container.historyRepository)
                            },
                        )
                        SearchScreen(
                            viewModel = model,
                            onOpenTerm = { navController.navigate("${Routes.detail}/$it") },
                            onSettings = { navController.navigate(Routes.settings) },
                        )
                    }
                    composable(
                        route = "${Routes.detail}/{id}",
                        arguments = listOf(navArgument("id") { type = NavType.LongType }),
                    ) { entry ->
                        val id = requireNotNull(entry.arguments?.getLong("id"))
                        val model: DetailViewModel = viewModel(
                            key = "detail-$id",
                            factory = ViewModelFactory { DetailViewModel(id, container.termsRepository) },
                        )
                        TermDetailScreen(model, navController::popBackStack)
                    }
                    composable(Routes.settings) {
                        val model: SettingsViewModel = viewModel(
                            factory = ViewModelFactory { SettingsViewModel(container.preferences, container.authRepository) },
                        )
                        SettingsScreen(
                            viewModel = model,
                            onBack = navController::popBackStack,
                            onLoggedOut = { navController.navigate(Routes.login) { popUpTo(0) } },
                        )
                    }
                }
            }
        }
    }
}

private object Routes {
    const val login = "login"
    const val register = "register"
    const val search = "search"
    const val detail = "detail"
    const val settings = "settings"
}
