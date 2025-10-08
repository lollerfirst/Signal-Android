package org.thoughtcrime.securesms.conversation.ui.payment

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.ColorInt
import androidx.core.view.ViewCompat
import com.google.android.material.progressindicator.CircularProgressIndicatorSpec
import com.google.android.material.progressindicator.IndeterminateDrawable
import org.signal.core.util.dp
import org.thoughtcrime.securesms.components.quotes.QuoteViewColorTheme
import org.thoughtcrime.securesms.conversation.colors.Colorizer
import org.thoughtcrime.securesms.databinding.CashuPaymentMessageViewBinding
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.visible

class CashuPaymentMessageView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

  private val binding: CashuPaymentMessageViewBinding = CashuPaymentMessageViewBinding.inflate(LayoutInflater.from(context), this, true)

  fun bind(directionText: String, amountText: String, note: String?, outgoing: Boolean, recipient: Recipient, colorizer: Colorizer) {
    binding.cashuPaymentDirection.apply {
      text = directionText
      setTextColor(if (outgoing) colorizer.getOutgoingFooterTextColor(context) else colorizer.getIncomingFooterTextColor(context, recipient.hasWallpaper))
    }

    binding.cashuPaymentNote.apply {
      if (!note.isNullOrEmpty()) {
        text = note
        visibility = View.VISIBLE
        setTextColor(if (outgoing) colorizer.getOutgoingBodyTextColor(context) else colorizer.getIncomingBodyTextColor(context, recipient.hasWallpaper))
      } else {
        visibility = View.GONE
      }
    }

    val theme = QuoteViewColorTheme.resolveTheme(outgoing, false, recipient.hasWallpaper)
    ViewCompat.setBackgroundTintList(binding.cashuPaymentAmountLayout, ColorStateList.valueOf(theme.getBackgroundColor(context)))

    binding.cashuPaymentAmount.text = amountText
    binding.cashuPaymentAmount.setTextColor(theme.getForegroundColor(context))

    showProgress(false)
    showSpinner(false)
    setReceiveButtonVisible(true)
    setReceiveButtonEnabled(true)
  }

  fun bindInProgress(directionText: String, outgoing: Boolean, recipient: Recipient, colorizer: Colorizer) {
    bind(directionText, "", null, outgoing, recipient, colorizer)
    showProgress(true)
  }

  fun showProgress(show: Boolean) {
    binding.cashuPaymentAmount.visible = !show
    binding.cashuPaymentInprogress.visible = show
    if (show) {
      binding.cashuPaymentInprogress.setImageDrawable(getInProgressDrawable(binding.cashuPaymentAmount.currentTextColor))
    } else {
      binding.cashuPaymentInprogress.setImageDrawable(null)
    }
  }

  fun showSpinner(show: Boolean) {
    binding.cashuPaymentReceiveSpinner.visible = show
    binding.cashuPaymentReceiveButton.visible = !show
  }

  fun setReceiveButtonEnabled(enabled: Boolean) {
    binding.cashuPaymentReceiveContainer.isEnabled = enabled
    binding.cashuPaymentReceiveButton.isEnabled = enabled
  }

  fun setReceiveButtonVisible(visible: Boolean) {
    binding.cashuPaymentReceiveContainer.visible = visible
    binding.cashuPaymentReceiveContainer.isEnabled = visible
    binding.cashuPaymentReceiveButton.isEnabled = visible
    if (!visible) {
      binding.cashuPaymentReceiveSpinner.visible = false
      binding.cashuPaymentReceiveButton.visible = false
    } else {
      val spinnerVisible = binding.cashuPaymentReceiveSpinner.visibility == View.VISIBLE
      binding.cashuPaymentReceiveButton.visible = !spinnerVisible
    }
  }

  fun updateAmountText(amountText: String) {
    binding.cashuPaymentAmount.text = amountText
  }

  fun getReceiveButton(): View = binding.cashuPaymentReceiveButton

  fun getReceiveContainer(): View = binding.cashuPaymentReceiveContainer

  private fun getInProgressDrawable(@ColorInt color: Int): IndeterminateDrawable<CircularProgressIndicatorSpec> {
    val spec = CircularProgressIndicatorSpec(context, null).apply {
      indicatorInset = 0
      indicatorColors = intArrayOf(color)
      indicatorSize = 20.dp
      trackThickness = 2.dp
    }

    return IndeterminateDrawable.createCircularDrawable(context, spec).apply {
      setBounds(0, 0, spec.indicatorSize, spec.indicatorSize)
    }
  }
}
