package com.kirdev.pulsedeckpanel.feature.connection

import android.content.Context
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/**
 * Google code scanner (Play services): the scanning UI runs in Play services, so the app
 * needs no camera permission and ships no camera code.
 */
object QrScanner {
    fun scan(context: Context, onResult: (String) -> Unit, onError: (String) -> Unit) {
        val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        val scanner = GmsBarcodeScanning.getClient(context, options)
        scanner.startScan()
            .addOnSuccessListener { barcode -> barcode.rawValue?.let(onResult) ?: onError("QR-код не прочитан.") }
            .addOnCanceledListener { }
            .addOnFailureListener {
                if (it.message.orEmpty().contains("download", ignoreCase = true)) {
                    // First use: Play services fetches the scanner UI once; ask for it now instead of waiting.
                    ModuleInstall.getClient(context).installModules(ModuleInstallRequest.newBuilder().addApi(scanner).build())
                    onError("Google Play загружает модуль сканера (один раз). Нужен интернет; попробуйте через минуту.")
                } else {
                    onError("Сканер недоступен: ${it.message ?: "нет сервисов Google Play"}. Введите код вручную.")
                }
            }
    }
}
