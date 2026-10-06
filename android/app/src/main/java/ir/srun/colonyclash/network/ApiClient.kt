package ir.srun.colonyclash.network

import ir.srun.colonyclash.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ApiClient(private val tokenProvider: () -> String?) {
    fun get(path:String): JSONObject = request("GET", path, null)
    fun post(path:String, body:JSONObject = JSONObject()): JSONObject = request("POST", path, body)
    fun delete(path:String): JSONObject = request("DELETE", path, null)

    private fun request(method:String,path:String,body:JSONObject?):JSONObject {
        val base=BuildConfig.API_BASE_URL.trimEnd('/')
        val conn=(URL("$base/api/v1/${path.trimStart('/')}").openConnection() as HttpURLConnection).apply {
            requestMethod=method
            connectTimeout=7_000
            readTimeout=10_000
            setRequestProperty("Accept","application/json")
            tokenProvider()?.takeIf { it.isNotBlank() }?.let { setRequestProperty("Authorization","Bearer $it") }
            if(body!=null){ doOutput=true; setRequestProperty("Content-Type","application/json"); outputStream.use { it.write(body.toString().toByteArray()) } }
        }
        val code=conn.responseCode
        val text=(if(code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        if(code !in 200..299) error("HTTP_$code:${text.take(300)}")
        return if(text.isBlank()) JSONObject() else JSONObject(text)
    }
}
