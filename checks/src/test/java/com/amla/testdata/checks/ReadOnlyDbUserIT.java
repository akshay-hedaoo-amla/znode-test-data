package com.amla.testdata.checks;

import com.amla.testdata.config.Env;
import com.amla.testdata.db.ReadOnlyConnectionFactory;
import com.amla.testdata.guard.DenyList;
import com.amla.testdata.guard.EnvironmentGuard;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Backs "Read-only DB user created and tested". Runs only with -P phase0-exit,test-env against a
 * QA SQL Server. Credentials come from environment variables or the git-ignored .env (see .env.example). The
 * environment guard runs before any connection is opened. This check reads metadata only.
 *
 * <p>The one tolerated exception (security_and_access_doc.md section 3, config/db-write-exceptions.yaml): the listed
 * shared logins may hold INSERT/UPDATE/DELETE on the listed tables only. Any database-level write right, writer/owner
 * role, or object write grant on any other table still fails. The agents never write to those tables either.
 */
class ReadOnlyDbUserIT {

	private static final List<String> REQUIRED = List.of("TDA_ENV_NAME", "TDA_DB_HOST", "TDA_DB_NAME", "TDA_DB_USER", "TDA_DB_PASSWORD");

	private static final Pattern WRITE_PERMISSION = Pattern.compile(
			"(?i)^(INSERT|UPDATE|DELETE|ALTER.*|CREATE.*|EXECUTE.*|CONTROL.*|TAKE OWNERSHIP|IMPERSONATE.*|BACKUP.*|ADMINISTER.*)$");
	private static final Set<String> TABLE_DATA_WRITES = Set.of("INSERT", "UPDATE", "DELETE");

	private static final String DATABASE_PERMISSIONS = "SELECT permission_name FROM fn_my_permissions(NULL, 'DATABASE')";

	// Explicit grants to this user, its roles and public, on the database, schemas and user objects, with their target.
	private static final String GRANTED_PERMISSIONS = """
			SELECT p.permission_name, p.class,
			       CASE WHEN p.class = 1 THEN OBJECT_SCHEMA_NAME(p.major_id) + '.' + OBJECT_NAME(p.major_id) END AS target
			FROM sys.database_permissions p
			WHERE p.state IN ('G', 'W')
			  AND (p.class IN (0, 3) OR (p.class = 1 AND OBJECTPROPERTY(p.major_id, 'IsMSShipped') = 0))
			  AND p.grantee_principal_id IN (
			      SELECT DATABASE_PRINCIPAL_ID()
			      UNION SELECT 0
			      UNION SELECT r.role_principal_id FROM sys.database_role_members r
			            WHERE r.member_principal_id = DATABASE_PRINCIPAL_ID())""";

	private static final String ROLES = """
			SELECT IS_SRVROLEMEMBER('sysadmin'), IS_ROLEMEMBER('db_owner'), IS_ROLEMEMBER('db_datawriter'),
			       IS_ROLEMEMBER('db_ddladmin'), IS_ROLEMEMBER('db_securityadmin')""";

	@Test
	void configuredDbUserHasNoWriteRightsBeyondTheDocumentedException() throws SQLException, IOException {
		// Environment variables first, then the git-ignored .env; the guard runs inside open().
		Env env = Env.load(RepoRoot.path());
		List<String> missing = REQUIRED.stream().filter(k -> env.get(k).isEmpty()).toList();
		assertTrue(missing.isEmpty(), "set these in .env or as environment variables (see .env.example): " + missing);
		ReadOnlyConnectionFactory connections = new ReadOnlyConnectionFactory(env,
				new EnvironmentGuard(DenyList.load(RepoRoot.path().resolve("config/deny-list.yaml"))));
		JsonNode exception = new ObjectMapper(new YAMLFactory()).readTree(RepoRoot.path().resolve("config/db-write-exceptions.yaml").toFile());
		Set<String> exceptionLogins = lowerCase(exception.path("logins"));
		Set<String> exceptionTables = lowerCase(exception.path("tables"));
		boolean excepted = exceptionLogins.contains(env.require("TDA_DB_USER").toLowerCase(Locale.ROOT));

		try (Connection connection = connections.open(); Statement statement = connection.createStatement()) {
			statement.setQueryTimeout(ReadOnlyConnectionFactory.QUERY_TIMEOUT_SECONDS);

			List<String> offending = new ArrayList<>();
			try (ResultSet rs = statement.executeQuery(DATABASE_PERMISSIONS)) {
				while (rs.next()) {
					if (WRITE_PERMISSION.matcher(rs.getString(1)).matches()) {
						offending.add(rs.getString(1) + " on the database");
					}
				}
			}
			try (ResultSet rs = statement.executeQuery(GRANTED_PERMISSIONS)) {
				while (rs.next()) {
					String permission = rs.getString(1);
					String target = rs.getString(3);
					if (!WRITE_PERMISSION.matcher(permission).matches()) {
						continue;
					}
					boolean allowed = excepted && rs.getInt(2) == 1 && target != null
							&& TABLE_DATA_WRITES.contains(permission.toUpperCase(Locale.ROOT))
							&& exceptionTables.contains(target.toLowerCase(Locale.ROOT));
					if (!allowed) {
						offending.add(permission + (target == null ? " (database/schema)" : " on " + target));
					}
				}
			}
			assertTrue(offending.isEmpty(), "DB user holds write/DDL/execute rights outside config/db-write-exceptions.yaml: "
					+ new TreeSet<>(offending));

			try (ResultSet rs = statement.executeQuery(ROLES)) {
				assertTrue(rs.next());
				String[] roles = {"sysadmin", "db_owner", "db_datawriter", "db_ddladmin", "db_securityadmin"};
				for (int i = 0; i < roles.length; i++) {
					assertEquals(0, rs.getInt(i + 1), "DB user is a member of " + roles[i]);
				}
			}
		}
	}

	private static Set<String> lowerCase(JsonNode list) {
		Set<String> out = new HashSet<>();
		list.forEach(n -> out.add(n.asText().trim().toLowerCase(Locale.ROOT)));
		return out;
	}
}
