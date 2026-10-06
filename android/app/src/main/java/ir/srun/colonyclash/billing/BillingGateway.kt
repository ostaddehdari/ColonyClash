package ir.srun.colonyclash.billing

data class StorePrice(val productId:String,val formattedPrice:String)
interface BillingGateway {
    fun connect(onReady:(Boolean)->Unit)
    fun queryGemProducts(productIds:List<String>, onResult:(List<StorePrice>)->Unit)
    fun close()
}
