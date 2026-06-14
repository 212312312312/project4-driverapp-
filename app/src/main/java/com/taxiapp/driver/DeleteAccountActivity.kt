package com.taxiapp.driver

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.utils.SessionManager
import kotlinx.coroutines.launch

class DeleteAccountActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var btnConfirmDelete: Button

    // Викидаємо на екран логіну


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_delete_account)

        sessionManager = SessionManager(this)
        btnConfirmDelete = findViewById(R.id.btnConfirmDelete)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            finish()
        }

        // Вызываем безопасный кастомный диалог перед удалением
        btnConfirmDelete.setOnClickListener {
            showConfirmDeleteAccountDialog()
        }
    }

    private fun requestDeletion() {
        setLoading(true)
        lifecycleScope.launch {
            try {
                val response = ApiClient.getInstance().getApiService(this@DeleteAccountActivity).requestAccountDeletion()
                if (response.isSuccessful) {
                    Toast.makeText(this@DeleteAccountActivity, "Акаунт переведено в чергу на видалення", Toast.LENGTH_LONG).show()

                    // Очищаємо сесію
                    sessionManager.saveAuthToken("")
                    sessionManager.saveDriverId(-1L)

                    // Викидаємо на екран логіну
                    // Викидаємо на стартовий екран WelcomeActivity
                    val intent = Intent(this@DeleteAccountActivity, WelcomeActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                } else {
                    Toast.makeText(this@DeleteAccountActivity, "Помилка при видаленні", Toast.LENGTH_SHORT).show()
                    setLoading(false)
                }
            } catch (e: Exception) {
                Toast.makeText(this@DeleteAccountActivity, "Помилка мережі", Toast.LENGTH_SHORT).show()
                setLoading(false)
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        btnConfirmDelete.isEnabled = !isLoading
        btnConfirmDelete.text = if (isLoading) "Обробка..." else "Підтвердити"
    }

    private fun showConfirmDeleteAccountDialog() {
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        val dialogView = layoutInflater.inflate(R.layout.dialog_confirm_delete_account, null)
        builder.setView(dialogView)

        val dialog = builder.create()
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

        val btnCancel = dialogView.findViewById<android.view.View>(R.id.btn_cancel_delete_account)
        val btnConfirm = dialogView.findViewById<android.view.View>(R.id.btn_confirm_delete_account)

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnConfirm.setOnClickListener {
            dialog.dismiss()
            requestDeletion() // Запускаем удаление только после подтверждения
        }

        dialog.show()
    }
}