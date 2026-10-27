package com.amla.testdata.checks;

import com.amla.testdata.schema.RepositorySchemaValidator;
import com.amla.testdata.schema.SchemaError;
import com.amla.testdata.validator.SqlQueryValidator;
import com.amla.testdata.validator.ValidatorSettings;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every repository/ entry passes its schema and the cross-file rules (the same check as `bin/tda validate`). */
class RepositoryValidationTest {

	@Test
	void repositoryEntriesAreValid() {
		RepositorySchemaValidator validator = new RepositorySchemaValidator(new SqlQueryValidator(
				ValidatorSettings.load(RepoRoot.path().resolve("config/validator.yaml"))));
		List<SchemaError> errors = validator.validateRepository(RepoRoot.path().resolve("repository"));
		assertTrue(errors.isEmpty(), "repository errors:\n" + String.join("\n", errors.stream().map(SchemaError::toString).toList()));
	}
}
