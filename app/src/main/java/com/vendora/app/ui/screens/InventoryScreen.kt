package com.vendora.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vendora.app.data.AppDatabase
import com.vendora.app.data.ProductEntity
import com.vendora.app.ui.theme.Motion

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(onNavigateToAddProduct: () -> Unit) {
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)
    val products by db.productDao().getAllProducts().collectAsState(initial = emptyList())
    var productToRestock by remember { mutableStateOf<ProductEntity?>(null) }
    var productToEdit by remember { mutableStateOf<ProductEntity?>(null) }
    val coroutineScope = rememberCoroutineScope()

    if (productToRestock != null) {
        var quantityStr by remember { mutableStateOf("") }
        var unitSelection by remember { mutableStateOf("Pieces") }
        AlertDialog(
            onDismissRequest = { productToRestock = null },
            title = { Text("Add to Restock Cart") },
            text = {
                Column {
                    Text("Order for: ${productToRestock?.name}", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = quantityStr,
                        onValueChange = { quantityStr = it },
                        label = { Text("Quantity (e.g. 5)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        FilterChip(selected = unitSelection == "Pieces", onClick = { unitSelection = "Pieces" }, label = { Text("Pieces") })
                        FilterChip(selected = unitSelection == "Cartons", onClick = { unitSelection = "Cartons" }, label = { Text("Cartons") })
                        FilterChip(selected = unitSelection == "Boxes", onClick = { unitSelection = "Boxes" }, label = { Text("Boxes") })
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (quantityStr.isNotBlank()) {
                        com.vendora.app.data.RestockCartManager.addToCart(productToRestock!!.id, "$quantityStr $unitSelection")
                        android.widget.Toast.makeText(context, "Added to Restock Cart!", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    productToRestock = null
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { productToRestock = null }) { Text("Cancel") }
            }
        )
    }

    if (productToEdit != null) {
        val editingProduct = productToEdit!!
        var editName by remember(editingProduct.id) { mutableStateOf(editingProduct.name) }
        var editPrice by remember(editingProduct.id) { mutableStateOf(editingProduct.sellingPrice.toString()) }
        var editStock by remember(editingProduct.id) { mutableStateOf(editingProduct.currentStock.toString()) }
        var editUnit by remember(editingProduct.id) { mutableStateOf(editingProduct.unit) }
        var unitMenuExpanded by remember { mutableStateOf(false) }
        val unitOptions = listOf("pcs", "kg", "ltr", "gm", "ml", "box", "dozen")

        AlertDialog(
            onDismissRequest = { productToEdit = null },
            title = { Text("Edit Product") },
            text = {
                Column {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Product Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editPrice,
                        onValueChange = { editPrice = it },
                        label = { Text("Selling Price (₹)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = editStock,
                            onValueChange = { editStock = it },
                            label = { Text("Current Stock") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                        )
                        ExposedDropdownMenuBox(
                            expanded = unitMenuExpanded,
                            onExpandedChange = { unitMenuExpanded = !unitMenuExpanded },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = editUnit,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Unit") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitMenuExpanded) },
                                modifier = Modifier.menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = unitMenuExpanded,
                                onDismissRequest = { unitMenuExpanded = false }
                            ) {
                                unitOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option) },
                                        onClick = {
                                            editUnit = option
                                            unitMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Barcode: ${editingProduct.barcode}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (editName.isBlank() || editPrice.isBlank()) {
                        android.widget.Toast.makeText(context, "Name and Price are required", android.widget.Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val newPrice = editPrice.toDoubleOrNull()
                    val newStock = editStock.toIntOrNull()
                    if (newPrice == null || newStock == null) {
                        android.widget.Toast.makeText(context, "Enter valid numbers for price and stock", android.widget.Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    coroutineScope.launch {
                        db.productDao().updateProduct(
                            editingProduct.copy(
                                name = editName,
                                sellingPrice = newPrice,
                                currentStock = newStock,
                                unit = editUnit
                            )
                        )
                        com.vendora.app.data.SupabaseSyncManager(context).pushAll()
                        android.widget.Toast.makeText(context, "Product Updated", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    productToEdit = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { productToEdit = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToAddProduct) {
                Icon(Icons.Filled.Add, contentDescription = "Add Product")
            }
        }
    ) { innerPadding ->
        if (products.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(Motion.enterTween()) + scaleIn(initialScale = 0.85f, animationSpec = Motion.smooth())
                ) {
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.Inventory2,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = "No products found. Tap + to add one!")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(products, key = { it.id }) { product ->
                    ProductCard(
                        product = product,
                        modifier = Modifier.animateItem(),
                        onRestockClick = { productToRestock = product },
                        onEditClick = { productToEdit = product },
                        onDeleteClick = {
                            coroutineScope.launch {
                                db.productDao().deleteProduct(product)
                                com.vendora.app.data.SupabaseSyncManager(context).pushAll()
                                android.widget.Toast.makeText(context, "Product Deleted", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ProductCard(
    product: ProductEntity,
    modifier: Modifier = Modifier,
    onRestockClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(text = product.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Row {
                            IconButton(onClick = onEditClick, modifier = Modifier.size(24.dp)) {
                                Icon(androidx.compose.material.icons.Icons.Default.Edit, contentDescription = "Edit Product", tint = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(onClick = onDeleteClick, modifier = Modifier.size(24.dp)) {
                                Icon(androidx.compose.material.icons.Icons.Default.Delete, contentDescription = "Delete Product", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "₹${product.sellingPrice}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text(
                        text = "Stock: ${product.currentStock} ${product.unit}",
                        color = when {
                            product.currentStock <= 0 -> com.vendora.app.ui.theme.Danger
                            product.currentStock < 5 -> com.vendora.app.ui.theme.Warning
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        fontWeight = if (product.currentStock < 5) FontWeight.Bold else FontWeight.Normal
                    )
                    Text(text = "Barcode: ${product.barcode}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                
                if (product.barcode.startsWith("VDR-")) {
                    Spacer(modifier = Modifier.width(16.dp))
                    val bitmap = remember(product.barcode) {
                        com.vendora.app.utils.BarcodeGenerator.generateQRCode(product.barcode, size = 512)
                    }
                    if (bitmap != null) {
                        androidx.compose.foundation.Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "QR Code",
                            modifier = Modifier.size(100.dp) // Increased for better scanning
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onRestockClick, modifier = Modifier.fillMaxWidth()) {
                Text("Add to Restock Cart")
            }
        }
    }
}
