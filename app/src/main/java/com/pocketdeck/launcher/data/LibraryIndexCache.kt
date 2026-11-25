package com.pocketdeck.launcher.data

import android.content.Context
import com.pocketdeck.launcher.data.model.Game
import com.pocketdeck.launcher.data.model.GameMetadata
import com.pocketdeck.launcher.data.model.Library
import com.pocketdeck.launcher.data.model.Platform
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/** 持久化在索引快照中的已安装 Android 应用摘要，用于冷启动首屏免 PackageManager Binder IPC。 */
data class CachedAndroidApp(
    val packageName: String,
    val label: String,
    val isAutoGame: Boolean,
    val apkSize: Long,
    val lastModifiedMs: Long,
)

data class CachedLibrarySnapshot(
    val library: Library,
    val installedApps: List<CachedAndroidApp>? = null,
)

/**
 * 极速二进制游戏库磁盘索引快照（Stale-While-Revalidate）。
 *
 * 冷启动时先读取本地二进制索引（万级游戏反序列化 < 30ms），立刻渲染首屏第一个标签页，
 * 随后后台静默核对真实磁盘目录与已安装应用列表，仅在发生变更时更新快照。
 */
class LibraryIndexCache(context: Context) {
    private val cacheFile = File(context.filesDir, "library_index_v1.bin")
    private val tmpFile = File(context.filesDir, "library_index_v1.bin.tmp")

    fun load(expectedRootsKey: String): Library? =
        loadSnapshotInternal(expectedRootsKey)?.library

    fun loadUnchecked(): Library? =
        loadSnapshotInternal(expectedRootsKey = null)?.library

    fun loadUncheckedSnapshot(): CachedLibrarySnapshot? =
        loadSnapshotInternal(expectedRootsKey = null)

    private fun loadSnapshotInternal(expectedRootsKey: String?): CachedLibrarySnapshot? {
        if (!cacheFile.isFile) return null
        return runCatching {
            DataInputStream(BufferedInputStream(FileInputStream(cacheFile), 32 * 1024)).use { inp ->
                val magic = inp.readInt()
                val version = inp.readInt()
                if (magic != MAGIC || (version != VERSION_V2 && version != VERSION_V3 && version != VERSION_V4)) return null
                val rootsKey = inp.readUTF()
                if (expectedRootsKey != null && rootsKey != expectedRootsKey) return null

                val rootCountSize = inp.readInt().coerceAtLeast(0)
                val rootCounts = LinkedHashMap<String, Int>(rootCountSize * 2)
                repeat(rootCountSize) {
                    val path = inp.readUTF()
                    val count = inp.readInt()
                    rootCounts[path] = count
                }

                val platCount = inp.readInt().coerceAtLeast(0)
                val platforms = ArrayList<Platform>(platCount)
                repeat(platCount) {
                    val id = inp.readUTF()
                    val shortName = inp.readUTF()
                    val fullName = inp.readUTF()
                    val coverAspect = inp.readFloat()
                    val libretroSystem = inp.readNullableUtf()
                    val core = inp.readNullableUtf()
                    platforms += Platform(
                        id = id,
                        shortName = shortName,
                        fullName = fullName,
                        coverAspect = coverAspect,
                        libretroSystem = libretroSystem,
                        core = core,
                    )
                }

                val bucketCount = inp.readInt().coerceAtLeast(0)
                val games = HashMap<String, List<Game>>(bucketCount * 2)
                repeat(bucketCount) {
                    val pid = inp.readUTF()
                    val gCount = inp.readInt().coerceAtLeast(0)
                    val list = ArrayList<Game>(gCount)
                    repeat(gCount) {
                        val id = inp.readUTF()
                        val platformId = inp.readUTF()
                        val baseName = inp.readUTF()
                        val fileName = inp.readUTF()
                        val title = inp.readUTF()
                        val localCover = inp.readNullableUtf()
                        val customCover = inp.readNullableUtf()
                        val fileSize = inp.readLong()
                        val lastModifiedMs = inp.readLong()
                        val discCount = inp.readInt().coerceAtLeast(0)
                        val discFiles = if (discCount > 0) {
                            List(discCount) { inp.readUTF() }
                        } else {
                            emptyList()
                        }
                        list += Game(
                            id = id,
                            platformId = platformId,
                            baseName = baseName,
                            fileName = fileName,
                            title = title,
                            localCover = localCover,
                            customCover = customCover,
                            fileSize = fileSize,
                            lastModifiedMs = lastModifiedMs,
                            discFiles = discFiles,
                        )
                    }
                    games[pid] = list
                }

                val metaCount = inp.readInt().coerceAtLeast(0)
                val localMetadata = HashMap<String, GameMetadata>(metaCount * 2)
                repeat(metaCount) {
                    val gid = inp.readUTF()
                    val releaseDate = inp.readNullableUtf()
                    val genre = inp.readNullableUtf()
                    val developer = inp.readNullableUtf()
                    val publisher = inp.readNullableUtf()
                    val description = if (version >= VERSION_V3) {
                        inp.readNullableLongString()
                    } else {
                        inp.readNullableUtf()
                    }
                    localMetadata[gid] = GameMetadata(releaseDate, genre, developer, publisher, description)
                }

                val snapCount = inp.readInt().coerceAtLeast(0)
                val dirSnapshots = HashMap<String, Pair<Long, Int>>(snapCount * 2)
                repeat(snapCount) {
                    val dirPath = inp.readUTF()
                    val mod = inp.readLong()
                    val count = inp.readInt()
                    dirSnapshots[dirPath] = mod to count
                }

                val installedApps: List<CachedAndroidApp>? = if (version >= VERSION_V3 && inp.readBoolean()) {
                    val appCount = inp.readInt().coerceAtLeast(0)
                    val list = ArrayList<CachedAndroidApp>(appCount)
                    repeat(appCount) {
                        list += CachedAndroidApp(
                            packageName = inp.readUTF(),
                            label = inp.readUTF(),
                            isAutoGame = inp.readBoolean(),
                            apkSize = inp.readLong(),
                            lastModifiedMs = inp.readLong(),
                        )
                    }
                    list
                } else {
                    null
                }

                val localCovers: Map<String, List<String>> = if (version >= VERSION_V4) {
                    val lcCount = inp.readInt().coerceAtLeast(0)
                    val lcMap = HashMap<String, List<String>>(lcCount * 2)
                    repeat(lcCount) {
                        val gid = inp.readUTF()
                        val pathCount = inp.readInt().coerceAtLeast(0)
                        val paths = List(pathCount) { inp.readUTF() }
                        if (paths.isNotEmpty()) lcMap[gid] = paths
                    }
                    lcMap
                } else {
                    emptyMap()
                }

                CachedLibrarySnapshot(
                    library = Library(
                        root = rootsKey,
                        platforms = platforms,
                        games = games,
                        rootCounts = rootCounts,
                        localMetadata = localMetadata,
                        dirSnapshots = dirSnapshots,
                        localCovers = localCovers,
                    ),
                    installedApps = installedApps,
                )
            }
        }.getOrNull()
    }

    @Synchronized
    fun save(
        rootsKey: String,
        library: Library,
        installedApps: List<CachedAndroidApp>? = null,
    ) {
        runCatching {
            DataOutputStream(BufferedOutputStream(FileOutputStream(tmpFile), 32 * 1024)).use { out ->
                out.writeInt(MAGIC)
                out.writeInt(VERSION_V4)
                out.writeUTF(rootsKey)

                out.writeInt(library.rootCounts.size)
                for ((path, count) in library.rootCounts) {
                    out.writeUTF(path)
                    out.writeInt(count)
                }

                out.writeInt(library.platforms.size)
                for (p in library.platforms) {
                    out.writeUTF(p.id)
                    out.writeUTF(p.shortName)
                    out.writeUTF(p.fullName)
                    out.writeFloat(p.coverAspect)
                    out.writeNullableUtf(p.libretroSystem)
                    out.writeNullableUtf(p.core)
                }

                out.writeInt(library.games.size)
                for ((pid, list) in library.games) {
                    out.writeUTF(pid)
                    out.writeInt(list.size)
                    for (g in list) {
                        out.writeUTF(g.id)
                        out.writeUTF(g.platformId)
                        out.writeUTF(g.baseName)
                        out.writeUTF(g.fileName)
                        out.writeUTF(g.title)
                        out.writeNullableUtf(g.localCover)
                        out.writeNullableUtf(g.customCover)
                        out.writeLong(g.fileSize)
                        out.writeLong(g.lastModifiedMs)
                        out.writeInt(g.discFiles.size)
                        for (d in g.discFiles) {
                            out.writeUTF(d)
                        }
                    }
                }

                out.writeInt(library.localMetadata.size)
                for ((gid, m) in library.localMetadata) {
                    out.writeUTF(gid)
                    out.writeNullableUtf(m.releaseDate)
                    out.writeNullableUtf(m.genre)
                    out.writeNullableUtf(m.developer)
                    out.writeNullableUtf(m.publisher)
                    out.writeNullableLongString(m.description)
                }

                out.writeInt(library.dirSnapshots.size)
                for ((dirPath, snap) in library.dirSnapshots) {
                    out.writeUTF(dirPath)
                    out.writeLong(snap.first)
                    out.writeInt(snap.second)
                }

                if (installedApps != null) {
                    out.writeBoolean(true)
                    out.writeInt(installedApps.size)
                    for (app in installedApps) {
                        out.writeUTF(app.packageName)
                        out.writeUTF(app.label)
                        out.writeBoolean(app.isAutoGame)
                        out.writeLong(app.apkSize)
                        out.writeLong(app.lastModifiedMs)
                    }
                } else {
                    out.writeBoolean(false)
                }

                out.writeInt(library.localCovers.size)
                for ((gid, paths) in library.localCovers) {
                    out.writeUTF(gid)
                    out.writeInt(paths.size)
                    for (p in paths) {
                        out.writeUTF(p)
                    }
                }
            }
            if (!tmpFile.renameTo(cacheFile)) {
                tmpFile.copyTo(cacheFile, overwrite = true)
                tmpFile.delete()
            }
        }.onFailure {
            runCatching { tmpFile.delete() }
        }
    }

    private fun DataOutputStream.writeNullableUtf(value: String?) {
        if (value == null) {
            writeBoolean(false)
        } else {
            writeBoolean(true)
            writeUTF(value)
        }
    }

    private fun DataInputStream.readNullableUtf(): String? =
        if (readBoolean()) readUTF() else null

    /** 支持超 64KB 长文本（如 gamelist.xml 详尽简介），避免 DataOutputStream.writeUTF 抛出 UTFDataFormatException。 */
    private fun DataOutputStream.writeNullableLongString(value: String?) {
        if (value == null) {
            writeBoolean(false)
        } else {
            val bytes = value.toByteArray(Charsets.UTF_8)
            writeBoolean(true)
            writeInt(bytes.size)
            write(bytes)
        }
    }

    private fun DataInputStream.readNullableLongString(): String? {
        if (!readBoolean()) return null
        val len = readInt()
        if (len < 0 || len > MAX_LONG_STRING_BYTES) {
            throw java.io.IOException("Corrupted long string length: $len")
        }
        if (len == 0) return ""
        val buf = ByteArray(len)
        readFully(buf)
        return String(buf, Charsets.UTF_8)
    }

    companion object {
        private const val MAGIC = 0x50444958 // "PDIX"
        private const val VERSION_V2 = 2
        private const val VERSION_V3 = 3
        private const val VERSION_V4 = 4
        private const val MAX_LONG_STRING_BYTES = 16 * 1024 * 1024
    }
}
