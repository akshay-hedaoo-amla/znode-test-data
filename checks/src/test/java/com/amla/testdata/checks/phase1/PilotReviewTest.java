package com.amla.testdata.checks.phase1;

import com.amla.testdata.scan.ScanResult;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exit criteria: every pilot test has a scenario summary; low-confidence or conflicting ones were reviewed; every
 * mapping was reviewed by a human (status approved with approver), with corrections recorded in docs/memory.md.
 */
class PilotReviewTest {

	@Test
	void everyPilotEntryIsReviewedByAHuman() {
		Map<String, JsonNode> requirements = PilotInputs.requirements();
		List<String> problems = new ArrayList<>();
		for (ScanResult scan : PilotInputs.pilotScans()) {
			for (ScanResult.ScannedTest t : scan.tests()) {
				JsonNode req = requirements.get(t.testId());
				if (req == null) {
					problems.add(t.testId() + ": no requirements entry");
					continue;
				}
				if (req.path("scenario").path("summary").asText().isBlank()) {
					problems.add(t.testId() + ": no scenario summary");
				}
				if (!"approved".equals(req.path("status").asText()) || req.path("approvedBy").asText().isBlank()) {
					problems.add(t.testId() + ": not reviewed (status " + req.path("status").asText() + ")");
				}
			}
		}
		assertTrue(problems.isEmpty(), "pilot entries awaiting human review:\n" + String.join("\n", problems));
	}

	@Test
	void reviewCorrectionsAreRecordedInMemory() throws Exception {
		String memory = Files.readString(PilotInputs.root().resolve("docs/memory.md"));
		int start = memory.indexOf("## Phase 1 mapping review");
		assertTrue(start >= 0, "docs/memory.md has no '## Phase 1 mapping review' section");
		String section = memory.substring(start + 1);
		int next = section.indexOf("\n## ");
		section = next >= 0 ? section.substring(0, next) : section;
		assertTrue(section.lines().anyMatch(l -> l.trim().startsWith("- ")),
				"'Phase 1 mapping review' needs at least one entry (a correction, or '- No corrections')");
	}
}
