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

/**
 * 极速二进制游戏库磁盘索引快照（Stale-While-Revalidate）。
 *
 * 冷启动时先读取本地二进制索引（万级游戏反序列化 < 30ms），立刻渲染首屏第一个标签页，
 * 随后后台静默核对真实磁盘目录并更新快照，彻底消除海量 ROM 冷启动白屏等待。
 */
class LibraryIndexCache(context: Context) {
    private val cacheFile = File(context.filesDir, "library_index_v1.bin")
    private val tmpFile = File(context.filesDir, "library_index_v1.bin.tmp")

    fun load(expectedRootsKey: String): Library? =
        loadInternal(expectedRootsKey)

    fun loadUnchecked(): Library? =
        loadInternal(expectedRootsKey = null)

    private fun loadInternal(expectedRootsKey: String?): Library? {
        if (!cacheFile.isFile) return null
        return runCatching {
            DataInputStream(BufferedInputStream(FileInputStream(cacheFile), 32 * 1024)).use { inp ->
                val magic = inp.readInt()
                val version = inp.readInt()
                if (magic != MAGIC || version != VERSION) return null
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
                    val description = inp.readNullableUtf()
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

                Library(
                    root = rootsKey,
                    platforms = platforms,
                    games = games,
                    rootCounts = rootCounts,
                    localMetadata = localMetadata,
                    dirSnapshots = dirSnapshots,
                )
            }
        }.getOrNull()
    }

    @Synchronized
    fun save(rootsKey: String, library: Library) {
        runCatching {
            DataOutputStream(BufferedOutputStream(FileOutputStream(tmpFile), 32 * 1024)).use { out ->
                out.writeInt(MAGIC)
                out.writeInt(VERSION)
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
                    out.writeNullableUtf(m.description)
                }

                out.writeInt(library.dirSnapshots.size)
                for ((dirPath, snap) in library.dirSnapshots) {
                    out.writeUTF(dirPath)
                    out.writeLong(snap.first)
                    out.writeInt(snap.second)
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

    companion object {
        private const val MAGIC = 0x50444958 // "PDIX"
        private const val VERSION = 2
    }
}
