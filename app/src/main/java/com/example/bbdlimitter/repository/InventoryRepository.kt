package com.example.bbdlimitter.repository

import android.content.Context
import androidx.room.Room
import com.example.bbdlimitter.data.AppDatabase
import com.example.bbdlimitter.data.LocationEntity
import com.example.bbdlimitter.data.ProductEntity
import com.example.bbdlimitter.network.OpenFoodFactsClient
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

@Serializable
data class ExportPayload(
    val locations: List<LocationEntity>,
    val products: List<ProductEntity>
)

class InventoryRepository(context: Context) {
    private val db = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        "bbd_limitter.db"
    ).build()

    private val productDao = db.productDao()
    private val locationDao = db.locationDao()
    private val foodClient = OpenFoodFactsClient()
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun observeProducts(): Flow<List<ProductEntity>> = productDao.observeProducts()
    fun observeLocations(): Flow<List<LocationEntity>> = locationDao.observeLocations()

    suspend fun addLocation(name: String): Long = locationDao.insert(LocationEntity(name = name))

    suspend fun addProduct(
        name: String,
        janCode: String?,
        quantity: Int,
        locationId: Long,
        expiryDate: LocalDate,
        isBestBefore: Boolean,
        remindBeforeDays: Int,
        isProduce: Boolean,
        produceShelfLifeDays: Int?
    ) {
        val autoProduct = janCode?.takeIf { it.isNotBlank() }?.let { foodClient.fetchByJan(it) }
        productDao.insert(
            ProductEntity(
                name = autoProduct?.productName?.takeIf { it.isNotBlank() } ?: name,
                janCode = janCode,
                photoUrl = autoProduct?.imageUrl,
                quantity = quantity,
                locationId = locationId,
                expiryDateEpochDay = expiryDate.toEpochDay(),
                isBestBefore = isBestBefore,
                remindBeforeDays = remindBeforeDays,
                isProduce = isProduce,
                produceShelfLifeDays = produceShelfLifeDays
            )
        )
    }

    suspend fun exportJson(): String {
        val payload = ExportPayload(
            locations = locationDao.getAllLocations(),
            products = productDao.getAllProducts()
        )
        return json.encodeToString(payload)
    }

    suspend fun importJson(raw: String) {
        val payload = json.decodeFromString<ExportPayload>(raw)
        locationDao.clearLocations()
        productDao.clearProducts()
        payload.locations.forEach { locationDao.insert(it.copy(id = 0)) }
        payload.products.forEach { productDao.insert(it.copy(id = 0)) }
    }

    suspend fun getDueProducts(today: LocalDate): List<ProductEntity> {
        val products = productDao.getAllProducts()
        return products.filter {
            val daysLeft = it.expiryDateEpochDay - today.toEpochDay()
            daysLeft <= it.remindBeforeDays
        }
    }
}
