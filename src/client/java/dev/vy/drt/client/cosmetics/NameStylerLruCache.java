package dev.vy.drt.client.cosmetics;

import java.util.LinkedHashMap;
import java.util.Map;

final class NameStylerLruCache<K, V> extends LinkedHashMap<K, V> {
	private final int maxEntries;

	NameStylerLruCache(int maxEntries) {
		super(maxEntries + 1, 0.75F, true);
		this.maxEntries = maxEntries;
	}

	synchronized V getCached(K key) {
		return super.get(key);
	}

	synchronized void putCached(K key, V value) {
		super.put(key, value);
	}

	synchronized void clearCache() {
		super.clear();
	}

	@Override
	protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
		return size() > maxEntries;
	}
}
