# Cashu Integration Implementation Journal

- [x] Build fix: bump minSdk to 24 to satisfy cdk-kotlin v0.13.0

This journal logs each incremental step taken to migrate from MobileCoin to Cashu using CDK Kotlin, including commands, file changes, and encountered issues.

Date: 2025-10-01
Author: Goose
- [x] Implement feature flag plumbing and scaffolding
  - [x] Add docs/cashu-migration-plan.md
  - [x] Add docs/implementation-journal.md
  - [x] Add Cashu enabled flag in PaymentsValues (key: cashu_enabled) and availability logic ignoring geo
  - [x] Add PaymentsEngine abstraction and provider; add CashuEngine skeleton and MobileCoinEngineAdapter stub
  - [x] Wire PaymentsHomeViewModel and PaymentsHomeFragment minimally: expose Cashu sats and show fiat estimate in header when flag is enabled
  - [x] Add Coinbase RatesProvider and sats-first display adjustments (provider added, UI wiring pending)
  - [ ] Implement CDK WalletSqliteDatabase initialization and P2PK key management in CashuEngine
  - [ ] Implement createRequest with default mint + P2PK
  - [ ] Implement send/import/history/backup using CDK


---

## 2025-10-01

- Created migration plan file at docs/cashu-migration-plan.md and updated with confirmed decisions (default mint, P2PK, sats display, Coinbase FX, single-device, remove compliance, drop block-height).
- Decision: use CDK WalletSqliteDatabase for persistence. Plan updated accordingly (to be reflected in storage section with explicit note in subsequent edit).
- Next: add feature flag, create PaymentsEngine interface, CashuEngine skeleton, Coinbase RatesProvider, and adjust PaymentsAvailability to expose Cashu when enabled.
- Added CoinbaseRatesProvider implementation to fetch BTC spot price and sats->fiat conversion helper. Not yet integrated in UI.

### 2025-10-01 (cont.)
- Add CashuUiRepository to bridge engine sats balance and show fiat using Coinbase FX; provides blocking helpers for legacy UI.
- Wire step: PaymentsHomeViewModel exposes cashu sats LiveData and fiat string; PaymentsHomeFragment displays fiat string when Cashu is enabled. Build succeeds for :Signal-Android:assembleDebug.

## 2025-10-02

Summary of progress and fixes

1) Engine and UI wiring
- PaymentsEngine + CashuEngine integrated with CDK Kotlin WalletSqliteDatabase and BIP39 mnemonic manager.
- PaymentsEngineProvider selects CashuEngine when SignalStore.payments.cashuEnabled() or DEBUG.
- Sats-first header with Coinbase FX estimate via CashuUiRepository; one-shot MintWatcher check on header refresh.

2) Add Money flow (Cashu)
- Inline amount entry + “Get invoice” button.
- On quote: persist pending via PendingMintStore; show BOLT11 invoice text + QR and guidance.
- MintWatcher polls pending quotes, attempts mint when paid; balance updated.

3) Recent Activity
- Show synthetic items from Cashu first (capped to 5 total):
  - "Pending top-up <amount> sat"
  - "Top-up completed"
- Avoid blocking UI: Cashu history fetched off main thread (mapAsync) and combined with store state to recompute list when data arrives; show InProgress while loading.

4) Crash/ANR fixes
- Fixed crash: No view holder factory for InfoCard → register InfoCard in PaymentsHomeAdapter.
- Avoided ANR: moved Cashu history retrieval off main thread; recompute list on background result.

5) Stores and pruning
- PendingMintStore:
  - Dedupe by id/invoice on add.
  - Prune on read/list: drop minted or expired quotes; normalize expiresAt if persisted in seconds; sort by createdAtMs desc; cap to 200; persist pruning.
- CashuHistoryStore:
  - Dedupe by id (keep latest timestamp); sort desc; cap to 500; persist pruning on read/add.

6) Known issues addressed
- Pending top-ups disappearing due to expiry unit mismatch: fixed by normalizing expiresAt.
- InfoCard mapping crash fixed.
- "Signal isn’t responding" dialog eliminated in Payments due to moving work off main thread.

7) Build status
- PlayStagingDebug and PlayProdDebug compile after fixes.

8) Next steps
- Event-based mint status (subscribe to BOLT11_MINT_QUOTE) to remove polling.
- Full CDK transaction mapping and unified history (replace synthetic items).
- Send token via Signal message; P2PK/NUT-07 plumbing.
- Backup export/import integration with encryption.
- Add optional debug toggles: mint URL, force cashuEnabled, prune now.
- UI polish: detail view for Cashu entries; copy invoice button; lifecycle scope for MintWatcher.

Representative commits
- Payments: fix InfoCard mapping crash; avoid ANR by moving Cashu history off main thread, recompute list when Cashu data arrives; show InProgress while loading; keep UI cap to 5 (Cashu first).
- Cashu: PendingMintStore pruning and cap size (remove expired/minted, dedupe by id/invoice, cap to 200, persist pruning on read).
- Cashu: CashuHistoryStore prune/dedupe/cap list (dedupe by id, keep latest, cap to 500, persist pruning on read & add).
- Cashu: PendingMintStore prune fix - normalize expiresAtMs if persisted in seconds to avoid over-pruning valid invoices.
