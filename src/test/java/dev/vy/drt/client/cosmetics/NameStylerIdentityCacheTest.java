package dev.vy.drt.client.cosmetics;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.ref.WeakReference;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class NameStylerIdentityCacheTest {
	@Test
	void usesIdentityInsteadOfEquality() {
		NameStylerIdentityCache<String, Object> cache = new NameStylerIdentityCache<>(8);
		String key = new String("same");
		Object value = new Object();
		cache.put(key, value);
		assertSame(value, cache.get(key));
		assertNull(cache.get(new String("same")));
	}

	@Test
	void doesNotStronglyRetainKeysOrValues() {
		NameStylerIdentityCache<Object, Object> cache = new NameStylerIdentityCache<>(8);
		WeakReference<Object>[] references = addTemporaryEntry(cache);
		assertTrue(waitForCollection(references), "identity cache retained a key or value strongly");
	}

	@Test
	void clearRemovesEntries() {
		NameStylerIdentityCache<Object, Object> cache = new NameStylerIdentityCache<>(8);
		Object key = new Object();
		Object value = new Object();
		cache.put(key, value);
		cache.clear();
		assertNull(cache.get(key));
	}

	@SuppressWarnings("unchecked")
	private static WeakReference<Object>[] addTemporaryEntry(NameStylerIdentityCache<Object, Object> cache) {
		Object key = new Object();
		Object value = new Object();
		cache.put(key, value);
		return new WeakReference[]{new WeakReference<>(key), new WeakReference<>(value)};
	}

	private static boolean waitForCollection(WeakReference<Object>[] references) {
		long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
		while (System.nanoTime() < deadline) {
			System.gc();
			if (references[0].get() == null && references[1].get() == null) return true;
			byte[] pressure = new byte[64 * 1024];
			pressure[0] = 1;
			Thread.yield();
		}
		return false;
	}
}
