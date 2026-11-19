package com.pocketdeck.launcher

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import com.pocketdeck.launcher.data.CoverSource
import com.pocketdeck.launcher.data.GameRepository
import com.pocketdeck.launcher.data.MetadataRepository
import com.pocketdeck.launcher.data.NetworkStatus
import com.pocketdeck.launcher.data.ScraperClient
import com.pocketdeck.launcher.data.UserStore
import com.pocketdeck.launcher.input.KeySound
import com.pocketdeck.launcher.lan.LanServerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okio.Path.Companion.toOkioPath

/** 手写依赖容器：不用 Hilt，少一次注解处理，冷启动更快。 */
class AppContainer(app: Application) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val userStore = UserStore(app)
    val scraperClient = ScraperClient(app)
    val repository = GameRepository(app, userStore, appScope)
    val covers = CoverSource(app, appScope, scraperClient)
    val metadata = MetadataRepository(app, appScope, scraperClient)
    val lanManager = LanServerManager(app, userStore, repository, covers, metadata, appScope)
}

class PocketDeckApp : Application(), SingletonImageLoader.Factory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        NetworkStatus.init(this)
        container = AppContainer(this)
        KeySound.warmup(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.22).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("covers").toOkioPath())
                    .maxSizeBytes(256L * 1024 * 1024)
                    .build()
            }
            .build()
}
