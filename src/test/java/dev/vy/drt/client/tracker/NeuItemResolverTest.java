package dev.vy.drt.client.tracker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class NeuItemResolverTest {
	@Test
	void knownDungeonShardsHaveAuthoritativeIdentityAndRarityColorOffline() {
		assertEquals("ATTRIBUTE_SHARD_BREEZE;1", NeuItemResolver.getAttributeShardInternalName("SHARD_WITHER_SPECTER"));
		assertEquals(0xFF5555FF, NeuItemResolver.getColor("SHARD_WITHER_SPECTER"));
		assertEquals(0xFF5555FF, NeuItemResolver.getColor("ATTRIBUTE_SHARD_BREEZE;1"));
		assertEquals(0xFFAA00AA, NeuItemResolver.getColor("SHARD_BONZO"));
	}
}
