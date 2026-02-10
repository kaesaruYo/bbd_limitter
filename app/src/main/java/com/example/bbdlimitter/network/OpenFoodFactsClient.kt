package com.example.bbdlimitter.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
data class ProductResponse(
    @SerialName("product_name") val productName: String? = null,
    @SerialName("image_url") val imageUrl: String? = null
)

@Serializable
data class OpenFoodFactsResponse(
    val status: Int,
    val product: ProductResponse? = null
)

class OpenFoodFactsClient(
    private val client: OkHttpClient = OkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    fun fetchByJan(janCode: String): ProductResponse? {
        val request = Request.Builder()
            .url("https://world.openfoodfacts.org/api/v2/product/$janCode")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val parsed = json.decodeFromString<OpenFoodFactsResponse>(body)
            return if (parsed.status == 1) parsed.product else null
        }
    }
}
