package org.thoughtcrime.securesms.conversation.v2.items

import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.cashudevkit.Amount
import org.cashudevkit.Token
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.conversation.ConversationMessage
import org.thoughtcrime.securesms.dependencies.AppDependencies

/**
 * Lightweight helper to detect cashu tokens in a text-only bubble and attach a small
 * receive UI. This avoids large refactors in the binding pipeline.
 */
object CashuTokenInlineRenderer {
  private val TAG = "CashuToken"
  private fun extractTokenFromAny(text: String?): String? {
    if (text.isNullOrEmpty()) return null
    val idx = text.indexOf("cashu", ignoreCase = true)
    if (idx < 0) return null
    var end = idx
    val len = text.length
    while (end < len) {
      val ch = text[end]
      if (ch.isWhitespace()) break
      end++
    }
    val candidate = text.substring(idx, end).trim()
    return if (
      candidate.startsWith("cashu:", true) ||
      candidate.startsWith("cashuA", true) ||
      candidate.startsWith("cashuB", true)
    ) candidate else null
  }

  fun resetIfPresent(binding: V2ConversationItemTextOnlyBindingBridge) {
    val parent = binding.bodyWrapper
    parent.findViewById<View>(R.id.cashu_token_receive_bar)?.let { parent.removeView(it) }
    binding.body.visibility = View.VISIBLE
  }

  private fun formatSats(sats: Long): String {
    val nf = java.text.NumberFormat.getInstance(java.util.Locale.getDefault())
    nf.maximumFractionDigits = 0
    nf.isGroupingUsed = true
    return nf.format(sats)
  }

  fun maybeAttachReceiveUi(binding: V2ConversationItemTextOnlyBindingBridge, conversationMessage: ConversationMessage): Boolean {
    val visibleToken = extractTokenFromAny(binding.body.text?.toString())
    val fullBody = conversationMessage.messageRecord.body
    val fullToken = extractTokenFromAny(fullBody)
    val token = fullToken ?: visibleToken ?: return false

    val parent = binding.bodyWrapper
    val existing = parent.findViewById<View>(R.id.cashu_token_receive_bar)
    if (existing != null) {
      binding.body.text = ""
      binding.body.visibility = View.GONE
      return true
    }

    binding.body.text = ""
    binding.body.visibility = View.GONE

    val ctx = parent.context
    val bar = View.inflate(ctx, R.layout.cashu_token_card, null)
    bar.id = R.id.cashu_token_receive_bar
    val amountView = bar.findViewById<TextView>(R.id.cashu_token_amount)
    val subtitleView = bar.findViewById<TextView>(R.id.cashu_token_subtitle)
    val receiveContainer = bar.findViewById<View>(R.id.cashu_token_receive_container)
    val receiveIcon = bar.findViewById<android.widget.ImageView>(R.id.cashu_token_receive_icon)
    val spinner = bar.findViewById<android.widget.ProgressBar>(R.id.cashu_token_receive_spinner)

    val sats: Long = try {
      val decoded = Token.decode(token)
      val amt = decoded.value() as Amount
      val v = amt.value.toLong()
      decoded.close()
      v
    } catch (_: Throwable) { 0L }

    if (sats > 0L) {
      amountView.text = formatSats(sats) + " sat"
      subtitleView.text = ctx.getString(R.string.cashu_token_label)
    } else {
      amountView.text = ctx.getString(R.string.cashu_token_label)
      subtitleView.text = ""
    }

    receiveContainer.setOnClickListener {
      receiveContainer.isEnabled = false
      receiveIcon.visibility = View.GONE
      spinner.visibility = View.VISIBLE

      val engine = org.thoughtcrime.securesms.payments.engine.PaymentsEngineProvider.get(AppDependencies.application)
      CoroutineScope(Dispatchers.Main).launch {
        val result = withContext(Dispatchers.IO) { engine.importToken(token) }
        result.onSuccess { r ->
          val peer = conversationMessage.messageRecord.fromRecipient
          val memo = "Received from|rid:" + peer.id.serialize() + "|name:" + peer.getDisplayName(ctx).replace("|", "\u2758")
          try {
            org.thoughtcrime.securesms.payments.engine.CashuReceiveStore(ctx).add(
              org.thoughtcrime.securesms.payments.engine.CashuReceiveStore.Received(null, r.addedSats, System.currentTimeMillis(), memo)
            )
          } catch (_: Throwable) {}
          amountView.text = ctx.getString(R.string.cashu_token_received_sats, r.addedSats)
          spinner.visibility = View.GONE
        }.onFailure { e ->
          spinner.visibility = View.GONE
          receiveIcon.visibility = View.VISIBLE
          receiveContainer.isEnabled = true
          org.signal.core.util.logging.Log.e(TAG, "Cashu receive failed", e)
          Toast.makeText(ctx, e?.message ?: ctx.getString(R.string.cashu_token_receive_failed), Toast.LENGTH_LONG).show()
        }
      }
    }

    val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    parent.addView(bar, params)
    return true
  }

  @JvmStatic
  fun maybeAttachReceiveUiClassic(parent: android.view.ViewGroup, body: TextView, conversationMessage: org.thoughtcrime.securesms.conversation.ConversationMessage): Boolean {
    parent.findViewById<View>(R.id.cashu_token_receive_bar)?.let { parent.removeView(it) }
    body.visibility = View.VISIBLE

    val visibleToken = extractTokenFromAny(body.text?.toString())
    val fullToken = extractTokenFromAny(conversationMessage.messageRecord.body)
    val token = fullToken ?: visibleToken ?: return false

    body.text = ""
    body.visibility = View.GONE

    val ctx = parent.context
    val bar = View.inflate(ctx, R.layout.cashu_token_card, null)
    bar.id = R.id.cashu_token_receive_bar
    val amountView = bar.findViewById<TextView>(R.id.cashu_token_amount)
    val subtitleView = bar.findViewById<TextView>(R.id.cashu_token_subtitle)
    val receiveContainer = bar.findViewById<View>(R.id.cashu_token_receive_container)
    val receiveIcon = bar.findViewById<android.widget.ImageView>(R.id.cashu_token_receive_icon)
    val spinner = bar.findViewById<android.widget.ProgressBar>(R.id.cashu_token_receive_spinner)

    val sats: Long = try {
      val decoded = Token.decode(token)
      val amt = decoded.value() as Amount
      val v = amt.value.toLong()
      decoded.close()
      v
    } catch (_: Throwable) { 0L }

    if (sats > 0L) {
      amountView.text = formatSats(sats) + " sat"
      subtitleView.text = ctx.getString(R.string.cashu_token_label)
    } else {
      amountView.text = ctx.getString(R.string.cashu_token_label)
      subtitleView.text = ""
    }

    receiveContainer.setOnClickListener {
      receiveContainer.isEnabled = false
      receiveIcon.visibility = View.GONE
      spinner.visibility = View.VISIBLE

      val engine = org.thoughtcrime.securesms.payments.engine.PaymentsEngineProvider.get(AppDependencies.application)
      CoroutineScope(Dispatchers.Main).launch {
        val result = withContext(Dispatchers.IO) { engine.importToken(token) }
        result.onSuccess { r ->
          val peer = conversationMessage.messageRecord.fromRecipient
          val memo = "Received from|rid:" + peer.id.serialize() + "|name:" + peer.getDisplayName(ctx).replace("|", "\u2758")
          try {
            org.thoughtcrime.securesms.payments.engine.CashuReceiveStore(ctx).add(
              org.thoughtcrime.securesms.payments.engine.CashuReceiveStore.Received(null, r.addedSats, System.currentTimeMillis(), memo)
            )
          } catch (_: Throwable) {}
          amountView.text = ctx.getString(R.string.cashu_token_received_sats, r.addedSats)
          spinner.visibility = View.GONE
        }.onFailure { e ->
          spinner.visibility = View.GONE
          receiveIcon.visibility = View.VISIBLE
          receiveContainer.isEnabled = true
          org.signal.core.util.logging.Log.e(TAG, "Cashu receive failed", e)
          Toast.makeText(ctx, e?.message ?: ctx.getString(R.string.cashu_token_receive_failed), Toast.LENGTH_LONG).show()
        }
      }
    }

    val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    parent.addView(bar, params)
    return true
  }
}
