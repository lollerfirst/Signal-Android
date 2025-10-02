package org.thoughtcrime.securesms.conversation.v2.items

import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
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
  private fun extractToken(text: CharSequence?): String? {
    val s = text?.toString() ?: return null
    val parts = s.split(Regex("\\s+"))
    val candidate = parts.firstOrNull { part ->
      val p = part.trim()
      p.startsWith("cashu:", ignoreCase = true) || p.startsWith("cashuA") || p.startsWith("cashuB")
    }
    return candidate
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
    val token = extractToken(binding.body.text) ?: return false

    // Avoid duplicating the UI if already added
    val parent = binding.bodyWrapper
    val existing = parent.findViewById<View>(R.id.cashu_token_receive_bar)
    if (existing != null) {
      // Ensure original text hidden if previously attached
      binding.body.text = ""
      binding.body.visibility = View.GONE
      return true
    }

    // Hide the original token text from the bubble; show only the lightweight receive UI
    binding.body.text = ""
    binding.body.visibility = View.GONE

    val ctx = parent.context
    val bar = View.inflate(ctx, R.layout.cashu_token_receive_bar, null)
    bar.id = R.id.cashu_token_receive_bar
    val label = bar.findViewById<TextView>(R.id.cashu_token_label)
    val receive = bar.findViewById<Button>(R.id.cashu_token_receive)

    // Try to decode token value to display sats amount inline
    val sats: Long = try {
      val decoded = Token.decode(token)
      val amt = decoded.value() as Amount
      val v = amt.value.toLong()
      decoded.close()
      v
    } catch (_: Throwable) { 0L }

    if (sats > 0L) {
      label.text = formatSats(sats) + " sat"
    } else {
      // Fallback if decoding fails
      label.text = ctx.getString(R.string.cashu_token_label)
    }

    receive.setOnClickListener {
      val engine = org.thoughtcrime.securesms.payments.engine.PaymentsEngineProvider.get(AppDependencies.application)
      val result = kotlinx.coroutines.runBlocking { engine.importToken(token) }
      result.onSuccess { r ->
        // Record for recent activity
        val peer = conversationMessage.messageRecord.fromRecipient
        val memo = "Received from|rid:" + peer.id.serialize() + "|name:" + peer.getDisplayName(ctx).replace("|", "\u2758")
        try {
          org.thoughtcrime.securesms.payments.engine.CashuReceiveStore(ctx).add(
            org.thoughtcrime.securesms.payments.engine.CashuReceiveStore.Received(null, r.addedSats, System.currentTimeMillis(), memo)
          )
        } catch (_: Throwable) {}
        // Feedback
        label.text = ctx.getString(R.string.cashu_token_received_sats, r.addedSats)
        receive.isEnabled = false
      }.onFailure {
        label.text = ctx.getString(R.string.cashu_token_receive_failed)
      }
    }

    // Insert below the body text
    val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    parent.addView(bar, params)
    return true
  }
}
