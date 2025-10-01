package org.thoughtcrime.securesms.payments.engine

/**
 * PaymentsEngine abstraction to support Cashu and allow phasing out MobileCoin.
 */
interface PaymentsEngine {
  suspend fun isAvailable(): Boolean
  suspend fun getBalance(): Balance
  suspend fun createRequest(amountSats: Long?, memo: String?): String
  suspend fun send(toTokenRequest: String?, amountSats: Long, memo: String?): Result<TxId>
  suspend fun importToken(token: String): Result<ImportResult>
  suspend fun listHistory(offset: Int, limit: Int): List<Tx>
  suspend fun backupExport(): EncryptedBlob
  suspend fun backupImport(blob: EncryptedBlob): Result<Unit>
}

data class Balance(
  val totalSats: Long,
  val spendableSats: Long
)

data class TxId(val id: String)

data class ImportResult(val addedSats: Long)

data class Tx(
  val id: String,
  val timestampMs: Long,
  val amountSats: Long, // positive incoming, negative outgoing
  val memo: String?
)

data class EncryptedBlob(val bytes: ByteArray)
