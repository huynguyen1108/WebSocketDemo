package com.example.feature.auth.domain.usecase

import com.example.feature.auth.domain.repository.AuthRepository
import javax.inject.Inject

class LogoutUseCase @Inject constructor(
    private val repository: AuthRepository,
) {
    operator fun invoke() = repository.logout()
}
