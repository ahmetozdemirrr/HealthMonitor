package com.ahmet.healthmonitor

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
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
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.ahmet.healthmonitor.databinding.FragmentSettingsBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.slider.Slider

class SettingsFragment : Fragment() {
    private lateinit var binding: FragmentSettingsBinding
    private var pulseAnimator: ObjectAnimator? = null

    // İzin İsteyici
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions: Map<String, Boolean> ->
            val granted = permissions.entries.all { it.value }
            if (granted) {
                startBleScan()
            } else {
                Toast.makeText(context, "Bluetooth izinleri gerekli", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentSettingsBinding.inflate(inflater, container, false)

        // BleManager'ı hazırla (Zaten MainActivity'de başladı ama garanti olsun)
        BleManager.init(requireContext())

        // Sadece bağlantı durumunu dinle (Veriyi MainActivity işliyor)
        setupConnectionListener()

        updateCurrentGoalText()
        updatePersonalInfoPreview()

        binding.swipeRefresh.setOnRefreshListener {
            updateConnectionUI(BleManager.isDeviceConnected())
            binding.root.postDelayed({
                binding.swipeRefresh.isRefreshing = false
            }, 1000)
        }

        binding.btnScanPage.setOnClickListener {
            checkPermissionsAndScan()
        }

        binding.btnDisconnect.setOnClickListener {
            BleManager.disconnect()
        }

        // Açılışta durumu güncelle
        updateConnectionUI(BleManager.isDeviceConnected())

        binding.cardStepGoal.setOnClickListener { showStepGoalDialog() }
        binding.cardPersonalInfo.setOnClickListener { showProfileDialog() }

        return binding.root
    }

    private fun setupConnectionListener() {
        // DİKKAT: Veri dinleyicisini (onDataReceived) BURADA SAKIN TANIMLAMA.
        // Onu MainActivity yönetiyor. Biz sadece bağlantı koparsa arayüzü güncelleyelim.

        BleManager.onConnectionStateChanged = { isConnected ->
            if (isAdded) {
                requireActivity().runOnUiThread {
                    updateConnectionUI(isConnected)
                    if (isConnected) {
                        Toast.makeText(context, "Cihaz Bağlandı!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Bağlantı Kesildi", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun checkPermissionsAndScan() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
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
        Toast.makeText(context, "Cihaz aranıyor...", Toast.LENGTH_SHORT).show()
        binding.btnScanPage.text = "Aranıyor..."
        binding.btnScanPage.isEnabled = false

        val bluetoothManager = requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as AndroidBluetoothManager
        val scanner = bluetoothManager.adapter.bluetoothLeScanner

        if (scanner == null) {
            Toast.makeText(context, "Bluetooth kapalı veya bulunamadı!", Toast.LENGTH_SHORT).show()
            binding.btnScanPage.text = "Tekrar Tara"
            binding.btnScanPage.isEnabled = true
            return
        }

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                val device = result?.device ?: return
                val name = device.name ?: "Unknown"
                val address = device.address

                Log.d("BLE_SCAN", "Bulundu: $name ($address)")

                // Filtreyi Genişlettik: İsimde 'Health', 'ESP', 'Monitor' geçenleri veya bilinen MAC'i yakala
                if (name.contains("Health", true) ||
                    name.contains("ESP", true) ||
                    name.contains("Monitor", true)) {

                    scanner.stopScan(this)
                    BleManager.connectDirectly(requireContext(), device)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                binding.btnScanPage.text = "Tekrar Tara"
                binding.btnScanPage.isEnabled = true
                Toast.makeText(context, "Tarama hatası: $errorCode", Toast.LENGTH_SHORT).show()
            }
        }

        scanner.startScan(callback)

        // 10 saniye zaman aşımı
        Handler(Looper.getMainLooper()).postDelayed({
            // Eğer hala bağlanmadıysa durdur
            if (!BleManager.isDeviceConnected()) {
                scanner.stopScan(callback)
                if (isAdded) {
                    binding.btnScanPage.text = "Cihaz Tara"
                    binding.btnScanPage.isEnabled = true
                    Toast.makeText(context, "Cihaz bulunamadı, tekrar deneyin.", Toast.LENGTH_SHORT).show()
                }
            }
        }, 10000)
    }

    private fun updateConnectionUI(isConnected: Boolean) {
        if (!isAdded) return

        if (isConnected) {
            binding.tvDeviceName.text = "Health Monitor"
            binding.tvConnectionStatus.text = "● Bağlı"
            binding.tvConnectionStatus.setTextColor(android.graphics.Color.parseColor("#00C853"))
            binding.tvConnectionStatus.setBackgroundColor(android.graphics.Color.parseColor("#DCEDC8"))

            binding.layoutBattery.visibility = View.VISIBLE
            binding.tvBatteryLevel.text = "100%" // Şimdilik sabit, sonra protokole eklenebilir

            binding.btnScanPage.visibility = View.GONE
            binding.btnDisconnect.visibility = View.VISIBLE
        } else {
            binding.tvDeviceName.text = "Bağlı Değil"
            binding.tvConnectionStatus.text = "● Bağlantı Yok"
            binding.tvConnectionStatus.setTextColor(android.graphics.Color.parseColor("#D32F2F"))
            binding.tvConnectionStatus.setBackgroundColor(android.graphics.Color.parseColor("#FFCDD2"))

            binding.layoutBattery.visibility = View.GONE

            binding.btnScanPage.visibility = View.VISIBLE
            binding.btnScanPage.text = "Cihaz Tara"
            binding.btnScanPage.isEnabled = true
            binding.btnDisconnect.visibility = View.GONE
        }
    }

    // --- ESKİ UI FONKSİYONLARI (Dokunulmadı) ---
    private fun updateCurrentGoalText() {
        val sharedPref = requireActivity().getSharedPreferences("HealthApp", Context.MODE_PRIVATE)
        val goal = sharedPref.getInt("daily_goal", 6000)
        binding.tvCurrentGoal.text = "$goal steps"
    }

    private fun updatePersonalInfoPreview() {
        val sharedPref = requireActivity().getSharedPreferences("HealthApp", Context.MODE_PRIVATE)
        val w = sharedPref.getInt("user_weight", 75)
        val h = sharedPref.getInt("user_height", 175)
        val a = sharedPref.getInt("user_age", 25)
        binding.tvPersonalInfoPreview.text = "${w}kg, ${h}cm, ${a}yo"
    }

    private fun showProfileDialog() {
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.bottom_sheet_profile, null)
        dialog.setContentView(view)

        val npWeight = view.findViewById<NumberPicker>(R.id.np_weight)
        val npHeight = view.findViewById<NumberPicker>(R.id.np_height)
        val npAge = view.findViewById<NumberPicker>(R.id.np_age)
        val btnSave = view.findViewById<Button>(R.id.btn_save_profile)

        val sharedPref = requireActivity().getSharedPreferences("HealthApp", Context.MODE_PRIVATE)

        npWeight.minValue = 30; npWeight.maxValue = 200; npWeight.value = sharedPref.getInt("user_weight", 75); npWeight.wrapSelectorWheel = false
        npHeight.minValue = 100; npHeight.maxValue = 250; npHeight.value = sharedPref.getInt("user_height", 175); npHeight.wrapSelectorWheel = false
        npAge.minValue = 10; npAge.maxValue = 100; npAge.value = sharedPref.getInt("user_age", 25); npAge.wrapSelectorWheel = false

        btnSave.setOnClickListener {
            sharedPref.edit().apply {
                putInt("user_weight", npWeight.value)
                putInt("user_height", npHeight.value)
                putInt("user_age", npAge.value)
                apply()
            }
            updatePersonalInfoPreview()
            dialog.dismiss()
            Toast.makeText(context, "Profile updated!", Toast.LENGTH_SHORT).show()
        }
        dialog.show()
    }

    private fun showStepGoalDialog() {
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_step_goal, null)
        dialog.setContentView(view)

        val slider = view.findViewById<Slider>(R.id.goal_slider)
        val tvPreview = view.findViewById<TextView>(R.id.tv_goal_preview)
        val ivAnimation = view.findViewById<ImageView>(R.id.iv_goal_animation)
        val btnSave = view.findViewById<Button>(R.id.btn_save_goal)
        val chip4k = view.findViewById<Chip>(R.id.chip_4k)
        val chip8k = view.findViewById<Chip>(R.id.chip_8k)
        val chip12k = view.findViewById<Chip>(R.id.chip_12k)
        val chip16k = view.findViewById<Chip>(R.id.chip_16k)
        val chip20k = view.findViewById<Chip>(R.id.chip_20k)

        val sharedPref = requireActivity().getSharedPreferences("HealthApp", Context.MODE_PRIVATE)
        val currentGoal = sharedPref.getInt("daily_goal", 6000)

        slider.value = currentGoal.toFloat()
        tvPreview.text = currentGoal.toString()
        startPulseAnimation(ivAnimation, currentGoal)
        updateIconBasedOnGoal(ivAnimation, currentGoal)

        slider.addOnChangeListener { _, value, _ ->
            val goal = value.toInt()
            tvPreview.text = goal.toString()
            updateIconBasedOnGoal(ivAnimation, goal)
            updateAnimationSpeed(goal)
        }

        chip4k.setOnClickListener  { slider.value = 4000f }
        chip8k.setOnClickListener  { slider.value = 8000f }
        chip12k.setOnClickListener { slider.value = 12000f }
        chip16k.setOnClickListener { slider.value = 16000f }
        chip20k.setOnClickListener { slider.value = 20000f }

        btnSave.setOnClickListener {
            val newGoal = slider.value.toInt()
            sharedPref.edit().putInt("daily_goal", newGoal).apply()
            updateCurrentGoalText()
            dialog.dismiss()
            Toast.makeText(context, "Goal updated: $newGoal steps!", Toast.LENGTH_SHORT).show()
        }
        dialog.setOnDismissListener { pulseAnimator?.cancel() }
        dialog.show()
    }

    private fun startPulseAnimation(view: View, goal: Int) {
        val scaleDown = PropertyValuesHolder.ofFloat("scaleX", 1.2f)
        val scaleUp = PropertyValuesHolder.ofFloat("scaleY", 1.2f)
        pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(view, scaleDown, scaleUp)
        pulseAnimator?.repeatCount = ObjectAnimator.INFINITE
        pulseAnimator?.repeatMode = ObjectAnimator.REVERSE
        pulseAnimator?.interpolator = OvershootInterpolator()
        updateAnimationSpeed(goal)
        pulseAnimator?.start()
    }

    private fun updateAnimationSpeed(goal: Int) {
        val maxDuration = 1000L
        val minDuration = 300L
        val percent = (goal - 1000f) / (20000f - 1000f)
        val newDuration = (maxDuration - (percent * (maxDuration - minDuration))).toLong()
        pulseAnimator?.duration = newDuration
    }

    private fun updateIconBasedOnGoal(imageView: ImageView, goal: Int) {
        when {
            goal < 8000 -> {
                imageView.setImageResource(R.drawable.ic_walk)
                imageView.setColorFilter(ContextCompat.getColor(requireContext(), R.color.sage_green_dark))
            }
            goal < 15000 -> {
                imageView.setImageResource(R.drawable.ic_run)
                imageView.setColorFilter(ContextCompat.getColor(requireContext(), R.color.sage_green_dark))
            }
            else -> {
                imageView.setImageResource(R.drawable.ic_fire)
                imageView.setColorFilter(ContextCompat.getColor(requireContext(), R.color.red_accent))
            }
        }
    }
}