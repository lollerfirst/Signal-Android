# Cashu Migration TODO (Working Checklist)

- [x] Add feature flag `cashu_enabled` and availability logic
- [x] Add PaymentsEngine abstraction + provider; CashuEngine skeleton; MobileCoinEngineAdapter stub
- [x] Add CoinbaseRatesProvider (BTC spot) and CashuUiRepository
- [x] Wire PaymentsHomeViewModel/Fragment minimally to show fiat estimate when Cashu is enabled
- [ ] Replace MOB MoneyView header when Cashu is enabled with sats-first display (and fiat secondary)
- [ ] Implement CDK WalletSqliteDatabase init in CashuEngine
  - [ ] Generate/load P2PK keypair (Android Keystore-backed)
  - [ ] Default mint config (https://mint.chorus.community)
  - [ ] Balance from proofs
- [ ] Implement createRequest(amountSats, memo) including P2PK in request (NUT-07)
- [ ] Implement send/import/history/backup using CDK
  - [ ] send(): split proofs, build token, optional P2PK output
  - [ ] importToken(): parse, verify with mint(s), store proofs
  - [ ] listHistory(): local log with statuses
  - [ ] backupExport/Import(): encrypted bundle with proofs + mint metadata
- [ ] Add internal dev toggle UI to enable/disable Cashu (debug builds)
- [ ] Tests: unit + Robolectric for PaymentsValues.cashuEnabled() path; engine provider selection
- [ ] QA plan: exchange offline, malformed tokens, double-spend mint reject, large proof sets
