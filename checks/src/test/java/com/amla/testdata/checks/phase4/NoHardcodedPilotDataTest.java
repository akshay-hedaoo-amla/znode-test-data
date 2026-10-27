package com.amla.testdata.checks.phase4;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exit criterion "pilot suites ... with no hardcoded data values except credential sources" (docs/phases.md, user
 * decision 2026-10-23): on the pilot branches, no approved pilot test still reads a value its requirement marks
 * {@code data} (it reads the resolved record instead). {@code credential} sources (usernames, passwords) may stay in
 * the test's own data, but then the test must declare the account with {@code useExisting} so it is validated and leased.
 * <p>
 * How a value is read: Selenium workbook cells through {@code .get("<row>").get("<column>")} (the last two segments of
 * the source's keyPath), Playwright JSON keys through {@code data.<key>}.
 */
class NoHardcodedPilotDataTest {

	@Test
	void pilotTestsReadTheirDataFromTheResolverAndDeclareTheirAccounts() throws IOException {
		List<PilotSources.Pilot> pilots = PilotSources.pilots();
		assertFalse(pilots.isEmpty(), "no approved pilot requirements");
		List<String> problems = new ArrayList<>();
		for (PilotSources.Pilot p : pilots) {
			if (!Files.isRegularFile(p.testFile())) {
				problems.add(p.testId() + ": test file not found " + p.testFile());
				continue;
			}
			String code = Files.readString(p.testFile(), StandardCharsets.UTF_8);
			boolean usesResolver = "selenium".equals(p.framework())
					? code.contains("adapters.selenium.runtime.TestData") : code.contains("testData");
			if (!usesResolver) {
				problems.add(p.testId() + ": does not read the resolver's TestData");
			}
			boolean hasCredential = false;
			for (JsonNode s : p.requirement().path("sources")) {
				String role = s.path("role").asText();
				if ("credential".equals(role)) {
					hasCredential = true;
				} else if ("data".equals(role) && readsSource(p.framework(), code, s)) {
					problems.add(p.testId() + ": still reads data source " + s.path("keyPath").asText() + " (" + s.path("location").asText() + ")");
				}
			}
			if (hasCredential && !code.contains("useExisting(")) {
				problems.add(p.testId() + ": keeps credentials in its own data but never declares the account (useExisting)");
			}
		}
		assertTrue(problems.isEmpty(), "pilot tests still using hardcoded data:\n" + String.join("\n", problems));
	}

	/** Whether the test code still reads this source (keyPath based; see the class comment). */
	static boolean readsSource(String framework, String code, JsonNode source) {
		String keyPath = source.path("keyPath").asText();
		if ("selenium".equals(framework)) {
			String[] seg = keyPath.split("/");
			if (seg.length < 2) {
				return code.contains("\"" + source.path("value").asText() + "\"");
			}
			String access = ".get(\"" + seg[seg.length - 2] + "\").get(\"" + seg[seg.length - 1] + "\")";
			return code.replaceAll("\\s+", "").contains(access);
		}
		String first = keyPath.split("[./]")[0];
		return Pattern.compile("\\bdata\\." + Pattern.quote(first) + "\\b").matcher(code).find();
	}
}
