package com.example.feature.auth.data.remote

import com.example.core.common.AppResult
import com.example.feature.auth.data.dto.LoginRequestDto
import com.example.feature.auth.data.dto.LoginResponseDto
import retrofit2.HttpException
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

class AuthRemoteDataSource @Inject constructor(
    private val apiService: AuthApiService,
) {
    suspend fun login(
        serverUrl: String,
        username: String,
        password: String,
    ): AppResult<LoginResponseDto> = try {
        AppResult.Success(
            apiService.login(
                url = "${serverUrl.toHttpBaseUrl()}/api/auth/login",
                request = LoginRequestDto(username, password),
            ),
        )
    } catch (e: HttpException) {
        Timber.e(e, "Login HTTP %d", e.code())
        AppResult.Error("Đăng nhập thất bại (${e.code()}): ${e.message()}")
    } catch (e: IOException) {
        Timber.e(e, "Login network error")
        AppResult.Error(e.message ?: "Lỗi kết nối mạng")
    }

    private fun String.toHttpBaseUrl(): String = trimEnd('/')
        .replace("ws://", "http://")
        .replace("wss://", "https://")
}
