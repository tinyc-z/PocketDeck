package com.pocketdeck.launcher

import android.app.ActivityManager
import android.app.Application
import android.content.ComponentCallbacks2
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.size.Precision
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.concurrent.TimeUnit
import android.util.Log
import com.pocketdeck.launcher.data.CoverSource
import com.pocketdeck.launcher.data.GameRepository
import com.pocketdeck.launcher.data.HttpLogInterceptor
import com.pocketdeck.launcher.data.MetadataRepository
import com.pocketdeck.launcher.data.NetworkStatus
import com.pocketdeck.launcher.data.OfflineGameDb
import com.pocketdeck.launcher.data.ScraperClient
import com.pocketdeck.launcher.data.UserStore
import com.pocketdeck.launcher.input.KeySound
import com.pocketdeck.launcher.lan.LanServerManager
import com.pocketdeck.launcher.ui.components.trimCoverCardUiCaches
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okio.Path.Companion.toOkioPath

/** 防止 SupervisorJob 根协程内未捕获异常沿线程 UncaughtExceptionHandler 击穿进程。 */
internal fun safeCoroutineHandler(tag: String): CoroutineExceptionHandler =
    CoroutineExceptionHandler { _, throwable ->
        Log.w(tag, "Uncaught coroutine exception", throwable)
    }

/** 手写依赖容器：不用 Hilt，少一次注解处理，冷启动更快。 */
class AppContainer(app: Application) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + safeCoroutineHandler("PocketDeckApp"))
    val userStore = UserStore(app)
    val scraperClient = ScraperClient(app)
    val repository = GameRepository(app, userStore, appScope, scraperClient.database)
    val covers = CoverSource(app, appScope, scraperClient)
    val metadata = MetadataRepository(app, appScope, scraperClient)
    val lanManager = LanServerManager(app, userStore, repository, covers, metadata, appScope)

    init {
        covers.searchNameResolver = metadata::bestSearchNameFor
        repository.onGameMigrated = { oldId, newId ->
            covers.migrateGameId(oldId, newId)
            metadata.migrateGameId(oldId, newId)
            scraperClient.migrateGameId(oldId, newId)
        }
        repository.onGameRemoved = { gameId ->
            covers.forget(gameId)
            metadata.forgetGame(gameId)
            scraperClient.forgetGame(gameId)
        }
        repository.onPathPrefixMigrated = { oldDir, newDir ->
            covers.migratePathPrefix(oldDir, newDir)
            metadata.migratePathPrefix(oldDir, newDir)
            scraperClient.migratePathPrefix(oldDir, newDir)
        }
        repository.onPathPrefixRemoved = { dirPath ->
            covers.forgetPathPrefix(dirPath)
            metadata.forgetPathPrefix(dirPath)
            scraperClient.forgetPathPrefix(dirPath)
        }
        appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            kotlinx.coroutines.delay(5_000L)
            scraperClient.database.pruneExpired()
        }
    }
}

class PocketDeckApp : Application(), SingletonImageLoader.Factory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        NetworkStatus.init(this)
        com.pocketdeck.launcher.data.CjkAliases.init(this)
        container = AppContainer(this)
        KeySound.warmup(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val am = getSystemService(ACTIVITY_SERVICE) as? ActivityManager
        val memPercent = if (am?.isLowRamDevice == true) 0.14 else 0.20
        val coilHttp = if (::container.isInitialized) {
            container.scraperClient.newCoilHttpClient()
        } else {
            OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(12, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .callTimeout(20, TimeUnit.SECONDS)
                .apply {
                    if (BuildConfig.DEBUG) addInterceptor(HttpLogInterceptor("coil"))
                }
                .addNetworkInterceptor { chain ->
                    val resp = chain.proceed(chain.request())
                    val len = resp.body?.contentLength() ?: -1L
                    if (len > 8L * 1024 * 1024) {
                        resp.close()
                        throw IOException("Image exceeds 8MB limit ($len bytes)")
                    }
                    resp
                }
                .build()
        }
        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { coilHttp }))
            }
            .precision(Precision.INEXACT)
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, memPercent).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("covers").toOkioPath())
                    .maxSizeBytes(256L * 1024 * 1024)
                    .build()
            }
            .build()
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            runCatching { SingletonImageLoader.get(this).memoryCache?.clear() }
            trimCoverCardUiCaches()
            // 退后台（如启动重型模拟器，UI_HIDDEN=20）或严重内存紧张（RUNNING_CRITICAL=15）时全量释放平台表；
            // 前台轻度吃紧时仅保留当前焦点附近的活跃平台。
            val aggressive = level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL
            val keep = if (aggressive) emptySet() else OfflineGameDb.activeSystems
            OfflineGameDb.trimMemory(keep)
            if (::container.isInitialized) {
                container.covers.trimMemory(keep)
                container.metadata.trimMemory(keep)
                container.scraperClient.trimMemory(keep, aggressive = aggressive)
            }
        }
    }
}
