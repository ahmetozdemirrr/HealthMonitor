package com.ahmet.healthmonitor

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.*

object BleManager {
    // ESP32 ile AYNI UUID'ler (0x00FF Servisi ve 0xFF01 Karakteristiği)
    private val SERVICE_UUID = UUID.fromString("000000ff-0000-1000-8000-00805f9b34fb")
    private val CHAR_UUID = UUID.fromString("0000ff01-0000-1000-8000-00805f9b34fb")
    private val DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var isConnected = false

    var onConnectionStateChanged: ((Boolean) -> Unit)? = null

    // GÜNCELLEME: String yerine ByteArray kullanıyoruz (Binary Veri)
    var onDataReceived: ((ByteArray) -> Unit)? = null

    fun init(context: Context) {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = manager.adapter
    }

    @SuppressLint("MissingPermission")
    fun startScanning(context: Context) {
        // Tarama işlemleri genellikle UI tarafında (SettingsFragment vs) yönetildiği için
        // burayı ihtiyacına göre doldurabilirsin veya boş bırakabilirsin.
    }

    @SuppressLint("MissingPermission")
    fun connectDirectly(context: Context, device: BluetoothDevice) {
        Log.d("BLE", "Connecting to ${device.address}...")
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d("BLE", "Connected to GATT server.")
                isConnected = true

                Handler(Looper.getMainLooper()).post {
                    onConnectionStateChanged?.invoke(true)
                }

                // Veri paketimiz küçük (17 byte) olsa da MTU istemek iyi bir alışkanlıktır.
                gatt?.requestMtu(512)

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d("BLE", "Disconnected from GATT server.")
                isConnected = false

                Handler(Looper.getMainLooper()).post {
                    onConnectionStateChanged?.invoke(false)
                }

                gatt?.close()
                bluetoothGatt = null
            }
        }

        // MTU değiştikten sonra servisleri tarıyoruz
        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            super.onMtuChanged(gatt, mtu, status)
            Log.d("BLE", "MTU Changed to: $mtu, Status: $status")
            gatt?.discoverServices()
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt?.getService(SERVICE_UUID)
                if (service != null) {
                    val characteristic = service.getCharacteristic(CHAR_UUID)
                    if (characteristic != null) {
                        // Bildirimleri (Notification) Aktif Et
                        gatt.setCharacteristicNotification(characteristic, true)

                        val descriptor = characteristic.getDescriptor(DESCRIPTOR_UUID)
                        if(descriptor != null) {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                            } else {
                                @Suppress("DEPRECATION")
                                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                @Suppress("DEPRECATION")
                                gatt.writeDescriptor(descriptor)
                            }
                            Log.d("BLE", "Notifications enabled.")
                        }
                    }
                }
            }
        }

        // Eski Android sürümleri için veri alma
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            val dataBytes = characteristic.value

            // Veriyi hiç işlemeden (String yapmadan) direkt gönderiyoruz
            Handler(Looper.getMainLooper()).post {
                onDataReceived?.invoke(dataBytes)
            }
        }

        // Yeni Android sürümleri (API 33+) için veri alma
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            // Veriyi hiç işlemeden direkt gönderiyoruz
            Handler(Looper.getMainLooper()).post {
                onDataReceived?.invoke(value)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        if (bluetoothGatt != null) {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
            bluetoothGatt = null
        }
        isConnected = false
        onConnectionStateChanged?.invoke(false)
    }

    fun isDeviceConnected(): Boolean = isConnected
}