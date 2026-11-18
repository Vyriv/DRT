package dev.vy.drt.tracking;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DungeonEntrySignalsTest {
 @Test void completionFloorHeadingsCannotStartAnotherRun() {
  for(String floor:new String[]{"V","VII"}) {
   assertFalse(DungeonEntrySignals.isEntry("MASTER MODE CATACOMBS - FLOOR "+floor,true));
   assertFalse(DungeonEntrySignals.isEntry("THE CATACOMBS - FLOOR "+floor,true));
  }
  assertFalse(DungeonEntrySignals.isEntry("Completed Catacombs (M5)",true));
 }
 @Test void explicitEntryAndCountdownStillStartRuns() {
  assertTrue(DungeonEntrySignals.isEntry("Starting in 1 second.",true));
  assertTrue(DungeonEntrySignals.isEntry("Starting in 5 seconds.",true));
  assertTrue(DungeonEntrySignals.isEntry("You entered The Catacombs - Floor V!",false));
  assertFalse(DungeonEntrySignals.isEntry("Starting in 5 seconds.",false));
 }
}
