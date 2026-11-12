package com.brianrobinson.fonds

import android.app.Application
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.brianrobinson.fonds.ai.ClaudeEngine
import com.brianrobinson.fonds.ai.Describer
import com.brianrobinson.fonds.ai.NanoEngine
import com.brianrobinson.fonds.ai.OcrEngine
import com.brianrobinson.fonds.ai.Pipeline
import com.brianrobinson.fonds.data.FondsDatabase
import com.brianrobinson.fonds.data.ImageStore
import com.brianrobinson.fonds.data.Repository
import com.brianrobinson.fonds.data.Settings
import com.brianrobinson.fonds.export.Exporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(app: Application) {
    /** For saves that must finish even if the screen that started them goes away. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val settings = Settings(app)
    val db = FondsDatabase.build(app)
    val images = ImageStore(app)
    val repo = Repository(db, images)
    val nano = NanoEngine()
    val claude = ClaudeEngine()
    val pipeline = Pipeline(repo, settings, OcrEngine(app), nano, claude)
    val describer = Describer(repo, settings, nano, claude)
    val exporter = Exporter(repo, settings)
}

class FondsApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.pipeline.start()
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = container.pipeline.setForeground(true)
            override fun onStop(owner: LifecycleOwner) = container.pipeline.setForeground(false)
        })
    }
}

val LocalContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }
