package com.pfms.app.viewmodel

import androidx.lifecycle.ViewModel
import com.pfms.app.domain.repository.SyncStatusRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SyncStatusViewModel @Inject constructor(private val repository: SyncStatusRepository) : ViewModel() {
    val uiState = repository.status
    fun retry() = repository.retry()
}
