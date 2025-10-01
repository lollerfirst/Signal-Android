# Cashu Migration Plan for Signal Android Payments

Author: Goose (re‑devised)
Date: 2025‑10‑01
Commit baseline: 4092cff584 "Cashu step 1: add CDK Kotlin dependency…"

## Summary
Replace the MobileCoin wallet integration with a Cashu wallet built on Cashu Dev Kit (CDK) Kotlin bindings.

We will introduce a payments engine abstraction, implement a Cashu engine using `org.cashudevkit:cdk-kotlin:v0.13.0`, migrate UI/flows where feasible, and safely retire MobileCoin‑specific code paths. Rollout will be guarded behind a feature flag.

This plan also lists areas where MobileCoin concepts do not map 1‑to‑1 to Cashu and calls out questions/assumptions for product decisions.


## Decisions (Confirmed)
- Addressing: Use a Cashu payment request QR/URI embedding the default mint and our P2PK pubkey so received tokens are locked to us.
- Default mint: https://mint.chorus.community (hardcoded for v1; user-configurable mints deferred).
- Top-up and withdraw: Supported in v1 via CDK “minting” (top-up) and “melting” (withdraw/transfer out). No additional custom integration beyond CDK.
- Device model: Single-device wallet is acceptable for v1; multi-device sync deferred.
- Currency display: Show sats universally across the app. Convert to local fiat using Coinbase API where needed (price previews, summaries). Implement a cached rate provider to avoid rate-limit issues.
- History semantics: Drop block-height entirely; use mint verification timestamps and local event timestamps.
- Compliance/geo: Remove MobileCoin geo/compliance restrictions from the app for Cashu flows.


## Scope
- Implement Cashu wallet core (proofs, mints, send/receive, balance, merge/split) using CDK.
- Replace MobileCoin send/receive flows and storage with Cashu equivalents.
- Update QR/deeplink parsing and generation to Cashu token formats.
- Provide local transaction history and backup/export/import for Cashu proofs.
- Feature flag, migrations, and staged rollout.

Non‑goals (initial phases):
- On‑chain MobileCoin to Cashu conversion; users must manage any legacy MOB externally.
- Lightning top‑up/withdraw UX beyond mint‑provided flows (may come in a later phase).
- Multi‑device real‑time wallet sync (covered as a later enhancement via backup/sync).


## Architecture Overview
- Introduce `PaymentsEngine` abstraction to decouple UI from coin implementation.
- Keep existing payments screens where feasible; swap data sources and semantics.
- Cashu engine relies on CDK Kotlin, with local secure storage of proofs and mint configs.
- No Fog/attestation; online checks are mint verification of proofs/spentness.


## Key Concept Mapping: MobileCoin → Cashu
- Network/Fog ledger → No global ledger. Cashu proofs are bearer tokens validated against a mint; history is local.
- Address (MobileCoinPublicAddress) → Cashu token requests or p2pk outputs; often a token string or a request QR (NUT specs).
- Transaction/receipt with block index → No block index. Define local statuses: pending (awaiting verification), complete (verified), failed.
- Fees/defragmentation → No network fee for p2p token transfer; mint fees apply on mint/withdraw/swap. CDK supports split/merge; we’ll auto‑consolidate proofs.
- Recovery phrase (BIP‑39) → Cashu typically uses seed + proofs backup; backup must export proofs and mint metadata.


## Deliverables and Milestones

- [ ] Phase 0: Foundations
  - [ ] Confirm CDK Kotlin dependency and toolchain (done by commit 4092cff584)
  - [ ] Add feature flag: `payments.cashu_enabled`
  - [ ] Create `payments/cashu/` module and baseline engine skeleton

- [ ] Phase 1: Abstraction Layer
  - [ ] Define `PaymentsEngine` interface (balance, send, receive/import, history, request/QR, backup)
  - [ ] Implement MobileCoin adapter over interface (temporary) to keep UI compiling
  - [ ] Wire UI ViewModels to use `PaymentsEngine` via provider/factory

- [ ] Phase 2: Cashu Engine Core
  - [ ] Storage: secure keystore for secrets; DB tables for proofs, mints, history
  - [ ] Implement balance from proofs (summing available, unspent proofs across mints)
  - [ ] Send flow: create token for recipient (support p2pk or standard token) and embed in Signal message / QR
  - [ ] Receive flow: parse/import token, verify with mint(s), store proofs, mark as spendable
  - [ ] Merge/split API: limit proof count and auto‑merge on receive

- [ ] Phase 3: UX Integration
  - [ ] Replace MobileCoin UI strings/icons and explain Cashu model (privacy tokens, mints)
  - [ ] Update QR scan/generate to Cashu token formats (NUT standards)
  - [ ] Payment confirmation: fee display logic changes (no Fog fees; mint or lightning fees only when applicable)
  - [ ] History: local transaction log with statuses

- [ ] Phase 4: Backup and Recovery
  - [ ] Export/import proofs (encrypted) with mint metadata
  - [ ] Integrate with existing “Payments Recovery” flow or create new “Cashu Backup” flow

- [ ] Phase 5: Migration & Rollout
  - [ ] Migration path: gracefully disable MobileCoin wallet, show deprecation notice, and entry points to export MOB externally
  - [ ] Feature flag rollout, QA, and telemetry
  - [ ] Remove MobileCoin code after stable release window

- [ ] Phase 6: Optional Enhancements
  - [ ] Lightning mint top‑up/withdraw
  - [ ] Cross‑device sync via encrypted backup
  - [ ] Multi‑mint management and swapping


## Detailed Work Plan

### 1) Gradle and Dependency Setup
- Confirm dependency exists in `app/build.gradle.kts`:
  ```kotlin
  // Cashu Dev Kit Kotlin bindings
  implementation("org.cashudevkit:cdk-kotlin:v0.13.0")
  ```
- Ensure JDK toolchain and Foojay resolver are configured (done in baseline commit).
- If needed for development, local sources at `~/cdk/crates/cdk-ffi` and `~/cdk-kotlin` can be used; prefer Maven artifact in release.

### 2) PaymentsEngine Abstraction
Create a Kotlin interface and internal model types decoupled from MobileCoin:
```kotlin
interface PaymentsEngine {
  suspend fun isAvailable(): Boolean
  suspend fun getBalance(): Balance // e.g., in sats or unit of mint
  suspend fun createRequest(amount: Long?, memo: String?): String // encodes Cashu request/QR
  suspend fun send(toTokenRequest: String?, amount: Long, memo: String?): Result<TxId>
  suspend fun importToken(token: String): Result<ImportResult>
  suspend fun listHistory(offset: Int, limit: Int): List<Tx>
  suspend fun backupExport(): EncryptedBlob
  suspend fun backupImport(blob: EncryptedBlob): Result<Unit>
}
```
Adapter layer:
- `MobileCoinEngineAdapter` (temporary) to keep UI compiling while we swap.
- `CashuEngine` implementation backed by CDK.
- Provider reads feature flag to choose engine.

Wire into existing ViewModels (`PaymentsHomeViewModel`, `CreatePaymentViewModel`, `ConfirmPaymentViewModel`, etc.).

### 3) Cashu Engine Implementation (CDK)
- Initialize CDK wallet context, keystore, and mint registry.
- Storage:
  - DB tables: `cashu_proofs`, `cashu_mints`, `cashu_history`, `cashu_keys`.
  - Encrypt sensitive data at rest; master key in Android Keystore.
- Balance:
  - Sum unspent proofs across mints (respecting keysets/units); expose canonical unit (sats) or per‑mint units.
- Send:
  - Create token for recipient using split of local proofs.
  - Prefer P2PK outputs (NUT‑07) if recipient supplies pubkey; fallback to standard token string.
  - Package token into Signal message attachment/URI and/or show QR.
- Receive:
  - Parse token (QR, paste, deeplink), verify against mint(s), check spentness, store proofs; auto‑merge if proof count is high.
- Merge/Split:
  - Use CDK split/merge to manage proof set size; run on background after import.

### 4) UI and UX Changes
- Replace MobileCoin brand assets (e.g., `ic_mobilecoin_*`) with Cashu‑neutral icons.
- Update copy to explain Cashu: privacy‑preserving eCash with mints; no public ledger; tokens are bearer.
- Remove Fog/attestation status and block index display; statuses:
  - Pending (verifying with mint)
  - Complete (verified and spendable)
  - Failed (invalid/spent)
- Fees:
  - Show “No network fee” for p2p; show mint or lightning fee only when relevant (e.g., top‑up/withdraw).
- QR:
  - Generate Cashu token or request QR.
  - Scanner recognizes Cashu token strings and standard NUT deeplinks.

### 5) Backup and Recovery
- Export encrypted bundle containing:
  - Unspent proofs, mint URLs, keysets, wallet public keys (if used for p2pk), metadata.
- Recovery flow updates:
  - Replace BIP‑39 recovery with import of encrypted bundle.
  - Optionally support seed‑based deterministic wallets if CDK supports such derivation for key material.

### 6) Database and Migration
- New migrations creating `cashu_*` tables.
- Mark MobileCoin wallet as deprecated:
  - Hide entry points when Cashu flag is enabled.
  - Provide notice and link to help article for MOB funds handling.
  - Preserve MobileCoin history locally for archival view (optional) or remove with user consent.

### 7) Telemetry, QA, and Rollout
- Add non‑sensitive metrics: successful imports, sends, verification failures (no token contents).
- Feature flag rollout:
  - Internal → Beta → Stable.
- Test matrix:
  - Single and multi‑mint, offline/online transitions, import malformed tokens, double‑spend protection (mint rejects), large proof sets, backup/restore.

### 8) Removal of MobileCoin Code
- After stabilization, remove classes under `org.thoughtcrime.securesms.payments` that are MobileCoin‑specific:
  - `MobileCoin*` classes, Fog/attestation code, defragmentation delegate.
  - Replace `Wallet.java` with `CashuWallet.kt` via the engine.


## Files and Modules to Touch (initial scan)
- Core payments: `app/src/main/java/org/thoughtcrime/securesms/payments/*`
- UI flows and fragments: `payments/*` subpackages (home, create, confirm, transfer, backup)
- Icons/layouts: `app/src/main/res/drawable/ic_mobilecoin_*`, payment layouts, QR views
- Jobs: `PaymentSendJob.java` to delegate to engine
- Settings/Availability: `PaymentsAvailability`, `PaymentsValues`




## Cutover Strategy
- Keep MobileCoin adapter wired until Cashu reaches feature parity for MVP.
- Switch feature flag to route UI to Cashu engine for beta.
- Disable MobileCoin funding/sending in beta; read‑only legacy history (optional).
- After stable, remove MobileCoin code.


## References
- CDK Kotlin: `org.cashudevkit:cdk-kotlin:v0.13.0`
- Local sources: `~/cdk/crates/cdk-ffi`, `~/cdk-kotlin`
- Cashu NUT specs: https://cashu.space/

