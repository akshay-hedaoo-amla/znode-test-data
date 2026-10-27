package com.amla.testdata.checks.phase5;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.Stream;

/** Temporary copies of the project for the Phase 5 checks, so breaking an entry never touches the real repository. */
final class Phase5Copies {

	private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

	private Phase5Copies() {
	}

	static Path root() {
		String root = System.getProperty("tda.repoRoot");
		if (root == null) {
			throw new IllegalStateException("System property tda.repoRoot is not set; run through Maven");
		}
		return Path.of(root).toAbsolutePath().normalize();
	}

	/** repository/, specs/, config/ and the stored schema snapshot; never .env, input/ or other work/ files. */
	static Path project(Path root, Path dest) throws IOException {
		for (String dir : List.of("repository", "specs", "config")) {
			copyTree(root.resolve(dir), dest.resolve(dir));
		}
		Path snapshot = root.resolve("work/db/schema-snapshot.json");
		if (Files.isRegularFile(snapshot)) {
			Files.createDirectories(dest.resolve("work/db"));
			Files.copy(snapshot, dest.resolve("work/db/schema-snapshot.json"));
		}
		return dest;
	}

	static JsonNode read(Path file) throws IOException {
		return JSON.readTree(Files.readString(file, StandardCharsets.UTF_8).replace("﻿", ""));
	}

	static void edit(Path file, Consumer<ObjectNode> change) throws IOException {
		ObjectNode node = (ObjectNode) read(file);
		change.accept(node);
		JSON.writeValue(file.toFile(), node);
	}

	/** Relative path to content of every file under the folder. */
	static Map<String, String> contents(Path dir) throws IOException {
		Map<String, String> out = new TreeMap<>();
		try (Stream<Path> s = Files.walk(dir)) {
			for (Path p : s.filter(Files::isRegularFile).toList()) {
				out.put(dir.relativize(p).toString(), Files.readString(p, StandardCharsets.UTF_8));
			}
		}
		return out;
	}

	private static void copyTree(Path from, Path to) throws IOException {
		try (Stream<Path> s = Files.walk(from)) {
			s.forEach(p -> {
				try {
					Path target = to.resolve(from.relativize(p).toString());
					if (Files.isDirectory(p)) {
						Files.createDirectories(target);
					} else {
						Files.copy(p, target, StandardCopyOption.REPLACE_EXISTING);
					}
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			});
		}
	}
}
