package com.amla.testdata.checks.phase3;

import com.amla.testdata.collections.SpecFingerprints;
import com.amla.testdata.queries.RepositoryData;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exit criterion "collections are approved before the resolver uses them": every consumable pilot key has an
 * approved, verified collection built for the current exported spec. (ApiResolverTest proves the resolver sends
 * nothing for a pending, stale or missing collection.)
 */
class ApprovedCollectionsTest {

	@Test
	void everyConsumablePilotKeyHasAnApprovedCurrentCollection() {
		Path root = Path.of(System.getProperty("tda.repoRoot")).toAbsolutePath().normalize();
		RepositoryData data = RepositoryData.load(root.resolve("repository"));
		Function<JsonNode, String> current = SpecFingerprints.current(root);
		List<String> keys = new ArrayList<>(data.consumableNeeds().keySet());
		assertFalse(keys.isEmpty(), "approved pilot requirements need no consumable keys?");

		List<String> problems = new ArrayList<>();
		for (String key : keys) {
			JsonNode c = data.collections().get(key);
			if (c == null || !c.has("create")) {
				problems.add(key + ": no collection that creates it");
			} else if (!data.isApproved(c) || c.path("approvedBy").asText().isBlank()) {
				problems.add(key + ": not approved (status " + c.path("status").asText() + ")");
			} else if (!c.hasNonNull("lastValidated")) {
				problems.add(key + ": never verified (run collection-verify)");
			} else if (!c.path("specFingerprint").asText().equals(current.apply(c))) {
				problems.add(key + ": built for another spec, or the spec file is missing (stale)");
			} else if (data.payloadTemplate(key) == null) {
				problems.add(key + ": payload template missing");
			}
		}
		assertTrue(problems.isEmpty(), "consumable pilot keys without an approved, verified, current collection:\n" + String.join("\n", problems));
	}
}
