package com.pfms.app.domain.model

class DuplicateExpenseException : IllegalStateException("A matching expense was submitted within 60 seconds. Confirm before saving another.")
