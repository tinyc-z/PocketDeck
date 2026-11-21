package com.pocketdeck.launcher.data

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

/**
 * 轻量级本地偏好存储（替代 androidx.datastore.preferences + protobuf-javalite）：
 * - 基于 [AtomicFile] + UTF-8 JSON 持久化，写操作原子落盘防损坏；
 * - 全部磁盘 I/O 在 [Dispatchers.IO] + [Mutex] 下串行执行，不阻塞主线程；
 * - 首次启动时若新文件不存在且检测到旧版 DataStore `datastore/user.preferences_pb`，
 *   自动通过内置精简 Protobuf wire-format 解析器无损迁移全部 Boolean / String / Set<String> 键值。
 */
internal class PrefsKey<T : Any>(val name: String)

internal fun booleanPrefsKey(name: String) = PrefsKey<Boolean>(name)
internal fun stringPrefsKey(name: String) = PrefsKey<String>(name)
internal fun stringSetPrefsKey(name: String) = PrefsKey<Set<String>>(name)
internal fun stringMapPrefsKey(name: String) = PrefsKey<Map<String, String>>(name)

internal class PrefsSnapshot(private val values: Map<String, Any>) {
    @Suppress("UNCHECKED_CAST")
    operator fun <T : Any> get(key: PrefsKey<T>): T? = values[key.name] as? T
}

internal class MutablePrefs(private val values: LinkedHashMap<String, Any>) {
    @Suppress("UNCHECKED_CAST")
    operator fun <T : Any> get(key: PrefsKey<T>): T? = values[key.name] as? T

    operator fun <T : Any> set(key: PrefsKey<T>, value: T) {
        values[key.name] = value
    }

    fun remove(key: PrefsKey<*>) {
        values.remove(key.name)
    }

    internal fun toSnapshot(): PrefsSnapshot = PrefsSnapshot(HashMap(values))
    internal fun rawMap(): Map<String, Any> = values
}

internal class LocalPrefsStore(
    private val targetFile: File,
    private val legacyPbFile: File? = null,
) {
    private val mutex = Mutex()
    private val state = MutableStateFlow<PrefsSnapshot?>(null)

    val data: Flow<PrefsSnapshot> = flow {
        if (state.value == null) {
            ensureLoaded()
        }
        emitAll(state.filterNotNull())
    }

    suspend fun edit(transform: (MutablePrefs) -> Unit) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val current = loadLocked()
                val mutable = MutablePrefs(LinkedHashMap(current))
                transform(mutable)
                val updated = mutable.rawMap()
                writeJsonAtomic(targetFile, updated)
                state.value = PrefsSnapshot(HashMap(updated))
            }
        }
    }

    private suspend fun ensureLoaded() {
        if (state.value != null) return
        withContext(Dispatchers.IO) {
            mutex.withLock {
                if (state.value == null) {
                    val loaded = loadLocked()
                    state.value = PrefsSnapshot(HashMap(loaded))
                }
            }
        }
    }

    private fun loadLocked(): Map<String, Any> {
        cachedMap?.let { return it }
        val legacy = legacyPbFile
        val map: Map<String, Any> = when {
            hasAtomicFile(targetFile) -> readJsonAtomic(targetFile)
            legacy != null && legacy.isFile && legacy.length() > 0L -> {
                val migrated = runCatching {
                    LegacyDataStorePbReader.decode(legacy.readBytes())
                }.getOrDefault(emptyMap())
                if (migrated.isNotEmpty()) {
                    runCatching { writeJsonAtomic(targetFile, migrated) }
                }
                migrated
            }
            else -> emptyMap()
        }
        cachedMap = map
        return map
    }

    @Volatile
    private var cachedMap: Map<String, Any>? = null

    private fun hasAtomicFile(file: File): Boolean =
        file.isFile || File(file.path + ".bak").isFile

    private fun readJsonAtomic(file: File): Map<String, Any> = runCatching {
        val bytes = AtomicFile(file).readFully()
        decodeJson(String(bytes, StandardCharsets.UTF_8))
    }.getOrDefault(emptyMap())

    private fun writeJsonAtomic(file: File, map: Map<String, Any>) {
        file.parentFile?.mkdirs()
        val atomic = AtomicFile(file)
        val out = atomic.startWrite()
        try {
            out.write(encodeJson(map).toByteArray(StandardCharsets.UTF_8))
            atomic.finishWrite(out)
            cachedMap = HashMap(map)
        } catch (t: Throwable) {
            atomic.failWrite(out)
            throw t
        }
    }

    companion object {
        internal const val SCHEMA_VERSION = 1
        private val instances = ConcurrentHashMap<String, LocalPrefsStore>()

        fun forUserPrefs(context: Context): LocalPrefsStore {
            val filesDir = context.applicationContext.filesDir
            val target = File(filesDir, "user_prefs.json")
            val legacy = File(filesDir, "datastore/user.preferences_pb")
            return instances.computeIfAbsent(target.absolutePath) {
                LocalPrefsStore(targetFile = target, legacyPbFile = legacy)
            }
        }

        internal fun encodeJson(map: Map<String, Any>): String {
            val booleans = JSONObject()
            val strings = JSONObject()
            val stringSets = JSONObject()
            val stringMaps = JSONObject()
            for ((k, v) in map) {
                when (v) {
                    is Boolean -> booleans.put(k, v)
                    is String -> strings.put(k, v)
                    is Set<*> -> {
                        val arr = JSONArray()
                        for (item in v) {
                            if (item is String) arr.put(item)
                        }
                        stringSets.put(k, arr)
                    }
                    is Map<*, *> -> {
                        val obj = JSONObject()
                        for ((mk, mv) in v) {
                            if (mk is String && mv is String) obj.put(mk, mv)
                        }
                        stringMaps.put(k, obj)
                    }
                }
            }
            return JSONObject()
                .put("v", SCHEMA_VERSION)
                .put("b", booleans)
                .put("s", strings)
                .put("ss", stringSets)
                .put("m", stringMaps)
                .toString()
        }

        internal fun decodeJson(text: String): Map<String, Any> {
            if (text.isBlank()) return emptyMap()
            val root = JSONObject(text)
            val out = LinkedHashMap<String, Any>()
            root.optJSONObject("b")?.let { obj ->
                val it = obj.keys()
                while (it.hasNext()) {
                    val k = it.next()
                    out[k] = obj.getBoolean(k)
                }
            }
            root.optJSONObject("s")?.let { obj ->
                val it = obj.keys()
                while (it.hasNext()) {
                    val k = it.next()
                    out[k] = obj.getString(k)
                }
            }
            root.optJSONObject("ss")?.let { obj ->
                val it = obj.keys()
                while (it.hasNext()) {
                    val k = it.next()
                    val arr = obj.optJSONArray(k) ?: continue
                    val set = LinkedHashSet<String>(arr.length() * 2)
                    for (i in 0 until arr.length()) {
                        set.add(arr.getString(i))
                    }
                    out[k] = set
                }
            }
            root.optJSONObject("m")?.let { obj ->
                val it = obj.keys()
                while (it.hasNext()) {
                    val k = it.next()
                    val sub = obj.optJSONObject(k) ?: continue
                    val m = LinkedHashMap<String, String>(sub.length() * 2)
                    val mkIt = sub.keys()
                    while (mkIt.hasNext()) {
                        val mk = mkIt.next()
                        m[mk] = sub.getString(mk)
                    }
                    out[k] = m
                }
            }
            return out
        }
    }
}

/**
 * 极简零依赖 Protobuf wire-format 读取器，仅用于一次性无损迁移旧版
 * `androidx.datastore.preferences` 生成的 `user.preferences_pb` 文件。
 *
 * Schema (`PreferencesProto`):
 * - `PreferenceMap`: repeated field 1 (wire 2) = `Entry`
 * - `Entry`: field 1 (wire 2) = `string key`, field 2 (wire 2) = `Value`
 * - `Value`:
 *   - field 1 (wire 0): `bool`
 *   - field 2 (wire 5): `float`
 *   - field 3 (wire 0): `int32`
 *   - field 4 (wire 0): `int64`
 *   - field 5 (wire 2): `string`
 *   - field 6 (wire 2): `StringSet` (`repeated string strings = 1`)
 *   - field 7 (wire 1): `double`
 *   - field 8 (wire 2): `bytes`
 */
internal object LegacyDataStorePbReader {
    fun decode(bytes: ByteArray): Map<String, Any> {
        val out = LinkedHashMap<String, Any>()
        val r = WireReader(bytes, 0, bytes.size)
        while (!r.eof) {
            val tag = r.readVarint()
            val fieldNum = (tag ushr 3).toInt()
            val wireType = (tag and 0x7L).toInt()
            if (fieldNum == 1 && wireType == 2) {
                val len = r.readVarint().toInt()
                val end = r.pos + len
                if (len < 0 || end > r.limit) break
                val entry = readEntry(bytes, r.pos, end)
                if (entry != null) out[entry.first] = entry.second
                r.pos = end
            } else {
                r.skipField(wireType)
            }
        }
        return out
    }

    private fun readEntry(buf: ByteArray, start: Int, end: Int): Pair<String, Any>? {
        val r = WireReader(buf, start, end)
        var key: String? = null
        var value: Any? = null
        while (!r.eof) {
            val tag = r.readVarint()
            val fieldNum = (tag ushr 3).toInt()
            val wireType = (tag and 0x7L).toInt()
            when {
                fieldNum == 1 && wireType == 2 -> key = r.readString()
                fieldNum == 2 && wireType == 2 -> {
                    val len = r.readVarint().toInt()
                    val vEnd = r.pos + len
                    if (len < 0 || vEnd > r.limit) return null
                    value = readValue(buf, r.pos, vEnd)
                    r.pos = vEnd
                }
                else -> r.skipField(wireType)
            }
        }
        val k = key ?: return null
        val v = value ?: return null
        return k to v
    }

    private fun readValue(buf: ByteArray, start: Int, end: Int): Any? {
        val r = WireReader(buf, start, end)
        var result: Any? = null
        while (!r.eof) {
            val tag = r.readVarint()
            val fieldNum = (tag ushr 3).toInt()
            val wireType = (tag and 0x7L).toInt()
            when {
                fieldNum == 1 && wireType == 0 -> result = r.readVarint() != 0L
                fieldNum == 5 && wireType == 2 -> result = r.readString()
                fieldNum == 6 && wireType == 2 -> {
                    val len = r.readVarint().toInt()
                    val sEnd = r.pos + len
                    if (len < 0 || sEnd > r.limit) return null
                    result = readStringSet(buf, r.pos, sEnd)
                    r.pos = sEnd
                }
                else -> r.skipField(wireType)
            }
        }
        return result
    }

    private fun readStringSet(buf: ByteArray, start: Int, end: Int): Set<String> {
        val r = WireReader(buf, start, end)
        val set = LinkedHashSet<String>()
        while (!r.eof) {
            val tag = r.readVarint()
            val fieldNum = (tag ushr 3).toInt()
            val wireType = (tag and 0x7L).toInt()
            if (fieldNum == 1 && wireType == 2) {
                set.add(r.readString())
            } else {
                r.skipField(wireType)
            }
        }
        return set
    }

    private class WireReader(
        private val buf: ByteArray,
        var pos: Int,
        val limit: Int,
    ) {
        val eof: Boolean get() = pos >= limit

        fun readVarint(): Long {
            var shift = 0
            var result = 0L
            while (pos < limit) {
                val b = buf[pos++].toInt() and 0xFF
                result = result or ((b and 0x7F).toLong() shl shift)
                if ((b and 0x80) == 0) return result
                shift += 7
                if (shift >= 64) break
            }
            return result
        }

        fun readString(): String {
            val len = readVarint().toInt()
            if (len == 0) return ""
            if (len < 0 || pos + len > limit) {
                pos = limit
                return ""
            }
            val s = String(buf, pos, len, StandardCharsets.UTF_8)
            pos += len
            return s
        }

        fun skipField(wireType: Int) {
            when (wireType) {
                0 -> readVarint()
                1 -> pos = (pos + 8).coerceAtMost(limit)
                2 -> {
                    val len = readVarint().toInt()
                    pos = if (len < 0) limit else (pos + len).coerceAtMost(limit)
                }
                5 -> pos = (pos + 4).coerceAtMost(limit)
                else -> pos = limit
            }
        }
    }
}
