package dev.vy.drt.client.cosmetics;

import java.lang.ref.WeakReference;
import java.util.Arrays;

final class NameStylerIdentityCache<K, V> {
	private final int mask;
	private final Entry<K, V>[] primary;
	private final Entry<K, V>[] secondary;

	@SuppressWarnings("unchecked")
	NameStylerIdentityCache(int requestedSize) {
		int size = nextPowerOfTwo(Math.max(2, requestedSize));
		this.mask = size - 1;
		this.primary = (Entry<K, V>[]) new Entry<?, ?>[size];
		this.secondary = (Entry<K, V>[]) new Entry<?, ?>[size];
	}

	synchronized V get(K key) {
		int primary = primaryIndex(key);
		V primaryValue = valueFor(primary, key, this.primary);
		if (primaryValue != null) return primaryValue;

		int secondary = secondaryIndex(key);
		return valueFor(secondary, key, this.secondary);
	}

	synchronized void put(K key, V value) {
		int primary = primaryIndex(key);
		Entry<K, V> primaryEntry = this.primary[primary];
		if (primaryEntry == null || primaryEntry.key.get() == null || primaryEntry.key.get() == key) {
			this.primary[primary] = new Entry<>(key, value);
			return;
		}

		int secondary = secondaryIndex(key);
		this.secondary[secondary] = new Entry<>(key, value);
	}

	synchronized void clear() {
		Arrays.fill(primary, null);
		Arrays.fill(secondary, null);
	}

	private V valueFor(int index, K requestedKey, Entry<K, V>[] entries) {
		Entry<K, V> entry = entries[index];
		if (entry == null) return null;
		K cachedKey = entry.key.get();
		V cachedValue = entry.value.get();
		if (cachedKey == requestedKey && cachedValue != null) return cachedValue;
		if (cachedKey == null || cachedValue == null) entries[index] = null;
		return null;
	}

	private int primaryIndex(K key) {
		return System.identityHashCode(key) & mask;
	}

	private int secondaryIndex(K key) {
		int identityHash = System.identityHashCode(key);
		return ((identityHash * 31) + (identityHash >>> 4)) & mask;
	}

	private static int nextPowerOfTwo(int input) {
		int value = input - 1;
		value |= value >>> 1;
		value |= value >>> 2;
		value |= value >>> 4;
		value |= value >>> 8;
		value |= value >>> 16;
		return value + 1;
	}

	private static final class Entry<K, V> {
		private final WeakReference<K> key;
		private final WeakReference<V> value;

		private Entry(K key, V value) {
			this.key = new WeakReference<>(key);
			this.value = new WeakReference<>(value);
		}
	}
}
