package com.amla.testdata.checks.phase5;

import com.amla.testdata.collections.CollectionVerifier;
import com.amla.testdata.collections.SpecFingerprints;
import com.amla.testdata.collections.SpecIndex;
import com.amla.testdata.maintenance.Maintenance;
import com.amla.testdata.queries.QueryVerifier;
import com.amla.testdata.queries.RepositoryData;
import com.amla.testdata.queries.SchemaFreshness;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 5 exit criterion 2 (offline, never calls the API): a changed endpoint is detected the same way. In a temporary
 * copy of the project, one operation an approved collection calls gets a new required parameter; {@code maintain}'s
 * check marks that collection {@code STALE_SPEC} and queues it, while a prose-only edit to another collection's operation
 * and every other approved entry leave them approved. The database and the offline verifiers are replaced by "unchanged,
 * passing" stand-ins: only the spec differs.
 */
class ChangedEndpointDetectionTest {

	private static final String CHANGED = "COUPON_AMOUNT_OFF_ORDER";

	@TempDir
	Path temp;

	@Test
	void aChangedOperationMarksItsCollectionStaleAndQueuesIt() throws Exception {
		Path home = Phase5Copies.project(Phase5Copies.root(), temp.resolve("home"));
		Path repository = home.resolve("repository");
		RepositoryData before = RepositoryData.load(repository);
		JsonNode changed = before.collections().get(CHANGED);
		assertNotNull(changed, CHANGED + " collection");
		assertEquals("approved", changed.path("status").asText());

		String specFile = changed.path("spec").path("file").asText();
		String operation = changed.path("spec").path("operations").get(0).asText();
		Phase5Copies.edit(home.resolve(specFile), spec -> {
			ObjectNode op = operation(spec, operation);
			ArrayNode params = op.has("parameters") ? (ArrayNode) op.get("parameters") : op.putArray("parameters");
			params.addObject().put("name", "phase5Changed").put("in", "query").put("required", true).putObject("schema").put("type", "string");
			// Prose only: must not make any collection stale.
			before.collections().values().stream().filter(c -> !CHANGED.equals(c.path("key").asText()))
					.filter(c -> specFile.equals(c.path("spec").path("file").asText()))
					.forEach(c -> operation(spec, c.path("spec").path("operations").get(0).asText()).put("summary", "phase5 prose edit"));
		});

		Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		Maintenance.Outcome outcome = Maintenance.check(repository, before, unchangedSchema(before, repository),
				SpecFingerprints.current(home),
				(query, vocabulary, paramSets) -> new QueryVerifier.Report(query.path("key").asText(), true, false, now.toString(), List.of()),
				(collection, paramSets) -> new CollectionVerifier.Report(collection.path("key").asText(), true,
						collection.path("specFingerprint").asText(), List.of(), List.of(), now.toString()),
				Map.of(), now);
		List<Maintenance.Finding> marked = Maintenance.apply(outcome, repository, home.resolve("work/maintenance/regeneration-queue.json"), now);

		assertEquals(1, marked.size(), "only the changed collection: " + outcome.findings());
		assertEquals(CHANGED, marked.get(0).key());
		assertEquals(Maintenance.Reason.STALE_SPEC, marked.get(0).reason());
		RepositoryData after = RepositoryData.load(repository);
		assertEquals("stale", after.collections().get(CHANGED).path("status").asText());
		assertEquals("STALE_SPEC", after.collections().get(CHANGED).path("staleReason").asText());
		after.collections().forEach((key, c) -> assertTrue(CHANGED.equals(key) || "approved".equals(c.path("status").asText()), key));
		JsonNode queue = Phase5Copies.read(home.resolve("work/maintenance/regeneration-queue.json"));
		assertEquals(1, queue.get("items").size());
		assertEquals(CHANGED, queue.at("/items/0/key").asText());
		assertEquals("open", queue.at("/items/0/status").asText());
	}

	/** "Schema unchanged": each entry's tables fingerprint to the stamp it already carries. */
	private static SchemaFreshness unchangedSchema(RepositoryData data, Path repository) throws Exception {
		Map<Set<String>, String> stamps = new HashMap<>();
		SchemaFreshness tables = new SchemaFreshness("phase5-offline", null, t -> null);
		data.queries().values().forEach(q -> stamps.put(tables.tablesOf(q), q.path("schemaFingerprint").asText()));
		for (Path f : Phase5Copies.contents(repository.resolve("entity-map")).keySet().stream().map(repository.resolve("entity-map")::resolve).toList()) {
			JsonNode m = Phase5Copies.read(f);
			stamps.put(tables.tablesOf(m), m.path("schemaFingerprint").asText());
		}
		return new SchemaFreshness("phase5-offline", null, t -> stamps.get(t));
	}

	/** The operation's node inside the spec tree (matched the way production matches it), to edit in place. */
	private static ObjectNode operation(JsonNode spec, String operation) {
		SpecIndex.Operation op = new SpecIndex(spec).find(operation);
		if (op == null) {
			throw new IllegalStateException("operation not in spec: " + operation);
		}
		return (ObjectNode) op.node();
	}
}
