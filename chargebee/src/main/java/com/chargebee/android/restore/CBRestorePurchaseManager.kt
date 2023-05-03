package com.chargebee.android.restore

import android.util.Log
import com.chargebee.android.Chargebee
import com.chargebee.android.ErrorDetail
import com.chargebee.android.billingservice.CBPurchase
import com.chargebee.android.billingservice.GPErrorCode
import com.chargebee.android.billingservice.RestorePurchaseCallback
import com.chargebee.android.exceptions.CBException
import com.chargebee.android.exceptions.ChargebeeResult
import com.chargebee.android.loggers.CBLogger
import com.chargebee.android.models.*
import com.chargebee.android.models.ResultHandler
import com.chargebee.android.resources.RestorePurchaseResource


class CBRestorePurchaseManager {

    companion object {
        private var allTransactions = ArrayList<PurchaseTransaction>()
        private var restorePurchases = ArrayList<CBRestoreSubscription>()
        private var activeTransactions = ArrayList<PurchaseTransaction>()
        lateinit var completionCallback: RestorePurchaseCallback

        private fun retrieveStoreSubscription(
            purchaseToken: String,
            completion: (ChargebeeResult<Any>) -> Unit
        ) {
            try {
                val logger = CBLogger(name = "restore", action = "retrieve_restore_subscriptions")
                ResultHandler.safeExecuter(
                    { RestorePurchaseResource().retrieveStoreSubscription(purchaseToken) },
                    completion,
                    logger
                )
            } catch (exp: Exception) {
                ChargebeeResult.Error(
                    exp = CBException(
                        error = ErrorDetail(
                            exp.message
                        )
                    )
                )
            }
        }

        internal fun retrieveRestoreSubscription(
            purchaseToken: String,
            result: (CBRestoreSubscription) -> Unit,
            error: (CBException) -> Unit
        ) {
            retrieveStoreSubscription(purchaseToken) {
                when (it) {
                    is ChargebeeResult.Success -> {
                        val restoreSubscription =
                            ((it.data) as CBRestorePurchases).in_app_subscriptions.firstOrNull()
                        restoreSubscription?.let {
                            result(restoreSubscription)
                        }
                    }
                    is ChargebeeResult.Error -> {
                        error(it.exp)
                    }
                }
            }
        }

        internal fun fetchStoreSubscriptionStatus(
            storeTransactions: ArrayList<PurchaseTransaction>,
            completionCallback: RestorePurchaseCallback
        ) {
            this.completionCallback = completionCallback
            val storeTransaction =
                storeTransactions.firstOrNull()?.also { storeTransactions.remove(it) }
            storeTransaction?.purchaseToken?.let { purchaseToken ->
                retrieveRestoreSubscription(purchaseToken, {
                    restorePurchases.add(it)
                    when (it.store_status) {
                        StoreStatus.active -> activeTransactions.add(storeTransaction)
                        else -> allTransactions.add(storeTransaction)
                    }
                    getRestorePurchases(storeTransactions)
                }, { _ ->
                    getRestorePurchases(storeTransactions)
                })
            }
        }

        internal fun getRestorePurchases(storeTransactions: ArrayList<PurchaseTransaction>) {
            if (storeTransactions.isEmpty()) {
                if(restorePurchases.isEmpty()) {
                    completionCallback.onError(
                        CBException(
                            ErrorDetail(
                                message = GPErrorCode.InvalidPurchaseToken.errorMsg,
                                httpStatusCode = 400
                            )
                        )
                    )
                } else {
                    val activePurchases = restorePurchases.filter { subscription ->
                        subscription.store_status == StoreStatus.active
                    }
                    val allPurchases = restorePurchases.filter { subscription ->
                        subscription.store_status == StoreStatus.active || subscription.store_status == StoreStatus.in_trial
                                || subscription.store_status == StoreStatus.cancelled || subscription.store_status == StoreStatus.paused
                    }
                    if (CBPurchase.inActivePurchases) {
                        completionCallback.onSuccess(activePurchases)
                        syncPurchaseWithChargebee(activeTransactions)
                    } else {
                        completionCallback.onSuccess(allPurchases)
                        syncPurchaseWithChargebee(allTransactions)
                    }
                }
                restorePurchases.clear()
            } else {
                fetchStoreSubscriptionStatus(storeTransactions, completionCallback)
            }
        }

        internal fun syncPurchaseWithChargebee(storeTransactions: ArrayList<PurchaseTransaction>) {
            storeTransactions.forEach { productIdList ->
                validateReceipt(productIdList.purchaseToken, productIdList.productId.first())
            }
        }

        internal fun validateReceipt(purchaseToken: String, productId: String) {
            CBPurchase.validateReceipt(purchaseToken, productId) {
                when (it) {
                    is ChargebeeResult.Success -> {
                        Log.i(javaClass.simpleName, "result :  ${it.data}")
                    }
                    is ChargebeeResult.Error -> {
                        Log.e(
                            javaClass.simpleName,
                            "Exception from Server - validateReceipt() :  ${it.exp.message}"
                        )
                    }
                }
            }
        }
    }
}