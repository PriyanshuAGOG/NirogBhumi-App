package com.nirogbhumi.app.payments

import android.app.Activity
import com.razorpay.Checkout
import org.json.JSONObject

object RazorpayPaymentLauncher {
    fun open(activity: Activity, payment: Map<String, Any?>, name: String, email: String, phone: String) {
        val keyId = payment["keyId"]?.toString().orEmpty()
        val orderId = payment["orderId"]?.toString().orEmpty()
        val amount = (payment["amount"] as? Number)?.toInt() ?: error("Invalid payment amount")
        require(keyId.isNotBlank() && orderId.isNotBlank()) { "Invalid payment configuration" }
        Checkout.preload(activity.applicationContext)
        val checkout = Checkout().apply { setKeyID(keyId) }
        val options = JSONObject().apply {
            put("name", "Nirog Bhumi")
            put("description", if (payment["kind"] == "order") "Store order" else "Health consultation")
            put("order_id", orderId)
            put("currency", payment["currency"]?.toString() ?: "INR")
            put("amount", amount)
            put("theme", JSONObject().put("color", "#314936"))
            put("prefill", JSONObject().put("name", name).put("email", email).put("contact", phone))
            put("retry", JSONObject().put("enabled", true).put("max_count", 2))
        }
        checkout.open(activity, options)
    }
}
