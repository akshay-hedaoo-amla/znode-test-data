package com.amla.testdata.checks.phase2;

import com.amla.testdata.profiler.SchemaSnapshot;
import com.amla.testdata.queries.SchemaFreshness;
import com.amla.testdata.profiler.SnapshotService;
import com.amla.testdata.checks.phase4.PilotSources;
import com.amla.testdata.queries.RepositoryData;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exit criterion: all read-only pilot keys have approved, verified queries built for the current schema. */
class ApprovedQueriesTest {

	@Test
	void everyReadOnlyPilotKeyHasAnApprovedVerifiedQuery() {
		Path root = Path.of(System.getProperty("tda.repoRoot")).toAbsolutePath().normalize();
		RepositoryData data = RepositoryData.load(root.resolve("repository"));
		SchemaFreshness freshness = SnapshotService.load(root.resolve("work/db/schema-snapshot.json")).map(SchemaSnapshot::freshness)
				.orElseThrow(() -> new AssertionError("no schema snapshot in work/db (run: snapshot)"));
		// Keys the approved pilot requirements need (config/scanners.yaml pilots; Phase 6 modules have their own checks).
		java.util.Set<String> pilotKeys = new java.util.TreeSet<>();
		data.requirements().stream()
				.filter(r -> data.isApproved(r) && PilotSources.isPilot(r.path("framework").asText(), r.path("testId").asText()))
				.forEach(r -> r.path("requires").forEach(n -> pilotKeys.add(n.path("key").asText())));
		List<String> keys = new ArrayList<>(data.readOnlyNeeds().keySet());
		keys.retainAll(pilotKeys);
		assertFalse(keys.isEmpty(), "approved pilot requirements need no read-only keys?");

		List<String> problems = new ArrayList<>();
		for (String key : keys) {
			JsonNode q = data.queries().get(key);
			if (q == null) {
				problems.add(key + ": no query");
			} else if (!data.isApproved(q) || q.path("approvedBy").asText().isBlank()) {
				problems.add(key + ": not approved (status " + q.path("status").asText() + ")");
			} else if (!q.hasNonNull("lastValidated")) {
				problems.add(key + ": never verified (run query-verify)");
			} else if (!freshness.isCurrent(q)) {
				problems.add(key + ": the tables it reads changed since it was built (stale)");
			}
		}
		assertTrue(problems.isEmpty(), "read-only pilot keys without an approved, verified, current query:\n" + String.join("\n", problems));
	}
}
