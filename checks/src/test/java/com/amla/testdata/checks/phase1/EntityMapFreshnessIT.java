package com.amla.testdata.checks.phase1;

import com.amla.testdata.config.Env;
import com.amla.testdata.db.ReadOnlyConnectionFactory;
import com.amla.testdata.guard.DenyList;
import com.amla.testdata.guard.EnvironmentGuard;
import com.amla.testdata.profiler.EntityMap;
import com.amla.testdata.queries.SchemaFreshness;
import com.amla.testdata.profiler.SnapshotService;
import com.amla.testdata.profiler.SqlServerCatalogReader;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exit criterion (live, -P phase1-exit,test-env): the entity map exists for the entities the pilots use, and every
 * entry's schema fingerprint matches the tables it maps as they are live (unrelated schema changes do not stale it). The live check reuses the stored snapshot when the catalog
 * signature is unchanged, so it costs one checksum query.
 */
class EntityMapFreshnessIT {

	@Test
	void entityMapCoversPilotEntitiesWithTheLiveFingerprint() {
		Path root = PilotInputs.root();
		Env env = Env.load(root);
		ReadOnlyConnectionFactory connections = new ReadOnlyConnectionFactory(env,
				new EnvironmentGuard(DenyList.load(root.resolve("config/deny-list.yaml"))));
		SchemaFreshness live = new SnapshotService(new SqlServerCatalogReader(connections), root.resolve("work/db/schema-snapshot.json")).current().snapshot().freshness();

		EntityMap map = EntityMap.load(root.resolve("repository/entity-map"));
		assertFalse(map.entries().isEmpty(), "repository/entity-map is empty");
		List<String> stale = new ArrayList<>();
		try (var files = java.nio.file.Files.list(root.resolve("repository/entity-map"))) {
			for (Path f : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
				JsonNode node = PilotInputs.read(f);
				if (!live.isCurrent(node)) {
					stale.add(node.path("entity").asText() + " (" + node.path("schemaFingerprint").asText() + ")");
				}
			}
		} catch (java.io.IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
		assertTrue(stale.isEmpty(), "entity map entries whose tables changed (live schema " + live.wholeFingerprint() + "): " + stale);

		// Every entity the profiler needed for pilot values must be mapped.
		Set<String> unmapped = new LinkedHashSet<>();
		JsonNode profile = PilotInputs.read(root.resolve("work/profiles/profile.json"));
		profile.path("observations").forEach(o -> {
			if ("NO_ENTITY_MAP".equals(o.path("status").asText())) {
				unmapped.add(o.path("entity").asText() + "." + o.path("lookupProperty").asText());
			}
		});
		assertTrue(unmapped.isEmpty(), "pilot values need entity map entries for: " + unmapped);
	}
}
