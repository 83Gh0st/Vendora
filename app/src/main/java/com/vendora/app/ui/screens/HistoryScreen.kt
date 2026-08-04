package com.vendora.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vendora.app.data.AppDatabase
import com.vendora.app.ui.components.AnimatedCounter
import com.vendora.app.ui.components.StaggeredEntrance
import com.vendora.app.ui.theme.Motion
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun HistoryScreen() {
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)
    val sales by db.saleDao().getAllSales().collectAsState(initial = emptyList())

    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

    // Calculate Today's Stats
    val startOfToday = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    val todaysSales = sales.filter { it.timestamp >= startOfToday }
    val todaysRevenue = todaysSales.sumOf { it.totalAmount }
    val todaysCash = todaysSales.sumOf { it.cashAmount }
    val todaysUpi = todaysSales.sumOf { it.upiAmount }
    val todaysCredit = todaysSales.filter { it.isCredit }.sumOf { it.totalAmount }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        Text(text = "End of Day Dashboard", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        StaggeredEntrance(index = 0) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Today's Revenue", fontSize = 16.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    AnimatedCounter(
                        value = todaysRevenue,
                        style = MaterialTheme.typography.headlineLarge.copy(fontSize = 36.sp, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Cash", fontSize = 12.sp)
                            AnimatedCounter(value = todaysCash, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                        }
                        Column {
                            Text("UPI", fontSize = 12.sp)
                            AnimatedCounter(value = todaysUpi, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                        }
                        Column {
                            Text("Credit", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                            AnimatedCounter(
                                value = todaysCredit,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(text = "All Transactions", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))

        val coroutineScope = rememberCoroutineScope()

        if (sales.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                androidx.compose.animation.AnimatedVisibility(visible = true, enter = fadeIn(Motion.enterTween())) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.ReceiptLong,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No sales yet. Start selling!", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(sales, key = { it.id }) { sale ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .animateItem(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    dateFormatter.format(Date(sale.timestamp)),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "₹${sale.totalAmount}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    IconButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                db.saleDao().deleteSale(sale)
                                                com.vendora.app.data.SupabaseSyncManager(context).pushAll()
                                                android.widget.Toast.makeText(context, "Sale Deleted", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(androidx.compose.material.icons.Icons.Default.Delete, contentDescription = "Delete Sale", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))

                            val paymentType = when {
                                sale.isCredit -> "Khata: ${sale.customerName}"
                                sale.cashAmount > 0 && sale.upiAmount > 0 -> "Split (Cash: ₹${sale.cashAmount}, UPI: ₹${sale.upiAmount})"
                                sale.cashAmount > 0 -> "Cash"
                                sale.upiAmount > 0 -> "UPI"
                                else -> "Unknown"
                            }

                            Text(
                                "Payment: $paymentType",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (sale.isCredit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                            )

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                sale.itemsSummary,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}
