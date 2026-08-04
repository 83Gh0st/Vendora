package com.vendora.app.ui.screens

import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.AccountCircle
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
import com.vendora.app.data.TutorialPreferences
import com.vendora.app.data.auth.AuthViewModel
import com.vendora.app.data.remote.ShopDetailsResult
import com.vendora.app.ui.components.StaggeredEntrance
import com.vendora.app.ui.components.TutorialOverlay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

@Composable
fun SettingsScreen(authViewModel: AuthViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val db = AppDatabase.getDatabase(context)
    val session by authViewModel.session.collectAsState()

    var shopDetails by remember { mutableStateOf<ShopDetailsResult?>(null) }
    LaunchedEffect(session) {
        if (session != null) {
            shopDetails = authViewModel.fetchShopDetails()
        }
    }

    val cartSize = com.vendora.app.data.RestockCartManager.cart.value.size
    var isSyncing by remember { mutableStateOf(false) }
    var showSignOutConfirm by remember { mutableStateOf(false) }
    var showTutorial by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Settings, contentDescription = "Settings", modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Settings & Tools", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("Account", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))

        StaggeredEntrance(index = 0) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.AccountCircle,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Signed in as", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                session?.phoneDigits?.let { "+91 ${it.take(5)} ${it.takeLast(5)}" } ?: "Not signed in",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            isSyncing = true
                            coroutineScope.launch {
                                SupabaseSyncManager(context).pushAll()
                                isSyncing = false
                                Toast.makeText(context, "Synced with Vendora Cloud", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = !isSyncing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AnimatedContentIcon(isSyncing)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isSyncing) "Syncing…" else "Sync Now")
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = { showSignOutConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Logout, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sign Out")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("Shop", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))

        StaggeredEntrance(index = 1) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(shopDetails?.shopName ?: "Loading…", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text(
                                shopDetails?.address ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        VerificationBadge(shopDetails?.verificationStatus)
                    }

                    if (shopDetails?.role == "owner") {
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Share this code so your staff can join your shop",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.primaryContainer, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                shopDetails?.joinCode ?: "",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 24.sp,
                                letterSpacing = 4.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("Data Management", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))

        StaggeredEntrance(index = 2) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Export Backup (CSV)", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Save a copy of your inventory and sales locally.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                try {
                                    val products = db.productDao().getAllProducts().first()
                                    val csvFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Vendora_Backup.csv")
                                    FileOutputStream(csvFile).bufferedWriter().use { out ->
                                        out.write("ID,Name,Barcode,Price,Stock,Unit\n")
                                        products.forEach {
                                            out.write("${it.id},${it.name},${it.barcode},${it.sellingPrice},${it.currentStock},${it.unit}\n")
                                        }
                                    }
                                    Toast.makeText(context, "Saved to Documents/Vendora_Backup.csv", Toast.LENGTH_LONG).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Failed to export CSV", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "Export")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Export Inventory to CSV")
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Factory Reset", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.error)
                    Text("Delete all products from the inventory. This cannot be undone.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                db.productDao().deleteAllProducts()
                                SupabaseSyncManager(context).pushAll()
                                Toast.makeText(context, "All inventory cleared!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Clear All Inventory")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("Printing & Reports", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))

        StaggeredEntrance(index = 3) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("QR Codes for Loose Items", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Print scannable QR codes for items you package yourself.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val products = db.productDao().getAllProducts().first()
                                val qrProducts = products.filter { it.barcode.startsWith("VDR-") }
                                if (qrProducts.isEmpty()) {
                                    Toast.makeText(context, "Tip: Leave the barcode blank when creating a product to generate QR codes!", Toast.LENGTH_LONG).show()
                                } else {
                                    val file = com.vendora.app.utils.PdfGenerator.generateQRCodesPdf(context, qrProducts)
                                    if (file != null) {
                                        com.vendora.app.utils.PdfGenerator.openPdf(context, file)
                                    } else {
                                        Toast.makeText(context, "Failed to generate QR Codes PDF", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Print, contentDescription = "Print QR")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generate QR Codes PDF")
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Custom Restock Order", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Generate a PDF for the $cartSize items in your Restock Cart.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val cartItems = com.vendora.app.data.RestockCartManager.cart.value
                                if (cartItems.isEmpty()) {
                                    Toast.makeText(context, "Your Restock Cart is empty! Add items from the Inventory screen.", Toast.LENGTH_LONG).show()
                                } else {
                                    val products = db.productDao().getAllProducts().first()
                                    val selectedProducts = products.filter { cartItems.containsKey(it.id) }

                                    val file = com.vendora.app.utils.PdfGenerator.generateRestockPdf(context, selectedProducts, cartItems)
                                    if (file != null) {
                                        com.vendora.app.utils.PdfGenerator.openPdf(context, file)
                                        com.vendora.app.data.RestockCartManager.clearCart()
                                    } else {
                                        Toast.makeText(context, "Failed to generate Restock PDF", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Print, contentDescription = "Print Restock")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generate Restock PDF ($cartSize items)")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("Help", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))

        StaggeredEntrance(index = 4) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("New here, or need a refresher?", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        "Take a quick walkthrough of Sell, Stock, Khata, History, and Tools.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { showTutorial = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.HelpOutline, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("How Vendora Works")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    if (showTutorial) {
        TutorialOverlay(
            onFinish = {
                session?.shopId?.let { TutorialPreferences(context).markTutorialSeen(it) }
                showTutorial = false
            }
        )
    }

    if (showSignOutConfirm) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirm = false },
            title = { Text("Sign out?") },
            text = { Text("You can sign back in anytime — your data stays backed up in the cloud.") },
            confirmButton = {
                TextButton(onClick = {
                    showSignOutConfirm = false
                    authViewModel.signOut()
                }) { Text("Sign Out", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun AnimatedContentIcon(isSyncing: Boolean) {
    androidx.compose.animation.AnimatedContent(targetState = isSyncing, label = "sync_icon") { syncing ->
        if (syncing) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Icon(Icons.Default.CloudSync, contentDescription = "Sync")
        }
    }
}

@Composable
private fun VerificationBadge(status: String?) {
    val (label, color) = when (status) {
        "verified" -> "Verified" to com.vendora.app.ui.theme.Success
        "rejected" -> "Rejected" to MaterialTheme.colorScheme.error
        else -> "Pending review" to com.vendora.app.ui.theme.Warning
    }
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.14f), androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
