package com.pfms.app.viewmodel

import androidx.lifecycle.ViewModel
import com.pfms.app.domain.repository.SyncStatusRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SyncStatusViewModel @Inject constructor(repository: SyncStatusRepository) : ViewModel() {
    val uiState = repository.status
}
