package dev.vy.drt.client.cosmetics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class NameStylerLruCacheTest {
	@Test
	void evictsTheLeastRecentlyUsedEntry() {
		NameStylerLruCache<String, Integer> cache = new NameStylerLruCache<>(2);
		cache.putCached("first", 1);
		cache.putCached("second", 2);
		assertEquals(1, cache.getCached("first"));
		cache.putCached("third", 3);
		assertNull(cache.getCached("second"));
		assertEquals(1, cache.getCached("first"));
		assertEquals(3, cache.getCached("third"));
	}
}
