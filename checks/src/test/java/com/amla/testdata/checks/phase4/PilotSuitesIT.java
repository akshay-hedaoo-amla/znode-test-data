package com.amla.testdata.checks.phase4;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 4 exit criteria, live on the test environment:
 * <ol>
 * <li>the Selenium and Playwright pilot suites pass, run at the same time (Selenium: the pilot suite XML, test classes in
 * parallel; Playwright: the pilot specs on parallel workers);</li>
 * <li>no two tests running at the same time received the same consumable record: each lease ID names its record
 * ({@code L<sha256(key, identity)>-...}), and the same record may only be held by tests whose run windows do not
 * overlap.</li>
 * </ol>
 * Pilot tests listed in config/pilot-known-failures.yaml (pre-existing failures outside the test data agents, excluded
 * by a user decision with evidence) still run and are reported, but do not fail the gate; once one passes again the gate
 * says its entry can be removed.
 * <p>
 * The run itself (and what it needs): {@link PilotRun}.
 */
public class PilotSuitesIT {

	private static final ObjectMapper JSON = new ObjectMapper();
	private static final Pattern LEASE = Pattern.compile("L([0-9a-f]{40})-[0-9a-f]+");

	/** One executed pilot test: its run window and the records (lease hashes) it held. */
	public record Run(String framework, String testId, String status, Instant start, Instant end, Set<String> records) {
	}

	@Test
	void pilotSuitesPassInParallelAndNeverShareAConsumableRecord() throws Exception {
		// One pilot run per JVM, shared with the Phase 5 report provenance check.
		PilotRun.Result result = PilotRun.get();
		Path root = PilotSources.root();
		List<Run> runs = result.runs();

		Set<String> known = knownFailures(root);
		List<String> problems = new ArrayList<>();
		for (PilotSources.Pilot pilot : PilotSources.pilots()) {
			Run run = runs.stream().filter(r -> r.testId().equals(pilot.testId())).findFirst().orElse(null);
			boolean passed = run != null && (run.status().equalsIgnoreCase("PASS") || run.status().equalsIgnoreCase("passed"));
			if (known.contains(pilot.testId())) {
				System.out.println("[phase4] known pre-existing failure " + pilot.testId() + ": " + (run == null ? "did not run" : run.status())
						+ (passed ? " - it passes now: remove it from config/pilot-known-failures.yaml" : ""));
			} else if (run == null) {
				problems.add(pilot.testId() + ": did not run");
			} else if (!passed) {
				problems.add(pilot.testId() + ": " + run.status());
			}
		}
		problems.addAll(sharedRecords(runs));
		assertTrue(problems.isEmpty(), "Phase 4 pilot run (logs and reports in work/phase4, Selenium surefire-reports):\n"
				+ String.join("\n", problems));
	}

	/** Test IDs of config/pilot-known-failures.yaml (absent file: none). */
	static Set<String> knownFailures(Path root) throws java.io.IOException {
		Path file = root.resolve("config/pilot-known-failures.yaml");
		Set<String> out = new LinkedHashSet<>();
		if (Files.isRegularFile(file)) {
			new ObjectMapper(new YAMLFactory()).readTree(file.toFile()).path("knownFailures").forEach(f -> {
				if (f.path("evidence").asText().isBlank()) {
					throw new IllegalStateException("known failure without evidence: " + f.path("testId").asText());
				}
				out.add(f.path("testId").asText());
			});
		}
		return out;
	}

	/** Pairs of tests that held the same record while both were running. */
	static List<String> sharedRecords(List<Run> runs) {
		List<String> out = new ArrayList<>();
		for (int i = 0; i < runs.size(); i++) {
			for (int j = i + 1; j < runs.size(); j++) {
				Run a = runs.get(i);
				Run b = runs.get(j);
				boolean overlap = a.start().isBefore(b.end()) && b.start().isBefore(a.end());
				for (String record : a.records()) {
					if (overlap && b.records().contains(record)) {
						out.add("record " + record.substring(0, 12) + "... leased to two parallel tests: " + a.testId() + " and " + b.testId());
					}
				}
			}
		}
		return out;
	}

	private static Set<String> records(String text) {
		Set<String> out = new LinkedHashSet<>();
		Matcher m = LEASE.matcher(text);
		while (m.find()) {
			out.add(m.group(1));
		}
		return out;
	}

	/** TestNG results: one run per test method, with the [tda] lines the listeners wrote to its reporter output. */
	static List<Run> seleniumRuns(File results) throws Exception {
		List<Run> out = new ArrayList<>();
		if (!results.isFile()) {
			return out;
		}
		var doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(results);
		NodeList classes = doc.getElementsByTagName("class");
		for (int c = 0; c < classes.getLength(); c++) {
			Element cls = (Element) classes.item(c);
			NodeList methods = cls.getElementsByTagName("test-method");
			for (int i = 0; i < methods.getLength(); i++) {
				Element m = (Element) methods.item(i);
				if ("true".equals(m.getAttribute("is-config"))) {
					continue;
				}
				StringBuilder lines = new StringBuilder();
				NodeList ls = m.getElementsByTagName("line");
				for (int l = 0; l < ls.getLength(); l++) {
					lines.append(ls.item(l).getTextContent()).append('\n');
				}
				out.add(new Run("selenium", cls.getAttribute("name") + "#" + m.getAttribute("name"), m.getAttribute("status"),
						testngTime(m.getAttribute("started-at")), testngTime(m.getAttribute("finished-at")), records(lines.toString())));
			}
		}
		return out;
	}

	/** TestNG writes local times ("2026-10-26T19:09:18 IST"); the run happens on this machine. */
	private static Instant testngTime(String value) {
		return LocalDateTime.parse(value.substring(0, 19)).atZone(ZoneId.systemDefault()).toInstant();
	}

	/** Playwright JSON report: one run per test, records from its tda annotations, test ID as the requirements write it. */
	static List<Run> playwrightRuns(Path report) throws Exception {
		List<Run> out = new ArrayList<>();
		if (!Files.isRegularFile(report)) {
			return out;
		}
		collect(JSON.readTree(report.toFile()), new ArrayList<>(), out);
		return out;
	}

	private static void collect(JsonNode suite, List<String> titles, List<Run> out) {
		for (JsonNode child : suite.path("suites")) {
			List<String> next = new ArrayList<>(titles);
			next.add(child.path("title").asText());
			collect(child, next, out);
		}
		for (JsonNode spec : suite.path("specs")) {
			for (JsonNode test : spec.path("tests")) {
				StringBuilder notes = new StringBuilder();
				test.path("annotations").forEach(a -> notes.append(a.path("description").asText()).append('\n'));
				Map<String, JsonNode> last = new LinkedHashMap<>();
				test.path("results").forEach(r -> last.put("r", r));
				JsonNode r = last.get("r");
				if (r == null) {
					continue;
				}
				// Requirement test ID: repo-relative spec path > describe titles > test title (the first title is the file).
				List<String> path = new ArrayList<>();
				path.add("testim-migrated/tests/webstore/" + spec.path("file").asText().replace('\\', '/'));
				path.addAll(titles.subList(Math.min(1, titles.size()), titles.size()));
				path.add(spec.path("title").asText());
				Instant start = Instant.parse(r.path("startTime").asText());
				out.add(new Run("playwright", String.join(" > ", path), r.path("status").asText(), start,
						start.plus(Duration.ofMillis(r.path("duration").asLong())), records(notes.toString())));
			}
		}
	}
}
