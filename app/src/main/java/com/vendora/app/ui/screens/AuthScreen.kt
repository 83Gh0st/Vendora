package com.vendora.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonPin
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.vendora.app.data.auth.AuthUiState
import com.vendora.app.data.auth.AuthViewModel
import com.vendora.app.ui.theme.BackgroundLight
import com.vendora.app.ui.theme.Danger
import com.vendora.app.ui.theme.GradientEnd
import com.vendora.app.ui.theme.GradientMid
import com.vendora.app.ui.theme.GradientStart
import com.vendora.app.ui.theme.Motion
import kotlinx.coroutines.launch

private enum class TopMode { SIGN_IN, REGISTER }
private enum class RegisterAs { NEW_SHOP, EMPLOYEE }

/**
 * Vendora's entire sign-in system, phone number + password: sign in,
 * register a new shop (credentials -> shop details -> verification photo,
 * all submitted together at the end), or join an existing shop with a
 * code. Nothing here talks to Supabase Auth at all — see AuthViewModel.
 */
@Composable
fun AuthScreen(authViewModel: AuthViewModel = viewModel()) {
    val uiState by authViewModel.uiState.collectAsState()
    var topMode by remember { mutableStateOf(TopMode.SIGN_IN) }
    var registerAs by remember { mutableStateOf(RegisterAs.NEW_SHOP) }

    Box(modifier = Modifier.fillMaxSize().background(BackgroundLight)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(
                    Brush.linearGradient(
                        colors = listOf(GradientStart, GradientMid, GradientEnd),
                        start = Offset(0f, 0f),
                        end = Offset(900f, 900f)
                    ),
                    shape = RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            var logoVisible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { logoVisible = true }
            AnimatedVisibility(
                visible = logoVisible,
                enter = fadeIn(Motion.enterTween()) + scaleIn(initialScale = 0.7f, animationSpec = Motion.bouncy())
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    VendoraMark(size = 58.dp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Vendora", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Your shop, in your pocket", color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 160.dp)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    if (topMode == TopMode.SIGN_IN) {
                        SignInForm(authViewModel, uiState)
                    } else {
                        RegisterFlow(authViewModel, uiState, registerAs, onRegisterAsChange = { registerAs = it })
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            if (topMode == TopMode.SIGN_IN) "New to Vendora?" else "Already have an account?",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            if (topMode == TopMode.SIGN_IN) "Create one" else "Sign in",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.tapText {
                                topMode = if (topMode == TopMode.SIGN_IN) TopMode.REGISTER else TopMode.SIGN_IN
                                authViewModel.dismissError()
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SignInForm(authViewModel: AuthViewModel, uiState: AuthUiState) {
    var phoneDigits by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    Text("Welcome back", style = MaterialTheme.typography.headlineMedium)
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        "Sign in with your phone number and password",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(20.dp))

    PhoneField(phoneDigits) { phoneDigits = it }
    Spacer(modifier = Modifier.height(12.dp))
    PasswordField(password, { password = it }, passwordVisible, { passwordVisible = !passwordVisible }, "Password")

    ErrorBanner(uiState.errorMessage)
    Spacer(modifier = Modifier.height(20.dp))

    Button(
        onClick = { authViewModel.signIn(phoneDigits, password) },
        enabled = !uiState.isLoading,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().height(52.dp)
    ) {
        LoadingOrText(uiState.isLoading, "Sign In")
    }
}

@Composable
private fun RegisterFlow(
    authViewModel: AuthViewModel,
    uiState: AuthUiState,
    registerAs: RegisterAs,
    onRegisterAsChange: (RegisterAs) -> Unit
) {
    var wizardStep by remember { mutableStateOf(0) }
    var phoneDigits by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var joinCode by remember { mutableStateOf("") }
    var shopName by remember { mutableStateOf("") }
    var ownerName by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var gstNumber by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(password, confirmPassword) { localError = null }
    val errorMessage = localError ?: uiState.errorMessage

    Text(
        if (registerAs == RegisterAs.EMPLOYEE) "Join your shop" else "Create your shop account",
        style = MaterialTheme.typography.headlineMedium
    )
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        "Set up a new shop, or join one your employer already created",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(16.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        SegmentTile("New shop", registerAs == RegisterAs.NEW_SHOP, Modifier.weight(1f)) {
            onRegisterAsChange(RegisterAs.NEW_SHOP); wizardStep = 0
        }
        SegmentTile("Join as employee", registerAs == RegisterAs.EMPLOYEE, Modifier.weight(1f)) {
            onRegisterAsChange(RegisterAs.EMPLOYEE); wizardStep = 0
        }
    }
    Spacer(modifier = Modifier.height(16.dp))

    if (registerAs == RegisterAs.EMPLOYEE) {
        PhoneField(phoneDigits) { phoneDigits = it }
        Spacer(modifier = Modifier.height(12.dp))
        PasswordField(password, { password = it }, passwordVisible, { passwordVisible = !passwordVisible }, "Password")
        Spacer(modifier = Modifier.height(12.dp))
        PasswordField(confirmPassword, { confirmPassword = it }, passwordVisible, { passwordVisible = !passwordVisible }, "Confirm password")
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = joinCode,
            onValueChange = { joinCode = it.uppercase() },
            label = { Text("Shop code from your owner") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )

        ErrorBanner(errorMessage)
        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                if (password != confirmPassword) {
                    localError = "Passwords don't match"
                } else {
                    authViewModel.joinShop(phoneDigits, password, joinCode)
                }
            },
            enabled = !uiState.isLoading,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            LoadingOrText(uiState.isLoading, "Join Shop")
        }
        return
    }

    // New-shop path: a 3-step wizard, submitted all at once on the last step.
    AnimatedContent(
        targetState = wizardStep,
        transitionSpec = {
            (slideInHorizontally(Motion.standardTween()) { it / 3 } + fadeIn(Motion.standardTween()))
                .togetherWith(fadeOut(Motion.quickTween()))
        },
        label = "register_wizard"
    ) { step ->
        when (step) {
            0 -> Column {
                PhoneField(phoneDigits) { phoneDigits = it }
                Spacer(modifier = Modifier.height(12.dp))
                PasswordField(password, { password = it }, passwordVisible, { passwordVisible = !passwordVisible }, "Password")
                Spacer(modifier = Modifier.height(12.dp))
                PasswordField(confirmPassword, { confirmPassword = it }, passwordVisible, { passwordVisible = !passwordVisible }, "Confirm password")

                ErrorBanner(errorMessage)
                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        when {
                            phoneDigits.length != 10 -> localError = "Enter a valid 10-digit mobile number"
                            password.length < 6 -> localError = "Password must be at least 6 characters"
                            password != confirmPassword -> localError = "Passwords don't match"
                            else -> wizardStep = 1
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text("Next", fontWeight = FontWeight.Bold) }
            }

            1 -> Column {
                OutlinedTextField(shopName, { shopName = it }, label = { Text("Shop name") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(ownerName, { ownerName = it }, label = { Text("Owner's full name") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(address, { address = it }, label = { Text("Shop address") }, minLines = 2, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(gstNumber, { gstNumber = it }, label = { Text("GST number (optional)") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())

                ErrorBanner(errorMessage)
                Spacer(modifier = Modifier.height(20.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { wizardStep = 0 }, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f).height(52.dp)) {
                        Text("Back")
                    }
                    Button(
                        onClick = {
                            when {
                                shopName.isBlank() -> localError = "Enter your shop's name"
                                ownerName.isBlank() -> localError = "Enter the owner's name"
                                address.isBlank() -> localError = "Enter the shop's address"
                                else -> wizardStep = 2
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(2f).height(52.dp)
                    ) { Text("Next", fontWeight = FontWeight.Bold) }
                }
            }

            else -> VerificationStep(
                isLoading = uiState.isLoading,
                errorMessage = errorMessage,
                onBack = { wizardStep = 1 },
                onSubmit = { method, bytes, extension ->
                    authViewModel.register(phoneDigits, password, shopName, ownerName, address, gstNumber, method, bytes, extension)
                }
            )
        }
    }
}

private enum class VerificationMethod(val dbValue: String, val label: String, val description: String, val icon: ImageVector) {
    SHOPFRONT("shopfront_photo", "Shopfront photo", "Your shop's signboard/entrance", Icons.Filled.Storefront),
    DOCUMENT("business_document", "Business document", "GST certificate, trade license, UDYAM, etc.", Icons.Filled.Badge),
    SELFIE("selfie_photo", "Photo at your shop", "You standing in front of your shop", Icons.Filled.PersonPin)
}

@Composable
private fun VerificationStep(
    isLoading: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onSubmit: (method: String, bytes: ByteArray, extension: String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedMethod by remember { mutableStateOf(VerificationMethod.SHOPFRONT) }
    var pickedUri by remember { mutableStateOf<Uri?>(null) }

    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> pickedUri = uri }

    Column {
        Text("Verify your shop", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "Upload one of these so we know you're a real business — takes 30 seconds.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))

        VerificationMethod.entries.forEach { method ->
            MethodTile(method, selectedMethod == method) { selectedMethod = method; pickedUri = null }
            Spacer(modifier = Modifier.height(8.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                .clickable { pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            contentAlignment = Alignment.Center
        ) {
            if (pickedUri != null) {
                AsyncImage(
                    model = pickedUri,
                    contentDescription = "Selected photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(selectedMethod.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Tap to choose a photo", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
            }
        }

        ErrorBanner(errorMessage)
        Spacer(modifier = Modifier.height(20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onBack, enabled = !isLoading, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f).height(52.dp)) {
                Text("Back")
            }
            Button(
                onClick = {
                    val uri = pickedUri ?: return@Button
                    coroutineScope.launch {
                        val bytes = try {
                            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        } catch (e: Exception) {
                            null
                        } ?: return@launch
                        val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                        val extension = if (mimeType.contains("png")) "png" else "jpg"
                        onSubmit(selectedMethod.dbValue, bytes, extension)
                    }
                },
                enabled = pickedUri != null && !isLoading,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(2f).height(52.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Create Account", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun MethodTile(method: VerificationMethod, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
            .border(if (selected) 2.dp else 0.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(method.icon, contentDescription = null, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(method.label, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(method.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SegmentTile(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .border(if (selected) 2.dp else 0.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun PhoneField(value: String, onValueChange: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.height(56.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) { Text("+91", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        Spacer(modifier = Modifier.width(10.dp))
        OutlinedTextField(
            value = value,
            onValueChange = { new -> onValueChange(new.filter { it.isDigit() }.take(10)) },
            placeholder = { Text("98765 43210") },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, letterSpacing = 1.sp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.weight(1f).height(56.dp)
        )
    }
}

@Composable
private fun PasswordField(value: String, onValueChange: (String) -> Unit, visible: Boolean, onToggleVisible: () -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
        trailingIcon = {
            IconButton(onClick = onToggleVisible) {
                Icon(if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = null)
            }
        },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ErrorBanner(message: String?) {
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn(Motion.quickTween()) + expandVertically(Motion.quickTween()),
        exit = fadeOut(Motion.quickTween()) + shrinkVertically(Motion.quickTween())
    ) {
        Column {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(12.dp)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = Danger, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(message ?: "", color = Danger, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun LoadingOrText(loading: Boolean, text: String) {
    AnimatedContent(targetState = loading, label = "button_loading") { isLoading ->
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        } else {
            Text(text, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun Modifier.tapText(onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this.clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
}

@Composable
private fun VendoraMark(size: Dp = 84.dp) {
    Box(
        modifier = Modifier.size(size).background(Color.White.copy(alpha = 0.18f), shape = CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size * 0.5f)) {
            val w = this.size.width
            val h = this.size.height
            drawLine(color = Color.White, start = Offset(w * 0.08f, h * 0.18f), end = Offset(w * 0.5f, h * 0.92f), strokeWidth = w * 0.16f, cap = StrokeCap.Round)
            drawLine(color = Color.White, start = Offset(w * 0.5f, h * 0.92f), end = Offset(w * 0.92f, h * 0.18f), strokeWidth = w * 0.16f, cap = StrokeCap.Round)
        }
    }
}
