package dev.vy.drt.client;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.vy.drt.client.screen.DrtNavTab;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * The /drt command tree. Player shortcuts open the DRT window on a tab; debug commands stay reachable for the
 * bug report links DRT posts in chat and for repricing saved chests.
 */
final class DrtCommands {
	static final String ROOT = "drt";
	static final Map<String, DrtNavTab> TAB_SHORTCUTS = tabShortcuts();

	interface Handler<S> {
		/** False, after telling the player, when no world is loaded. */
		boolean inGame(S source);

		void openTab(DrtNavTab tab);

		boolean copyReport(String reportId);

		boolean saveReplay(String reportId);

		boolean exportBug(String reportId);

		boolean reprice(Integer chestNumber);

		boolean openFolder(String reportId);

		boolean copyZip(String reportId);
	}

	private DrtCommands() {
	}

	static <S> LiteralArgumentBuilder<S> drt(Handler<S> handler) {
		LiteralArgumentBuilder<S> root = LiteralArgumentBuilder.<S>literal(ROOT)
			.executes(context -> openTab(context, handler, DrtNavTab.OVERVIEW));
		TAB_SHORTCUTS.forEach((name, tab) ->
			root.then(LiteralArgumentBuilder.<S>literal(name).executes(context -> openTab(context, handler, tab))));
		root.then(LiteralArgumentBuilder.<S>literal("debug")
			.then(reportCommand("copyreport", handler, handler::copyReport))
			.then(reportCommand("savereplay", handler, handler::saveReplay))
			.then(reportCommand("exportbug", handler, handler::exportBug))
			.then(LiteralArgumentBuilder.<S>literal("reprice")
				.executes(context -> run(context, handler, () -> handler.reprice(null)))
				.then(RequiredArgumentBuilder.<S, Integer>argument("chestNumber", IntegerArgumentType.integer(1))
					.executes(context -> run(context, handler,
						() -> handler.reprice(IntegerArgumentType.getInteger(context, "chestNumber"))))))
			.then(reportCommand("openfolder", handler, handler::openFolder))
			.then(reportCommand("copyzip", handler, handler::copyZip)));
		return root;
	}

	private static Map<String, DrtNavTab> tabShortcuts() {
		Map<String, DrtNavTab> shortcuts = new LinkedHashMap<>();
		shortcuts.put("runs", DrtNavTab.RUNS);
		shortcuts.put("loot", DrtNavTab.LOOT);
		shortcuts.put("overlay", DrtNavTab.OVERLAY);
		shortcuts.put("config", DrtNavTab.SETTINGS);
		shortcuts.put("settings", DrtNavTab.SETTINGS);
		return Collections.unmodifiableMap(shortcuts);
	}

	private static <S> LiteralArgumentBuilder<S> reportCommand(String name, Handler<S> handler, Function<String, Boolean> action) {
		return LiteralArgumentBuilder.<S>literal(name)
			.then(RequiredArgumentBuilder.<S, String>argument("reportId", StringArgumentType.word())
				.executes(context -> run(context, handler,
					() -> action.apply(StringArgumentType.getString(context, "reportId")))));
	}

	private static <S> int openTab(CommandContext<S> context, Handler<S> handler, DrtNavTab tab) {
		if (!handler.inGame(context.getSource())) return 0;
		handler.openTab(tab);
		return Command.SINGLE_SUCCESS;
	}

	private static <S> int run(CommandContext<S> context, Handler<S> handler, BooleanSupplier action) {
		if (!handler.inGame(context.getSource())) return 0;
		return action.getAsBoolean() ? Command.SINGLE_SUCCESS : 0;
	}
}
