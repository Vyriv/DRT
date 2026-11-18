package dev.vy.drt.client.sync.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;

final class DrtInstallKey {
	private static final Path KEY_PATH = FabricLoader.getInstance().getConfigDir()
		.resolve("drt")
		.resolve("auth-v2.key");
	private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
	private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
	private static volatile DrtInstallKey cached;

	private final PrivateKey privateKey;
	private final String publicKey;

	private DrtInstallKey(PrivateKey privateKey, String publicKey) {
		this.privateKey = privateKey;
		this.publicKey = publicKey;
	}

	static DrtInstallKey load() throws Exception {
		DrtInstallKey current = cached;
		if (current != null) return current;
		synchronized (DrtInstallKey.class) {
			if (cached == null) cached = Files.exists(KEY_PATH) ? read() : generate();
			return cached;
		}
	}

	String publicKey() {
		return publicKey;
	}

	String sign(String transcript) throws Exception {
		Signature signature = Signature.getInstance("Ed25519");
		signature.initSign(privateKey);
		signature.update(transcript.getBytes(StandardCharsets.UTF_8));
		return ENCODER.encodeToString(signature.sign());
	}

	private static DrtInstallKey read() throws Exception {
		List<String> lines = Files.readAllLines(KEY_PATH, StandardCharsets.UTF_8);
		if (lines.size() != 2) throw new IOException("Invalid DRT auth key file");
		KeyFactory factory = KeyFactory.getInstance("Ed25519");
		PrivateKey privateKey = factory.generatePrivate(new PKCS8EncodedKeySpec(DECODER.decode(lines.get(0).trim())));
		String publicKey = lines.get(1).trim();
		var parsedPublic = factory.generatePublic(new X509EncodedKeySpec(DECODER.decode(publicKey)));
		String probe = "betterpv-install-key-check";
		Signature signer = Signature.getInstance("Ed25519");
		signer.initSign(privateKey);
		signer.update(probe.getBytes(StandardCharsets.UTF_8));
		Signature verifier = Signature.getInstance("Ed25519");
		verifier.initVerify(parsedPublic);
		verifier.update(probe.getBytes(StandardCharsets.UTF_8));
		if (!verifier.verify(signer.sign())) throw new IOException("DRT auth key pair does not match");
		return new DrtInstallKey(privateKey, publicKey);
	}

	private static DrtInstallKey generate() throws Exception {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519");
		KeyPair pair = generator.generateKeyPair();
		String privateKey = ENCODER.encodeToString(pair.getPrivate().getEncoded());
		String publicKey = ENCODER.encodeToString(pair.getPublic().getEncoded());
		Files.createDirectories(KEY_PATH.getParent());
		Path temporary = Files.createTempFile(KEY_PATH.getParent(), "auth-v2-", ".tmp");
		try {
			Files.writeString(temporary, privateKey + "\n" + publicKey + "\n", StandardCharsets.UTF_8);
			try {
				Files.setPosixFilePermissions(temporary, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
			} catch (UnsupportedOperationException ignored) {
				// Windows ACLs are inherited from the private user config directory.
			}
			try {
				Files.move(temporary, KEY_PATH, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException exception) {
				Files.move(temporary, KEY_PATH);
			}
		} finally {
			Files.deleteIfExists(temporary);
		}
		return new DrtInstallKey(pair.getPrivate(), publicKey);
	}
}
