package com.vendora.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vendora.app.MainActivity
import com.vendora.app.data.AppDatabase
import com.vendora.app.data.ProductEntity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddProductScreen(mainActivity: MainActivity, initialBarcode: String? = null, onNavigateBack: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var barcode by remember { mutableStateOf(initialBarcode ?: "") }
    var sellingPrice by remember { mutableStateOf("") }
    var stock by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("pcs") }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val db = AppDatabase.getDatabase(context)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "Add New Product", style = MaterialTheme.typography.titleLarge)

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Product Name") },
            modifier = Modifier.fillMaxWidth()
        )

        if (initialBarcode != "") {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = barcode,
                    onValueChange = { barcode = it },
                    label = { Text("Barcode") },
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        mainActivity.launchScanner { result ->
                            if (result != null) {
                                barcode = result
                            }
                        }
                    },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("Scan")
                }
            }
        }

        OutlinedTextField(
            value = sellingPrice,
            onValueChange = { sellingPrice = it },
            label = { Text("Selling Price (₹)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = stock,
                onValueChange = { stock = it },
                label = { Text("Initial Stock") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            var expanded by remember { mutableStateOf(false) }
            val unitOptions = listOf("pcs", "kg", "ltr", "gm", "ml", "box", "dozen")
            
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = unit,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Unit") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    unitOptions.forEach { selectionOption ->
                        DropdownMenuItem(
                            text = { Text(selectionOption) },
                            onClick = {
                                unit = selectionOption
                                expanded = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                if (name.isBlank() || sellingPrice.isBlank()) {
                    Toast.makeText(context, "Name and Price are required", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                
                val price = sellingPrice.toDoubleOrNull() ?: 0.0
                val qty = stock.toIntOrNull() ?: 0
                val finalBarcode = if (barcode.isNotBlank()) barcode else "VDR-${System.currentTimeMillis()}"

                coroutineScope.launch {
                    db.productDao().insertProduct(
                        ProductEntity(
                            name = name,
                            barcode = finalBarcode,
                            sellingPrice = price,
                            currentStock = qty,
                            unit = unit
                        )
                    )
                    
                    // Push the new/updated product up to Supabase
                    com.vendora.app.data.SupabaseSyncManager(context).pushAll()
                    
                    Toast.makeText(context, "Product Saved!", Toast.LENGTH_SHORT).show()
                    onNavigateBack()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Product")
        }
    }
}
