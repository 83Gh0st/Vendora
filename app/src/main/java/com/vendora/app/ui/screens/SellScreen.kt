package com.vendora.app.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.vendora.app.MainActivity
import com.vendora.app.data.AppDatabase
import com.vendora.app.data.ProductEntity
import com.vendora.app.ui.components.AnimatedCounter
import com.vendora.app.ui.theme.Motion
import com.vendora.app.ui.theme.Success
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SellScreen(mainActivity: MainActivity, onNavigateToAddProduct: (String) -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val db = AppDatabase.getDatabase(context)

    val allProducts by db.productDao().getAllProducts().collectAsState(initial = emptyList())
    var showManualPicker by remember { mutableStateOf(false) }
    var showCheckoutDialog by remember { mutableStateOf(false) }

    // Cart holds Pair of Product and Quantity
    var cart by remember { mutableStateOf(listOf<Pair<ProductEntity, Int>>()) }
    var heldCart by remember { mutableStateOf<List<Pair<ProductEntity, Int>>?>(null) }

    val totalAmount = cart.sumOf { it.first.sellingPrice * it.second }

    fun addToCart(product: ProductEntity) {
        val existingItem = cart.find { it.first.id == product.id }
        if (existingItem != null) {
            cart = cart.map {
                if (it.first.id == product.id) it.copy(second = it.second + 1) else it
            }
        } else {
            cart = cart + Pair(product, 1)
        }
    }

    fun decrementCart(product: ProductEntity) {
        val existingItem = cart.find { it.first.id == product.id }
        if (existingItem != null) {
            if (existingItem.second > 1) {
                cart = cart.map {
                    if (it.first.id == product.id) it.copy(second = it.second - 1) else it
                }
            } else {
                cart = cart.filter { it.first.id != product.id }
            }
        }
    }

    if (showManualPicker) {
        Dialog(onDismissRequest = { showManualPicker = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                tonalElevation = 8.dp,
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f)
            ) {
                var manualSearchQuery by remember { mutableStateOf("") }
                val filteredProducts = allProducts.filter {
                    it.name.contains(manualSearchQuery, ignoreCase = true) || it.barcode.contains(manualSearchQuery, ignoreCase = true)
                }

                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Available Items", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = manualSearchQuery,
                        onValueChange = { manualSearchQuery = it },
                        label = { Text("Search by name or barcode") },
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    if (filteredProducts.isEmpty()) {
                        Text("No items match your search.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(filteredProducts, key = { it.id }) { product ->
                                Card(
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    modifier = Modifier.fillMaxWidth().animateItem().clickable {
                                        addToCart(product)
                                        showManualPicker = false
                                        Toast.makeText(context, "Added ${product.name}", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(product.name, fontWeight = FontWeight.Bold)
                                        Text("₹${product.sellingPrice} / ${product.unit}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { showManualPicker = false }, modifier = Modifier.align(Alignment.End)) {
                        Text("Close")
                    }
                }
            }
        }
    }

    if (showCheckoutDialog) {
        CheckoutDialog(
            totalAmount = totalAmount,
            onDismiss = { showCheckoutDialog = false },
            onConfirm = { paymentMode, cashAmount, upiAmount, customerName, customerPhone ->
                coroutineScope.launch {
                    cart.forEach { item ->
                        db.productDao().decrementStock(item.first.id, item.second)
                    }
                    val summary = cart.joinToString(", ") { "${it.second}x ${it.first.name}" }
                    val sale = com.vendora.app.data.SaleEntity(
                        timestamp = System.currentTimeMillis(),
                        totalAmount = totalAmount,
                        itemsSummary = summary,
                        cashAmount = cashAmount,
                        upiAmount = upiAmount,
                        isCredit = paymentMode == "Khata",
                        customerName = if (paymentMode == "Khata") customerName else "",
                        customerPhone = if (paymentMode == "Khata") customerPhone else ""
                    )
                    db.saleDao().insertSale(sale)
                    com.vendora.app.data.SupabaseSyncManager(context).pushAll()

                    cart = emptyList()
                    showCheckoutDialog = false
                    Toast.makeText(context, "Sale completed!", Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Current Sale", style = MaterialTheme.typography.headlineMedium)
            if (heldCart != null) {
                OutlinedButton(
                    shape = RoundedCornerShape(12.dp),
                    onClick = {
                        val temp = cart
                        cart = heldCart!!
                        heldCart = if (temp.isNotEmpty()) temp else null
                    }
                ) {
                    Text("Resume Held Cart")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f).height(56.dp),
                onClick = {
                    mainActivity.launchScanner { scannedBarcode ->
                        if (scannedBarcode != null) {
                            coroutineScope.launch {
                                val product = db.productDao().getProductByBarcode(scannedBarcode)
                                if (product != null) {
                                    addToCart(product)
                                    Toast.makeText(context, "Added ${product.name}", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "New Product Detected!", Toast.LENGTH_LONG).show()
                                    onNavigateToAddProduct(scannedBarcode)
                                }
                            }
                        }
                    }
                }
            ) {
                Text("SCAN", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f).height(56.dp),
                onClick = { showManualPicker = true }
            ) {
                Text("ALL ITEMS", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f).height(56.dp),
                onClick = { onNavigateToAddProduct("") }
            ) {
                Text("CREATE", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (cart.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                androidx.compose.animation.AnimatedVisibility(visible = true, enter = fadeIn(Motion.enterTween())) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.PointOfSale,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Scan or select a product to start", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(cart, key = { it.first.id }) { item ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().animateItem(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        colors = if (item.first.currentStock < 5) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer) else CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.first.name, fontWeight = FontWeight.Bold)
                                Text("₹${item.first.sellingPrice} / ${item.first.unit}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (item.first.currentStock < 5) {
                                    Text("Only ${item.first.currentStock} left!", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { decrementCart(item.first) },
                                    modifier = Modifier.size(36.dp).background(Color.White, RoundedCornerShape(10.dp))
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(18.dp))
                                }
                                AnimatedContent(
                                    targetState = item.second,
                                    transitionSpec = {
                                        (scaleIn(initialScale = 0.6f, animationSpec = Motion.bouncy<Float>()) + fadeIn(Motion.quickTween()))
                                            .togetherWith(scaleOut(targetScale = 0.6f, animationSpec = Motion.quickTween()) + fadeOut(Motion.quickTween()))
                                    },
                                    label = "cart_qty",
                                    modifier = Modifier.padding(horizontal = 10.dp).widthIn(min = 20.dp)
                                ) { qty ->
                                    Text("$qty", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                                IconButton(
                                    onClick = { addToCart(item.first) },
                                    modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Increase", tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }

                            Text(
                                "₹${item.first.sellingPrice * item.second}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                modifier = Modifier.padding(start = 8.dp).widthIn(min = 56.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.End
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Row(
                modifier = Modifier.padding(18.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Total", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                AnimatedCounter(
                    value = totalAmount,
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp, fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (cart.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f).height(56.dp),
                    onClick = {
                        heldCart = cart
                        cart = emptyList()
                        Toast.makeText(context, "Cart held for later!", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("HOLD", fontWeight = FontWeight.Bold)
                }

                Button(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(2f).height(56.dp),
                    onClick = { showCheckoutDialog = true }
                ) {
                    Text("CHECKOUT · ₹$totalAmount", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * A single, calm checkout flow: pick a payment method as a big obvious tile,
 * fill in only what that method actually needs, confirm. Scrollable so
 * nothing overflows off-screen regardless of device size or keyboard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CheckoutDialog(
    totalAmount: Double,
    onDismiss: () -> Unit,
    onConfirm: (paymentMode: String, cashAmount: Double, upiAmount: Double, customerName: String, customerPhone: String) -> Unit
) {
    var paymentMode by remember { mutableStateOf("Cash") }
    var cashGivenStr by remember { mutableStateOf("") }
    var upiGivenStr by remember { mutableStateOf("") }
    var customerName by remember { mutableStateOf("") }
    var customerPhone by remember { mutableStateOf("") }

    val cashGiven = cashGivenStr.toDoubleOrNull() ?: 0.0
    val upiGiven = upiGivenStr.toDoubleOrNull() ?: 0.0

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.88f)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Checkout", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("Amount due", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AnimatedCounter(
                    value = totalAmount,
                    style = MaterialTheme.typography.headlineLarge.copy(fontSize = 32.sp),
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(20.dp))
                Text("How is the customer paying?", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(10.dp))

                PaymentMethodGrid(selected = paymentMode, onSelect = { paymentMode = it })

                Spacer(modifier = Modifier.height(20.dp))

                AnimatedContent(targetState = paymentMode, label = "payment_details") { mode ->
                    Column {
                        when (mode) {
                            "Cash" -> CashPaymentSection(
                                totalAmount = totalAmount,
                                cashGivenStr = cashGivenStr,
                                onCashGivenChange = { cashGivenStr = it }
                            )
                            "UPI" -> UpiPaymentSection(totalAmount = totalAmount)
                            "Split" -> SplitPaymentSection(
                                totalAmount = totalAmount,
                                cashGivenStr = cashGivenStr,
                                onCashGivenChange = { cashGivenStr = it },
                                upiGivenStr = upiGivenStr,
                                onUpiGivenChange = { upiGivenStr = it }
                            )
                            "Khata" -> KhataSection(
                                customerName = customerName,
                                onNameChange = { customerName = it },
                                customerPhone = customerPhone,
                                onPhoneChange = { customerPhone = it }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.weight(1f).height(52.dp)
                    ) {
                        Text("Cancel", maxLines = 1, softWrap = false)
                    }

                    val isConfirmEnabled = when (paymentMode) {
                        "Cash" -> cashGiven >= totalAmount
                        "UPI" -> true
                        "Split" -> (cashGiven + upiGiven) >= totalAmount
                        "Khata" -> customerName.isNotBlank()
                        else -> false
                    }

                    Button(
                        enabled = isConfirmEnabled,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(2f).height(52.dp),
                        onClick = {
                            val cashAmount = when (paymentMode) {
                                "Cash" -> totalAmount
                                "Split" -> cashGiven
                                else -> 0.0
                            }
                            val upiAmount = when (paymentMode) {
                                "UPI" -> totalAmount
                                "Split" -> upiGiven
                                else -> 0.0
                            }
                            onConfirm(paymentMode, cashAmount, upiAmount, customerName, customerPhone)
                        }
                    ) {
                        Text("Confirm Sale", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentMethodGrid(selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            PaymentMethodTile("Cash", Icons.Filled.Payments, selected == "Cash", Modifier.weight(1f)) { onSelect("Cash") }
            PaymentMethodTile("UPI", Icons.Filled.QrCode2, selected == "UPI", Modifier.weight(1f)) { onSelect("UPI") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            PaymentMethodTile("Split", Icons.Filled.CallSplit, selected == "Split", Modifier.weight(1f)) { onSelect("Split") }
            PaymentMethodTile("Khata", Icons.Filled.Person, selected == "Khata", Modifier.weight(1f)) { onSelect("Khata") }
        }
    }
}

@Composable
private fun PaymentMethodTile(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(16.dp)
            )
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            label,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 13.sp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun CashPaymentSection(
    totalAmount: Double,
    cashGivenStr: String,
    onCashGivenChange: (String) -> Unit
) {
    val cashGiven = cashGivenStr.toDoubleOrNull() ?: 0.0
    Column {
        OutlinedTextField(
            value = cashGivenStr,
            onValueChange = onCashGivenChange,
            label = { Text("Cash received (₹)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = RoundedCornerShape(14.dp),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(100, 200, 500, 2000).forEach { note ->
                OutlinedButton(
                    onClick = { onCashGivenChange(note.toString()) },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.weight(1f).height(40.dp)
                ) {
                    Text("₹$note", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { onCashGivenChange(totalAmount.toString()) },
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
            modifier = Modifier.fillMaxWidth().height(44.dp)
        ) {
            Text("Exact Amount (₹$totalAmount)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }

        MoneyStatusBanner(given = cashGiven, due = totalAmount)
    }
}

@Composable
private fun UpiPaymentSection(totalAmount: Double) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.QrCode2, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                "Once the customer's UPI payment shows as received, tap Confirm Sale below.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SplitPaymentSection(
    totalAmount: Double,
    cashGivenStr: String,
    onCashGivenChange: (String) -> Unit,
    upiGivenStr: String,
    onUpiGivenChange: (String) -> Unit
) {
    val cashGiven = cashGivenStr.toDoubleOrNull() ?: 0.0
    val upiGiven = upiGivenStr.toDoubleOrNull() ?: 0.0

    Column {
        OutlinedTextField(
            value = cashGivenStr,
            onValueChange = onCashGivenChange,
            label = { Text("Cash portion (₹)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = RoundedCornerShape(14.dp),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedTextField(
            value = upiGivenStr,
            onValueChange = onUpiGivenChange,
            label = { Text("UPI portion (₹)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = RoundedCornerShape(14.dp),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        MoneyStatusBanner(given = cashGiven + upiGiven, due = totalAmount)
    }
}

@Composable
private fun MoneyStatusBanner(given: Double, due: Double) {
    AnimatedVisibility(
        visible = given > 0,
        enter = fadeIn(Motion.quickTween()) + expandVertically(Motion.quickTween()),
        exit = fadeOut(Motion.quickTween()) + shrinkVertically(Motion.quickTween())
    ) {
        val isSettled = given >= due
        Row(
            modifier = Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
                .background(
                    if (isSettled) Success.copy(alpha = 0.12f) else MaterialTheme.colorScheme.errorContainer,
                    RoundedCornerShape(12.dp)
                )
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSettled) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Success, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (given > due) "Change to return: ₹${given - due}" else "Fully paid",
                    color = Success,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Text(
                    "Still due: ₹${due - given}",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun KhataSection(
    customerName: String,
    onNameChange: (String) -> Unit,
    customerPhone: String,
    onPhoneChange: (String) -> Unit
) {
    Column {
        OutlinedTextField(
            value = customerName,
            onValueChange = onNameChange,
            label = { Text("Customer name") },
            shape = RoundedCornerShape(14.dp),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedTextField(
            value = customerPhone,
            onValueChange = onPhoneChange,
            label = { Text("Phone number (optional)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            shape = RoundedCornerShape(14.dp),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
