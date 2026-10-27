package com.amla.testdata.checks;

import com.amla.testdata.guard.DenyList;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exit criterion: the project's access checklist (docs/access-checklist.md) complete, backed by config checks. */
class AccessChecklistTest {

	@Test
	void everyChecklistItemIsTicked() {
		String section = RepoRoot.section("docs/access-checklist.md", "## Access checklist");
		List<String> open = section.lines().map(String::trim).filter(l -> l.startsWith("- [ ]")).toList();
		assertTrue(section.lines().anyMatch(l -> l.trim().startsWith("- [")), "checklist not found");
		assertTrue(open.isEmpty(), "unticked access checklist items:\n" + String.join("\n", open));
	}

	/** Backs "Production deny-list configured and tested": the real config must load (fails closed while hosts are empty). */
	@Test
	void productionDenyListIsConfigured() {
		assertDoesNotThrow(() -> DenyList.load(RepoRoot.path().resolve("config/deny-list.yaml")));
	}

	/** Backs "Column mask list agreed for customer data". */
	@Test
	void columnMaskListIsAgreed() throws IOException {
		JsonNode root = new ObjectMapper(new YAMLFactory()).readTree(RepoRoot.path().resolve("config/column-mask.yaml").toFile());
		assertTrue(root != null && root.path("columns").isArray() && !root.path("columns").isEmpty(),
				"config/column-mask.yaml has no columns yet");
	}
}
