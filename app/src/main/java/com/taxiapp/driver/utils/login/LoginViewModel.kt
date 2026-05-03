package com.taxiapp.driver.ui.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.taxiapp.driver.network.DriverApiService
import com.taxiapp.driver.network.LoginRequest
import com.taxiapp.driver.utils.SessionManager
import kotlinx.coroutines.launch
import java.net.ConnectException

class LoginViewModel(
    private val apiService: DriverApiService,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _loginResult = MutableLiveData<Result<Boolean>>()
    val loginResult: LiveData<Result<Boolean>> = _loginResult

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    fun login(phone: String, password: String) {
        if (phone.isBlank() || password.isBlank()) {
            _loginResult.value = Result.failure(IllegalArgumentException("Заполните все поля"))
            return
        }

        _isLoading.value = true

        viewModelScope.launch {
            try {
                val response = apiService.login(LoginRequest(phone, password))

                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!

                    // Сохраняем оба токена
                    sessionManager.saveAuthToken(body.token)
                    body.refreshToken?.let { sessionManager.saveRefreshToken(it) } // <-- СОХРАНЯЕМ REFRESH

                    _loginResult.value = Result.success(true)
                } else {
                    val errorMessage = when (response.code()) {
                        401, 403 -> "Неверный телефон или пароль"
                        404 -> "Пользователь не найден"
                        500 -> "Ошибка на сервере"
                        else -> "Ошибка входа: ${response.code()}"
                    }
                    _loginResult.value = Result.failure(Exception(errorMessage))
                }
            } catch (e: ConnectException) {
                _loginResult.value = Result.failure(Exception("Нет интернета или сервер недоступен"))
            } catch (e: Exception) {
                _loginResult.value = Result.failure(e)
            } finally {
                _isLoading.value = false
            }
        }
    }
}