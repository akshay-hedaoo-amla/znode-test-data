package com.amla.testdata.checks;

import com.amla.testdata.profiler.ColumnMask;
import com.amla.testdata.profiler.EntityMap;
import com.amla.testdata.profiler.LookupQueryBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** This project's config/column-mask.yaml: customer data is masked, and no column the entity map reads is hidden by accident. */
class ColumnMaskConfigTest {

	private static final Pattern AGGREGATE = Pattern.compile("^(SUM|COUNT|MIN|MAX)\\((DISTINCT )?(.+)\\)$");
	private final ColumnMask mask = ColumnMask.load(RepoRoot.path().resolve("config/column-mask.yaml"));

	@Test
	void customerDataIsMaskedAndKeyColumnsAreNot() throws IOException {
		JsonNode cases;
		try (InputStream in = ColumnMaskConfigTest.class.getClassLoader().getResourceAsStream("mask/column-cases.json")) {
			cases = new ObjectMapper().readTree(in);
		}
		cases.get("masked").forEach(c -> assertTrue(mask.test(c.asText()), "should be masked: " + c.asText()));
		cases.get("notMasked").forEach(c -> assertFalse(mask.test(c.asText()), "should not be masked: " + c.asText()));
	}

	@Test
	void entityMapPropertiesAreNotHiddenByTheMask() {
		EntityMap map = EntityMap.load(RepoRoot.path().resolve("repository/entity-map"));
		List<String> hidden = new ArrayList<>();
		map.entries().values().forEach(e -> e.properties().forEach((property, ref) -> {
			var agg = AGGREGATE.matcher(ref);
			var ifNull = LookupQueryBuilder.IF_NULL.matcher(ref);
			String column = agg.matches() ? agg.group(3) : ifNull.matches() ? ifNull.group(1) : ref;
			String[] parts = column.split("\\.");
			String qualified = parts.length == 2 ? e.aliases().get(parts[0]) + "." + parts[1] : column;
			// A property the entry itself lists as masked is deliberate: it is only filtered or counted on (e.g. the
			// user name that carries the run ID tag), never read back.
			if (mask.test(qualified) && !e.maskedColumns().contains(qualified.toLowerCase())) {
				hidden.add(e.entity() + "." + property + " -> " + qualified);
			}
		}));
		assertTrue(hidden.isEmpty(), "entity-map properties the profiler can no longer read (map them elsewhere or drop them): " + hidden);
	}
}
