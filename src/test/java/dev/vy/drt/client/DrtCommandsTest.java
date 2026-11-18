package dev.vy.drt.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import dev.vy.drt.client.screen.DrtNavTab;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DrtCommandsTest {
	private final CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
	private final Recorder recorder = new Recorder();

	@BeforeEach
	void register() {
		dispatcher.register(DrtCommands.drt(recorder));
	}

	@Test
	void everyPlayerShortcutOpensItsTab() throws CommandSyntaxException {
		assertOpens("drt", DrtNavTab.OVERVIEW);
		assertOpens("drt runs", DrtNavTab.RUNS);
		assertOpens("drt loot", DrtNavTab.LOOT);
		assertOpens("drt overlay", DrtNavTab.OVERLAY);
		assertOpens("drt config", DrtNavTab.SETTINGS);
		assertOpens("drt settings", DrtNavTab.SETTINGS);
	}

	@Test
	void settingsAliasesBehaveIdentically() throws CommandSyntaxException {
		dispatcher.execute("drt config", new Object());
		dispatcher.execute("drt settings", new Object());
		assertEquals(List.of("open SETTINGS", "open SETTINGS"), recorder.calls);
	}

	@Test
	void onlyTheAgreedCommandsAreRegistered() {
		assertEquals(Set.of("drt"), childNames(dispatcher.getRoot()));
		CommandNode<Object> drt = dispatcher.getRoot().getChild("drt");
		assertEquals(Set.of("runs", "loot", "overlay", "config", "settings", "debug"), childNames(drt));
		assertEquals(Set.of("copyreport", "savereplay", "exportbug", "reprice", "openfolder", "copyzip"),
			childNames(drt.getChild("debug")));
	}

	@Test
	void removedCommandsNoLongerParse() {
		for (String removed : List.of("drt move", "drt setup", "drt onboarding", "drt toggle", "drt toggle UI",
			"drt toggle tracking", "drt debug triggerincident", "drt debug triggerincident new", "drt visual 1",
			"drtfontstress", "drtfontstress on")) {
			assertThrows(CommandSyntaxException.class, () -> dispatcher.execute(removed, new Object()), removed);
		}
		assertTrue(recorder.calls.isEmpty());
	}

	@Test
	void debugCommandsKeepTheirBehaviour() throws CommandSyntaxException {
		dispatcher.execute("drt debug exportbug abc123", new Object());
		dispatcher.execute("drt debug openfolder abc123", new Object());
		dispatcher.execute("drt debug savereplay abc123", new Object());
		dispatcher.execute("drt debug copyzip abc123", new Object());
		dispatcher.execute("drt debug copyreport abc123", new Object());
		dispatcher.execute("drt debug reprice", new Object());
		dispatcher.execute("drt debug reprice 42", new Object());
		assertEquals(List.of("exportbug abc123", "openfolder abc123", "savereplay abc123", "copyzip abc123",
			"copyreport abc123", "reprice null", "reprice 42"), recorder.calls);
		assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("drt debug reprice 0", new Object()));
	}

	@Test
	void bugReportChatLinksStillResolve() throws CommandSyntaxException {
		// The tracking issue chat message runs and suggests these exact commands.
		dispatcher.execute("drt debug openfolder 7f3a9c", new Object());
		dispatcher.execute("drt debug exportbug 7f3a9c", new Object());
		assertEquals(List.of("openfolder 7f3a9c", "exportbug 7f3a9c"), recorder.calls);
	}

	@Test
	void commandsDoNothingOutsideAWorld() throws CommandSyntaxException {
		recorder.inGame = false;
		assertEquals(0, dispatcher.execute("drt loot", new Object()));
		assertEquals(0, dispatcher.execute("drt debug exportbug abc123", new Object()));
		assertTrue(recorder.calls.isEmpty());
		assertNull(recorder.lastTab);
	}

	private void assertOpens(String command, DrtNavTab tab) throws CommandSyntaxException {
		assertEquals(1, dispatcher.execute(command, new Object()), command);
		assertEquals(tab, recorder.lastTab, command);
	}

	private static Set<String> childNames(CommandNode<Object> node) {
		return node.getChildren().stream().map(CommandNode::getName).collect(Collectors.toSet());
	}

	private static final class Recorder implements DrtCommands.Handler<Object> {
		final List<String> calls = new ArrayList<>();
		boolean inGame = true;
		DrtNavTab lastTab;

		@Override
		public boolean inGame(Object source) {
			return inGame;
		}

		@Override
		public void openTab(DrtNavTab tab) {
			lastTab = tab;
			calls.add("open " + tab);
		}

		@Override
		public boolean copyReport(String reportId) {
			return record("copyreport " + reportId);
		}

		@Override
		public boolean saveReplay(String reportId) {
			return record("savereplay " + reportId);
		}

		@Override
		public boolean exportBug(String reportId) {
			return record("exportbug " + reportId);
		}

		@Override
		public boolean reprice(Integer chestNumber) {
			return record("reprice " + chestNumber);
		}

		@Override
		public boolean openFolder(String reportId) {
			return record("openfolder " + reportId);
		}

		@Override
		public boolean copyZip(String reportId) {
			return record("copyzip " + reportId);
		}

		private boolean record(String call) {
			calls.add(call);
			return true;
		}
	}
}
