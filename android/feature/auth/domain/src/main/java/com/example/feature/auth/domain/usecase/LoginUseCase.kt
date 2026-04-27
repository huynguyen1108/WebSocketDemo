package com.example.feature.auth.domain.usecase

import com.example.core.common.AppResult
import com.example.feature.auth.domain.model.AuthUser
import com.example.feature.auth.domain.repository.AuthRepository
import javax.inject.Inject

class LoginUseCase @Inject constructor(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke(
        serverUrl: String,
        username: String,
        password: String,
    ): AppResult<AuthUser> {
        if (serverUrl.isBlank()) return AppResult.Error("Server URL không được để trống")
        if (username.isBlank()) return AppResult.Error("Tên đăng nhập không được để trống")
        if (password.isBlank()) return AppResult.Error("Mật khẩu không được để trống")
        return repository.login(serverUrl.trim(), username.trim(), password)
    }
}
