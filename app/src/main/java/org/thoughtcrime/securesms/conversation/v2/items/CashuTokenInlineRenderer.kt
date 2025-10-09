package org.thoughtcrime.securesms.conversation.v2.items

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.cashudevkit.Amount
import org.cashudevkit.Token
import org.signal.core.util.StreamUtil
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.conversation.ConversationMessage
import org.thoughtcrime.securesms.conversation.colors.Colorizer
import org.thoughtcrime.securesms.conversation.ui.payment.CashuPaymentMessageView
import android.widget.Toast

import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.mms.PartAuthority
import org.thoughtcrime.securesms.payments.engine.CashuReceiveStore
import org.thoughtcrime.securesms.payments.engine.PaymentsEngineProvider
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.hasTextSlide
import org.thoughtcrime.securesms.util.requireTextSlide

typealias TextOnlyBinding = V2ConversationItemTextOnlyBindingBridge

object CashuTokenInlineRenderer {
  private val TAG = Log.tag(CashuTokenInlineRenderer::class.java)

  private data class TokenMeta(val sats: Long?, val memo: String?)
  private fun removeExistingPill(parent: ViewGroup) {
    for (i in parent.childCount - 1 downTo 0) {
      if (parent.getChildAt(i) is CashuPaymentMessageView) {
        parent.removeViewAt(i)
      }
    }
  }



  private data class PillBinding(
    val view: CashuPaymentMessageView,
    val meta: TokenMeta,
    val outgoing: Boolean,
    val recipient: Recipient,
    val amountText: String
  )

  private fun extractToken(text: CharSequence?): String? {
    val s = text?.toString() ?: return null
    val parts = s.split(Regex("\\s+"))
    val candidate = parts.firstOrNull { part ->
      val p = part.trim()
      p.startsWith("cashu:", ignoreCase = true) || p.startsWith("cashuA") || p.startsWith("cashuB")
    }
    return candidate
  }

  private fun extractTokenFromAny(text: String?): String? {
    if (text.isNullOrEmpty()) return null
    val candidate = text.split(Regex("\\s+")).firstOrNull { part ->
      part.startsWith("cashu:", ignoreCase = true) ||
      part.startsWith("cashuA", ignoreCase = true) ||
      part.startsWith("cashuB", ignoreCase = true)
    }
    return candidate
  }

  private fun extractTokenFromTextSlideIfPresent(context: Context, conversationMessage: ConversationMessage): String? {
    return try {
      val record = conversationMessage.messageRecord
      if (!record.isMms || !record.hasTextSlide()) return null
      val textSlideUri = record.requireTextSlide().uri ?: return null
      PartAuthority.getAttachmentStream(context, textSlideUri).use { input ->
        val fullText = StreamUtil.readFullyAsString(input)
        extractTokenFromAny(fullText)
      }
    } catch (_: Throwable) {
      null
    }
  }

  private fun decodeTokenMeta(token: String): TokenMeta {
    return try {
      val decoded = Token.decode(token)
      val amount = decoded.value() as? Amount
      val sats = amount?.value?.toLong()
      val memo = try {
        decoded.memo()
      } catch (_: Throwable) {
        null
      }
      decoded.close()
      TokenMeta(sats, memo)
    } catch (_: Throwable) {
      TokenMeta(null, null)
    }
  }

  fun resetIfPresent(binding: TextOnlyBinding) {
    removeExistingPill(binding.bodyWrapper)
    binding.body.visibility = View.VISIBLE
  }

  fun maybeAttachReceiveUi(binding: TextOnlyBinding, conversationMessage: ConversationMessage): Boolean {
    val parent = binding.bodyWrapper
    removeExistingPill(parent)

    val token = findToken(binding.body.text?.toString(), parent.context, conversationMessage) ?: return false

    val pillBinding = createPill(parent.context, token, conversationMessage)
    configureReceive(pillBinding, token, conversationMessage)
    addPill(parent, binding.body, pillBinding.view)

    binding.body.text = ""
    binding.body.visibility = View.GONE
    return true
  }

  fun resetIfPresent(parent: ViewGroup) {
    removeExistingPill(parent)
  }

  fun maybeAttachReceiveUi(parent: ViewGroup, body: TextView, conversationMessage: ConversationMessage): Boolean {
    removeExistingPill(parent)

    val token = findToken(body.text?.toString(), parent.context, conversationMessage) ?: return false

    val pillBinding = createPill(parent.context, token, conversationMessage)
    configureReceive(pillBinding, token, conversationMessage)
    addPill(parent, body, pillBinding.view)

    body.text = ""
    body.visibility = View.GONE
    return true
  }

  private fun findToken(visibleText: String?, context: Context, conversationMessage: ConversationMessage): String? {
    // Check all possible locations for a cashu token, prioritizing the full message body and text slide
    // over the potentially truncated visible text
    val bodyToken = extractTokenFromAny(conversationMessage.messageRecord.body)
    val attachmentToken = extractTokenFromTextSlideIfPresent(context, conversationMessage)
    val visibleToken = extractTokenFromAny(visibleText)
    
    return bodyToken ?: attachmentToken ?: visibleToken
  }

  private fun createPill(context: Context, token: String, conversationMessage: ConversationMessage): PillBinding {
    val meta = decodeTokenMeta(token)
    val outgoing = conversationMessage.messageRecord.isOutgoing
    val recipient: Recipient = if (outgoing) conversationMessage.messageRecord.toRecipient else conversationMessage.messageRecord.fromRecipient

    val direction = if (outgoing) {
      context.getString(R.string.PaymentMessageView_you_sent_s, recipient.getShortDisplayName(context))
    } else {
      context.getString(R.string.PaymentMessageView_s_sent_you, recipient.getShortDisplayName(context))
    }

    val amountText = if (meta.sats != null && meta.sats > 0) {
      val formatter = java.text.NumberFormat.getInstance()
      formatter.maximumFractionDigits = 0
      formatter.format(meta.sats).let { "₿ $it" }
    } else {
      context.getString(R.string.cashu_token_label)
    }

    val pill = CashuPaymentMessageView(context)
    val colorizer = Colorizer()
    pill.bind(direction, amountText, meta.memo?.takeIf { it.isNotBlank() }, outgoing, recipient, colorizer)

    return PillBinding(pill, meta, outgoing, recipient, amountText)
  }

  private fun configureReceive(pillBinding: PillBinding, token: String, conversationMessage: ConversationMessage) {
    val pill = pillBinding.view

    pill.setReceiveButtonVisible(true)
    pill.setReceiveButtonEnabled(true)

    val receiveButton = pill.getReceiveButton()
    val receiveContainer = pill.getReceiveContainer()
    val clickListener = View.OnClickListener {
      pill.setReceiveButtonEnabled(false)
      pill.showSpinner(true)

      val engine = PaymentsEngineProvider.get(AppDependencies.application)

      CoroutineScope(Dispatchers.Main).launch {
        val ctx = pill.context
        val result = withContext(Dispatchers.IO) { engine.importToken(token) }
        result.onSuccess { received ->
          val peer = conversationMessage.messageRecord.fromRecipient
          val memo = "Received from|rid:" + peer.id.serialize() + "|name:" + peer.getDisplayName(ctx).replace("|", "｜")
          try {
            CashuReceiveStore(ctx).add(
              CashuReceiveStore.Received(null, received.addedSats, System.currentTimeMillis(), memo)
            )
          } catch (_: Throwable) {}

          pill.updateAmountText(ctx.getString(R.string.cashu_token_received_sats, received.addedSats))
          pill.showSpinner(false)
          pill.setReceiveButtonVisible(false)
        }.onFailure { throwable ->
          Log.e(TAG, "Cashu receive failed", throwable)
          pill.showSpinner(false)
          pill.setReceiveButtonEnabled(true)
          pill.setReceiveButtonVisible(true)
          pill.updateAmountText(pillBinding.amountText)

          val message = throwable.message ?: ctx.getString(R.string.cashu_token_receive_failed)
          Toast.makeText(ctx, message, Toast.LENGTH_LONG).show()
        }
      }
    }
    receiveContainer.setOnClickListener(clickListener)
    receiveButton.setOnClickListener(clickListener)
  }

  private fun addPill(parent: ViewGroup, anchor: View, pill: View) {
    val index = parent.indexOfChild(anchor)
    val baseLp = anchor.layoutParams
    val newLp = when (baseLp) {
      is ConstraintLayout.LayoutParams -> ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_PARENT, ConstraintLayout.LayoutParams.WRAP_CONTENT).apply {
        leftToLeft = baseLp.leftToLeft
        rightToRight = baseLp.rightToRight
        topToTop = baseLp.topToTop
        bottomToBottom = baseLp.bottomToBottom
        setMargins(baseLp.leftMargin, baseLp.topMargin, baseLp.rightMargin, baseLp.bottomMargin)
      }
      is ViewGroup.MarginLayoutParams -> ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
        setMargins(baseLp.leftMargin, baseLp.topMargin, baseLp.rightMargin, baseLp.bottomMargin)
      }
      else -> ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
    parent.addView(pill, if (index >= 0) index else parent.childCount, newLp)
  }
}
