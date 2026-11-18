package dev.vy.drt.api;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DungeonPlayerStatsTest {
	@Test
	void parsesKnownValuesAndPreservesMissingFloorValues() {
		var root = JsonParser.parseString("""
			{"success":true,"player":{"username":"Player","uuid":"abc"},
			 "profile":{"cuteName":"Apple"},"dungeons":{"catacombs":{"level":40,"fractionalLevel":40.5,"xp":123,"progress":0.5},
			 "classes":{},"averageClassLevel":30.25,"secretsFound":9001,"totalCompletions":12,
			 "floors":[{"id":"F7","completions":12,"bestScore":305,"fastestTimeMs":null}]},
			 "recentRuns":[],"cache":{"state":"HIT"}}
			""").getAsJsonObject();

		DungeonPlayerStats stats = DungeonPlayerStats.parse(root);
		assertEquals("Player", stats.username());
		assertEquals(40.5, stats.catacombs().fractionalLevel());
		assertEquals(12L, stats.floor("F7").completions());
		assertNull(stats.floor("F7").fastestTimeMs());
		assertEquals("HIT", stats.cacheState());
	}

	@Test
	void rejectsErrorResponses() {
		assertNull(DungeonPlayerStats.parse(JsonParser.parseString("{\"success\":false}").getAsJsonObject()));
	}
}
