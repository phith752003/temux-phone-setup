package com.autoclicker.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.*
import android.widget.AdapterView
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.EditText
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.graphics.Bitmap
import com.autoclicker.app.R
import com.autoclicker.app.macro.*
import com.autoclicker.app.storage.ProfileStorage
import com.autoclicker.app.ui.PointerView
import com.autoclicker.app.ui.CropOverlayView
import com.autoclicker.app.MainActivity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest



/**
 * Floating overlay service that provides macro control panel.
 * 
 * Design principles:
 * - No continuous animation
 * - No continuous redraw (only update when state changes)
 * - Compact, lightweight panel
 * - Emergency Stop always visible when running
 */
class OverlayService : Service() {

    companion object {
        private const val TAG = "OverlayService"

        @Volatile
        var isRunning = false
            private set

        @Volatile
        var instance: OverlayService? = null
            private set

        fun start(context: Context) {
            context.startService(Intent(context, OverlayService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private val macroRunner = MacroRunner()
    private var currentProfile: MacroProfile? = null
    private val activePointers = mutableListOf<PointerView>()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var editorView: View? = null
    private var cropView: CropOverlayView? = null


    // UI references
    private var tvStatus: TextView? = null
    private var tvOcrLog: TextView? = null
    private var btnStart: Button? = null
    private var btnPause: Button? = null
    private var btnStop: Button? = null
    private var btnEmergencyStop: Button? = null
    private var overlayBody: LinearLayout? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        instance = this
        Log.i(TAG, "Overlay service created")

        startAsForeground()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createOverlay()
        loadCurrentProfile()
        showPointers()
        observeState()
    }


    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        instance = null
        macroRunner.stop()
        serviceScope.cancel()
        removeOverlay()
        clearPointers()
        removeEditor()
        removeCropOverlay()
        stopForeground(STOP_FOREGROUND_REMOVE)
        Log.i(TAG, "Overlay service destroyed")
    }



    private fun createOverlay() {
        val inflater = LayoutInflater.from(this)
        overlayView = inflater.inflate(R.layout.overlay_panel, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 200
        }

        setupDragAndDrop(params)
        setupButtons()

        windowManager?.addView(overlayView, params)
        Log.i(TAG, "Overlay added to window")
    }

    private fun setupDragAndDrop(params: WindowManager.LayoutParams) {
        val header = overlayView?.findViewById<View>(R.id.overlay_header)
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        header?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(overlayView, params)
                    true
                }
                else -> false
            }
        }
    }

    private fun setupButtons() {
        val view = overlayView ?: return

        tvStatus = view.findViewById(R.id.tv_status)
        tvOcrLog = view.findViewById(R.id.tv_ocr_log)
        btnStart = view.findViewById(R.id.btn_start)
        btnPause = view.findViewById(R.id.btn_pause)
        btnStop = view.findViewById(R.id.btn_stop)
        btnEmergencyStop = view.findViewById(R.id.btn_emergency_stop)
        overlayBody = view.findViewById(R.id.overlay_body)

        btnStart?.setOnClickListener { onStartClicked() }
        btnPause?.setOnClickListener { onPauseClicked() }
        btnStop?.setOnClickListener { onStopClicked() }
        btnEmergencyStop?.setOnClickListener { onStopClicked() }

        // Add action buttons
        view.findViewById<Button>(R.id.btn_add_tap)?.setOnClickListener {
            addQuickTap()
        }
        view.findViewById<Button>(R.id.btn_add_swipe)?.setOnClickListener {
            addQuickSwipe()
        }
        view.findViewById<Button>(R.id.btn_add_wait)?.setOnClickListener {
            addQuickWait()
        }
        view.findViewById<Button>(R.id.btn_save)?.setOnClickListener {
            saveCurrentProfile()
        }
        view.findViewById<ImageButton>(R.id.btn_hide)?.setOnClickListener {
            toggleBody()
        }
    }

    private fun onStartClicked() {
        val profile = currentProfile
        if (profile == null) {
            Toast.makeText(this, "No profile loaded", Toast.LENGTH_SHORT).show()
            return
        }
        if (!AutoClickerService.isServiceEnabled()) {
            Toast.makeText(this, "Enable Accessibility Service first", Toast.LENGTH_SHORT).show()
            return
        }

        when (macroRunner.state.value) {
            MacroState.PAUSED -> macroRunner.resume()
            MacroState.IDLE, MacroState.STOPPED, MacroState.ERROR -> {
                macroRunner.start(profile)
                // Start foreground service
                MacroForegroundService.start(this, profile.name)
            }
            else -> { /* already running */ }
        }
    }

    private fun onPauseClicked() {
        if (macroRunner.state.value == MacroState.PAUSED) {
            macroRunner.resume()
        } else {
            macroRunner.pause()
        }
    }

    private fun onStopClicked() {
        macroRunner.stop()
        MacroForegroundService.stop(this)
    }

    private fun addQuickTap() {
        val profile = currentProfile ?: createNewProfile()
        val index = profile.actions.size
        profile.actions.add(
            MacroAction(
                type = ActionType.TAP,
                x = 540f, y = 960f,
                durationMs = 100,
                delayAfterMs = 1000,
                orderIndex = index
            )
        )
        currentProfile = profile
        showPointers()
        Toast.makeText(this, "Tap added (540, 960)", Toast.LENGTH_SHORT).show()
    }

    private fun addQuickSwipe() {
        val profile = currentProfile ?: createNewProfile()
        val index = profile.actions.size
        profile.actions.add(
            MacroAction(
                type = ActionType.SWIPE,
                x = 540f, y = 1200f,
                x2 = 540f, y2 = 400f,
                durationMs = 300,
                delayAfterMs = 1000,
                orderIndex = index
            )
        )
        currentProfile = profile
        showPointers()
        Toast.makeText(this, "Swipe added", Toast.LENGTH_SHORT).show()
    }

    private fun addQuickWait() {
        val profile = currentProfile ?: createNewProfile()
        val index = profile.actions.size
        profile.actions.add(
            MacroAction(
                type = ActionType.WAIT,
                durationMs = 2000,
                delayAfterMs = 0,
                orderIndex = index
            )
        )
        currentProfile = profile
        showPointers()
        Toast.makeText(this, "Wait 2s added", Toast.LENGTH_SHORT).show()
    }

    private fun createNewProfile(): MacroProfile {
        val profile = MacroProfile(name = "Quick Macro")
        currentProfile = profile
        return profile
    }

    private fun saveCurrentProfile() {
        val profile = currentProfile ?: return
        profile.updatedAt = System.currentTimeMillis()
        val storage = ProfileStorage(this)
        storage.saveProfile(profile)
        Toast.makeText(this, "Profile saved: ${profile.name}", Toast.LENGTH_SHORT).show()
    }

    private fun toggleBody() {
        val body = overlayBody ?: return
        body.visibility = if (body.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }

    private fun loadCurrentProfile() {
        val storage = ProfileStorage(this)
        val profiles = storage.getAllProfiles()
        currentProfile = if (profiles.isNotEmpty()) {
            profiles.first()
        } else {
            // Create and save default test profile
            val default = MacroProfile.createDefaultTestProfile()
            storage.saveProfile(default)
            default
        }
        Log.i(TAG, "Loaded profile: ${currentProfile?.name}")
    }

    /**
     * Observe macro runner state changes and update UI.
     * Only updates UI when state actually changes (no continuous redraw).
     */
    private fun observeState() {
        serviceScope.launch {
            macroRunner.state.collectLatest { state ->
                updateStatusUI(state)
            }
        }
    }

    private fun updateStatusUI(state: MacroState) {
        tvStatus?.text = when (state) {
            MacroState.IDLE -> "IDLE"
            MacroState.RUNNING -> "▶ RUNNING L${macroRunner.currentLoop.value}"
            MacroState.PAUSED -> "⏸ PAUSED"
            MacroState.STOPPED -> "⏹ STOPPED"
            MacroState.ERROR -> "⚠ ERROR"
        }

        // Show/hide emergency stop
        btnEmergencyStop?.visibility = if (state == MacroState.RUNNING) View.VISIBLE else View.GONE

        // Update pause button text
        btnPause?.text = if (state == MacroState.PAUSED) "▶" else "⏸"

        // Show/Hide floating target pointers
        if (state == MacroState.RUNNING || state == MacroState.PAUSED) {
            clearPointers()
        } else {
            showPointers()
            tvOcrLog?.visibility = View.GONE
        }

        // Update foreground service notification
        if (state == MacroState.RUNNING) {
            MacroForegroundService.updateNotification(
                this,
                currentProfile?.name ?: "Macro",
                macroRunner.currentLoop.value,
                macroRunner.currentActionIndex.value
            )
        } else if (state == MacroState.STOPPED || state == MacroState.IDLE) {
            MacroForegroundService.stop(this)
        }
    }

    private fun removeOverlay() {
        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to remove overlay: ${e.message}")
            }
        }
        overlayView = null
    }

    private fun showPointers() {
        clearPointers()
        val profile = currentProfile ?: return
        val wm = windowManager ?: return

        for (action in profile.actions) {
            val labelIndex = action.orderIndex + 1
            when (action.type) {
                ActionType.TAP -> {
                    val pointer = PointerView(
                        this, wm, labelIndex.toString(),
                        action.x.toInt(), action.y.toInt(),
                        onClicked = {
                            showEditor(action)
                        },
                        onPositionChanged = { newX, newY ->
                            action.x = newX.toFloat()
                            action.y = newY.toFloat()
                        }
                    )
                    try {
                        wm.addView(pointer, pointer.layoutParamsWm)
                        activePointers.add(pointer)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to add TAP pointer: ${e.message}")
                    }
                }
                ActionType.SWIPE -> {
                    val startPointer = PointerView(
                        this, wm, "S$labelIndex",
                        action.x.toInt(), action.y.toInt(),
                        onClicked = {
                            showEditor(action)
                        },
                        onPositionChanged = { newX, newY ->
                            action.x = newX.toFloat()
                            action.y = newY.toFloat()
                        }
                    )
                    val endPointer = PointerView(
                        this, wm, "E$labelIndex",
                        action.x2.toInt(), action.y2.toInt(),
                        onClicked = {
                            showEditor(action)
                        },
                        onPositionChanged = { newX, newY ->
                            action.x2 = newX.toFloat()
                            action.y2 = newY.toFloat()
                        }
                    )
                    try {
                        wm.addView(startPointer, startPointer.layoutParamsWm)
                        wm.addView(endPointer, endPointer.layoutParamsWm)
                        activePointers.add(startPointer)
                        activePointers.add(endPointer)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to add SWIPE pointers: ${e.message}")
                    }
                }
                ActionType.WAIT -> {
                    // No visual pointer needed for waiting action
                }
            }
        }
    }

    private fun clearPointers() {
        val wm = windowManager ?: return
        for (pointer in activePointers) {
            try {
                wm.removeView(pointer)
            } catch (e: Exception) {
                // Ignore
            }
        }
        activePointers.clear()
    }

    private fun showEditor(action: MacroAction) {
        if (editorView != null) {
            removeEditor()
        }

        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.floating_action_editor, null)
        editorView = view

        val header = view.findViewById<View>(R.id.editor_header)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        header.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(view, params)
                    true
                }
                else -> false
            }
        }

        val tvTitle = view.findViewById<TextView>(R.id.tv_title)
        tvTitle.text = "Cấu hình Hành động #${action.orderIndex + 1} (${action.type.name})"

        val etDuration = view.findViewById<EditText>(R.id.et_duration)
        val etDelay = view.findViewById<EditText>(R.id.et_delay)
        etDuration.setText(action.durationMs.toString())
        etDelay.setText(action.delayAfterMs.toString())

        val spCondition = view.findViewById<Spinner>(R.id.sp_condition_type)
        val condOptions = ConditionType.values().map { 
            when (it) {
                ConditionType.NONE -> "Không điều kiện"
                ConditionType.IMAGE_FOUND -> "Nếu thấy ảnh"
                ConditionType.IMAGE_NOT_FOUND -> "Nếu không thấy ảnh"
                ConditionType.TEXT_NUMBER_GE -> "Nhận diện số >="
            }
        }
        spCondition.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, condOptions)
        spCondition.setSelection(action.conditionType.ordinal)

        val tvRegionSummary = view.findViewById<TextView>(R.id.tv_region_summary)
        val layoutImageCond = view.findViewById<View>(R.id.layout_image_cond_fields)
        val layoutNumberCond = view.findViewById<View>(R.id.layout_number_cond_fields)
        val layoutBranchJumps = view.findViewById<View>(R.id.layout_branch_jumps)

        val etCondValue = view.findViewById<EditText>(R.id.et_condition_value)
        etCondValue.setText(action.conditionValue.toString())

        val cbClickMatchedImage = view.findViewById<CheckBox>(R.id.cb_click_matched_image)
        cbClickMatchedImage.isChecked = action.clickMatchedImage

        val tvImageStatus = view.findViewById<TextView>(R.id.tv_image_status)
        tvImageStatus.text = if (action.templateImageBase64 != null) "Trạng thái ảnh mẫu: ĐÃ CÓ" else "Trạng thái ảnh mẫu: CHƯA CÓ"

        val etRegionL = view.findViewById<EditText>(R.id.et_region_l)
        val etRegionT = view.findViewById<EditText>(R.id.et_region_t)
        val etRegionR = view.findViewById<EditText>(R.id.et_region_r)
        val etRegionB = view.findViewById<EditText>(R.id.et_region_b)
        etRegionL.setText(action.searchRegionLeft.toString())
        etRegionT.setText(action.searchRegionTop.toString())
        etRegionR.setText(action.searchRegionRight.toString())
        etRegionB.setText(action.searchRegionBottom.toString())

        fun updateRegionSummaryText() {
            tvRegionSummary.text = "Khung quét: L:${etRegionL.text}%, T:${etRegionT.text}%, R:${etRegionR.text}%, B:${etRegionB.text}%"
        }
        updateRegionSummaryText()

        val etThreshold = view.findViewById<EditText>(R.id.et_threshold)
        etThreshold.setText(action.similarityThreshold.toString())

        val etTrueJump = view.findViewById<EditText>(R.id.et_true_jump)
        val etFalseJump = view.findViewById<EditText>(R.id.et_false_jump)
        etTrueJump.setText(if (action.onTrueGoToIndex >= 0) (action.onTrueGoToIndex + 1).toString() else "-1")
        etFalseJump.setText(if (action.onFalseGoToIndex >= 0) (action.onFalseGoToIndex + 1).toString() else "-1")

        val btnCrop = view.findViewById<Button>(R.id.btn_crop_template)
        btnCrop.setOnClickListener {
            saveUiToAction(action, etDuration, etDelay, spCondition, etThreshold, etCondValue, cbClickMatchedImage, etTrueJump, etFalseJump)
            startScreenCrop(action, onlySelectRegion = false)
        }

        val btnSelectRegion = view.findViewById<Button>(R.id.btn_select_search_region)
        btnSelectRegion.setOnClickListener {
            saveUiToAction(action, etDuration, etDelay, spCondition, etThreshold, etCondValue, cbClickMatchedImage, etTrueJump, etFalseJump)
            startScreenCrop(action, onlySelectRegion = true)
        }

        fun updateVisibilityBasedOnCondition(cond: ConditionType) {
            when (cond) {
                ConditionType.NONE -> {
                    btnCrop.visibility = View.GONE
                    btnSelectRegion.visibility = View.GONE
                    tvRegionSummary.visibility = View.GONE
                    layoutImageCond.visibility = View.GONE
                    layoutNumberCond.visibility = View.GONE
                    layoutBranchJumps.visibility = View.GONE
                }
                ConditionType.IMAGE_FOUND, ConditionType.IMAGE_NOT_FOUND -> {
                    btnCrop.visibility = View.VISIBLE
                    btnSelectRegion.visibility = View.VISIBLE
                    tvRegionSummary.visibility = View.VISIBLE
                    layoutImageCond.visibility = View.VISIBLE
                    layoutNumberCond.visibility = View.GONE
                    layoutBranchJumps.visibility = View.VISIBLE
                }
                ConditionType.TEXT_NUMBER_GE -> {
                    btnCrop.visibility = View.GONE
                    btnSelectRegion.visibility = View.VISIBLE
                    tvRegionSummary.visibility = View.VISIBLE
                    layoutImageCond.visibility = View.GONE
                    layoutNumberCond.visibility = View.VISIBLE
                    layoutBranchJumps.visibility = View.VISIBLE
                }
            }
        }

        spCondition.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedCond = ConditionType.values()[position]
                updateVisibilityBasedOnCondition(selectedCond)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val btnDelete = view.findViewById<Button>(R.id.btn_delete_action)
        btnDelete.setOnClickListener {
            currentProfile?.actions?.removeAll { it.id == action.id }
            currentProfile?.actions?.forEachIndexed { idx, act -> act.orderIndex = idx }
            saveCurrentProfile()
            showPointers()
            removeEditor()
            Toast.makeText(this, "Đã xóa hành động", Toast.LENGTH_SHORT).show()
        }

        val btnCancel = view.findViewById<Button>(R.id.btn_cancel_edit)
        btnCancel.setOnClickListener {
            removeEditor()
        }

        val btnSave = view.findViewById<Button>(R.id.btn_save_edit)
        btnSave.setOnClickListener {
            saveUiToAction(action, etDuration, etDelay, spCondition, etThreshold, etCondValue, cbClickMatchedImage, etTrueJump, etFalseJump)
            
            // Cập nhật thêm các toạ độ từ EditText ẩn
            action.searchRegionLeft = etRegionL.text.toString().toIntOrNull() ?: action.searchRegionLeft
            action.searchRegionTop = etRegionT.text.toString().toIntOrNull() ?: action.searchRegionTop
            action.searchRegionRight = etRegionR.text.toString().toIntOrNull() ?: action.searchRegionRight
            action.searchRegionBottom = etRegionB.text.toString().toIntOrNull() ?: action.searchRegionBottom

            saveCurrentProfile()
            showPointers()
            removeEditor()
            Toast.makeText(this, "Đã lưu cấu hình", Toast.LENGTH_SHORT).show()
        }

        try {
            windowManager?.addView(view, params)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add editor: ${e.message}", e)
        }
    }

    private fun removeEditor() {
        editorView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                // ignore
            }
        }
        editorView = null
    }

    private fun startScreenCrop(action: MacroAction, onlySelectRegion: Boolean) {
        if (!ScreenCaptureHelper.isAuthorized) {
            Toast.makeText(this, "Vui lòng cấp quyền Chụp màn hình ở màn hình chính trước!", Toast.LENGTH_LONG).show()
            return
        }

        removeEditor()
        removeOverlay()
        clearPointers()

        val checkProjection = ScreenCaptureHelper.startProjection(this)
        if (!checkProjection) {
            Toast.makeText(this, "Không thể khởi động ghi màn hình", Toast.LENGTH_SHORT).show()
            createOverlay()
            showPointers()
            showEditor(action)
            return
        }

        serviceScope.launch {
            delay(400) // Đợi các overlay ẩn hẳn
            val screenBmp = ScreenCaptureHelper.captureScreen()
            if (screenBmp != null) {
                showCropOverlay(screenBmp, action, onlySelectRegion)
            } else {
                Toast.makeText(this@OverlayService, "Không thể chụp màn hình. Hãy thử lại.", Toast.LENGTH_SHORT).show()
                createOverlay()
                showPointers()
                showEditor(action)
            }
        }
    }

    private fun showCropOverlay(screenshot: Bitmap, action: MacroAction, onlySelectRegion: Boolean) {
        val wm = windowManager ?: return

        val cropOverlay = CropOverlayView(
            this, screenshot,
            onCropCompleted = { croppedBmp, left, top, right, bottom ->
                if (onlySelectRegion) {
                    action.searchRegionLeft = left
                    action.searchRegionTop = top
                    action.searchRegionRight = right
                    action.searchRegionBottom = bottom
                    Toast.makeText(this, "Đã lưu vùng nhận diện mới!", Toast.LENGTH_SHORT).show()
                } else {
                    val maxDim = 80
                    val scaledBmp = if (croppedBmp.width > maxDim || croppedBmp.height > maxDim) {
                        val ratio = croppedBmp.width.toFloat() / croppedBmp.height
                        val newW = if (ratio > 1) maxDim else (maxDim * ratio).toInt()
                        val newH = if (ratio > 1) (maxDim / ratio).toInt() else maxDim
                        Bitmap.createScaledBitmap(croppedBmp, newW, newH, true)
                    } else {
                        croppedBmp
                    }

                    val base64 = BitmapMatcher.encodeBitmapToBase64(scaledBmp)
                    action.templateImageBase64 = base64

                    // Tự động tối ưu region ± 10%
                    action.searchRegionLeft = Math.max(0, left - 10)
                    action.searchRegionTop = Math.max(0, top - 10)
                    action.searchRegionRight = Math.min(100, right + 10)
                    action.searchRegionBottom = Math.min(100, bottom + 10)

                    Toast.makeText(this, "Đã lưu ảnh mẫu điều kiện!", Toast.LENGTH_SHORT).show()

                    if (scaledBmp != croppedBmp) {
                        scaledBmp.recycle()
                    }
                }

                croppedBmp.recycle()
                screenshot.recycle()

                removeCropOverlay()

                // Lưu lại profile sau khi cấu hình thay đổi
                saveCurrentProfile()

                // Phục hồi
                createOverlay()
                showPointers()
                showEditor(action)
            },
            onCropCancelled = {
                screenshot.recycle()
                removeCropOverlay()

                // Phục hồi
                createOverlay()
                showPointers()
                showEditor(action)
            }
        )

        cropView = cropOverlay

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        try {
            wm.addView(cropOverlay, params)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add crop view: ${e.message}", e)
            createOverlay()
            showPointers()
            showEditor(action)
        }
    }

    private fun removeCropOverlay() {
        cropView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                // ignore
            }
        }
        cropView = null
    }

    private fun startAsForeground() {
        val channelId = "overlay_service_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Overlay Controller",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Bảng điều khiển Auto Clicker")
            .setContentText("Bảng điều khiển đang chạy nổi trên màn hình")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()

        startForeground(1002, notification)
    }

    fun updateOcrLog(logText: String) {
        serviceScope.launch {
            tvOcrLog?.visibility = View.VISIBLE
            tvOcrLog?.text = logText
        }
    }

    private fun saveUiToAction(
        action: MacroAction,
        etDuration: EditText,
        etDelay: EditText,
        spCondition: Spinner,
        etThreshold: EditText,
        etCondValue: EditText,
        cbClickMatchedImage: CheckBox,
        etTrueJump: EditText,
        etFalseJump: EditText
    ) {
        action.durationMs = etDuration.text.toString().toLongOrNull() ?: action.durationMs
        action.delayAfterMs = etDelay.text.toString().toLongOrNull() ?: action.delayAfterMs
        action.conditionType = ConditionType.values()[spCondition.selectedItemPosition]
        action.similarityThreshold = etThreshold.text.toString().toFloatOrNull() ?: action.similarityThreshold
        action.conditionValue = etCondValue.text.toString().toDoubleOrNull() ?: action.conditionValue
        action.clickMatchedImage = cbClickMatchedImage.isChecked

        val tJump = etTrueJump.text.toString().toIntOrNull() ?: -1
        action.onTrueGoToIndex = if (tJump > 0) tJump - 1 else -1

        val fJump = etFalseJump.text.toString().toIntOrNull() ?: -1
        action.onFalseGoToIndex = if (fJump > 0) fJump - 1 else -1
    }
}

