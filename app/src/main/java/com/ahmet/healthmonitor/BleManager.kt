package com.ahmet.healthmonitor

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.*

object BleManager {
    // UUID Tanımları (ESP32 ile birebir aynı)
    private val SERVICE_UUID = UUID.fromString("000000ff-0000-1000-8000-00805f9b34fb")
    private val CHAR_UUID = UUID.fromString("0000ff01-0000-1000-8000-00805f9b34fb")
    private val DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var isConnected = false

    var onConnectionStateChanged: ((Boolean) -> Unit)? = null
    var onDataReceived: ((ByteArray) -> Unit)? = null

    // Context referansını saklamak yerine processData içinde kullanmak için init fonksiyonunu koruyoruz
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = manager.adapter
    }

    @SuppressLint("MissingPermission")
    fun connectDirectly(context: Context, device: BluetoothDevice) {
        Log.d("BLE", "Bağlanılıyor: ${device.address}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            bluetoothGatt = device.connectGatt(context, false, gattCallback)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d("BLE", "GATT Sunucusuna Bağlandı.")
                isConnected = true

                Handler(Looper.getMainLooper()).post {
                    onConnectionStateChanged?.invoke(true)
                }
                gatt?.requestMtu(512)

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d("BLE", "Bağlantı Koptu.")
                isConnected = false

                Handler(Looper.getMainLooper()).post {
                    onConnectionStateChanged?.invoke(false)
                }

                gatt?.close()
                bluetoothGatt = null
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            Log.d("BLE", "MTU Değişti: $mtu. Servisler taranıyor...")
            gatt?.discoverServices()
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && gatt != null) {
                val service = gatt.getService(SERVICE_UUID)
                if (service != null) {
                    val characteristic = service.getCharacteristic(CHAR_UUID)
                    if (characteristic != null) {
                        gatt.setCharacteristicNotification(characteristic, true)
                        val descriptor = characteristic.getDescriptor(DESCRIPTOR_UUID)
                        if (descriptor != null) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                            } else {
                                @Suppress("DEPRECATION")
                                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                @Suppress("DEPRECATION")
                                gatt.writeDescriptor(descriptor)
                            }
                        }
                    }
                }
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("BLE", "BAŞARILI: Bildirimler resmen aktif!")
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            processData(value)
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            processData(characteristic.value)
        }

        private fun processData(data: ByteArray) {
            // 1. Veriyi Parse Et (BPM hesaplandı)
            val healthData = BleDataParser.parse(data)

            // 2. SharedPreferences'a Kaydet
            appContext?.let { ctx ->
                val sharedPref = ctx.getSharedPreferences("HealthApp", Context.MODE_PRIVATE)
                with(sharedPref.edit()) {
                    // DİKKAT: heartRateRaw DEĞİL, heartRateBpm KULLANIYORUZ
                    putInt("live_hr", healthData.heartRateBpm)
                    putFloat("live_temp", healthData.temperature)
                    putInt("live_steps", healthData.stepCount)
                    apply()
                }

                // 3. Veritabanına Kaydet
                DatabaseManager.saveLiveReading(
                    ctx,
                    healthData.heartRateBpm, // <-- BURASI DA DEĞİŞTİ (Raw -> Bpm)
                    healthData.temperature,
                    healthData.stepCount
                )
            }

            Handler(Looper.getMainLooper()).post {
                onDataReceived?.invoke(data)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        isConnected = false
        onConnectionStateChanged?.invoke(false)
    }

    fun isDeviceConnected(): Boolean = isConnected

    // --- YENİ EKLENECEK FONKSİYON ---
    @SuppressLint("MissingPermission")
    fun setMonitoring(enable: Boolean) {
        val gatt = bluetoothGatt
        if (gatt == null) {
            Log.e("BLE", "GATT null, işlem yapılamadı.")
            return
        }

        val service = gatt.getService(SERVICE_UUID)
        if (service == null) {
            Log.e("BLE", "Servis bulunamadı.")
            return
        }

        val characteristic = service.getCharacteristic(CHAR_UUID)
        if (characteristic == null) {
            Log.e("BLE", "Karakteristik bulunamadı.")
            return
        }

        // 1. Android tarafında bildirimleri aç/kapat
        gatt.setCharacteristicNotification(characteristic, enable)

        // 2. ESP32'ye "Bana veri gönderme" veya "Gönder" komutunu yaz (CCCD)
        val descriptor = characteristic.getDescriptor(DESCRIPTOR_UUID)
        if (descriptor != null) {
            val value = if (enable) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(descriptor, value)
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = value
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(descriptor)
            }
            Log.d("BLE", "Veri akışı durumu değiştirildi: $enable")
        }
    }
}