package com.taxiapp.driver.ui.dialogs

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import com.taxiapp.driver.R

/**
 * Ізольований фабричний клас модальних діалогів для MainActivity та OrderProgressActivity:
 * - оцінка пасажира після завершення поїздки;
 * - блокуюче сповіщення про скасування замовлення;
 * - помилка безготівкової оплати (HTTP 402);
 * - підтвердження сигналу SOS та відновлення акаунту.
 */
object OrderProgressDialogs {

    fun showSosConfirmationDialog(context: Context, onConfirmSos: () -> Unit) {
        val customView = LayoutInflater.from(context).inflate(R.layout.dialog_sos_confirm, null)
        val dialog = AlertDialog.Builder(context)
            .setView(customView)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val btnCancel = customView.findViewById<View>(R.id.btnCancelSos)
        val btnConfirm = customView.findViewById<View>(R.id.btnConfirmSos)
        btnCancel.setOnClickListener { dialog.dismiss() }
        btnConfirm.setOnClickListener {
            dialog.dismiss()
            onConfirmSos()
        }
        dialog.show()
    }

    fun showRestoreAccountDialog(
        context: Context,
        onCancel: (Dialog) -> Unit,
        onConfirm: (Dialog, AppCompatButton) -> Unit
    ): Dialog {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_restore_account)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.setCancelable(false)

        val btnCancel = dialog.findViewById<AppCompatButton>(R.id.btnCancelRestore)
        val btnConfirm = dialog.findViewById<AppCompatButton>(R.id.btnConfirmRestore)

        btnCancel?.setOnClickListener { onCancel(dialog) }
        btnConfirm?.setOnClickListener {
            btnConfirm.isEnabled = false
            btnConfirm.text = "Відновлення..."
            onConfirm(dialog, btnConfirm)
        }
        dialog.show()
        return dialog
    }

    fun showOrderCancelledDialog(context: Context, onUnderstood: () -> Unit) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_permission_overlay, null)
        val alertDialog = android.app.AlertDialog.Builder(context)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        val tvTitle = (dialogView as LinearLayout).getChildAt(0) as TextView
        val tvDesc = dialogView.getChildAt(1) as TextView

        tvTitle.setText(R.string.dialog_order_cancelled_title)
        tvDesc.setText(R.string.dialog_order_cancelled_desc)

        val btnCancel = dialogView.findViewById<View>(R.id.btnCancelPermission)
        val btnAllow = dialogView.findViewById<AppCompatButton>(R.id.btnAllowPermission)

        btnCancel.visibility = View.GONE

        val params = btnAllow.layoutParams as LinearLayout.LayoutParams
        params.weight = 2f
        params.marginStart = 0
        btnAllow.layoutParams = params

        btnAllow.text = context.getString(R.string.btn_understood)
        btnAllow.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.driver_error_red))
        btnAllow.setTextColor(Color.WHITE)

        btnAllow.setOnClickListener {
            alertDialog.dismiss()
            onUnderstood()
        }

        alertDialog.show()
        alertDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    fun showRatingDialog(
        context: Context,
        onSubmit: (score: Int, comment: String, dialog: Dialog) -> Unit
    ) {
        val dialog = Dialog(context)
        dialog.setContentView(R.layout.dialog_rate_client)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.setCancelable(false)

        val etComment = dialog.findViewById<EditText>(R.id.et_comment)
        val btnSubmit = dialog.findViewById<Button>(R.id.btn_submit_rating)

        var selectedRating = 0
        val starsList = listOf<ImageView>(
            dialog.findViewById(R.id.star1),
            dialog.findViewById(R.id.star2),
            dialog.findViewById(R.id.star3),
            dialog.findViewById(R.id.star4),
            dialog.findViewById(R.id.star5)
        )

        val activeTint = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.driver_neon_teal))
        val inactiveTint = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.driver_text_secondary))

        fun renderStars(rating: Int) {
            selectedRating = rating
            starsList.forEachIndexed { index, imageView ->
                imageView.imageTintList = if (index < rating) activeTint else inactiveTint
            }
        }

        renderStars(0)
        starsList.forEachIndexed { index, imageView ->
            imageView.setOnClickListener { renderStars(index + 1) }
        }

        btnSubmit.setOnClickListener {
            if (selectedRating == 0) {
                Toast.makeText(context, "Поставте оцінку", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            onSubmit(selectedRating, etComment.text.toString(), dialog)
        }
        dialog.show()
    }

    fun showPaymentErrorDialog(context: Context) {
        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.payment_failed_title))
            .setMessage(context.getString(R.string.payment_failed_message))
            .setCancelable(false)
            .setPositiveButton(context.getString(R.string.btn_understood)) { dialog, _ -> dialog.dismiss() }
            .show()
    }
}
