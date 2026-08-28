package com.rangia.app

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Google Play Billing wrapper for the permanent RangIA Pro unlock.
 *
 * Play Console product id: rangia_pro_lifetime
 * Product type: one-time / non-consumable.
 *
 * Debug builds are unlocked separately in the UI so sideloaded test APKs remain fully usable.
 */
class BillingManager(context: Context) : PurchasesUpdatedListener {
    companion object {
        const val PRO_PRODUCT_ID = "rangia_pro_lifetime"
    }

    private val prefs = context.getSharedPreferences("rangia_billing", Context.MODE_PRIVATE)

    private val _isPro = MutableStateFlow(prefs.getBoolean("pro_cached", false))
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    private val _price = MutableStateFlow<String?>(null)
    val price: StateFlow<String?> = _price.asStateFlow()

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    private var productDetails: ProductDetails? = null

    private val billingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .enableAutoServiceReconnection()
        .build()

    fun start() {
        if (billingClient.isReady) {
            refresh()
            return
        }
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProduct()
                    queryPurchases()
                } else {
                    _status.value = "Google Play Billing indisponible (${result.responseCode})."
                }
            }

            override fun onBillingServiceDisconnected() {
                _status.value = "Connexion Google Play temporairement indisponible."
            }
        })
    }

    fun refresh() {
        if (!billingClient.isReady) {
            start()
            return
        }
        queryProduct()
        queryPurchases()
    }

    fun launchPurchase(activity: Activity) {
        if (!billingClient.isReady) {
            _status.value = "Connexion à Google Play… réessaie dans quelques secondes."
            start()
            return
        }

        val details = productDetails
        if (details == null) {
            _status.value = "Le produit RangIA Pro n’est pas encore disponible sur ce canal Google Play."
            queryProduct()
            return
        }

        val selectedOffer = details.oneTimePurchaseOfferDetailsList?.firstOrNull()
        val productParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
        selectedOffer?.offerToken
            ?.takeIf { it.isNotBlank() }
            ?.let(productParamsBuilder::setOfferToken)

        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParamsBuilder.build()))
            .build()

        val result = billingClient.launchBillingFlow(activity, params)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _status.value = "Impossible d’ouvrir l’achat (${result.responseCode})."
        }
    }

    fun restorePurchases() {
        if (!billingClient.isReady) start() else queryPurchases()
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases.orEmpty().forEach(::processPurchase)
            BillingClient.BillingResponseCode.USER_CANCELED -> _status.value = "Achat annulé."
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> queryPurchases()
            else -> _status.value = "Achat non finalisé (${result.responseCode})."
        }
    }

    private fun queryProduct() {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PRO_PRODUCT_ID)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(product))
            .build()

        billingClient.queryProductDetailsAsync(params) { result, queryResult ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryProductDetailsAsync
            productDetails = queryResult.productDetailsList.firstOrNull { it.productId == PRO_PRODUCT_ID }
            val details = productDetails
            val offer = details?.oneTimePurchaseOfferDetailsList?.firstOrNull()
            _price.value = offer?.formattedPrice ?: details?.oneTimePurchaseOfferDetails?.formattedPrice
        }
    }

    private fun queryPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        billingClient.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync
            val owned = purchases.filter { it.products.contains(PRO_PRODUCT_ID) }
            if (owned.isEmpty()) {
                setPro(false)
                _status.value = "Achats restaurés."
            } else {
                owned.forEach(::processPurchase)
            }
        }
    }

    private fun processPurchase(purchase: Purchase) {
        if (!purchase.products.contains(PRO_PRODUCT_ID)) return
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                if (purchase.isAcknowledged) {
                    setPro(true)
                    _status.value = "RangIA Pro est activé."
                } else {
                    val params = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                    billingClient.acknowledgePurchase(params) { result ->
                        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                            setPro(true)
                            _status.value = "RangIA Pro est activé."
                        } else {
                            _status.value = "Achat reçu, validation Google Play en attente."
                        }
                    }
                }
            }
            Purchase.PurchaseState.PENDING -> _status.value = "Paiement en attente de confirmation."
            else -> Unit
        }
    }

    private fun setPro(value: Boolean) {
        _isPro.value = value
        prefs.edit().putBoolean("pro_cached", value).apply()
    }

    fun consumeStatus() {
        _status.value = null
    }

    fun close() {
        if (billingClient.isReady) billingClient.endConnection()
    }
}
