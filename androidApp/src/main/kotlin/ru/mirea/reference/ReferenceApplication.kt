package ru.mirea.reference

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import ru.mirea.reference.data.ApiService
import ru.mirea.reference.data.AuthRepository
import ru.mirea.reference.data.HistoryRepository
import ru.mirea.reference.data.PreferencesStore
import ru.mirea.reference.data.TermsRepository

val Context.preferencesDataStore by preferencesDataStore(name = "reference_preferences")

class ReferenceApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(context: Context) {
    val preferences = PreferencesStore(context.preferencesDataStore)
    private val api = ApiService(BuildConfig.API_BASE_URL, preferences)
    val authRepository = AuthRepository(api, preferences)
    val termsRepository = TermsRepository(api)
    val historyRepository = HistoryRepository(preferences)
}

