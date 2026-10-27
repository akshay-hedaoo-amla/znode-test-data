/*
  Dedicated Znode API client for the test data agents (security_and_access_doc.md §10).
  Replaces the shared ClientId 9 for every API write the resolver makes.

  Run by a human with an admin login on the `at` database ONLY. Never on production.
  The agents' own DB user (tda_reader) is read-only and cannot run this.

  What it does:
    - inserts one dbo.ZnodeClient row named @ClientName, copying Issuer, Audience, AllowedApiScopes, ApplicationType
      and CreatedBy from the existing `at` API client (ClientId 9), so tokens it issues are accepted by the same APIs;
    - generates a random 64-character secret in SQL Server, so the secret never passes through chat or the agents;
    - prints the client name and secret ONCE. Copy them straight into the git-ignored .env:
          TDA_API_CLIENT_ID=<name>
          TDA_API_CLIENT_SECRET=<secret>
          TDA_API_CLIENT_DEDICATED=true
    - does nothing if a client with that name already exists (re-running is safe).

  Gateway facts (znode10-api-gateways, AuthService/TokenController):
    - token: POST <gateway>/Token with JSON {"ClientName": ..., "ClientKey": <secret>}; response {"Token", "RefreshToken"};
    - the secret is compared as plain text, and the gateway caches the client list (sliding expiration from its
      CacheSettings), so the new client may only work after that cache expires or the gateway restarts;
    - the gateway does not filter on IsActive (that check is commented out), so to revoke this client later, delete
      the row or change its secret; setting IsActive = 0 alone does not stop it.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;

DECLARE @ClientName nvarchar(200) = N'tda-test-data-agents-at';
DECLARE @TemplateClientId int = 9;

IF EXISTS (SELECT 1 FROM dbo.ZnodeClient WHERE ClientName = @ClientName)
BEGIN
    PRINT 'Client ' + @ClientName + ' already exists; nothing changed. Rotate its secret instead if it was lost.';
    RETURN;
END;

IF NOT EXISTS (SELECT 1 FROM dbo.ZnodeClient WHERE ClientId = @TemplateClientId)
BEGIN
    RAISERROR('Template client %d not found; set @TemplateClientId to the at API client.', 16, 1, @TemplateClientId);
    RETURN;
END;

DECLARE @Secret nvarchar(64) = CONVERT(nvarchar(64), CRYPT_GEN_RANDOM(32), 2);

BEGIN TRANSACTION;

INSERT INTO dbo.ZnodeClient (ClientName, ClientSecret, ApplicationType, Issuer, Audience, AllowedApiScopes, IsActive,
                             CreatedBy, CreatedDate, ModifiedBy, ModifiedDate)
SELECT @ClientName, @Secret, t.ApplicationType, t.Issuer, t.Audience, t.AllowedApiScopes, 1,
       t.CreatedBy, GETDATE(), t.CreatedBy, GETDATE()
FROM dbo.ZnodeClient t
WHERE t.ClientId = @TemplateClientId;

COMMIT TRANSACTION;

-- Shown once. Put both values in .env, then clear this result from your screen.
SELECT c.ClientId, c.ClientName, @Secret AS ClientSecret, c.Issuer, c.Audience, c.ApplicationType
FROM dbo.ZnodeClient c
WHERE c.ClientName = @ClientName;
