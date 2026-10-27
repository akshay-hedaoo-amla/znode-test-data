# Access checklist (Znode)

The Znode project's Phase 0 access checklist (security_and_access_doc.md section 10 in the test data agents repository
lists the items every project must tick). `checks/.../AccessChecklistTest` keeps it complete.

## Access checklist

Ticked 2026-10-21 at the project owner's instruction. Evidence and caveats: `docs/memory.md`.

- [x] Read-only DB user created and tested (`tda_reader`; `ReadOnlyDbUserIT`)
- [x] Test API service account created (temporary: shared `ZnodeClient` 9; dedicated client required before Phase 3)
- [x] Secrets stored outside git (git-ignored `.env`; `SecretScanTest`)
- [x] Production deny-list configured and tested (`config/deny-list.yaml`; `EnvironmentGuardTest`, `AccessChecklistTest`)
- [x] Validator rejects a deliberately unsafe query (`SqlQueryValidatorTest`)
- [x] Column mask list agreed for customer data (`config/column-mask.yaml`; starting list, to be extended before Phase 1 profiling)
