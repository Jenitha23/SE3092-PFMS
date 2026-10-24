package com.pfms.app.ui.screen.income

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.pfms.app.R
import com.pfms.app.viewmodel.IncomeRecordIcon

/** Local vectors retain the original coin brand colors, independently of gain/loss styling. */
@Composable
internal fun IncomeRecordIconImage(icon: IncomeRecordIcon, tint: Color = MaterialTheme.colorScheme.primary) {
    val asset = when (icon) {
        IncomeRecordIcon.SALARY -> R.drawable.ic_income_salary
        IncomeRecordIcon.FREELANCE -> R.drawable.ic_income_freelance
        IncomeRecordIcon.ADSENSE -> R.drawable.ic_income_adsense
        IncomeRecordIcon.OTHER -> R.drawable.ic_income_other
        IncomeRecordIcon.CRYPTO -> R.drawable.ic_income_crypto
        IncomeRecordIcon.BTC -> R.drawable.ic_income_btc
        IncomeRecordIcon.ETH -> R.drawable.ic_income_eth
        IncomeRecordIcon.USDT -> R.drawable.ic_income_usdt
        IncomeRecordIcon.SOL -> R.drawable.ic_income_sol
        IncomeRecordIcon.BNB -> R.drawable.ic_income_bnb
        IncomeRecordIcon.XRP -> R.drawable.ic_income_xrp
        IncomeRecordIcon.ADA -> R.drawable.ic_income_ada
    }
    val brand = when (icon) {
        IncomeRecordIcon.BTC, IncomeRecordIcon.ETH, IncomeRecordIcon.USDT, IncomeRecordIcon.SOL,
        IncomeRecordIcon.BNB, IncomeRecordIcon.XRP, IncomeRecordIcon.ADA -> true
        else -> false
    }
    Icon(painterResource(asset), contentDescription = null, modifier = Modifier.size(if (brand) 28.dp else 23.dp),
        tint = if (brand) Color.Unspecified else tint)
}
