/*
  Dedicated SQL login for the LOCAL Znode API stack (gateway, v1, v2 started by the znode-core launcher), so the
  launcher no longer needs `sa`. Used only by those local services, never by the test data agents (they keep the
  read-only tda_reader).

  Run by a human in SSMS, connected to ACL-283\SQL2022 with an admin login (Windows authentication is fine).
  It refuses to run on any other server.

  What it does:
    - creates login [znode_local_api] with a random password generated in SQL Server (shown ONCE at the end);
    - maps it to [wbst_z10_at_16_10_2026_Hotfix_10_14_0_7] only, as its default database;
    - grants db_datareader, db_datawriter and EXECUTE on that database - nothing on any other database,
      no server roles, no schema changes;
    - does nothing if the login already exists (re-running is safe).

  Afterwards, in C:\Users\akshay.hedaoo\.znode_sphere_config\user_config.json set:
        "Db_Sql_User": "znode_local_api",
        "Db_Sql_Password": "<the password shown>"
  then clear the SSMS result. If a local API later fails with a permission error (some Znode procedures create or
  truncate tables), tell Claude which statement failed; grant only that, never db_owner or sysadmin.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;

DECLARE @ExpectedServer sysname = N'ACL-283\SQL2022';
DECLARE @Database sysname = N'wbst_z10_at_16_10_2026_Hotfix_10_14_0_7';
DECLARE @Login sysname = N'znode_local_api';

IF @@SERVERNAME <> @ExpectedServer
BEGIN
    RAISERROR('This script is for the local copy on %s only; this server is %s. Nothing changed.', 16, 1, @ExpectedServer, @@SERVERNAME);
    RETURN;
END;

IF DB_ID(@Database) IS NULL
BEGIN
    RAISERROR('Database %s not found. Nothing changed.', 16, 1, @Database);
    RETURN;
END;

IF EXISTS (SELECT 1 FROM sys.server_principals WHERE name = @Login)
BEGIN
    PRINT 'Login ' + @Login + ' already exists; nothing changed. Use ALTER LOGIN [' + @Login + '] in SSMS to give it a new password.';
    RETURN;
END;

-- Upper and lower case, digit and symbol, so it meets the Windows password policy.
DECLARE @Password nvarchar(64) = N'Zl!' + LOWER(LEFT(CONVERT(nvarchar(64), CRYPT_GEN_RANDOM(16), 2), 16))
                                 + CONVERT(nvarchar(64), CRYPT_GEN_RANDOM(16), 2);

DECLARE @sql nvarchar(max) =
    N'CREATE LOGIN ' + QUOTENAME(@Login) + N' WITH PASSWORD = ' + QUOTENAME(@Password, '''')
  + N', DEFAULT_DATABASE = ' + QUOTENAME(@Database) + N', CHECK_POLICY = ON, CHECK_EXPIRATION = OFF;';
EXEC (@sql);

SET @sql = N'USE ' + QUOTENAME(@Database) + N';
CREATE USER ' + QUOTENAME(@Login) + N' FOR LOGIN ' + QUOTENAME(@Login) + N';
ALTER ROLE db_datareader ADD MEMBER ' + QUOTENAME(@Login) + N';
ALTER ROLE db_datawriter ADD MEMBER ' + QUOTENAME(@Login) + N';
GRANT EXECUTE TO ' + QUOTENAME(@Login) + N';';
EXEC (@sql);

-- Shown once. Put it in the Sphere config (Db_Sql_User / Db_Sql_Password), then clear this result.
SELECT @Login AS Db_Sql_User, @Password AS Db_Sql_Password, @Database AS [Database];
