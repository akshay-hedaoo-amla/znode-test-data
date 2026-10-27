package com.amla.testdata.checks;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exit criterion: pilot test classes and spec files listed in docs/memory.md.
 * Expected format under "## Pilot scope":
 * <pre>
 * - Selenium pilot: &lt;module&gt;
 *   - path/to/SomeTest.java
 * - Playwright pilot: &lt;module&gt;
 *   - path/to/some.spec.ts
 * </pre>
 */
class PilotScopeTest {

	@Test
	void pilotsAreChosenAndListed() {
		String section = RepoRoot.section("docs/memory.md", "## Pilot scope");
		assertFalse(section.contains("_to be chosen_"), "Pilot scope in docs/memory.md still says _to be chosen_");

		List<String> selenium = new ArrayList<>();
		List<String> playwright = new ArrayList<>();
		List<String> current = null;
		for (String line : section.split("\\R")) {
			String trimmed = line.trim();
			if (trimmed.startsWith("- Selenium pilot:")) {
				current = selenium;
			} else if (trimmed.startsWith("- Playwright pilot:")) {
				current = playwright;
			} else if (current != null && line.startsWith("  ") && trimmed.startsWith("- ")) {
				current.add(trimmed.substring(2).replace("`", "").trim());
			}
		}
		assertTrue(selenium.stream().anyMatch(s -> s.endsWith(".java")), "no Selenium pilot test classes (*.java) listed");
		assertTrue(playwright.stream().anyMatch(s -> s.endsWith(".spec.ts")), "no Playwright pilot spec files (*.spec.ts) listed");
	}
}
