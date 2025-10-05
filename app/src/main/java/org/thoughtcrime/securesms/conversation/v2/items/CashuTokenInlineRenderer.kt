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
import org.signal.core.util.StreamUtil
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.conversation.ConversationMessage
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.mms.PartAuthority
import org.thoughtcrime.securesms.util.hasTextSlide
import org.thoughtcrime.securesms.util.requireTextSlide

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

  private fun extractTokenFromTextSlideIfPresent(ctx: Context, conversationMessage: ConversationMessage): String? {
    return try {
      val record = conversationMessage.messageRecord
      if (!record.isMms || !record.hasTextSlide()) return null
      val textSlideUri = record.requireTextSlide().uri ?: return null
      PartAuthority.getAttachmentStream(ctx, textSlideUri).use { input ->
        val fullText = StreamUtil.readFullyAsString(input)
        extractTokenFromAny(fullText)
      }
    } catch (t: Throwable) {
      null
    }
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
    val ctx = binding.bodyWrapper.context
    val attachmentToken = if (fullToken == null && visibleToken == null) extractTokenFromTextSlideIfPresent(ctx, conversationMessage) else null
    val token = fullToken ?: visibleToken ?: attachmentToken ?: return false

    val parent = binding.bodyWrapper
    val existing = parent.findViewById<View>(R.id.cashu_token_receive_bar)
    if (existing != null) {
      binding.body.text = ""
      binding.body.visibility = View.GONE
      return true
    }

    binding.body.text = ""
    binding.body.visibility = View.GONE

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
      subtitleView.text = ""
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
    // Insert card where the text would be, with similar layout params so the footer (timestamp) stays below
    val index = parent.indexOfChild(binding.body)
    val baseLp = binding.body.layoutParams
    val newLp: android.view.ViewGroup.LayoutParams = when (baseLp) {
      is androidx.constraintlayout.widget.ConstraintLayout.LayoutParams ->
        androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT).apply {
          leftToLeft = baseLp.leftToLeft
          rightToRight = baseLp.rightToRight
          topToTop = baseLp.topToTop
          bottomToBottom = baseLp.bottomToBottom
          setMargins(baseLp.leftMargin, baseLp.topMargin, baseLp.rightMargin, baseLp.bottomMargin)
        }
      is android.widget.LinearLayout.LayoutParams ->
        android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT).apply {
          setMargins(baseLp.leftMargin, baseLp.topMargin, baseLp.rightMargin, baseLp.bottomMargin)
          gravity = baseLp.gravity
        }
      is android.view.ViewGroup.MarginLayoutParams ->
        android.view.ViewGroup.MarginLayoutParams(android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT).apply {
          setMargins(baseLp.leftMargin, baseLp.topMargin, baseLp.rightMargin, baseLp.bottomMargin)
        }
      else -> android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
    }
    // Ensure the bar replaces the body at the same position so footer remains below
    val insertAt = if (index >= 0) index else parent.childCount
    parent.addView(bar, insertAt, newLp)
    val insertAt = if (index >= 0) index else parent.childCount
    parent.addView(bar, insertAt, newLp)
    return true

  @JvmStatic
  fun maybeAttachReceiveUiClassic(parent: android.view.ViewGroup, body: TextView, conversationMessage: org.thoughtcrime.securesms.conversation.ConversationMessage): Boolean {
    parent.findViewById<View>(R.id.cashu_token_receive_bar)?.let { parent.removeView(it) }
    body.visibility = View.VISIBLE

    val visibleToken = extractTokenFromAny(body.text?.toString())
    val fullToken = extractTokenFromAny(conversationMessage.messageRecord.body)
    val ctx = parent.context
    val attachmentToken = if (fullToken == null && visibleToken == null) extractTokenFromTextSlideIfPresent(ctx, conversationMessage) else null
    val token = fullToken ?: visibleToken ?: attachmentToken ?: return false

    body.text = ""
    body.visibility = View.GONE

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
      subtitleView.text = ""
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
    // Insert card where the text would be, with similar layout params so the footer (timestamp) stays below
    val index = parent.indexOfChild(body)
    val baseLp = body.layoutParams
    val newLp: android.view.ViewGroup.LayoutParams = when (baseLp) {
      is androidx.constraintlayout.widget.ConstraintLayout.LayoutParams ->
        androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT).apply {
          leftToLeft = baseLp.leftToLeft
          rightToRight = baseLp.rightToRight
          topToTop = baseLp.topToTop
          bottomToBottom = baseLp.bottomToBottom
          setMargins(baseLp.leftMargin, baseLp.topMargin, baseLp.rightMargin, baseLp.bottomMargin)
        }
      is android.widget.LinearLayout.LayoutParams ->
        android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT).apply {
          setMargins(baseLp.leftMargin, baseLp.topMargin, baseLp.rightMargin, baseLp.bottomMargin)
          gravity = baseLp.gravity
        }
      is android.view.ViewGroup.MarginLayoutParams ->
        android.view.ViewGroup.MarginLayoutParams(android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT).apply {
          setMargins(baseLp.leftMargin, baseLp.topMargin, baseLp.rightMargin, baseLp.bottomMargin)
        }
      else -> android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
    }
    parent.addView(bar, if (index >= 0) index else -1, newLp)
    return true
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
