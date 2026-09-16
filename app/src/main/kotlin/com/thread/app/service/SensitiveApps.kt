package com.thread.app.service

/**
 * Apps Thread will not read the contents of, ever.
 *
 * Reading screen text across every app is how the Google-search case gets solved,
 * and it is also how a memory aid becomes a record of somebody's bank balance,
 * their diagnosis, or what they said to their sister. The capability and the harm
 * are the same capability.
 *
 * So: interruption and return are still tracked for these apps, because knowing
 * the user *left* requires only a package name and betrays nothing. What stops at
 * the door is content. Thread will note that you went to your banking app; it will
 * not note what it said.
 *
 * Matching is by keyword against the package name, which is deliberately broad -
 * it will catch apps that are not sensitive. A false positive costs a slightly
 * thinner card. A false negative costs a user their privacy, once, permanently.
 */
object SensitiveApps {

    private val keywords = listOf(
        // money
        "bank", "wallet", "pay", "upi", "finance", "invest", "trading", "crypto",
        "insurance", "tax", "loan", "credit",
        // health
        "health", "medic", "clinic", "hospital", "doctor", "pharma", "therapy",
        "fitness", "period", "pregnan",
        // private correspondence and identity
        "message", "messeng", "whatsapp", "telegram", "signal", "dating", "tinder",
        "password", "authenticator", "vault", "keeper", "vpn",
        // government identity
        "aadhaar", "digilocker", "gov",
    )

    private val explicit = setOf(
        "com.android.settings",
        "com.google.android.apps.messaging",
        "com.whatsapp",
        "org.thoughtcrime.securesms",
        "com.android.vending",
    )

    fun readContent(packageName: String): Boolean = !isSensitive(packageName)

    fun isSensitive(packageName: String): Boolean {
        if (packageName in explicit) return true
        val lower = packageName.lowercase()
        return keywords.any { lower.contains(it) }
    }
}
