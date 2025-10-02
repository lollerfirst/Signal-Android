package org.thoughtcrime.securesms.payments.engine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.cashudevkit.Amount
import org.cashudevkit.CurrencyUnit
import org.cashudevkit.PreparedSend
import org.cashudevkit.ReceiveOptions
import org.cashudevkit.SendKind
import org.cashudevkit.SendMemo
import org.cashudevkit.SendOptions
import org.cashudevkit.SplitTarget
import org.cashudevkit.Token
import org.cashudevkit.Transaction
import org.cashudevkit.Wallet
import org.cashudevkit.WalletConfig
import org.cashudevkit.WalletSqliteDatabase
import org.signal.core.util.logging.Log

/**
 * CashuEngine backed by CDK Kotlin.
 */
class CashuEngine(private val appContext: Context) : PaymentsEngine {

  companion object {
    private val TAG = Log.tag(CashuEngine::class.java)
    private const val DEFAULT_MINT_URL = "https://mint.chorus.community"
    private const val DB_NAME = "cashu-wallet.db"
  }

  private val keyManager by lazy { CashuKeyManager(appContext) }
  private val mnemonicManager by lazy { CashuMnemonicManager(appContext) }
  private val pendingStore by lazy { PendingMintStore(appContext) }
  private val historyStore by lazy { CashuHistoryStore(appContext) }

  private var db: WalletSqliteDatabase? = null
  private var wallet: Wallet? = null
  private var initialized = false

  private suspend fun ensureInitialized() = withContext(Dispatchers.IO) {
    if (initialized) return@withContext
    try {
      val dbPath = appContext.filesDir.resolve(DB_NAME).absolutePath
      db = WalletSqliteDatabase(dbPath)

      val mnemonic = mnemonicManager.getOrCreateMnemonic()
      val config = WalletConfig(targetProofCount = 10u)

      wallet = Wallet(
        mintUrl = DEFAULT_MINT_URL,
        unit = CurrencyUnit.Sat,
        mnemonic = mnemonic,
        db = db!!,
        config = config
      )

      // Ensure we have mint keysets/info on first run
      runCatching { wallet!!.refreshKeysets() }.onFailure { Log.w(TAG, "refreshKeysets failed during init", it) }
      runCatching { wallet!!.getMintInfo() }.onFailure { Log.w(TAG, "getMintInfo failed during init", it) }

      val p2pk = keyManager.getOrCreateP2pk()
      Log.i(TAG, "Cashu wallet initialized. P2PK pub=${'$'}{p2pk.pubkeyHex.take(16)}…")
      initialized = true
    } catch (e: Throwable) {
      Log.w(TAG, "Failed to init CashuEngine", e)
      throw e
    }
  }

  override suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
    runCatching { ensureInitialized() }.isSuccess
  }

  override suspend fun getBalance(): Balance = withContext(Dispatchers.IO) {
    ensureInitialized()
    val amt = runCatching { wallet!!.totalBalance() as Amount }.getOrElse { Amount(0u) }
    val sats = amt.value.toLong().coerceAtLeast(0)
    Balance(totalSats = sats, spendableSats = sats)
  }

  override suspend fun createRequest(amountSats: Long?, memo: String?): String = withContext(Dispatchers.IO) {
    ensureInitialized()
    val pub = keyManager.getOrCreateP2pk().pubkeyHex
    "cashu:request?mint=${'$'}DEFAULT_MINT_URL&pub=${'$'}pub&amount=${'$'}{amountSats ?: 0}"
  }

  override suspend fun requestMintQuote(amountSats: Long): Result<MintQuote> = withContext(Dispatchers.IO) {
    ensureInitialized()
    runCatching {
      val cdkQuote = wallet!!.mintQuote(Amount(amountSats.toULong()), "Signal top-up") as org.cashudevkit.MintQuote
      val quote = MintQuote(
        mintUrl = DEFAULT_MINT_URL,
        amountSats = amountSats,
        feeSats = 0L,
        totalSats = amountSats,
        expiresAtMs = cdkQuote.expiry.toLong(),
        invoiceBolt11 = cdkQuote.request,
        id = cdkQuote.id
      )
      // Record as pending so watcher can auto-mint when paid
      recordPendingMint(quote)
      quote
    }
  }

  override suspend fun createSendToken(amountSats: Long, memo: String?): Result<String> = withContext(Dispatchers.IO) {
    ensureInitialized()
    runCatching {
      val memoObj = if (memo.isNullOrBlank()) null else SendMemo(memo, includeMemo = true)
      val sendOptions = SendOptions(
        memo = memoObj,
        conditions = null,
        amountSplitTarget = SplitTarget.None,
        sendKind = SendKind.OnlineExact,
        includeFee = true,
        maxProofs = null,
        metadata = emptyMap()
      )
      val prepared = wallet!!.prepareSend(Amount(amountSats.toULong()), sendOptions) as PreparedSend
      val token = prepared.confirm("") as Token
      val tokenString = token.encode()
      prepared.close(); token.close()
      tokenString
    }
  }

  override suspend fun send(toTokenRequest: String?, amountSats: Long, memo: String?): Result<TxId> = withContext(Dispatchers.IO) {
    createSendToken(amountSats, memo).map { TxId(it.take(16)) }
  }

  override suspend fun importToken(token: String): Result<ImportResult> = withContext(Dispatchers.IO) {
    ensureInitialized()
    runCatching {
      val decoded = Token.decode(token)
      val added = wallet!!.receive(decoded, ReceiveOptions(
        amountSplitTarget = SplitTarget.None,
        p2pkSigningKeys = emptyList(),
        preimages = emptyList(),
        metadata = emptyMap()
      )) as Amount
      decoded.close()
      ImportResult(added.value.toLong())
    }
  }

  override suspend fun listHistory(offset: Int, limit: Int): List<Tx> = withContext(Dispatchers.IO) {
    ensureInitialized()
    val onchain = runCatching {
      (wallet!!.listTransactions(null) as List<*>).drop(offset).take(limit).map { it as Transaction }.map { tx ->
        val amt = tx.amount.value.toLong()
        Tx(
          id = tx.id.hex,
          timestampMs = System.currentTimeMillis(),
          amountSats = amt,
          memo = null
        )
      }
    }.getOrDefault(emptyList())

    // Add pending top-ups with requested amount for display
    val pending = pendingStore.list().map {
      Tx(
        id = it.id ?: (it.invoice ?: ("pending-" + it.createdAtMs)),
        timestampMs = it.createdAtMs,
        amountSats = it.amountSats, // show intended amount instead of 0
        memo = "Pending top-up ${it.amountSats} sat"
      )
    }

    // Completed top-ups synthesized from local history (until CDK exposes full TX list we can map)
    val completed = historyStore.list().map {
      Tx(
        id = it.id ?: ("topup-" + it.timestampMs),
        timestampMs = it.timestampMs,
        amountSats = it.amountSats,
        memo = "Top-up completed"
      )
    }

    // Include locally recorded outgoing sent ecash
    val sent = CashuSendStore(appContext).list().map {
      Tx(
        id = it.id ?: ("sent-" + it.timestampMs),
        timestampMs = it.timestampMs,
        amountSats = -it.amountSats, // negative for outgoing
        memo = it.memo ?: "Sent ecash"
      )
    }

    (completed + pending + sent + onchain).sortedByDescending { it.timestampMs }
  }

  override suspend fun backupExport(): EncryptedBlob = withContext(Dispatchers.IO) {
    ensureInitialized()
    EncryptedBlob(byteArrayOf())
  }

  override suspend fun mintPaidQuote(secretKeyOrId: String): Result<Unit> = withContext(Dispatchers.IO) {
    ensureInitialized()
    runCatching {
      wallet!!.mint(secretKeyOrId, SplitTarget.None, null)
      try {
        val match = pendingStore.list().firstOrNull { it.invoice == secretKeyOrId || it.id == secretKeyOrId }
        if (match != null) {
          pendingStore.markMinted(match.id)
          historyStore.add(CashuHistoryStore.CompletedTopUp(match.id, match.amountSats, System.currentTimeMillis()))
        }
      } catch (_: Throwable) {}
      Unit
    }
  }

  override suspend fun backupImport(blob: EncryptedBlob): Result<Unit> = withContext(Dispatchers.IO) {
    ensureInitialized()
    Result.success(Unit)
  }

  override suspend fun recordPendingMint(quote: MintQuote) {
    try {
      pendingStore.add(PendingMintStore.PendingMintQuote(
        id = quote.id,
        invoice = quote.invoiceBolt11,
        amountSats = quote.amountSats,
        expiresAtMs = quote.expiresAtMs,
        mintUrl = quote.mintUrl
      ))
    } catch (t: Throwable) {
      Log.w(TAG, "Failed to record pending mint", t)
    }
  }

  // Temporary exposure for UI helper; in production expose a narrower surface
  fun getCdkWalletUnsafe(): Wallet? = wallet
}
