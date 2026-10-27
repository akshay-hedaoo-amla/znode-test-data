package com.amla.testdata.checks.phase5;

import com.amla.testdata.config.Env;
import com.amla.testdata.db.ReadOnlyConnectionFactory;
import com.amla.testdata.guard.DenyList;
import com.amla.testdata.guard.EnvironmentGuard;
import com.amla.testdata.maintenance.Maintenance;
import com.amla.testdata.profiler.MaintenanceRun;
import com.amla.testdata.validator.SqlQueryValidator;
import com.amla.testdata.validator.ValidatorSettings;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 5 exit criterion 1, live on the test environment: a deliberately broken query is detected, marked stale and
 * queued for regeneration automatically ({@code maintain}). Always on a temporary copy of the project (repository, specs,
 * config, stored schema snapshot): the real repository and queue are never touched.
 * <ul>
 * <li>an approved query given an unknown column: {@code VERIFY_FAILED};</li>
 * <li>an approved query whose filter now excludes every row: {@code ZERO_ROWS};</li>
 * <li>every other approved entry stays approved (no false alarm);</li>
 * <li>an unreachable database aborts the run and marks nothing stale (a VPN drop never invalidates the cache).</li>
 * </ul>
 * {@code /regenerate} turning the queue into pending_approval entries needs the model; it is checked by hand
 * (docs/memory.md).
 */
class BrokenQueryDetectionIT {

	private static final String BROKEN = "PRODUCT_SIMPLE_IN_STOCK";
	// No collection takes an input from it, so nothing else depends on it.
	private static final String EMPTY = "PRODUCT_SIMPLE_NOT_OF_BRAND_IN_STOCK";
	private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");

	@TempDir
	Path temp;

	@Test
	void aBrokenAndAnEmptyQueryAreMarkedStaleAndQueued() throws Exception {
		Path root = Phase5Copies.root();
		Path home = Phase5Copies.project(root, temp.resolve("home"));
		Path broken = home.resolve("repository/queries/" + BROKEN + ".json");
		Path empty = home.resolve("repository/queries/" + EMPTY + ".json");
		Phase5Copies.edit(broken, q -> q.put("sql", q.get("sql").asText().replaceFirst("SELECT TOP 10 ",
				"SELECT TOP 10 p.Phase5NoSuchColumn AS phase5Broken, ")));
		// The stock threshold is a constant: no product has this much stock.
		Phase5Copies.edit(empty, q -> ((com.fasterxml.jackson.databind.node.ObjectNode) q.get("constants")).put("minStock", "1000000000"));

		ReadOnlyConnectionFactory connections = new ReadOnlyConnectionFactory(Env.load(root),
				new EnvironmentGuard(DenyList.load(root.resolve("config/deny-list.yaml"))));
		SqlQueryValidator validator = new SqlQueryValidator(ValidatorSettings.load(root.resolve("config/validator.yaml")));
		MaintenanceRun.Result r = MaintenanceRun.run(home, home, connections, validator, false);

		assertEquals(null, r.outcome().aborted(), "maintenance aborted");
		Map<String, Maintenance.Reason> marked = new java.util.TreeMap<>();
		r.markedStale().forEach(f -> marked.put(f.key(), f.reason()));
		assertEquals(Map.of(BROKEN, Maintenance.Reason.VERIFY_FAILED, EMPTY, Maintenance.Reason.ZERO_ROWS), marked,
				"only the two broken queries are marked, with their reasons: " + r.outcome().findings());
		for (Path file : List.of(broken, empty)) {
			JsonNode q = Phase5Copies.read(file);
			assertEquals("stale", q.get("status").asText(), file.getFileName().toString());
			assertTrue(q.hasNonNull("staleReason") && q.hasNonNull("staleSince"));
		}
		JsonNode queue = Phase5Copies.read(home.resolve("work/maintenance/regeneration-queue.json"));
		List<String> queued = new ArrayList<>();
		queue.get("items").forEach(i -> queued.add(i.get("key").asText() + ":" + i.get("status").asText()));
		assertEquals(List.of(BROKEN + ":open", EMPTY + ":open").stream().sorted().toList(), queued.stream().sorted().toList());
		assertTrue(Files.isRegularFile(r.report()));
	}

	@Test
	void anUnreachableDatabaseAbortsWithoutMarkingAnything() throws Exception {
		Path root = Phase5Copies.root();
		Path home = Phase5Copies.project(root, temp.resolve("down"));
		Map<String, String> before = Phase5Copies.contents(home.resolve("repository"));

		// The real maintain command, run in the copy with the database pointed at a closed local port. Environment
		// variables win over .env, and the copy has no .env: no credential is involved.
		List<String> cmd = List.of(Path.of(System.getProperty("java.home"), "bin", WINDOWS ? "java.exe" : "java").toString(),
				"-cp", System.getProperty("java.class.path"), "com.amla.testdata.profiler.ProfilerCli", "maintain");
		ProcessBuilder pb = new ProcessBuilder(cmd).directory(home.toFile()).redirectErrorStream(true);
		Map<String, String> env = pb.environment();
		env.put("TDA_ENV_NAME", Env.load(root).require("TDA_ENV_NAME"));
		env.put("TDA_DB_HOST", "127.0.0.1");
		env.put("TDA_DB_PORT", "1");
		env.put("TDA_DB_NAME", "phase5-unreachable");
		env.put("TDA_DB_USER", "phase5-unreachable");
		env.put("TDA_DB_PASSWORD", "phase5-unreachable");
		Process p = pb.start();
		String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		assertTrue(p.waitFor(5, TimeUnit.MINUTES), "maintain did not finish");

		assertTrue(out.contains("ABORTED, nothing marked stale"), out);
		assertEquals(before, Phase5Copies.contents(home.resolve("repository")), "no repository file changed");
		assertFalse(Files.exists(home.resolve("work/maintenance/regeneration-queue.json")), "nothing queued");
	}
}
