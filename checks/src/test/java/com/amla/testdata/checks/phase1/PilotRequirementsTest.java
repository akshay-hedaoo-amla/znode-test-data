package com.amla.testdata.checks.phase1;

import com.amla.testdata.scan.ScanResult;
import com.amla.testdata.schema.RepositorySchemaValidator;
import com.amla.testdata.schema.SchemaError;
import com.amla.testdata.validator.SqlQueryValidator;
import com.amla.testdata.validator.ValidatorSettings;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exit criteria: every pilot test has a schema-valid requirements entry; no free-text data types remain (every need
 * is a vocabulary key and every scanned value is accounted for in the entry's sources).
 */
class PilotRequirementsTest {

	@Test
	void everyPilotTestHasAValidRequirementsEntry() {
		Map<String, JsonNode> requirements = PilotInputs.requirements();
		List<String> missing = new ArrayList<>();
		for (ScanResult scan : PilotInputs.pilotScans()) {
			for (ScanResult.ScannedTest t : scan.tests()) {
				if (!requirements.containsKey(t.testId())) {
					missing.add(t.testId());
				}
			}
		}
		assertTrue(missing.isEmpty(), "pilot tests without a requirements entry:\n" + String.join("\n", missing));

		RepositorySchemaValidator validator = new RepositorySchemaValidator(
				new SqlQueryValidator(ValidatorSettings.load(PilotInputs.root().resolve("config/validator.yaml"))));
		List<SchemaError> errors = validator.validateRepository(PilotInputs.root().resolve("repository"));
		assertTrue(errors.isEmpty(), "repository errors (schema, vocabulary keys, params, aliases):\n"
				+ String.join("\n", errors.stream().map(SchemaError::toString).toList()));
	}

	@Test
	void everyScannedValueIsAccountedFor() {
		Map<String, JsonNode> requirements = PilotInputs.requirements();
		List<String> unmapped = new ArrayList<>();
		for (ScanResult scan : PilotInputs.pilotScans()) {
			for (ScanResult.ScannedTest t : scan.tests()) {
				JsonNode req = requirements.get(t.testId());
				if (req == null) {
					continue; // reported by the test above
				}
				for (ScanResult.Value v : t.values()) {
					boolean found = false;
					for (JsonNode s : req.path("sources")) {
						if (s.path("location").asText().equals(v.location()) && s.path("value").asText().equals(v.value())) {
							found = true;
							break;
						}
					}
					if (!found) {
						unmapped.add(t.testId() + ": " + v.value() + " @ " + v.location());
					}
				}
			}
		}
		assertTrue(unmapped.isEmpty(), "scanned values missing from requirements sources:\n" + String.join("\n", unmapped));
	}

	@Test
	void pilotListsAgree() throws Exception {
		// docs/memory.md "Pilot scope" and config/scanners.yaml must name the same pilot files.
		String memory = Files.readString(PilotInputs.root().resolve("docs/memory.md"));
		String section = memory.substring(memory.indexOf("## Pilot scope"));
		int next = section.indexOf("\n## ", 1);
		section = next >= 0 ? section.substring(0, next) : section;
		// Only path-like entries (with a folder), not the "*.java" format hint.
		Matcher m = Pattern.compile("`([^`*\\s]+/[^`\\s]+\\.(?:java|spec\\.ts))`").matcher(section);
		List<String> fromMemory = new ArrayList<>();
		while (m.find()) {
			fromMemory.add(m.group(1).replaceFirst("^Integration/", ""));
		}
		List<String> fromConfig = new ArrayList<>();
		PilotInputs.pilots().values().forEach(fromConfig::addAll);
		assertEquals(fromConfig.stream().sorted().toList(), fromMemory.stream().distinct().sorted().toList(),
				"pilot files differ between docs/memory.md and config/scanners.yaml");
		assertTrue(Files.isRegularFile(Path.of(PilotInputs.root().toString(), "config/scanners.yaml")));
	}
}
