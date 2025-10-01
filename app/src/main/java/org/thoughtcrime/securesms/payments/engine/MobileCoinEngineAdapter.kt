package org.thoughtcrime.securesms.payments.engine

import android.content.Context

/**
 * Temporary bridge that provides a PaymentsEngine API for existing MobileCoin wallet until Cashu is ready.
 * Not fully implemented; methods may throw or adapt minimal behavior.
 */
class MobileCoinEngineAdapter(private val context: Context) : PaymentsEngine {
  override suspend fun isAvailable(): Boolean = false
  override suspend fun getBalance(): Balance = Balance(0, 0)
  override suspend fun createRequest(amountSats: Long?, memo: String?): String = ""
  override suspend fun send(toTokenRequest: String?, amountSats: Long, memo: String?) = Result.failure<TxId>(UnsupportedOperationException("MobileCoin adapter is read-only"))
  override suspend fun importToken(token: String) = Result.failure<ImportResult>(UnsupportedOperationException("Not supported"))
  override suspend fun listHistory(offset: Int, limit: Int): List<Tx> = emptyList()
  override suspend fun backupExport(): EncryptedBlob = EncryptedBlob(ByteArray(0))
  override suspend fun backupImport(blob: EncryptedBlob): Result<Unit> = Result.failure(UnsupportedOperationException("Not supported"))

  override suspend fun requestMintQuote(amountSats: Long) = Result.failure<MintQuote>(UnsupportedOperationException("Not supported"))
  override suspend fun createSendToken(amountSats: Long, memo: String?) = Result.failure<String>(UnsupportedOperationException("Not supported"))
  override suspend fun mintPaidQuote(secretKey: String): Result<Unit> = Result.failure(UnsupportedOperationException("Not supported"))
}
