package org.thoughtcrime.securesms.payments.engine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.signal.core.util.logging.Log

/**
 * Skeleton CashuEngine using CDK Kotlin. Wires to a WalletSqliteDatabase and default mint.
 * TODO: Fill in CDK calls: wallet initialization, minting/melting, p2pk request, token import.
 */
class CashuEngine(private val appContext: Context) : PaymentsEngine {

  companion object {
    private val TAG = Log.tag(CashuEngine::class.java)
    private const val DEFAULT_MINT_URL = "https://mint.chorus.community"
    private const val DB_NAME = "cashu-wallet.db"
  }

  // lateinit var wallet: CdkWallet // Placeholder for CDK wallet type
  // lateinit var db: WalletSqliteDatabase
  private val keyManager by lazy { CashuKeyManager(appContext) }

  private var initialized = false

  private suspend fun ensureInitialized() = withContext(Dispatchers.IO) {
    if (initialized) return@withContext
    try {
      // TODO: Initialize CDK WalletSqliteDatabase and wallet
      // db = WalletSqliteDatabase.open(appContext, DB_NAME)
      val p2pk = keyManager.getOrCreateP2pk()
      Log.i(TAG, "Cashu P2PK pub=${'$'}{p2pk.pubkeyHex.take(16)}…")
      // wallet = CdkWallet(db, DEFAULT_MINT_URL, p2pk)
      initialized = true
    } catch (e: Throwable) {
      Log.w(TAG, "Failed to init CashuEngine", e)
      throw e
    }
  }

  override suspend fun isAvailable(): Boolean {
    return try {
      ensureInitialized(); true
    } catch (_: Throwable) {
      false
    }
  }

  override suspend fun getBalance(): Balance = withContext(Dispatchers.IO) {
    ensureInitialized()
    // TODO: Query wallet balance in sats
    Balance(totalSats = 0L, spendableSats = 0L)
  }

  override suspend fun createRequest(amountSats: Long?, memo: String?): String = withContext(Dispatchers.IO) {
    ensureInitialized()
    // TODO: Build Cashu payment request embedding DEFAULT_MINT_URL and our P2PK pubkey
    // return wallet.createPaymentRequest(amountSats, memo)
    val pub = keyManager.getOrCreateP2pk().pubkeyHex
    "cashu:request?mint=${'$'}DEFAULT_MINT_URL&pub=${'$'}pub&amount=${'$'}{amountSats ?: 0}"
  }

  override suspend fun send(toTokenRequest: String?, amountSats: Long, memo: String?): Result<TxId> = withContext(Dispatchers.IO) {
    ensureInitialized()
    // TODO: Split proofs, create token, optionally p2pk output if the recipient provided a pubkey in request
    Result.success(TxId("TODO"))
  }

  override suspend fun importToken(token: String): Result<ImportResult> = withContext(Dispatchers.IO) {
    ensureInitialized()
    // TODO: Parse+verify token with mint(s), store proofs, return added sats
    Result.success(ImportResult(addedSats = 0L))
  }

  override suspend fun listHistory(offset: Int, limit: Int): List<Tx> = withContext(Dispatchers.IO) {
    ensureInitialized()
    // TODO: Read from wallet/db local history
    emptyList()
  }

  override suspend fun backupExport(): EncryptedBlob = withContext(Dispatchers.IO) {
    ensureInitialized()
    // TODO: Export encrypted proofs bundle + mint metadata
    EncryptedBlob(byteArrayOf())
  }

  override suspend fun backupImport(blob: EncryptedBlob): Result<Unit> = withContext(Dispatchers.IO) {
    ensureInitialized()
    // TODO: Import encrypted bundle via CDK
    Result.success(Unit)
  }

  // New Cashu specifics
  override suspend fun requestMintQuote(amountSats: Long): Result<MintQuote> = withContext(Dispatchers.IO) {
    ensureInitialized()
    // TODO: Call CDK/mint API to fetch quote; this is a stub value for now
    val fee = (amountSats * 5) / 10_000 // 0.05% stub
    Result.success(MintQuote(DEFAULT_MINT_URL, amountSats, fee, amountSats + fee, System.currentTimeMillis() + 5 * 60_000))
  }

  override suspend fun createSendToken(amountSats: Long, memo: String?): Result<String> = withContext(Dispatchers.IO) {
    ensureInitialized()
    // TODO: Use wallet to split proofs and construct a token string for amountSats
    Result.success("cashu:token:TODO:$amountSats")
  }
}
