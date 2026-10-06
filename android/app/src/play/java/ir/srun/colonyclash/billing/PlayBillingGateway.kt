package ir.srun.colonyclash.billing

import android.content.Context
import com.android.billingclient.api.*

class PlayBillingGateway(context:Context):BillingGateway, PurchasesUpdatedListener {
    private val client = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    override fun connect(onReady:(Boolean)->Unit){
        client.startConnection(object:BillingClientStateListener{
            override fun onBillingSetupFinished(result:BillingResult){onReady(result.responseCode==BillingClient.BillingResponseCode.OK)}
            override fun onBillingServiceDisconnected(){onReady(false)}
        })
    }

    override fun queryGemProducts(productIds:List<String>,onResult:(List<StorePrice>)->Unit){
        val products=productIds.map { QueryProductDetailsParams.Product.newBuilder().setProductId(it).setProductType(BillingClient.ProductType.INAPP).build() }
        val params=QueryProductDetailsParams.newBuilder().setProductList(products).build()
        client.queryProductDetailsAsync(params){ result, details ->
            if(result.responseCode!=BillingClient.BillingResponseCode.OK){onResult(emptyList());return@queryProductDetailsAsync}
            onResult(details.productDetailsList.mapNotNull { pd ->
                val offer=pd.oneTimePurchaseOfferDetailsList?.firstOrNull()
                val price=offer?.formattedPrice ?: return@mapNotNull null
                StorePrice(pd.productId,price)
            })
        }
    }

    override fun onPurchasesUpdated(result:BillingResult,purchases:MutableList<Purchase>?){
        // Stage 03 deliberately does not grant currency client-side.
        // Stage 08 will send the purchase token to the backend, verify it, grant once, then acknowledge/consume.
    }
    override fun close(){client.endConnection()}
}
