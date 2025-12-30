package com.ahmet.healthmonitor

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager as AndroidBluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.ahmet.healthmonitor.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {
    private lateinit var binding: FragmentSettingsBinding

    // İzin İsteyici
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions: Map<String, Boolean> ->
            val granted = permissions.entries.all { it.value }
            if (granted) {
                startBleScan()
            } else {
                Toast.makeText(context, "Bluetooth permissions required", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentSettingsBinding.inflate(inflater, container, false)

        // BleManager Başlat
        BleManager.init(requireContext())

        // Sadece bağlantı durumunu dinle (Veriyi MainActivity işleyecek)
        setupConnectionListener()

        binding.btnScanPage.setOnClickListener {
            checkPermissionsAndScan()
        }

        binding.btnDisconnect.setOnClickListener {
            BleManager.disconnect()
        }

        // UI Güncelle
        updateConnectionUI(BleManager.isDeviceConnected())

        return binding.root
    }

    private fun setupConnectionListener() {
        BleManager.onConnectionStateChanged = { isConnected ->
            // UI işlemleri için Main Thread şart
            if (isAdded) {
                requireActivity().runOnUiThread {
                    updateConnectionUI(isConnected)
                    if (isConnected) {
                        Toast.makeText(context, "Bağlandı!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Bağlantı Kesildi", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun checkPermissionsAndScan() {
        val permissionsToRequest = mutableListOf<String>()
        // Android 12 ve üzeri için
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            // Eski Androidler için Konum izni şarttır
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        val allGranted = permissionsToRequest.all {
            ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            startBleScan()
        } else {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    @SuppressLint("MissingPermission")
    private fun startBleScan() {
        Toast.makeText(context, "Taranıyor...", Toast.LENGTH_SHORT).show()
        binding.btnScanPage.text = "Aranıyor..."
        binding.btnScanPage.isEnabled = false

        val bluetoothManager = requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as AndroidBluetoothManager
        val scanner = bluetoothManager.adapter.bluetoothLeScanner

        if (scanner == null) {
            Toast.makeText(context, "Bluetooth Kapalı!", Toast.LENGTH_SHORT).show()
            return
        }

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                val device = result?.device ?: return
                val name = device.name ?: "Unknown"
                val address = device.address

                Log.d("BLE_SCAN", "Found: $name - $address") // Logcat'te görmek için

                // İsim boşsa bile bağlanmayı dene (veya adrese göre)
                // ESP32'nin MAC adresini biliyorsan buraya ekleyebilirsin ama genelde gerekmez
                if (name.contains("Health", true) ||
                    name.contains("ESP", true) ||
                    name.contains("Monitor", true)) { // Filtreyi genişlettik

                    scanner.stopScan(this)
                    BleManager.connectDirectly(requireContext(), device)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                binding.btnScanPage.text = "Tekrar Tara"
                binding.btnScanPage.isEnabled = true
                Toast.makeText(context, "Hata: $errorCode", Toast.LENGTH_SHORT).show()
            }
        }

        scanner.startScan(callback)

        // 10 saniye sonra bulamazsa durdur
        Handler(Looper.getMainLooper()).postDelayed({
            scanner.stopScan(callback)
            if (!BleManager.isDeviceConnected()) {
                if(isAdded) {
                    binding.btnScanPage.text = "Cihaz Tara"
                    binding.btnScanPage.isEnabled = true
                    Toast.makeText(context, "Cihaz Bulunamadı", Toast.LENGTH_SHORT).show()
                }
            }
        }, 10000)
    }

    private fun updateConnectionUI(isConnected: Boolean) {
        if (!isAdded) return

        if (isConnected) {
            binding.tvDeviceName.text = "Health Monitor Band"
            binding.tvConnectionStatus.text = "● Bağlı"
            binding.tvConnectionStatus.setTextColor(android.graphics.Color.parseColor("#00C853")) // Yeşil

            binding.btnScanPage.visibility = View.GONE
            binding.btnDisconnect.visibility = View.VISIBLE
        } else {
            binding.tvDeviceName.text = "Cihaz Yok"
            binding.tvConnectionStatus.text = "● Bağlantı Yok"
            binding.tvConnectionStatus.setTextColor(android.graphics.Color.parseColor("#D32F2F")) // Kırmızı

            binding.btnScanPage.visibility = View.VISIBLE
            binding.btnScanPage.text = "Cihaz Tara"
            binding.btnScanPage.isEnabled = true
            binding.btnDisconnect.visibility = View.GONE
        }
    }
}