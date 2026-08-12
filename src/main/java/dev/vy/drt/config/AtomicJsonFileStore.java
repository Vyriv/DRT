package dev.vy.drt.config;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

public final class AtomicJsonFileStore {
	private AtomicJsonFileStore() {
	}

	public static void writeCrashSafe(Path path, Gson gson, Object value, boolean preserveMalformedPrimary) throws IOException {
		if (path == null) throw new IOException("Missing config path");
		if (gson == null) throw new IOException("Missing Gson");
		if (path.getParent() != null) Files.createDirectories(path.getParent());
		Path temp = path.resolveSibling(path.getFileName() + ".tmp");
		Path backup = backupPath(path);
		String json = gson.toJson(value);
		writeAndSync(temp, json);
		JsonParser.parseString(Files.readString(temp, StandardCharsets.UTF_8));
		if (Files.exists(path)) {
			if (preserveMalformedPrimary) {
				Path malformed = path.resolveSibling(path.getFileName() + ".malformed-" + DateTimeFormatter.ISO_INSTANT.format(Instant.now()).replace(':', '-'));
				Files.copy(path, malformed, StandardCopyOption.REPLACE_EXISTING);
			} else {
				Files.copy(path, backup, StandardCopyOption.REPLACE_EXISTING);
			}
		}
		moveReplacing(temp, path);
		JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
	}

	public static Path backupPath(Path path) {
		return path.resolveSibling(path.getFileName() + ".bak");
	}

	private static void writeAndSync(Path path, String content) throws IOException {
		byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
		try (FileChannel channel = FileChannel.open(
			path,
			StandardOpenOption.CREATE,
			StandardOpenOption.TRUNCATE_EXISTING,
			StandardOpenOption.WRITE
		)) {
			channel.write(ByteBuffer.wrap(bytes));
			channel.force(true);
		}
	}

	private static void moveReplacing(Path source, Path target) throws IOException {
		try {
			Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException ignored) {
			Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
