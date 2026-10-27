package com.amla.testdata.checks;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Backs "Secrets stored outside git": no credentials, tokens or connection strings in this project (the same scan as
 * the test data agents' own, over the data repository).
 * Patterns live in secrets/patterns.txt; every pattern is proven by a fake sample in secrets/positive.
 */
class SecretScanTest {

	// input/ and work/ hold raw downloads and scan output; both are git-ignored.
	private static final Set<String> SKIP_DIRS = Set.of("target", ".git", "node_modules", ".idea", "input", "work");
	private static final List<String> TEXT_EXTENSIONS = List.of(".java", ".json", ".yaml", ".yml", ".md", ".properties",
			".xml", ".ts", ".js", ".sql", ".txt", ".toml", ".env", ".example", ".cfg", ".ini", ".sh", ".ps1", ".bat", ".cmd");

	@Test
	void projectContainsNoSecrets() throws IOException {
		List<Pattern> patterns = patterns();
		Path root = RepoRoot.path();
		Path ownFixtures = root.resolve("checks/src/test/resources/secrets");
		Set<String> allowed = allowed();
		Set<String> used = new HashSet<>();
		List<String> hits = new ArrayList<>();
		try (Stream<Path> files = Files.walk(root)) {
			for (Path file : files.filter(Files::isRegularFile).toList()) {
				Path rel = root.relativize(file);
				if (file.startsWith(ownFixtures) || skipped(rel) || isLocalSecretsFile(rel) || !isText(file)) {
					continue;
				}
				String relName = rel.toString().replace('\\', '/');
				List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
				for (int i = 0; i < lines.size(); i++) {
					for (Pattern p : patterns) {
						Matcher m = p.matcher(lines.get(i));
						while (m.find()) {
							String entry = "sha256:" + sha256(m.group()) + "\t" + relName;
							if (allowed.contains(entry)) {
								used.add(entry);
								continue;
							}
							// Report location and pattern only, never the matched text.
							hits.add(rel + ":" + (i + 1) + " matches " + p.pattern());
						}
					}
				}
			}
		}
		assertTrue(hits.isEmpty(), "possible secrets:\n" + String.join("\n", hits));
		Set<String> unused = new LinkedHashSet<>(allowed);
		unused.removeAll(used);
		assertTrue(unused.isEmpty(), "secrets/allowed.txt entries that no longer match anything (remove them):\n" + String.join("\n", unused));
	}

	/**
	 * secrets/allowed.txt: exact matches that are known not to be secrets (e.g. documentation placeholders in exported
	 * specs), as "sha256:&lt;hash of the matched text&gt; TAB &lt;file&gt; TAB &lt;reason&gt;". Only that value in that file is
	 * ignored. Returns "sha256:&lt;hash&gt; TAB &lt;file&gt;".
	 */
	private static Set<String> allowed() throws IOException {
		Set<String> out = new LinkedHashSet<>();
		for (String line : Files.readAllLines(resource("secrets/allowed.txt"), StandardCharsets.UTF_8)) {
			if (line.isBlank() || line.startsWith("#")) {
				continue;
			}
			String[] parts = line.split("\t");
			assertTrue(parts.length == 3 && parts[0].matches("sha256:[0-9a-f]{64}") && !parts[2].isBlank(),
					"allowed.txt line needs hash, file and reason: " + line);
			out.add(parts[0] + "\t" + parts[1]);
		}
		return out;
	}

	private static String sha256(String text) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	@TestFactory
	List<DynamicTest> everyPatternCatchesItsSample() throws IOException {
		List<Pattern> patterns = patterns();
		List<DynamicTest> tests = new ArrayList<>();
		try (Stream<Path> files = Files.list(resource("secrets/positive"))) {
			for (Path sample : files.sorted().toList()) {
				String text = Files.readString(sample, StandardCharsets.UTF_8);
				tests.add(DynamicTest.dynamicTest(sample.getFileName().toString(),
						() -> assertTrue(patterns.stream().anyMatch(p -> p.matcher(text).find()), "not detected")));
			}
		}
		tests.add(DynamicTest.dynamicTest("every pattern has a sample", () -> {
			for (Pattern p : patterns) {
				boolean covered = false;
				try (Stream<Path> files = Files.list(resource("secrets/positive"))) {
					for (Path sample : files.toList()) {
						covered |= p.matcher(Files.readString(sample, StandardCharsets.UTF_8)).find();
					}
				}
				assertTrue(covered, "no sample for " + p.pattern());
			}
		}));
		return tests;
	}

	private static List<Pattern> patterns() throws IOException {
		return Files.readAllLines(resource("secrets/patterns.txt"), StandardCharsets.UTF_8).stream()
				.filter(l -> !l.isBlank() && !l.startsWith("#")).map(Pattern::compile).toList();
	}

	private static boolean skipped(Path rel) {
		for (Path part : rel) {
			if (SKIP_DIRS.contains(part.toString())) {
				return true;
			}
		}
		return false;
	}

	/** The git-ignored .env (and .env.* other than the template) at the project root holds real values by design. */
	private static boolean isLocalSecretsFile(Path rel) {
		String name = rel.toString();
		return rel.getNameCount() == 1 && name.startsWith(".env") && !name.equals(".env.example");
	}

	private static boolean isText(Path file) {
		String name = file.getFileName().toString().toLowerCase();
		return name.startsWith(".env") || name.equals(".gitignore") || TEXT_EXTENSIONS.stream().anyMatch(name::endsWith);
	}

	private static Path resource(String name) {
		try {
			return Path.of(SecretScanTest.class.getClassLoader().getResource(name).toURI());
		} catch (java.net.URISyntaxException e) {
			throw new IllegalStateException(e);
		}
	}
}
