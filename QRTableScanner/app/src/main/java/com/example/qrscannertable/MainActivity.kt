package com.example.qrscannertable

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.media.MediaPlayer
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

data class ScannedItem(
    val hu: String,
    val material: String,
    val quantity: Int,
    val mfgDate: String,
    val plantCode: String
)

class MainActivity : AppCompatActivity() {

    companion object {
        const val COMPANY_NAME = "KrystaliteGT"
    }

    private lateinit var barcodeView: DecoratedBarcodeView
    private lateinit var adapter: ScannedAdapter
    private val scannedList = mutableListOf<ScannedItem>()
    private val scannedSet = HashSet<String>()
    private lateinit var successBeep: MediaPlayer
    private lateinit var duplicateBeep: MediaPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val tvCompany = findViewById<android.widget.TextView>(R.id.tvCompany)
        tvCompany.text = COMPANY_NAME

        val etObd = findViewById<android.widget.EditText>(R.id.etObd)
        val tvDate = findViewById<android.widget.TextView>(R.id.tvDate)
        tvDate.text = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        barcodeView = findViewById(R.id.barcode_scanner)

        successBeep = MediaPlayer.create(this, R.raw.success_beep)
        duplicateBeep = MediaPlayer.create(this, R.raw.duplicate_beep)

        val rv = findViewById<RecyclerView>(R.id.rvScanned)
        adapter = ScannedAdapter(scannedList)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        findViewById<View>(R.id.btnClear).setOnClickListener {
            scannedList.clear()
            scannedSet.clear()
            adapter.notifyDataSetChanged()
            Toast.makeText(this, "Cleared", Toast.LENGTH_SHORT).show()
        }

        findViewById<View>(R.id.btnSubmit).setOnClickListener {
            val obd = etObd.text.toString().trim()
            if (obd.isEmpty()) {
                Toast.makeText(this, "Please enter OBD # before submit", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showSubmitDialogAndPdf(obd, tvDate.text.toString())
        }

        // Camera permission
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            startScanner()
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startScanner() else Toast.makeText(this, "Camera permission required", Toast.LENGTH_LONG).show()
    }

    private fun startScanner() {
        barcodeView.decodeContinuous(object : BarcodeCallback {
            override fun barcodeResult(result: BarcodeResult?) {
                result?.text?.let { handleScannedText(it.trim()) }
            }
            override fun possibleResultPoints(resultPoints: MutableList<com.google.zxing.ResultPoint>?) {}
        })
    }

    private fun handleScannedText(text: String) {
        val etObd = findViewById<android.widget.EditText>(R.id.etObd)
        if (etObd.text.toString().trim().isEmpty()) {
            Toast.makeText(this, "Enter OBD # before scanning", Toast.LENGTH_SHORT).show()
            return
        }

        // Check duplicate HU#
        val hu = text.split(",")[0].trim()
        if (scannedSet.contains(hu)) {
            duplicateBeep.start()
            Toast.makeText(this, "Duplicate: $hu", Toast.LENGTH_SHORT).show()
            return
        }

        // Parse required fields
        try {
            val parts = text.split(",")
            val material = parts[2].trim()
            val quantity = parts[4].trim().toDouble().toInt() // Keep integer
            val plantCode = parts[10].trim()
            val mfgRaw = parts.last().replace("Manuf. Date", "").trim()
            val mfgDate = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).parse(mfgRaw)?.let {
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(it)
            } ?: mfgRaw

            val item = ScannedItem(hu, material, quantity, mfgDate, plantCode)
            scannedList.add(item)
            scannedSet.add(hu)
            adapter.notifyItemInserted(scannedList.size - 1)
            successBeep.start()
        } catch (e: Exception) {
            Toast.makeText(this, "Invalid QR format", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        try { barcodeView.resume() } catch (e: Exception) {}
    }

    override fun onPause() {
        super.onPause()
        try { barcodeView.pause() } catch (e: Exception) {}
    }

    private fun showSubmitDialogAndPdf(obd: String, date: String) {
        val count = scannedList.size
        val message = "These cartons are scanned.\nCount: $count\nOBD #: $obd\nDate: $date"
        AlertDialog.Builder(this)
            .setTitle("Submit")
            .setMessage(message)
            .setPositiveButton("Share as PDF") { _, _ -> createPdfAndShare(obd, date) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createPdfAndShare(obd: String, date: String) {
        if (scannedList.isEmpty()) {
            Toast.makeText(this, "No cartons scanned", Toast.LENGTH_SHORT).show()
            return
        }

        val pageWidth = 595
        val pageHeight = 842
        val pdf = PdfDocument()
        val paint = Paint()
        val titlePaint = Paint()
        titlePaint.textSize = 16f
        titlePaint.isFakeBoldText = true

        val rowsPerPage = 70
        val totalPages = (scannedList.size + rowsPerPage - 1) / rowsPerPage

        for (pageIndex in 0 until totalPages) {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create()
            val page = pdf.startPage(pageInfo)
            val canvas: Canvas = page.canvas

            var y = 30
            canvas.drawText(COMPANY_NAME, 20f, y.toFloat(), titlePaint)
            y += 20
            canvas.drawText("OBD #: $obd", 20f, y.toFloat(), paint)
            y += 15
            canvas.drawText("Date: $date", 20f, y.toFloat(), paint)
            y += 20

            paint.textSize = 12f
            paint.isFakeBoldText = true
            val colXSerial = 20f
            val colXPlant = 60f
            val colXHU = 120f
            val colXProduct = 260f
            val colXPcs = 420f
            val colXMfg = 480f

            canvas.drawText("#", colXSerial, y.toFloat(), paint)
            canvas.drawText("Plant", colXPlant, y.toFloat(), paint)
            canvas.drawText("HU #", colXHU, y.toFloat(), paint)
            canvas.drawText("Product", colXProduct, y.toFloat(), paint)
            canvas.drawText("Pcs", colXPcs, y.toFloat(), paint)
            canvas.drawText("MFG Date", colXMfg, y.toFloat(), paint)
            paint.isFakeBoldText = false
            y += 12

            val start = pageIndex * rowsPerPage
            val end = minOf(start + rowsPerPage, scannedList.size)
            for (i in start until end) {
                val item = scannedList[i]
                y += 18
                canvas.drawText((i + 1).toString(), colXSerial, y.toFloat(), paint)
                canvas.drawText(item.plantCode, colXPlant, y.toFloat(), paint)
                canvas.drawText(item.hu, colXHU, y.toFloat(), paint)
                canvas.drawText(item.material, colXProduct, y.toFloat(), paint)
                canvas.drawText(item.quantity.toString(), colXPcs, y.toFloat(), paint)
                canvas.drawText(item.mfgDate, colXMfg, y.toFloat(), paint)
            }

            // Footer
            val footerPaint = Paint()
            footerPaint.textSize = 12f
            footerPaint.isFakeBoldText = true
            val footerText = "Created By Umair Meer"
            val x = pageWidth / 2f - (footerPaint.measureText(footerText) / 2)
            val yFooter = pageHeight - 20f
            canvas.drawText(footerText, x, yFooter, footerPaint)

            pdf.finishPage(page)
        }

        val fileName = "scanned_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.pdf"
        val outputDir = File(getExternalFilesDir(null), "pdfs")
        if (!outputDir.exists()) outputDir.mkdirs()
        val file = File(outputDir, fileName)
        try {
            pdf.writeTo(FileOutputStream(file))
            pdf.close()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error creating PDF: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }

        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        val share = Intent(Intent.ACTION_SEND)
        share.type = "application/pdf"
        share.putExtra(Intent.EXTRA_STREAM, uri)
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(share, "Share PDF"))
    }
}
