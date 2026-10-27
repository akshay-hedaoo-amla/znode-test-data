package com.amla.testdata.checks.phase4;

import com.amla.testdata.config.Env;
import com.amla.testdata.queries.RepositoryData;
import com.fasterxml.jackson.databind.JsonNode;

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

	/** Every approved requirement with its test source file. */
	public static List<Pilot> pilots() {
		RepositoryData data = RepositoryData.load(root().resolve("repository"));
		List<Pilot> out = new ArrayList<>();
		for (JsonNode req : data.requirements()) {
			if (!data.isApproved(req)) {
				continue;
			}
			String fw = req.path("framework").asText();
			String testId = req.path("testId").asText();
			Path file = "selenium".equals(fw)
					? repo(fw).resolve("src/test/java").resolve(testId.substring(0, testId.indexOf('#')).replace('.', '/') + ".java")
					: repo(fw).resolve(testId.substring(0, testId.indexOf(" > ")));
			out.add(new Pilot(fw, testId, file, req));
		}
		return out;
	}
}
