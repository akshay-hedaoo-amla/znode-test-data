package com.amla.testdata.checks.phase2;

import com.amla.testdata.config.Env;
import com.amla.testdata.db.ReadOnlyConnectionFactory;
import com.amla.testdata.guard.DenyList;
import com.amla.testdata.guard.EnvironmentGuard;
import com.amla.testdata.queries.SchemaFreshness;
import com.amla.testdata.profiler.SnapshotService;
import com.amla.testdata.profiler.SqlServerCatalogReader;
import com.amla.testdata.queries.JdbcSqlExecutor;
import com.amla.testdata.queries.RepositoryData;
import com.amla.testdata.resolver.DbResolver;
import com.amla.testdata.resolver.api.DataRequirement;
import com.amla.testdata.resolver.api.Framework;
import com.amla.testdata.resolver.api.ResolveRequest;
import com.amla.testdata.resolver.api.ResolveResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.sql.Connection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exit criteria (live, -P phase2-exit,test-env): every read-only need of every approved pilot requirement resolves
 * through the deterministic resolver from approved queries, nothing is reported as a miss (the resolver records misses
 * instead of calling anything; its isolation is proven by ResolverIsolationTest), and two consecutive runs with fresh
 * resolvers and different run IDs return identical data.
 */
class PilotResolutionIT {

	private static final ObjectMapper JSON = new ObjectMapper();

	@Test
	void readOnlyPilotNeedsResolveConsistentlyWithoutMisses() throws Exception {
		Path root = Path.of(System.getProperty("tda.repoRoot")).toAbsolutePath().normalize();
		Env env = Env.load(root);
		ReadOnlyConnectionFactory connections = new ReadOnlyConnectionFactory(env,
				new EnvironmentGuard(DenyList.load(root.resolve("config/deny-list.yaml"))));
		SchemaFreshness fingerprint = new SnapshotService(new SqlServerCatalogReader(connections), root.resolve("work/db/schema-snapshot.json")).current().snapshot().freshness();
		RepositoryData data = RepositoryData.load(root.resolve("repository"));

		List<ResolveRequest> requests = new ArrayList<>();
		for (JsonNode req : data.requirements()) {
			if (!data.isApproved(req)) {
				continue;
			}
			List<DataRequirement> needs = new ArrayList<>();
			for (JsonNode n : req.path("requires")) {
				if (data.isReadOnly(n.path("key").asText())) {
					@SuppressWarnings("unchecked")
					Map<String, Object> params = n.has("params") ? JSON.convertValue(n.get("params"), LinkedHashMap.class) : null;
					needs.add(new DataRequirement(n.path("key").asText(), params, n.path("alias").asText()));
				}
			}
			if (!needs.isEmpty()) {
				requests.add(new ResolveRequest("run-a", req.path("testId").asText(),
						Framework.valueOf(req.path("framework").asText().toUpperCase()), needs));
			}
		}
		assertFalse(requests.isEmpty(), "no approved pilot requirement has read-only needs");

		List<String> misses = new ArrayList<>();
		try (Connection c = connections.open()) {
			JdbcSqlExecutor executor = new JdbcSqlExecutor(c);
			DbResolver first = new DbResolver(data, executor, fingerprint, (k, r) -> misses.add(k + ": " + r), Instant::now);
			DbResolver second = new DbResolver(data, executor, fingerprint, (k, r) -> misses.add(k + ": " + r), Instant::now);
			for (ResolveRequest request : requests) {
				ResolveResponse a = first.resolve(request);
				ResolveResponse b = second.resolve(new ResolveRequest("run-b", request.testId(), request.framework(), request.requires()));
				assertTrue(a.unavailable().isEmpty(), request.testId() + " unavailable: " + a.unavailable());
				assertEquals(request.requires().size(), a.data().size(), request.testId() + " resolved " + a.data().keySet());
				assertEquals(a.data(), b.data(), request.testId() + ": two runs resolved different records");
			}
		}
		assertTrue(misses.isEmpty(), "resolver recorded misses: " + misses);
	}
}
