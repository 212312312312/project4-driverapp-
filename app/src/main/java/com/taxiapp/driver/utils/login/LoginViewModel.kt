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

    // Состояния, за которыми следит Activity
    private val _loginResult = MutableLiveData<Result<Boolean>>()
    val loginResult: LiveData<Result<Boolean>> = _loginResult

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    // ИСПРАВЛЕНО: license -> password (так как в UI у нас поле для пароля)
    fun login(phone: String, password: String) {
        if (phone.isBlank() || password.isBlank()) {
            _loginResult.value = Result.failure(IllegalArgumentException("Заполните все поля"))
            return
        }

        _isLoading.value = true

        viewModelScope.launch {
            try {
                // 1. Делаем запрос
                // Убедись, что LoginRequest принимает (phone, password)
                val response = apiService.login(LoginRequest(phone, password))

                // 2. Обрабатываем ответ
                if (response.isSuccessful && response.body() != null) {
                    val token = response.body()!!.token

                    // Сохраняем токен
                    sessionManager.saveAuthToken(token)
                    _loginResult.value = Result.success(true)
                } else {
                    // Улучшенная обработка ошибок
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
                // Скрываем загрузку в любом случае
                _isLoading.value = false
            }
        }
    }
}