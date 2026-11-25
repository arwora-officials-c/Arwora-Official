package com.arwora.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.arwora.MainActivity
import com.arwora.utils.AppLockerManager

/**
 * Handles secret dialer codes (`*#*#2796#*#*`) to reopen Arwora Hyper Toolkit's
 * PIN/Biometric-gated Hidden Apps Vault even while Self-Stealth launcher hiding is active.
 */
class SecretCodeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != "android.provider.Telephony.SECRET_CODE") return

        AppLockerManager.init(context.applicationContext)
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            )
            putExtra("skip_splash", true)
            putExtra(EXTRA_OPEN_HIDDEN_VAULT, true)
        }
        runCatching { context.startActivity(launchIntent) }
    }

    companion object {
        const val DEFAULT_SECRET_CODE = "2796"
        const val EXTRA_OPEN_HIDDEN_VAULT = "extra_open_hidden_vault"
    }
}
