# Znode test data

The Znode project's data for the test data agents: what each Selenium and Playwright test needs (requirements), the
data-type vocabulary, and the approved database queries and API collections the resolver uses to find or create that
data before a suite runs. The code is released separately
([automation-testdata-generator](https://github.com/akshay-hedaoo-amla/automation-testdata-generator)); this repository
pins the version it uses (`tda.version` in `pom.xml`, `package.json`).

- Everything an agent proposes arrives as `pending_approval`; a code owner (`.github/CODEOWNERS`) approves it by setting
  `status: approved`, `approvedBy` and `approvedAt` in a pull request.
- Setup, commands and rules: `CLAUDE.md`. Project state and decisions: `docs/memory.md`.

History: this repository starts from the code repository's commit `7169a85` (2026-10-27), where the project data and the
code still lived together.
