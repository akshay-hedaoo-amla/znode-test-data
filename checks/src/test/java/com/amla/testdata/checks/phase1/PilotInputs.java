package com.amla.testdata.checks.phase1;

import com.amla.testdata.scan.ScanResult;
import com.amla.testdata.scan.ScanResultWriter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Pilot scan results and repository entries the Phase 1 checks read. */
final class PilotInputs {

	private static final ObjectMapper JSON = new ObjectMapper();

	private PilotInputs() {
	}

	static Path root() {
		String root = System.getProperty("tda.repoRoot");
		if (root == null) {
			throw new IllegalStateException("System property tda.repoRoot is not set; run through Maven");
		}
		return Path.of(root).toAbsolutePath().normalize();
	}

	/** framework → pilot files, from config/scanners.yaml. */
	static Map<String, List<String>> pilots() {
		try {
			JsonNode config = new ObjectMapper(new YAMLFactory()).readTree(root().resolve("config/scanners.yaml").toFile());
			Map<String, List<String>> out = new LinkedHashMap<>();
			for (String fw : List.of("selenium", "playwright")) {
				List<String> files = new ArrayList<>();
				config.path(fw).path("pilots").forEach(p -> files.add(p.asText()));
				out.put(fw, files);
			}
			return out;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** Scan results for every pilot file; fails naming the missing ones. */
	static List<ScanResult> pilotScans() {
		List<ScanResult> scans = new ArrayList<>();
		List<String> missing = new ArrayList<>();
		pilots().forEach((fw, files) -> files.forEach(f -> {
			Path p = root().resolve("work/scans").resolve(ScanResultWriter.nameFor(fw, f) + ".json");
			if (!Files.isRegularFile(p)) {
				missing.add(fw + ": " + f);
				return;
			}
			try {
				scans.add(JSON.readValue(p.toFile(), ScanResult.class));
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}));
		if (!missing.isEmpty()) {
			throw new AssertionError("No scan result for these pilots (run the scanners, see agents/README.md): " + missing);
		}
		return scans;
	}

	/** testId → requirements entry. */
	static Map<String, JsonNode> requirements() {
		Map<String, JsonNode> out = new LinkedHashMap<>();
		// Entries sit in folders that mirror their test's path, so read them at any depth.
		for (Path f : jsonFilesAtAnyDepth(root().resolve("repository/requirements"))) {
			JsonNode n = read(f);
			out.put(n.path("testId").asText(), n);
		}
		return out;
	}

	static List<Path> jsonFilesAtAnyDepth(Path dir) {
		if (!Files.isDirectory(dir)) {
			return List.of();
		}
		try (Stream<Path> files = Files.walk(dir)) {
			return files.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".json")).sorted().toList();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	static List<Path> jsonFiles(Path dir) {
		if (!Files.isDirectory(dir)) {
			return List.of();
		}
		try (Stream<Path> files = Files.list(dir)) {
			return files.filter(p -> p.toString().endsWith(".json")).sorted().toList();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	static JsonNode read(Path file) {
		try {
			return JSON.readTree(file.toFile());
		} catch (IOException e) {
			throw new UncheckedIOException("Could not read " + file, e);
		}
	}
}
