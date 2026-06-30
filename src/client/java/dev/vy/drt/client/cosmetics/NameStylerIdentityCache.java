package dev.vy.drt.client.cosmetics;

import java.util.Arrays;

final class NameStylerIdentityCache<K, V> {
	private final int mask;
	private final Object[] primaryKeys;
	private final Object[] primaryValues;
	private final Object[] secondaryKeys;
	private final Object[] secondaryValues;

	NameStylerIdentityCache(int requestedSize) {
		int size = nextPowerOfTwo(Math.max(2, requestedSize));
		this.mask = size - 1;
		this.primaryKeys = new Object[size];
		this.primaryValues = new Object[size];
		this.secondaryKeys = new Object[size];
		this.secondaryValues = new Object[size];
	}

	@SuppressWarnings("unchecked")
	V get(K key) {
		int primary = primaryIndex(key);
		if (primaryKeys[primary] == key) {
			return (V) primaryValues[primary];
		}

		int secondary = secondaryIndex(key);
		if (secondaryKeys[secondary] == key) {
			return (V) secondaryValues[secondary];
		}
		return null;
	}

	void put(K key, V value) {
		int primary = primaryIndex(key);
		if (primaryKeys[primary] == key || primaryKeys[primary] == null) {
			primaryKeys[primary] = key;
			primaryValues[primary] = value;
			return;
		}

		int secondary = secondaryIndex(key);
		secondaryKeys[secondary] = key;
		secondaryValues[secondary] = value;
	}

	void clear() {
		Arrays.fill(primaryKeys, null);
		Arrays.fill(primaryValues, null);
		Arrays.fill(secondaryKeys, null);
		Arrays.fill(secondaryValues, null);
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
}
