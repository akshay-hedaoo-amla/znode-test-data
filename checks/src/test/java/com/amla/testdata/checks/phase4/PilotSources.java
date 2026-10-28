package com.amla.testdata.checks.phase4;

import com.amla.testdata.config.Env;
import com.amla.testdata.queries.RepositoryData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** The approved pilot requirements and where their tests live in the framework repositories (TDA_*_REPO). */
public final class PilotSources {

	public record Pilot(String framework, String testId, Path testFile, JsonNode requirement) {
	}

	private PilotSources() {
	}

	public static Path root() {
		String root = System.getProperty("tda.repoRoot");
		if (root == null) {
			throw new IllegalStateException("System property tda.repoRoot is not set; run through Maven");
		}
		return Path.of(root).toAbsolutePath().normalize();
	}

	public static Path repo(String framework) {
		return Path.of(Env.load(root()).require("selenium".equals(framework) ? "TDA_SELENIUM_REPO" : "TDA_PLAYWRIGHT_REPO"))
				.toAbsolutePath().normalize();
	}

	/**
	 * The test's source file relative to its framework repository: a Selenium class under src/test/java, or the
	 * Playwright spec named before the first " > ".
	 */
	public static String testFile(String framework, String testId) {
		return "selenium".equals(framework)
				? "src/test/java/" + testId.substring(0, testId.indexOf('#')).replace('.', '/') + ".java"
				: testId.substring(0, testId.indexOf(" > "));
	}

	/**
	 * The test is in a pilot file of config/scanners.yaml. Phase 6 adds approved requirements module by module; the
	 * Phase 2-5 pilot checks keep covering the pilots only.
	 */
	public static boolean isPilot(String framework, String testId) {
		try {
			JsonNode config = new ObjectMapper(new YAMLFactory()).readTree(root().resolve("config/scanners.yaml").toFile());
			for (JsonNode f : config.path(framework).path("pilots")) {
				if (f.asText().equals(testFile(framework, testId))) {
					return true;
				}
			}
			return false;
		} catch (java.io.IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
	}

	/** Every approved pilot requirement with its test source file. */
	public static List<Pilot> pilots() {
		RepositoryData data = RepositoryData.load(root().resolve("repository"));
		List<Pilot> out = new ArrayList<>();
		for (JsonNode req : data.requirements()) {
			if (!data.isApproved(req) || !isPilot(req.path("framework").asText(), req.path("testId").asText())) {
				continue;
			}
			String fw = req.path("framework").asText();
			String testId = req.path("testId").asText();
			Path file = repo(fw).resolve(testFile(fw, testId));
			out.add(new Pilot(fw, testId, file, req));
		}
		return out;
	}
}
