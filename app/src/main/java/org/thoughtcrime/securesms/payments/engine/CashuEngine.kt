package org.thoughtcrime.securesms.payments.engine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.cashudevkit.Amount
import org.cashudevkit.CurrencyUnit
import org.cashudevkit.Melted
import org.cashudevkit.MintUrl
import org.cashudevkit.PreparedSend
import org.cashudevkit.ReceiveOptions
import org.cashudevkit.SendKind
import org.cashudevkit.SendMemo
import org.cashudevkit.SendOptions
import org.cashudevkit.SplitTarget
import org.cashudevkit.Token
import org.cashudevkit.Wallet
import org.cashudevkit.WalletConfig
import org.cashudevkit.WalletSqliteDatabase
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.keyvalue.SignalStore

/**
 * CashuEngine backed by CDK Kotlin.
 */
class CashuEngine(private val appContext: Context) : PaymentsEngine {

  companion object {
    private val TAG = Log.tag(CashuEngine::class.java)
    private const val DEFAULT_MINT_URL = "https://mint.chorus.community"
    private const val DB_NAME = "cashu-wallet.db"
    private const val CLEANUP_DELAY_MS = 2000L // Give operations 2 seconds to complete
  }

  private val keyManager by lazy { CashuKeyManager(appContext) }
  private val mnemonicManager by lazy { CashuMnemonicManager(appContext) }
  private val pendingStore by lazy { PendingMintStore(appContext) }
  private val historyStore by lazy { CashuHistoryStore(appContext) }
  private val withdrawalStore by lazy { CashuWithdrawalStore(appContext) }

  // Track which mint a given melt quote (or invoice) was created against, so we can melt on the same mint
  private val meltQuoteMintCache: java.util.concurrent.ConcurrentHashMap<String, String> = java.util.concurrent.ConcurrentHashMap()

  // Synchronization for thread-safe initialization
  private val initLock = Mutex()
  
  @Volatile private var db: WalletSqliteDatabase? = null
  @Volatile private var wallet: Wallet? = null
  @Volatile private var initialized: Boolean = false
  @Volatile private var currentMintUrl: String? = null

  private suspend fun ensureInitialized(): Unit = withContext(Dispatchers.IO) {
    val activeMint = try { SignalStore.payments.getActiveMint() } catch (_: Throwable) { DEFAULT_MINT_URL }
    ensureInitializedForMint(activeMint)
  }

  private suspend fun ensureInitializedForMint(mintUrl: String) = withContext(Dispatchers.IO) {
    // Thread-safe check with mutex to prevent race conditions
    initLock.withLock {
      // Double-check pattern: verify again inside the lock
      if (initialized && wallet != null && currentMintUrl == mintUrl) {
        return@withContext
      }

      Log.i(TAG, "Initializing Cashu wallet for mint: $mintUrl")
      
      try {
        // Capture old instances for delayed cleanup
        val oldWallet = wallet
        val oldDb = db

        // Open new database
        val dbPath = appContext.filesDir.resolve(DB_NAME).absolutePath
        val newDb = WalletSqliteDatabase(dbPath)

        // Create new wallet
        val mnemonic = mnemonicManager.getOrCreateMnemonic()
        val config = WalletConfig(targetProofCount = 10u)

        val newWallet = Wallet(
          mintUrl = mintUrl,
          unit = CurrencyUnit.Sat,
          mnemonic = mnemonic,
          db = newDb,
          config = config
        )

        // Best-effort priming on new wallet
        runCatching { newWallet.refreshKeysets() }.onFailure { Log.w(TAG, "refreshKeysets failed during init", it) }
        runCatching { newWallet.getMintInfo() }.onFailure { Log.w(TAG, "getMintInfo failed during init", it) }

        // ATOMIC SWAP: Replace references atomically
        wallet = newWallet
        db = newDb
        currentMintUrl = mintUrl
        initialized = true
        
        val p2pk = keyManager.getOrCreateP2pk()
        Log.i(TAG, "Cashu wallet initialized for $mintUrl. P2PK pub=${p2pk.pubkeyHex.take(16)}…")

        // Schedule cleanup of old instances after delay to allow in-flight operations to complete
        if (oldWallet != null || oldDb != null) {
          GlobalScope.launch(Dispatchers.IO) {
            delay(CLEANUP_DELAY_MS)
            runCatching { oldWallet?.close() }.onFailure { 
              Log.w(TAG, "Error closing old wallet during cleanup", it) 
            }
            runCatching { oldDb?.close() }.onFailure { 
              Log.w(TAG, "Error closing old database during cleanup", it) 
            }
            Log.d(TAG, "Old wallet/db instances cleaned up")
          }
        }
        
        // After successful initialization, clean up legacy MobileCoin entropy
        try {
          SignalStore.payments.cleanupLegacyEntropyAfterCashuMigration()
        } catch (e: Throwable) {
          Log.w(TAG, "Failed to cleanup legacy entropy", e)
        }
      } catch (e: Throwable) {
        Log.e(TAG, "Failed to init CashuEngine for $mintUrl", e)
        throw e
      }
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
    val mint = try { SignalStore.payments.getActiveMint() } catch (_: Throwable) { DEFAULT_MINT_URL }
    "cashu:request?mint=${'$'}mint&pub=${'$'}pub&amount=${'$'}{amountSats ?: 0}"
  }

  override suspend fun requestMintQuote(amountSats: Long): Result<MintQuote> = withContext(Dispatchers.IO) {
    ensureInitialized()
    runCatching {
      val cdkQuote = wallet!!.mintQuote(Amount(amountSats.toULong()), "Signal top-up") as org.cashudevkit.MintQuote
      val activeMint = currentMintUrl ?: try { SignalStore.payments.getActiveMint() } catch (_: Throwable) { DEFAULT_MINT_URL }
      val quote = MintQuote(
        mintUrl = activeMint,
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
    runCatching {
      val decoded = Token.decode(token)
      try {
        // Allow receiving from any mint; initialize wallet for that mint and add to known list
        val tokenMint = runCatching { (decoded.mintUrl() as MintUrl).url }.getOrElse { DEFAULT_MINT_URL }
        try { SignalStore.payments.addKnownMint(tokenMint) } catch (_: Throwable) {}
        ensureInitializedForMint(tokenMint)

        val added = wallet!!.receive(decoded, ReceiveOptions(
          amountSplitTarget = SplitTarget.None,
          p2pkSigningKeys = emptyList(),
          preimages = emptyList(),
          metadata = emptyMap()
        )) as Amount
        ImportResult(added.value.toLong())
      } finally {
        decoded.close()
      }
    }
  }

  override suspend fun listHistory(offset: Int, limit: Int): List<Tx> = withContext(Dispatchers.IO) {
    ensureInitialized()

    val onchain = emptyList<Tx>()

    // Add pending top-ups with requested amount for display
    val pending = pendingStore.list().map {
      Tx(
        id = it.id ?: (it.invoice ?: ("pending-" + it.createdAtMs)),
        timestampMs = it.createdAtMs,
        amountSats = it.amountSats,
        memo = "Pending top-up ${'$'}{it.amountSats} sat"
      )
    }

    val completed = historyStore.list().map {
      Tx(
        id = it.id ?: ("topup-" + it.timestampMs),
        timestampMs = it.timestampMs,
        amountSats = it.amountSats,
        memo = "Top-up completed"
      )
    }

    val sent = CashuSendStore(appContext).list().map {
      Tx(
        id = it.id ?: ("sent-" + it.timestampMs),
        timestampMs = it.timestampMs,
        amountSats = -it.amountSats,
        memo = it.memo ?: "Sent ecash"
      )
    }

    val received = CashuReceiveStore(appContext).list().map {
      Tx(
        id = it.id ?: ("recv-" + it.timestampMs),
        timestampMs = it.timestampMs,
        amountSats = it.amountSats,
        memo = it.memo ?: "Received from"
      )
    }

    val withdrawals = withdrawalStore.list().map {
      Tx(
        id = it.id ?: ("wd-" + it.timestampMs),
        timestampMs = it.timestampMs,
        amountSats = - (it.amountSats + it.feeSats),
        memo = it.memo ?: "Withdrawal"
      )
    }

    (completed + pending + sent + received + withdrawals + onchain).sortedByDescending { it.timestampMs }
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

  // Lightning withdrawal (melt) with real fields
  override suspend fun requestMeltQuote(invoiceBolt11: String): Result<MeltQuote> = withContext(Dispatchers.IO) {
    ensureInitialized()
    runCatching {
      val cdkQuote = wallet!!.meltQuote(invoiceBolt11, null) as org.cashudevkit.MeltQuote
      val amountSats = (cdkQuote.amount as Amount).value.toLong()
      val feeReserveSats = (cdkQuote.feeReserve as Amount).value.toLong()
      val mintForQuote = currentMintUrl ?: try { SignalStore.payments.getActiveMint() } catch (_: Throwable) { DEFAULT_MINT_URL }
      val res = MeltQuote(
        amountSats = amountSats,
        feeSats = feeReserveSats,
        totalSats = amountSats + feeReserveSats,
        expiresAtMs = cdkQuote.expiry.toLong(),
        invoiceBolt11 = cdkQuote.request,
        id = cdkQuote.id
      )
      // Cache mint association for melt step, keyed by id and invoice
      if (res.id != null) {
        meltQuoteMintCache[res.id!!] = mintForQuote
      }
      meltQuoteMintCache[res.invoiceBolt11] = mintForQuote
      res
    }
  }

  override suspend fun melt(quote: MeltQuote): Result<TxId> = withContext(Dispatchers.IO) {
    // Ensure we are on the same mint used for the quote
    val preferredMint: String? = quote.id?.let { meltQuoteMintCache[it] } ?: meltQuoteMintCache[quote.invoiceBolt11]
    if (preferredMint != null) {
      ensureInitializedForMint(preferredMint)
    } else {
      ensureInitialized() // fallback to active
    }

    runCatching {
      val quoteId = quote.id ?: throw IllegalStateException("Missing melt quote id")
      val melted = wallet!!.melt(quoteId) as Melted
      val paidAmountSats = (melted.amount as Amount).value.toLong()
      val feePaidSats = (melted.feePaid as Amount).value.toLong()
      withdrawalStore.add(CashuWithdrawalStore.Withdrawal(
        id = quoteId,
        amountSats = paidAmountSats,
        feeSats = feePaidSats,
        timestampMs = System.currentTimeMillis(),
        memo = "Withdrawal"
      ))
      TxId(quoteId)
    }
  }

  fun getCdkWalletUnsafe(): Wallet? = wallet
}
