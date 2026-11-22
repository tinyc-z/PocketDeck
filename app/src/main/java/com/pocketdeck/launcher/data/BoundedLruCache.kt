package com.pocketdeck.launcher.data

/**
 * 线程安全、纯 JVM 可测的有界 LRU 缓存（基于访问序 `LinkedHashMap`）。
 *
 * 相比 `android.util.LruCache`，在 JVM 单元测试环境不依赖 Android Stub，
 * 且支持在 `onTrimMemory` / 淘汰时保护指定的活跃 key（如当前正在浏览的平台）。
 */
internal class BoundedLruCache<K : Any, V : Any>(
    val maxSize: Int,
) {
    init {
        require(maxSize > 0) { "maxSize must be positive: $maxSize" }
    }

    private val lock = Any()
    private val map = LinkedHashMap<K, V>(minOf(maxSize, 64), 0.75f, true)

    val size: Int
        get() = synchronized(lock) { map.size }

    operator fun get(key: K): V? = synchronized(lock) { map[key] }

    fun containsKey(key: K): Boolean = synchronized(lock) { map.containsKey(key) }

    operator fun set(key: K, value: V): V? = put(key, value, emptySet())

    fun put(key: K, value: V, protectedKeys: Set<K> = emptySet()): V? = synchronized(lock) {
        val prev = map.put(key, value)
        evictLocked(maxSize, protectedKeys, keepKey = key)
        prev
    }

    fun remove(key: K): V? = synchronized(lock) { map.remove(key) }

    fun clear() = synchronized(lock) { map.clear() }

    /** 将缓存收缩至最多 [targetSize] 条（优先淘汰最久未使用的非保护条目）。 */
    fun trimToSize(targetSize: Int, protectedKeys: Set<K> = emptySet()) = synchronized(lock) {
        val cap = targetSize.coerceAtLeast(0)
        if (cap == 0 && protectedKeys.isEmpty()) {
            map.clear()
            return@synchronized
        }
        evictLocked(cap, protectedKeys, keepKey = null)
    }

    /** 仅保留 [protectedKeys] 中的条目，释放其余全部条目。 */
    fun retainOnly(protectedKeys: Set<K>) = synchronized(lock) {
        if (protectedKeys.isEmpty()) {
            map.clear()
        } else {
            map.keys.retainAll(protectedKeys)
        }
    }

    fun keysSnapshot(): Set<K> = synchronized(lock) { map.keys.toSet() }

    private fun evictLocked(limit: Int, protectedKeys: Set<K>, keepKey: K?) {
        if (map.size <= limit) return
        val it = map.entries.iterator()
        while (map.size > limit && it.hasNext()) {
            val k = it.next().key
            if (k != keepKey && k !in protectedKeys) {
                it.remove()
            }
        }
    }
}
