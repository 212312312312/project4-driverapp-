package com.taxiapp.driver.ui.dialogs

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.taxiapp.driver.R

/**
 * Єдиний перевикористовуваний презентер BottomSheet-діалогів зі списками опцій
 * та меню зв'язку з диспетчерською/підтримкою (для MainActivity та OrderProgressActivity).
 */
object DriverBottomSheetHelper {

    data class SheetOption(
        val id: Long = 0L,
        val text: String,
        @DrawableRes val iconRes: Int? = null,
        val isDestructive: Boolean = false,
        val onSelect: (() -> Unit)? = null
    )

    fun showOptionsSheet(
        context: Context,
        title: String,
        options: List<SheetOption>,
        onOptionClick: ((SheetOption) -> Unit)? = null
    ) {
        val dialog = BottomSheetDialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.layout_bottom_sheet_generic, null)
        dialog.setContentView(view)
        dialog.window?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.background =
            ColorDrawable(Color.TRANSPARENT)

        val tvTitle = view.findViewById<TextView>(R.id.tv_sheet_title)
        val rvOptions = view.findViewById<RecyclerView>(R.id.rv_sheet_options)

        tvTitle.text = title
        rvOptions.layoutManager = LinearLayoutManager(context)
        rvOptions.adapter = OptionsAdapter(context, options) { selected ->
            dialog.dismiss()
            selected.onSelect?.invoke()
            onOptionClick?.invoke(selected)
        }
        dialog.show()
    }

    fun showSupportSheet(context: Context) {
        try {
            val dialog = BottomSheetDialog(context)
            val view = LayoutInflater.from(context).inflate(R.layout.layout_bottom_sheet_support, null)
            dialog.setContentView(view)
            dialog.window?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.background =
                ColorDrawable(Color.TRANSPARENT)

            view.findViewById<View>(R.id.bs_btn_telegram)?.setOnClickListener {
                dialog.dismiss()
                openTelegramSupport(context)
            }
            view.findViewById<View>(R.id.bs_btn_call)?.setOnClickListener {
                dialog.dismiss()
                callDispatcher(context)
            }
            dialog.show()
        } catch (e: Exception) {
            Toast.makeText(context, "Помилка", Toast.LENGTH_SHORT).show()
        }
    }

    fun openTelegramSupport(context: Context) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/TaxiSupport")))
        } catch (e: Exception) {
            Toast.makeText(context, "Telegram не встановлено", Toast.LENGTH_SHORT).show()
        }
    }

    fun callDispatcher(context: Context) {
        try {
            context.startActivity(Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:+380999999999")
            })
        } catch (e: Exception) {
            Toast.makeText(context, "Помилка дзвінка", Toast.LENGTH_SHORT).show()
        }
    }

    private class OptionsAdapter(
        private val context: Context,
        private val items: List<SheetOption>,
        private val onClick: (SheetOption) -> Unit
    ) : RecyclerView.Adapter<OptionsAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvText: TextView = view.findViewById(R.id.tv_option_text)
            val ivIcon: ImageView = view.findViewById(R.id.iv_option_icon)
            val divider: View = view.findViewById(R.id.divider_option)
            val container: View = view.findViewById(R.id.container_option)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_bottom_sheet_option, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            val primaryColor = ContextCompat.getColor(context, R.color.driver_text_primary)
            val errorColor = ContextCompat.getColor(context, R.color.driver_error_red)

            holder.tvText.text = item.text
            if (item.iconRes != null) {
                holder.ivIcon.visibility = View.VISIBLE
                holder.ivIcon.setImageResource(item.iconRes)
                val targetColor = if (item.isDestructive) errorColor else primaryColor
                holder.ivIcon.setColorFilter(targetColor)
                holder.tvText.setTextColor(targetColor)
            } else {
                holder.ivIcon.visibility = View.GONE
                holder.tvText.setTextColor(if (item.isDestructive) errorColor else primaryColor)
            }
            holder.divider.visibility = if (position == items.size - 1) View.GONE else View.VISIBLE
            holder.container.setOnClickListener { onClick(item) }
        }

        override fun getItemCount(): Int = items.size
    }
}
