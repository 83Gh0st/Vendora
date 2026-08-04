package com.vendora.app.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vendora.app.data.AppDatabase
import com.vendora.app.data.SupabaseSyncManager
import com.vendora.app.ui.components.AnimatedCounter
import com.vendora.app.ui.components.StaggeredEntrance
import com.vendora.app.ui.theme.Motion
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val db = AppDatabase.getDatabase(context)
    val creditSales by db.saleDao().getCreditSales().collectAsState(initial = emptyList())

    val totalDebt = creditSales.sumOf { it.totalAmount }
    val dateFormat = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Customer Ledger (Khata)", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        StaggeredEntrance(index = 0) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Total Outstanding Debt", fontSize = 16.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                    AnimatedCounter(
                        value = totalDebt,
                        style = MaterialTheme.typography.headlineMedium.copy(fontSize = 32.sp, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (creditSales.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                androidx.compose.animation.AnimatedVisibility(visible = true, enter = fadeIn(Motion.enterTween())) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.TaskAlt,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No outstanding debts! Great job.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(creditSales, key = { it.id }) { sale ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .animateItem(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Person, contentDescription = "Customer")
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(sale.customerName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                        if (sale.customerPhone.isNotBlank()) {
                                            Text(sale.customerPhone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                                        }
                                    }
                                }
                                Text("₹${sale.totalAmount}", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.error)
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Date: ${dateFormat.format(Date(sale.timestamp))}", style = MaterialTheme.typography.bodySmall)
                            Text("Items: ${sale.itemsSummary}", style = MaterialTheme.typography.bodySmall)

                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    coroutineScope.launch {
                                        db.saleDao().markAsPaid(sale.id)
                                        SupabaseSyncManager(context).pushAll()
                                        Toast.makeText(context, "Debt cleared for ${sale.customerName}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Check, contentDescription = "Mark Paid")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Mark as Paid (Cash)")
                            }
                        }
                    }
                }
            }
        }
    }
}
