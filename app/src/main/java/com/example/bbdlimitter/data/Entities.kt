package com.example.bbdlimitter.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "products")
@Serializable
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val janCode: String? = null,
    val photoUrl: String? = null,
    val quantity: Int,
    val locationId: Long,
    val expiryDateEpochDay: Long,
    val isBestBefore: Boolean,
    val remindBeforeDays: Int,
    val isProduce: Boolean,
    val produceShelfLifeDays: Int? = null
)

@Entity(tableName = "locations")
@Serializable
data class LocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)
