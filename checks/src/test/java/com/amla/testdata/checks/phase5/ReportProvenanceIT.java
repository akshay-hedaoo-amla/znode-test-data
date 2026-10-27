package com.amla.testdata.checks.phase5;

import com.amla.testdata.checks.phase4.PilotRun;
import com.amla.testdata.checks.phase4.PilotSources;
import com.amla.testdata.checks.phase4.PilotSuitesIT;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 5 exit criterion 3, live on the test environment: every test report shows where its data came from. On the
 * pilot run shared with the Phase 4 check ({@link PilotRun}: one run per gate), for every approved pilot test that ran
 * (passed, failed or skipped for data):
 * <ul>
 * <li>Selenium: its ExtentReports entry (extent-Reports/&lt;date&gt;/IntegrationUIRegressionExecutionReport.html, written
 * during this run) holds its {@code [tda]} provenance lines;</li>
 * <li>Playwright: its result in the JSON report has the {@code tda-provenance} attachment;</li>
 * <li>both: the run's provenance feed (work/runs/&lt;runId&gt;/provenance.jsonl, the RCA agent's input) has its entry.</li>
 * </ul>
 */
class ReportProvenanceIT {

	private static final ObjectMapper JSON = new ObjectMapper();
	private static final String EXTENT_TEST = "class='test-name'>";

	@Test
	void everyPilotTestReportShowsWhereItsDataCameFrom() throws Exception {
		PilotRun.Result run = PilotRun.get();
		Path root = PilotSources.root();
		Map<String, String> extent = extentEntries(run.selenium(), run.started());
		Map<String, Set<String>> attachments = playwrightAttachments(run.playwrightReport());
		Set<String> fed = feedTests(root.resolve("work/runs"), run.started());

		List<String> problems = new ArrayList<>();
		int checked = 0;
		for (PilotSources.Pilot pilot : PilotSources.pilots()) {
			PilotSuitesIT.Run r = run.runs().stream().filter(x -> x.testId().equals(pilot.testId())).findFirst().orElse(null);
			if (r == null) {
				problems.add(pilot.testId() + ": did not run, so it has no report");
				continue;
			}
			checked++;
			if ("selenium".equals(pilot.framework())) {
				String method = pilot.testId().substring(pilot.testId().indexOf('#') + 1);
				String entry = extent.get(method);
				if (entry == null || !entry.contains("[tda] ")) {
					problems.add(pilot.testId() + ": no [tda] provenance in its ExtentReports entry" + (entry == null ? " (no entry)" : ""));
				}
			} else if (!attachments.getOrDefault(pilot.testId().substring(pilot.testId().lastIndexOf(" > ") + 3), Set.of()).contains("tda-provenance")) {
				problems.add(pilot.testId() + ": no tda-provenance attachment in the Playwright report");
			}
			if (!fed.contains(pilot.testId())) {
				problems.add(pilot.testId() + ": no entry in this run's provenance feed");
			}
		}
		assertFalse(checked == 0, "no pilot test ran");
		assertTrue(problems.isEmpty(), "report provenance (reports: Selenium extent-Reports, work/phase4/playwright-results.json, work/runs/*):\n"
				+ String.join("\n", problems));
	}

	/** Method name to its Extent entry, from the main (not rerun) reports written during this run. */
	private static Map<String, String> extentEntries(Path selenium, Instant since) throws Exception {
		Map<String, String> out = new HashMap<>();
		Path dir = selenium.resolve("extent-Reports");
		if (!Files.isDirectory(dir)) {
			return out;
		}
		List<Path> reports;
		try (Stream<Path> s = Files.walk(dir, 2)) {
			reports = s.filter(p -> p.getFileName().toString().equals("IntegrationUIRegressionExecutionReport.html"))
					.filter(p -> modified(p).isAfter(since)).toList();
		}
		for (Path report : reports) {
			String html = Files.readString(report, StandardCharsets.UTF_8);
			String[] parts = html.split(java.util.regex.Pattern.quote(EXTENT_TEST));
			for (int i = 1; i < parts.length; i++) {
				String name = parts[i].substring(0, parts[i].indexOf('<')).strip();
				out.merge(name, parts[i], String::concat);
			}
		}
		return out;
	}

	/** Test title to the attachment names of its results. */
	private static Map<String, Set<String>> playwrightAttachments(Path report) throws Exception {
		Map<String, Set<String>> out = new HashMap<>();
		if (Files.isRegularFile(report)) {
			collect(JSON.readTree(report.toFile()), out);
		}
		return out;
	}

	private static void collect(JsonNode suite, Map<String, Set<String>> out) {
		suite.path("suites").forEach(s -> collect(s, out));
		for (JsonNode spec : suite.path("specs")) {
			Set<String> names = out.computeIfAbsent(spec.path("title").asText(), t -> new LinkedHashSet<>());
			spec.path("tests").forEach(t -> t.path("results").forEach(r -> r.path("attachments").forEach(a -> names.add(a.path("name").asText()))));
		}
	}

	/** Test IDs with a resolved or declared entry in the feeds written during this run. */
	private static Set<String> feedTests(Path runs, Instant since) throws Exception {
		Set<String> out = new LinkedHashSet<>();
		if (!Files.isDirectory(runs)) {
			return out;
		}
		List<Path> feeds;
		try (Stream<Path> s = Files.walk(runs, 2)) {
			feeds = s.filter(p -> p.getFileName().toString().equals("provenance.jsonl")).filter(p -> modified(p).isAfter(since)).toList();
		}
		for (Path feed : feeds) {
			for (String line : Files.readAllLines(feed, StandardCharsets.UTF_8)) {
				if (!line.isBlank()) {
					JsonNode n = JSON.readTree(line);
					if (!"outcome".equals(n.path("event").asText())) {
						out.add(n.path("testId").asText());
					}
				}
			}
		}
		return out;
	}

	private static Instant modified(Path p) {
		try {
			return Files.getLastModifiedTime(p).toInstant();
		} catch (java.io.IOException e) {
			return Instant.EPOCH;
		}
	}
}
