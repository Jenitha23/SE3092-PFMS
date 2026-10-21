package com.pfms.app.domain.repository

import kotlinx.coroutines.flow.StateFlow

interface ConnectivityObserver { val isOnline: StateFlow<Boolean> }
