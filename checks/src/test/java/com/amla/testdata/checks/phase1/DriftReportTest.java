package com.amla.testdata.checks.phase1;

import com.amla.testdata.scan.ScanResult;
import com.amla.testdata.schema.EntryKind;
import com.amla.testdata.schema.RepositorySchemaValidator;
import com.amla.testdata.schema.SchemaError;
import com.amla.testdata.validator.SqlQueryValidator;
import com.amla.testdata.validator.ValidatorSettings;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exit criterion: a drift report exists for the pilot data files. */
class DriftReportTest {

	@Test
	void latestDriftReportIsValidAndCoversEveryPilotDataFile() {
		List<Path> reports = PilotInputs.jsonFiles(PilotInputs.root().resolve("reports/drift"));
		assertFalse(reports.isEmpty(), "no drift report in reports/drift (run: profile, then drift)");
		Path latest = reports.get(reports.size() - 1);

		RepositorySchemaValidator validator = new RepositorySchemaValidator(
				new SqlQueryValidator(ValidatorSettings.load(PilotInputs.root().resolve("config/validator.yaml"))));
		List<SchemaError> errors = validator.validateFile(EntryKind.DRIFT_REPORT, latest);
		assertTrue(errors.isEmpty(), latest.getFileName() + " does not match the schema: " + errors);

		Set<String> covered = new LinkedHashSet<>();
		PilotInputs.read(latest).path("sourceFiles").forEach(s -> covered.add(s.asText()));
		List<String> missing = new ArrayList<>();
		for (ScanResult scan : PilotInputs.pilotScans()) {
			for (ScanResult.DataFile f : scan.dataFiles()) {
				String id = f.sheet() == null ? f.path() : f.path() + "!" + f.sheet();
				if (!covered.contains(id)) {
					missing.add(id);
				}
			}
		}
		assertTrue(missing.isEmpty(), latest.getFileName() + " does not cover pilot data files: " + missing);

		JsonNode summary = PilotInputs.read(latest).path("summary");
		assertTrue(summary.path("ok").asInt() + summary.path("drifted").asInt() + summary.path("notFound").asInt() > 0,
				"drift report profiled nothing; check the entity map and config/profile-rules.yaml");
	}
}
