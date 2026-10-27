package com.amla.testdata.checks.phase3;

import com.amla.testdata.api.HttpApiClient;
import com.amla.testdata.collections.SpecFingerprints;
import com.amla.testdata.collections.TaggedRecordQuery;
import com.amla.testdata.config.Env;
import com.amla.testdata.db.ReadOnlyConnectionFactory;
import com.amla.testdata.guard.DenyList;
import com.amla.testdata.guard.EnvironmentGuard;
import com.amla.testdata.queries.SchemaFreshness;
import com.amla.testdata.profiler.SnapshotService;
import com.amla.testdata.profiler.SqlServerCatalogReader;
import com.amla.testdata.queries.JdbcSqlExecutor;
import com.amla.testdata.queries.RepositoryData;
import com.amla.testdata.resolver.ApiResolver;
import com.amla.testdata.resolver.DbResolver;
import com.amla.testdata.resolver.RunLedger;
import com.amla.testdata.resolver.api.DataRequirement;
import com.amla.testdata.resolver.api.Framework;
import com.amla.testdata.resolver.api.ResolveRequest;
import com.amla.testdata.resolver.api.ResolveResponse;
import com.amla.testdata.resolver.api.TeardownResult;
import com.amla.testdata.validator.SqlQueryValidator;
import com.amla.testdata.validator.ValidatorSettings;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.sql.Connection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exit criteria (live, -P phase3-exit,test-env, dedicated API client):
 * <ul>
 *   <li>every consumable pilot key can be created fresh per test: each consumable need of each approved pilot
 *   requirement is resolved for two tests in one run, giving two different records, each found in the DB by its tag;</li>
 *   <li>after a run, no records tagged with that run ID remain: teardown reports no failures and the read-only count
 *   of tagged records is zero.</li>
 * </ul>
 * Writes go only through approved collections and the write gate; teardown always runs.
 */
class ConsumableLifecycleIT {

	private static final ObjectMapper JSON = new ObjectMapper();

	@Test
	void consumableNeedsAreCreatedFreshPerTestAndTornDown() throws Exception {
		Path root = Path.of(System.getProperty("tda.repoRoot")).toAbsolutePath().normalize();
		Env env = Env.load(root);
		ReadOnlyConnectionFactory connections = new ReadOnlyConnectionFactory(env,
				new EnvironmentGuard(DenyList.load(root.resolve("config/deny-list.yaml"))));
		SchemaFreshness schemaFingerprint = new SnapshotService(new SqlServerCatalogReader(connections), root.resolve("work/db/schema-snapshot.json")).current().snapshot().freshness();
		RepositoryData data = RepositoryData.load(root.resolve("repository"));
		SqlQueryValidator validator = new SqlQueryValidator(ValidatorSettings.load(root.resolve("config/validator.yaml")));
		HttpApiClient api = HttpApiClient.create(root, env, data.approvedWriteOperations());

		List<ResolveRequest> pilots = new ArrayList<>();
		for (JsonNode req : data.requirements()) {
			if (!data.isApproved(req)) {
				continue;
			}
			List<DataRequirement> needs = new ArrayList<>();
			for (JsonNode n : req.path("requires")) {
				if (data.isConsumable(n.path("key").asText())) {
					@SuppressWarnings("unchecked")
					Map<String, Object> params = n.has("params") ? JSON.convertValue(n.get("params"), LinkedHashMap.class) : null;
					needs.add(new DataRequirement(n.path("key").asText(), params, n.path("alias").asText()));
				}
			}
			if (!needs.isEmpty()) {
				pilots.add(new ResolveRequest("unused", req.path("testId").asText(), Framework.valueOf(req.path("framework").asText().toUpperCase()), needs));
			}
		}
		assertFalse(pilots.isEmpty(), "no approved pilot requirement has consumable needs");

		String runId = "tda-it-" + Instant.now().toEpochMilli();
		String tag = "TDA-" + runId + "-";
		List<String> misses = new ArrayList<>();
		try (Connection c = connections.open()) {
			JdbcSqlExecutor sql = new JdbcSqlExecutor(c);
			DbResolver db = new DbResolver(data, sql, schemaFingerprint, (k, r) -> misses.add(k + ": " + r), Instant::now);
			ApiResolver resolver = new ApiResolver(data, db, api, SpecFingerprints.current(root), new RunLedger(root.resolve("work/runs")),
					(k, r) -> misses.add(k + ": " + r), Instant::now, new SecureRandom());
			Map<String, Integer> createdPerKey = new LinkedHashMap<>();
			TeardownResult teardown;
			try {
				for (ResolveRequest pilot : pilots) {
					ResolveResponse a = resolver.resolve(new ResolveRequest(runId, pilot.testId(), pilot.framework(), pilot.requires()));
					ResolveResponse b = resolver.resolve(new ResolveRequest(runId, pilot.testId() + " (second test)", pilot.framework(), pilot.requires()));
					assertTrue(a.unavailable().isEmpty() && b.unavailable().isEmpty(), pilot.testId() + " unavailable: " + a.unavailable() + b.unavailable());
					for (DataRequirement need : pilot.requires()) {
						assertNotEquals(a.data().get(need.alias()), b.data().get(need.alias()), need.key() + ": two tests got the same record");
						createdPerKey.merge(need.key(), 2, Integer::sum);
					}
				}
				createdPerKey.forEach((key, created) -> assertTrue(tagged(root, data, key, tag, sql, validator) >= created,
						key + ": created records not found in the DB by their tag"));
			} finally {
				teardown = resolver.teardown(runId);
			}
			assertTrue(teardown.failedRecordIds().isEmpty(), "teardown failed for " + teardown.failedRecordIds());
			for (String key : createdPerKey.keySet()) {
				assertEquals(0, tagged(root, data, key, tag, sql, validator), key + ": records tagged " + tag + " remain after teardown");
			}
		}
		assertTrue(misses.isEmpty(), "resolver recorded misses: " + misses);
	}

	private static long tagged(Path root, RepositoryData data, String key, String tag, JdbcSqlExecutor sql, SqlQueryValidator validator) {
		String entity = data.vocabulary().get(key).path("entity").asText();
		JsonNode entityMap = entityMap(root, entity);
		String property = data.collections().get(key).at("/create/runIdTag/property").asText();
		List<Map<String, Object>> rows = sql.query(TaggedRecordQuery.sql(entityMap, property, validator), TaggedRecordQuery.params(tag),
				List.of(TaggedRecordQuery.COUNT_OUTPUT));
		return ((Number) rows.get(0).get(TaggedRecordQuery.COUNT_OUTPUT)).longValue();
	}

	private static JsonNode entityMap(Path root, String entity) {
		try (Stream<Path> files = Files.list(root.resolve("repository/entity-map"))) {
			for (Path f : files.filter(p -> p.toString().endsWith(".json")).toList()) {
				JsonNode n = JSON.readTree(f.toFile());
				if (entity.equals(n.path("entity").asText())) {
					return n;
				}
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		throw new IllegalStateException("no entity map for " + entity);
	}
}
