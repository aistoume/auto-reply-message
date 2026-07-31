package solutions.aicon.aplomb

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

/**
 * Google Play 订阅 —— 三档，每月自动续电池。
 *
 * 商品 id 和 iOS 用同一套，服务端按 id 判档位就不用分平台写两遍。
 *
 * 一条硬规则：**客户端说自己订阅了不算数**。购买成功后只是把 purchaseToken
 * 交给服务器，由服务器去 Google Play Developer API 核实再发电池。这里发的
 * 电池数只是给用户看的预期值。
 */
object Subscriptions {

    /** 与 iOS 一致；服务端 TIERS 也用这三个 key。 */
    const val LITE = "aplomb.sub.lite"
    const val PLUS = "aplomb.sub.plus"
    const val PRO = "aplomb.sub.pro"

    private val ORDER = listOf(LITE, PLUS, PRO)

    private fun barsOf(id: String) = when (id) {
        LITE -> 100; PLUS -> 300; PRO -> 1000; else -> 0
    }

    private fun titleOf(c: Context, id: String) = c.getString(
        when (id) {
            LITE -> R.string.tier_lite
            PLUS -> R.string.tier_plus
            else -> R.string.tier_pro
        }
    )

    // ── 付费页 ────────────────────────────────────────────────────────

    /**
     * 拉起订阅页。整个 BillingClient 的生命周期跟着这个对话框走 ——
     * 常驻一个连接没有意义，用户一年也点不了几次。
     */
    fun showPaywall(activity: Activity, onDone: () -> Unit) {
        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 22), dp(activity, 12), dp(activity, 22), 0)
        }
        val status = TextView(activity).apply {
            text = activity.getString(R.string.sub_loading)
            textSize = 13f; setTextColor(Color.GRAY)
        }
        body.addView(TextView(activity).apply {
            text = activity.getString(R.string.sub_sub)
            textSize = 13f; setTextColor(Color.GRAY)
            setPadding(0, 0, 0, dp(activity, 14))
        })
        val cards = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        body.addView(cards)
        body.addView(status)
        body.addView(TextView(activity).apply {
            text = activity.getString(R.string.sub_terms) + "\n\n" +
                activity.getString(R.string.sub_byok)
            textSize = 11f; setTextColor(Color.GRAY)
            setPadding(0, dp(activity, 14), 0, 0)
        })

        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.sub_title)
            .setView(ScrollView(activity).apply { addView(body) })
            .setPositiveButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.sub_restore, null)
            .create()

        lateinit var client: BillingClient

        val listener = PurchasesUpdatedListener { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK || purchases == null) return@PurchasesUpdatedListener
            purchases.forEach { p -> settle(activity, client, p, onDone) }
        }

        client = BillingClient.newBuilder(activity)
            .setListener(listener)
            .enablePendingPurchases()
            .build()

        dialog.setOnDismissListener { runCatching { client.endConnection() } }
        dialog.show()
        // 恢复购买不能关对话框 —— 默认行为会关，所以拿到按钮后重设监听
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
            restore(activity, client, onDone)
        }

        client.startConnection(object : BillingClientStateListener {
            override fun onBillingServiceDisconnected() {}
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    activity.runOnUiThread {
                        status.text = activity.getString(R.string.sub_unavailable)
                    }
                    return
                }
                loadProducts(activity, client) { details ->
                    activity.runOnUiThread {
                        if (details.isEmpty()) {
                            status.text = activity.getString(R.string.sub_unavailable)
                            return@runOnUiThread
                        }
                        status.visibility = TextView.GONE
                        cards.removeAllViews()
                        details.forEach { d ->
                            cards.addView(card(activity, d) { launch(activity, client, d) })
                        }
                    }
                }
            }
        })
    }

    private fun loadProducts(
        c: Context,
        client: BillingClient,
        onResult: (List<ProductDetails>) -> Unit,
    ) {
        val products = ORDER.map {
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(it)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }
        client.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder().setProductList(products).build()
        ) { result, list ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                onResult(emptyList()); return@queryProductDetailsAsync
            }
            // Play 返回的顺序不保证，按我们的档位顺序排一下
            onResult(list.sortedBy { ORDER.indexOf(it.productId) })
        }
    }

    private fun launch(activity: Activity, client: BillingClient, d: ProductDetails) {
        val offer = d.subscriptionOfferDetails?.firstOrNull() ?: return
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(d)
                        .setOfferToken(offer.offerToken)
                        .build()
                )
            ).build()
        client.launchBillingFlow(activity, params)
    }

    // ── 结算 ──────────────────────────────────────────────────────────

    /**
     * 交给服务器核实并发电池，然后 acknowledge。
     *
     * 顺序很重要：**先发电池再 acknowledge**。Google 要求三天内确认，否则
     * 自动退款；万一服务器那边挂了，没确认的订单会退回给用户，比收了钱不发
     * 货好得多。
     */
    private fun settle(
        activity: Activity,
        client: BillingClient,
        purchase: Purchase,
        onDone: () -> Unit,
    ) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        val productId = purchase.products.firstOrNull() ?: return

        Thread {
            val battery = BatteryClient.reportPlayPurchase(activity, productId, purchase.purchaseToken)
            activity.runOnUiThread {
                if (battery == null) {
                    toast(activity, activity.getString(R.string.battery_offline))
                } else {
                    toast(
                        activity,
                        activity.getString(R.string.sub_current, titleOf(activity, productId)),
                    )
                    onDone()
                }
            }
            if (battery != null && !purchase.isAcknowledged) {
                client.acknowledgePurchase(
                    AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken).build()
                ) { }
            }
        }.start()
    }

    /** 换手机/重装后把已有订阅捞回来。 */
    private fun restore(activity: Activity, client: BillingClient, onDone: () -> Unit) {
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS).build()
        ) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK || purchases.isEmpty()) {
                activity.runOnUiThread { toast(activity, activity.getString(R.string.sub_unavailable)) }
                return@queryPurchasesAsync
            }
            purchases.forEach { settle(activity, client, it, onDone) }
        }
    }

    // ── 卡片 ──────────────────────────────────────────────────────────

    private fun card(c: Context, d: ProductDetails, onClick: () -> Unit): LinearLayout {
        val price = d.subscriptionOfferDetails
            ?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()
            ?.formattedPrice.orEmpty()
        return LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12))
            background = GradientDrawable().apply {
                setColor(Color.argb(16, 128, 128, 128)); cornerRadius = dp(c, 14).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(c, 10) }

            addView(LinearLayout(c).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(c).apply {
                    text = titleOf(c, d.productId); textSize = 16f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                })
                addView(TextView(c).apply {
                    text = c.getString(R.string.sub_bars, barsOf(d.productId))
                    textSize = 12f; setTextColor(Color.GRAY)
                })
            })
            addView(TextView(c).apply {
                text = price; textSize = 16f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            setOnClickListener { onClick() }
        }
    }

    private fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density).toInt()
    private fun toast(c: Context, m: String) = Toast.makeText(c, m, Toast.LENGTH_LONG).show()
}
