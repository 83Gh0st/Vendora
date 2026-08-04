package com.vendora.app.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Environment
import com.vendora.app.data.ProductEntity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.content.Intent
import androidx.core.content.FileProvider

object PdfGenerator {

    fun openPdf(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Open PDF"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun generateQRCodesPdf(context: Context, products: List<ProductEntity>): File? {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 Size
        
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas
        
        val paint = Paint()
        val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = 12f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val qrCodesPerRow = 4
        val padding = 20f
        val startX = 40f
        val startY = 40f
        val qrSize = 100f
        val cellWidth = qrSize + padding * 2
        val cellHeight = qrSize + 40f
        
        var x = startX
        var y = startY
        var itemsOnPage = 0
        
        products.forEach { product ->
            if (itemsOnPage >= 24) { // Max items per A4 page roughly
                pdfDocument.finishPage(page)
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                x = startX
                y = startY
                itemsOnPage = 0
            }

            val bitmap = BarcodeGenerator.generateQRCode(product.barcode, size = qrSize.toInt())
            if (bitmap != null) {
                canvas.drawBitmap(bitmap, x + padding, y, paint)
                canvas.drawText(product.name, x + padding + qrSize / 2, y + qrSize + 15f, textPaint)
                canvas.drawText("₹${product.sellingPrice}", x + padding + qrSize / 2, y + qrSize + 30f, textPaint)
            }
            
            itemsOnPage++
            x += cellWidth
            if (itemsOnPage % qrCodesPerRow == 0) {
                x = startX
                y += cellHeight
            }
        }

        pdfDocument.finishPage(page)

        return try {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            if (dir != null && !dir.exists()) {
                dir.mkdirs()
            }
            val file = File(dir, "Vendora_QRCodes.pdf")
            pdfDocument.writeTo(FileOutputStream(file))
            pdfDocument.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            pdfDocument.close()
            null
        }
    }

    fun generateRestockPdf(context: Context, lowStockProducts: List<ProductEntity>, orderQuantities: Map<Int, String>): File? {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 Size
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas

        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        
        val headerPaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        
        val rowPaint = Paint().apply {
            color = Color.BLACK
            textSize = 12f
        }

        // Draw Title
        val dateStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())
        canvas.drawText("Stock Replenishment Report", pageInfo.pageWidth / 2f, 50f, titlePaint)
        
        val subtitlePaint = Paint(titlePaint).apply { textSize = 14f; typeface = Typeface.DEFAULT }
        canvas.drawText("Date: $dateStr", pageInfo.pageWidth / 2f, 75f, subtitlePaint)

        // Draw Table Header
        var currentY = 120f
        val col1X = 50f
        val col2X = 300f
        val col3X = 450f
        
        canvas.drawLine(40f, currentY - 15f, pageInfo.pageWidth - 40f, currentY - 15f, headerPaint)
        canvas.drawText("Product Name", col1X, currentY, headerPaint)
        canvas.drawText("Current Stock", col2X, currentY, headerPaint)
        canvas.drawText("Qty to Order", col3X, currentY, headerPaint)
        canvas.drawLine(40f, currentY + 10f, pageInfo.pageWidth - 40f, currentY + 10f, headerPaint)
        
        currentY += 30f

        // Draw Rows
        lowStockProducts.forEach { product ->
            if (currentY > 800f) {
                pdfDocument.finishPage(page)
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                currentY = 50f // Reset Y for new page
            }
            
            canvas.drawText(product.name, col1X, currentY, rowPaint)
            canvas.drawText("${product.currentStock} ${product.unit}", col2X, currentY, rowPaint)
            val orderQty = orderQuantities[product.id]
            if (orderQty.isNullOrBlank()) {
                canvas.drawText("________________", col3X, currentY, rowPaint) // Blank line for writing
            } else {
                canvas.drawText(orderQty, col3X, currentY, rowPaint) // Custom order qty
            }
            
            currentY += 25f
        }

        pdfDocument.finishPage(page)

        return try {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            if (dir != null && !dir.exists()) {
                dir.mkdirs()
            }
            val file = File(dir, "Vendora_Restock_Report.pdf")
            pdfDocument.writeTo(FileOutputStream(file))
            pdfDocument.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            pdfDocument.close()
            null
        }
    }
}
