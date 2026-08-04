package com.vendora.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Size
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ScannerActivity : ComponentActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private val barcodeScanner = BarcodeScanning.getClient()
    
    // Cooldown map for 1200ms debounce
    private val scanCooldowns = mutableMapOf<String, Long>()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(this, "Camera permission is required to scan barcodes", Toast.LENGTH_LONG).show()
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        setContent {
            ScannerScreenContent()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        barcodeScanner.close()
    }

    @Composable
    fun ScannerScreenContent() {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val previewView = remember { PreviewView(context) }
        var camera by remember { mutableStateOf<Camera?>(null) }
        var torchOn by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                
                // 1. Preview
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                // 2. Image Analysis with 720p resolution configuration
                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(1280, 720))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                var lastAnalysisTimestamp = 0L

                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    val currentTimestamp = System.currentTimeMillis()
                    
                    // Rule 2: 10 FPS Rate Limiter (skip if less than 100ms since last check)
                    if (currentTimestamp - lastAnalysisTimestamp < 100) {
                        imageProxy.close()
                        return@setAnalyzer
                    }
                    lastAnalysisTimestamp = currentTimestamp

                    val mediaImage = imageProxy.image
                    if (mediaImage != null) {
                        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                        barcodeScanner.process(image)
                            .addOnSuccessListener { barcodes ->
                                for (barcode in barcodes) {
                                    val rawValue = barcode.rawValue
                                    if (!rawValue.isNullOrBlank()) {
                                        // Rule 4: 1200ms Duplicate Debounce
                                        val lastScan = scanCooldowns[rawValue] ?: 0L
                                        if (currentTimestamp - lastScan >= 1200) {
                                            scanCooldowns[rawValue] = currentTimestamp
                                            
                                            // Found barcode! Return result and close activity
                                            val resultIntent = Intent().apply {
                                                putExtra("SCAN_RESULT", rawValue)
                                            }
                                            runOnUiThread {
                                                setResult(Activity.RESULT_OK, resultIntent)
                                                finish()
                                            }
                                            break
                                        }
                                    }
                                }
                            }
                            .addOnCompleteListener {
                                // Rule 3: Memory-Safe Frame Release
                                imageProxy.close()
                            }
                    } else {
                        imageProxy.close()
                    }
                }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider.unbindAll()
                    camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                } catch (exc: Exception) {
                    Toast.makeText(context, "Use case binding failed", Toast.LENGTH_SHORT).show()
                }

            }, ContextCompat.getMainExecutor(context))
        }

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )
            
            // Scanner overlay HUD
            ScannerOverlay(
                hasFlash = camera?.cameraInfo?.hasFlashUnit() == true,
                torchOn = torchOn,
                onToggleTorch = {
                    val newState = !torchOn
                    camera?.cameraControl?.enableTorch(newState)
                    torchOn = newState
                },
                onCloseClicked = {
                    setResult(Activity.RESULT_CANCELED)
                    finish()
                }
            )
        }
    }

    @Composable
    fun ScannerOverlay(
        hasFlash: Boolean,
        torchOn: Boolean,
        onToggleTorch: () -> Unit,
        onCloseClicked: () -> Unit
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Draw translucent dark overlay with a cut-out scanning area
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                
                val boxWidth = 280.dp.toPx()
                val boxHeight = 180.dp.toPx()
                
                val left = (canvasWidth - boxWidth) / 2f
                val top = (canvasHeight - boxHeight) / 2f
                
                val overlayColor = Color.Black.copy(alpha = 0.6f)
                
                // Top Overlay Rect
                drawRect(
                    color = overlayColor,
                    topLeft = Offset(0f, 0f),
                    size = androidx.compose.ui.geometry.Size(canvasWidth, top)
                )
                // Bottom Overlay Rect
                drawRect(
                    color = overlayColor,
                    topLeft = Offset(0f, top + boxHeight),
                    size = androidx.compose.ui.geometry.Size(canvasWidth, canvasHeight - (top + boxHeight))
                )
                // Left Overlay Rect
                drawRect(
                    color = overlayColor,
                    topLeft = Offset(0f, top),
                    size = androidx.compose.ui.geometry.Size(left, boxHeight)
                )
                // Right Overlay Rect
                drawRect(
                    color = overlayColor,
                    topLeft = Offset(left + boxWidth, top),
                    size = androidx.compose.ui.geometry.Size(canvasWidth - (left + boxWidth), boxHeight)
                )
            }
            
            // Center HUD UI elements
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.size(width = 280.dp, height = 180.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 4.dp.toPx()
                        val lineLength = 24.dp.toPx()
                        val color = Color(0xFF4CAF50) // Material Green
                        
                        // Top-Left corner
                        drawLine(color, Offset(0f, 0f), Offset(lineLength, 0f), strokeWidth)
                        drawLine(color, Offset(0f, 0f), Offset(0f, lineLength), strokeWidth)
                        
                        // Top-Right corner
                        drawLine(color, Offset(size.width, 0f), Offset(size.width - lineLength, 0f), strokeWidth)
                        drawLine(color, Offset(size.width, 0f), Offset(size.width, lineLength), strokeWidth)
                        
                        // Bottom-Left corner
                        drawLine(color, Offset(0f, size.height), Offset(lineLength, size.height), strokeWidth)
                        drawLine(color, Offset(0f, size.height), Offset(0f, size.height - lineLength), strokeWidth)
                        
                        // Bottom-Right corner
                        drawLine(color, Offset(size.width, size.height), Offset(size.width - lineLength, size.height), strokeWidth)
                        drawLine(color, Offset(size.width, size.height), Offset(size.width, size.height - lineLength), strokeWidth)
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = if (hasFlash) {
                            "Position barcode within the frame · tap the flash icon in dim light"
                        } else {
                            "Position barcode within the frame"
                        },
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            // Torch/flashlight toggle — most scan misreads happen in low light,
            // so this lets the shop owner light up the barcode directly rather
            // than needing better room lighting.
            if (hasFlash) {
                IconButton(
                    onClick = onToggleTorch,
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(16.dp)
                        .align(Alignment.TopStart)
                        .background(
                            if (torchOn) Color(0xFF4CAF50) else Color.Black.copy(alpha = 0.5f),
                            RoundedCornerShape(50)
                        )
                ) {
                    Icon(
                        imageVector = if (torchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = if (torchOn) "Turn off flashlight" else "Turn on flashlight for low light",
                        tint = Color.White
                    )
                }
            }

            // Close Button
            IconButton(
                onClick = onCloseClicked,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(16.dp)
                    .align(Alignment.TopEnd)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Scanner",
                    tint = Color.White
                )
            }
        }
    }
}
