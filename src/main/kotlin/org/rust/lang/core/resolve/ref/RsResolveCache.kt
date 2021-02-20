/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

@file:Suppress("DEPRECATION")

package org.rust.lang.core.resolve.ref

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.RecursionManager
import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveResult
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider.Result
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.containers.ConcurrentWeakKeySoftValueHashMap
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.messages.MessageBus
import org.rust.lang.core.psi.RUST_STRUCTURE_CHANGE_TOPIC
import org.rust.lang.core.psi.RustStructureChangeListener
import org.rust.lang.core.psi.ext.findModificationTrackerOwner
import org.rust.lang.core.psi.rustStructureModificationTracker
import org.rust.openapiext.Testmark
import java.lang.ref.ReferenceQueue
import java.util.concurrent.ConcurrentMap

/**
 * The implementation is inspired by Intellij platform's [com.intellij.psi.impl.source.resolve.ResolveCache].
 * The main difference from the platform one: we invalidate the cache after rust structure change, when
 * platform cache invalidates on any PSI change.
 * See [org.rust.lang.core.psi.RsPsiManager.rustStructureModificationTracker].
 *
 * Use with caution: You should ensure that your resolve depends on rust structure only.
 * I.e. you can't use this cache for local variable references
 */
class RsResolveCache(messageBus: MessageBus) {
    private val cache: ConcurrentMap<PsiElement, Any?> = createWeakMap()
    private val guard = RecursionManager.createGuard("RsResolveCache")

    init {
        messageBus.connect().subscribe(RUST_STRUCTURE_CHANGE_TOPIC, object : RustStructureChangeListener {
            override fun rustStructureChanged() = onRustStructureChanged()
        })
    }

    /**
     * Retrieve a cached value by [key] or compute a new value by [resolver].
     * Internally recursion-guarded by [key].
     *
     * Expected resolve results: [PsiElement], [ResolveResult] or a [List]/[Array] of [ResolveResult]
     */
    @Suppress("UNCHECKED_CAST")
    fun <K : PsiElement, V> resolveWithCaching(key: K, resolver: (K) -> V): V? {
        ProgressManager.checkCanceled()
        val map = getCacheFor(key)
        return map[key] as V? ?: run {
            val stamp = guard.markStack()
            val result = guard.doPreventingRecursion(key, true) { resolver(key) }
            ensureValidResult(result)

            if (stamp.mayCacheNow()) {
                cache(map, key, result)
            }
            result
        }
    }

    private fun getCacheFor(element: PsiElement): ConcurrentMap<PsiElement, Any?> {
        // 1. The global resolve cache invalidates only on rust structure changes.
        // 2. If some reference element is located inside RsModificationTrackerOwner,
        //    the global cache will not be invalidated on its change
        // 3. PSI uses default identity-based equals/hashCode
        // 4. Intellij does incremental updates of a mutable PSI tree.
        //
        // It means that some reference element may be changed and then should be
        // re-resolved to another target. But if we use the global cache, we will
        // retrieve its previous resolve result from the cache. So if the reference
        // element is located inside RsModificationTrackerOwner, we should use a
        // separate cache for it.
        val owner = element.findModificationTrackerOwner()
        return if (owner != null) {
            CachedValuesManager.getCachedValue(owner, LOCAL_CACHE_KEY) {
                Result.create(
                    createWeakMap(),
                    owner.project.rustStructureModificationTracker,
                    owner.modificationTracker
                )
            }
        } else {
            cache
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <K : PsiElement, V> cache(map: ConcurrentMap<PsiElement, Any?>, element: K, result: V?) {
        // optimization: less contention
        val cached = map[element] as V?
        if (cached !== null && cached === result) return
        map[element] = result ?: NULL_RESULT as V
    }

    private fun onRustStructureChanged() {
        Testmarks.cacheCleared.hit()
        cache.clear()
    }

    companion object {
        fun getInstance(project: Project): RsResolveCache =
            ServiceManager.getService(project, RsResolveCache::class.java)
    }

    object Testmarks {
        val cacheCleared = Testmark("cacheCleared")
    }
}

private fun <K, V> createWeakMap(): ConcurrentMap<K, V> {
    return object : ConcurrentWeakKeySoftValueHashMap<K, V>(
        100,
        0.75f,
        Runtime.getRuntime().availableProcessors(),
        ContainerUtil.canonicalStrategy()
    ) {
        override fun createValueReference(
            value: V,
            queue: ReferenceQueue<in V>
        ): ConcurrentWeakKeySoftValueHashMap.ValueReference<K, V> {
            val isTrivialValue = value === NULL_RESULT ||
                value is Array<*> && value.size == 0 ||
                value is List<*> && value.size == 0
            return if (isTrivialValue) {
                createStrongReference(value)
            } else {
                super.createValueReference(value, queue)
            }
        }

        @Suppress("UNCHECKED_CAST")
        override fun get(key: K): V? {
            val v = super.get(key)
            return if (v === NULL_RESULT) null else v
        }
    }
}

private class StrongValueReference<K, V>(
    private val value: V
) : ConcurrentWeakKeySoftValueHashMap.ValueReference<K, V> {
    override fun getKeyReference(): ConcurrentWeakKeySoftValueHashMap.KeyReference<K, V> {
        // will never GC so this method will never be called so no implementation is necessary
        throw UnsupportedOperationException()
    }

    override fun get(): V = value
}

@Suppress("UNCHECKED_CAST")
private fun <K, V> createStrongReference(value: V): StrongValueReference<K, V> {
    return when {
        value === NULL_RESULT -> NULL_VALUE_REFERENCE as StrongValueReference<K, V>
        value === ResolveResult.EMPTY_ARRAY -> EMPTY_RESOLVE_RESULT as StrongValueReference<K, V>
        value is List<*> && value.size == 0 -> EMPTY_LIST as StrongValueReference<K, V>
        else -> StrongValueReference(value)
    }
}

private val NULL_RESULT = Any()
private val NULL_VALUE_REFERENCE = StrongValueReference<Any, Any>(NULL_RESULT)
private val EMPTY_RESOLVE_RESULT = StrongValueReference<Any, Array<ResolveResult>>(ResolveResult.EMPTY_ARRAY)
private val EMPTY_LIST = StrongValueReference<Any, List<Any>>(emptyList())

private val LOCAL_CACHE_KEY: Key<CachedValue<ConcurrentMap<PsiElement, Any?>>> = Key.create("LOCAL_CACHE_KEY")

private fun ensureValidResult(result: Any?): Unit = when (result) {
    is ResolveResult -> ensureValidPsi(result)
    is Array<*> -> ensureValidResults(result)
    is List<*> -> ensureValidResults(result)
    is PsiElement -> PsiUtilCore.ensureValid(result)
    else -> Unit
}

private fun ensureValidResults(result: Array<*>) =
    result.forEach { ensureValidResult(it) }

private fun ensureValidResults(result: List<*>) =
    result.forEach { ensureValidResult(it) }

private fun ensureValidPsi(resolveResult: ResolveResult) {
    val element = resolveResult.element
    if (element != null) {
        PsiUtilCore.ensureValid(element)
    }
}
