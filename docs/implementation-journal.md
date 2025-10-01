# Cashu Integration Implementation Journal

This journal logs each incremental step taken to migrate from MobileCoin to Cashu using CDK Kotlin, including commands, file changes, and encountered issues.

Date: 2025-10-01
Author: Goose
- [ ] Implement feature flag plumbing and scaffolding
  - [x] Add docs/cashu-migration-plan.md
  - [x] Add docs/implementation-journal.md
  - [x] Add Cashu enabled flag in PaymentsValues (key: cashu_enabled) and availability logic ignoring geo
  - [x] Add PaymentsEngine abstraction and provider; add CashuEngine skeleton and MobileCoinEngineAdapter stub
  - [ ] Wire PaymentsHomeViewModel and related screens to use PaymentsEngineProvider
  - [ ] Add Coinbase RatesProvider and sats-first display adjustments
  - [ ] Implement CDK WalletSqliteDatabase initialization and P2PK key management in CashuEngine
  - [ ] Implement createRequest with default mint + P2PK
  - [ ] Implement send/import/history/backup using CDK


---

## 2025-10-01

- Created migration plan file at docs/cashu-migration-plan.md and updated with confirmed decisions (default mint, P2PK, sats display, Coinbase FX, single-device, remove compliance, drop block-height).
- Decision: use CDK WalletSqliteDatabase for persistence. Plan updated accordingly (to be reflected in storage section with explicit note in subsequent edit).
- Next: add feature flag, create PaymentsEngine interface, CashuEngine skeleton, Coinbase RatesProvider, and adjust PaymentsAvailability to expose Cashu when enabled.

Open items:
- Verify CDK Kotlin API surface (WalletSqliteDatabase, minting/melting calls) and P2PK support (NUT-07) exact method names.
- Ensure OkHttp or equivalent HTTP client available for Coinbase FX. If not, add minimal client or reuse existing network stack.
