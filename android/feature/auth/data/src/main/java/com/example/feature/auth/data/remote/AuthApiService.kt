package com.example.feature.auth.data.remote

import com.example.feature.auth.data.dto.LoginRequestDto
import com.example.feature.auth.data.dto.LoginResponseDto
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url

interface AuthApiService {
    // @Url overrides the Retrofit base URL — used for dynamic server address
    @POST
    suspend fun login(
        @Url url: String,
        @Body request: LoginRequestDto,
    ): LoginResponseDto
}
