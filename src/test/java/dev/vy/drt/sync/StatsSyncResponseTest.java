package dev.vy.drt.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class StatsSyncResponseTest {
	@Test
	void parsesAcceptedRejectedDeletedAndRetractionAcknowledgements() {
		StatsSyncResponse response = StatsSyncResponse.parse("""
			{"success":true,"stored":2,"unchanged":1,"deleted":["gone-1"],
			 "rejected":[{"id":"bad-1","cause":"invalid_time"},{"id":null,"cause":"invalid_id"}],"retracted":1}
			""");
		assertEquals(Set.of("bad-1"), response.rejectedIds());
		assertEquals(Set.of("gone-1"), response.deletedIds());
		assertTrue(response.hasRetractionResult());
	}

	@Test
	void acceptsTheOlderSuccessfulShapeWithoutRetractions() {
		StatsSyncResponse response = StatsSyncResponse.parse("{\"success\":true,\"stored\":1,\"unchanged\":0,\"deleted\":[],\"rejected\":[]}");
		assertTrue(response.rejectedIds().isEmpty());
		assertTrue(response.deletedIds().isEmpty());
		assertFalse(response.hasRetractionResult());
	}

	@Test
	void rejectsMalformedOrUnsuccessfulAcknowledgements() {
		assertNull(StatsSyncResponse.parse("not json"));
		assertNull(StatsSyncResponse.parse("{\"success\":false}"));
		assertNull(StatsSyncResponse.parse("{\"success\":true,\"deleted\":{}}"));
	}
}
