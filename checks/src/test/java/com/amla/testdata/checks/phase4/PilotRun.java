package com.amla.testdata.checks.phase4;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pilot suites, run once per JVM and shared by the checks that need a real run (Phase 4 pass/parallel checks, Phase 5
 * report provenance): Selenium (the pilot suite XML, test classes in parallel) and Playwright (the pilot specs on
 * parallel workers) at the same time, on one TDA home. Failsafe runs every IT in one JVM, so the ~90 minute run
 * happens once per gate.
 * <p>
 * Needs TDA_SELENIUM_REPO / TDA_PLAYWRIGHT_REPO (the framework repositories on their pilot branches), the resolver built
 * ({@code mvn install}), and {@code -Dtda.mvn=<path to mvn(.cmd)>} when Maven is not on PATH. Optional:
 * {@code -Dtda.pilot.seleniumSuite} (default the pilot suite), {@code -Dtda.pilot.environment} (default atstage).
 */
public final class PilotRun {

	private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");
	private static Result result;

	/**
	 * @param started          when the suites were started (reports and run feeds written after it belong to this run)
	 * @param selenium         the Selenium framework repository
	 * @param playwrightReport the Playwright JSON report
	 * @param runs             one entry per executed pilot test
	 */
	public record Result(Instant started, Path selenium, Path playwrightReport, List<PilotSuitesIT.Run> runs) {
	}

	private PilotRun() {
	}

	public static synchronized Result get() throws Exception {
		if (result == null) {
			result = run();
		}
		return result;
	}

	private static Result run() throws Exception {
		Path root = PilotSources.root();
		Path work = Files.createDirectories(root.resolve("work/phase4"));
		Path selenium = PilotSources.repo("selenium");
		Path playwright = PilotSources.repo("playwright");
		String suite = System.getProperty("tda.pilot.seleniumSuite", "src/test/java/testSuites/integration/marketing/tdaPilot.xml");
		String environment = System.getProperty("tda.pilot.environment", "atstage");
		Path pwReport = work.resolve("playwright-results.json");
		Files.deleteIfExists(pwReport);
		Path testngResults = selenium.resolve("target/surefire-reports/testng-results.xml");
		Files.deleteIfExists(testngResults);

		List<String> pwSpecs = new ArrayList<>();
		PilotSources.pilots().stream().filter(p -> "playwright".equals(p.framework()))
				.forEach(p -> pwSpecs.add(playwright.relativize(p.testFile()).toString().replace('\\', '/')));

		ProcessBuilder seleniumRun = new ProcessBuilder(System.getProperty("tda.mvn", WINDOWS ? "mvn.cmd" : "mvn"), "-o", "test",
				"-Denvironment=" + environment, "-DsuiteXmlFile=" + suite)
				.directory(selenium.toFile()).redirectErrorStream(true).redirectOutput(work.resolve("selenium.log").toFile());
		List<String> pwCommand = new ArrayList<>(List.of(WINDOWS ? "npx.cmd" : "npx", "playwright", "test",
				"--config=playwright.testim-webstore.config.ts", "--reporter=line,json"));
		pwCommand.addAll(pwSpecs);
		ProcessBuilder playwrightRun = new ProcessBuilder(pwCommand)
				.directory(playwright.toFile()).redirectErrorStream(true).redirectOutput(work.resolve("playwright.log").toFile());
		playwrightRun.environment().put("PLAYWRIGHT_JSON_OUTPUT_NAME", pwReport.toString());
		for (ProcessBuilder pb : List.of(seleniumRun, playwrightRun)) {
			pb.environment().put("TDA_HOME", root.toString());
		}

		// Both suites at the same time: parallel runs share one TDA home, its leases and the test database.
		Instant started = Instant.now();
		Process s = seleniumRun.start();
		Process p = playwrightRun.start();
		boolean done = s.waitFor(90, TimeUnit.MINUTES) & p.waitFor(90, TimeUnit.MINUTES);
		assertTrue(done, "the pilot suites did not finish within 90 minutes (see work/phase4/*.log)");

		List<PilotSuitesIT.Run> runs = new ArrayList<>();
		runs.addAll(PilotSuitesIT.seleniumRuns(testngResults.toFile()));
		runs.addAll(PilotSuitesIT.playwrightRuns(pwReport));
		return new Result(started, selenium, pwReport, List.copyOf(runs));
	}
}
