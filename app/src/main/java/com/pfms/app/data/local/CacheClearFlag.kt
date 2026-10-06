package com.pfms.app.data.local

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore

/**
 * FR-05: "clear the user's locally cached data after logout".
 *
 * Firestore's on-device cache can only be wiped with clearPersistence() BEFORE the Firestore
 * client is first used in a process (or after terminate(), which makes the instance unusable
 * until restart). So logout only raises this flag, and PFMSApplication.onCreate() performs the
 * wipe at the next cold start, before any Firestore call. Until then the cache is still
 * protected: queries are scoped to users/{uid} and Security Rules deny other users.
 *
 * SharedPreferences (not DataStore) because Application.onCreate needs a synchronous read.
 */
object CacheClearFlag {

    private const val PREFS = "pfms_cache_policy"
    private const val KEY_CLEAR_ON_LAUNCH = "clear_firestore_cache_on_launch"

    fun request(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_CLEAR_ON_LAUNCH, true)
            .apply()
    }

    /** Must be called from Application.onCreate(), before Firestore is used. */
    fun applyIfRequested(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_CLEAR_ON_LAUNCH, false)) return

        FirebaseFirestore.getInstance().clearPersistence().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                prefs.edit().putBoolean(KEY_CLEAR_ON_LAUNCH, false).apply()
            }
        }
    }
}
