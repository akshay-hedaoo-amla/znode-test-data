package com.amla.testdata.checks;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Project root and Markdown section helpers for the exit checks. */
final class RepoRoot {

	private RepoRoot() {
	}

	static Path path() {
		String root = System.getProperty("tda.repoRoot");
		if (root == null) {
			throw new IllegalStateException("System property tda.repoRoot is not set; run through Maven");
		}
		return Path.of(root).toAbsolutePath().normalize();
	}

	/** Text of the Markdown section whose heading line starts with {@code headingPrefix}, up to the next heading of the same level. */
	static String section(String relativeFile, String headingPrefix) {
		String text;
		try {
			text = Files.readString(path().resolve(relativeFile), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		String[] lines = text.split("\\R");
		StringBuilder out = new StringBuilder();
		String level = null;
		for (String line : lines) {
			if (level == null) {
				if (line.startsWith(headingPrefix)) {
					level = line.substring(0, line.indexOf(' ') + 1);
				}
				continue;
			}
			if (line.startsWith(level)) {
				break;
			}
			out.append(line).append('\n');
		}
		if (level == null) {
			throw new IllegalStateException("Section '" + headingPrefix + "' not found in " + relativeFile);
		}
		return out.toString();
	}
}
