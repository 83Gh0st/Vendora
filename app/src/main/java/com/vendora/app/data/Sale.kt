package com.vendora.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "sales")
data class SaleEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long,
    val totalAmount: Double,
    val itemsSummary: String,
    val cashAmount: Double = 0.0,
    val upiAmount: Double = 0.0,
    val isCredit: Boolean = false,
    val customerName: String = "",
    val customerPhone: String = ""
)

@Dao
interface SaleDao {
    @Query("SELECT * FROM sales ORDER BY timestamp DESC")
    fun getAllSales(): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sales WHERE isCredit = 1 ORDER BY timestamp DESC")
    fun getCreditSales(): Flow<List<SaleEntity>>

    @Insert
    suspend fun insertSale(sale: SaleEntity)
    
    @Query("UPDATE sales SET isCredit = 0, cashAmount = totalAmount WHERE id = :saleId")
    suspend fun markAsPaid(saleId: Int)

    @androidx.room.Delete
    suspend fun deleteSale(sale: SaleEntity)
}
