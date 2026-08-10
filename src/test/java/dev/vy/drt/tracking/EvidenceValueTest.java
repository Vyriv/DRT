package dev.vy.drt.tracking;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.vy.drt.config.DungeonFloor;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class EvidenceValueTest {
	@Test
	void weakerEvidenceCannotOverwriteStrongerEvidence() {
		EvidenceValue<DungeonFloor> value = EvidenceValue.<DungeonFloor>empty()
			.update(DungeonFloor.M7, EvidenceStrength.CONFIRMED_SCOREBOARD, DetectionSource.CONFIRMED_SCOREBOARD, 1L, Instant.EPOCH)
			.value();

		EvidenceUpdate<DungeonFloor> update = value.update(
			DungeonFloor.K5,
			EvidenceStrength.FALLBACK_GUESS,
			DetectionSource.PLAYER_INVENTORY,
			2L,
			Instant.EPOCH.plusSeconds(1)
		);

		assertEquals(EvidenceDecision.REJECTED_WEAKER, update.decision());
		assertEquals(DungeonFloor.M7, update.value().value());
		assertEquals(1, update.value().conflicts().size());
	}

	@Test
	void equalStrengthContradictionCreatesConflictWithoutOverwrite() {
		EvidenceValue<DungeonFloor> value = EvidenceValue.<DungeonFloor>empty()
			.update(DungeonFloor.F7, EvidenceStrength.CONFIRMED_GUI_COMPONENT, DetectionSource.CONFIRMED_GUI_COMPONENT, 1L, Instant.EPOCH)
			.value();

		EvidenceUpdate<DungeonFloor> update = value.update(
			DungeonFloor.M7,
			EvidenceStrength.CONFIRMED_GUI_COMPONENT,
			DetectionSource.CONFIRMED_GUI_COMPONENT,
			2L,
			Instant.EPOCH.plusSeconds(1)
		);

		assertEquals(EvidenceDecision.CONFLICT, update.decision());
		assertEquals(DungeonFloor.F7, update.value().value());
		assertEquals(1, update.value().conflicts().size());
	}
}
