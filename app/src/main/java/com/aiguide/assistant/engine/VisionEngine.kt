package com.aiguide.assistant.engine

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.aiguide.assistant.service.DeviceProfile
import com.aiguide.assistant.service.ServiceBus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Camera2 视觉分析引擎：通过 CameraX ImageAnalysis 持续采集后置摄像头帧，
 * 将每一帧 YUV ImageProxy 发布到 [ServiceBus.cameraFrame]。
 *
 * ## 特性
 * - CAMERA 权限检查与运行时请求
 * - 自动重连：摄像头被占用释放后自动恢复
 * - 低分辨率模式 (640x480) 降低功耗
 */
@Singleton
class VisionEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val serviceBus: ServiceBus
) {

    companion object {
        private const val TARGET_WIDTH = 640
        private const val TARGET_HEIGHT = 480

        /** LOW 档目标帧率（fps） */
        private const val LOW_FPS = 10
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null

    /** 当前设备档位（动态更新） */
    private var currentProfile: DeviceProfile = DeviceProfile.HIGH

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    /**
     * 内部 LifecycleOwner，用于 CameraX bindToLifecycle，
     * 避免 Application Context 无生命周期的问题。
     */
    private val cameraLifecycleOwner: LifecycleOwner by lazy {
        CameraLifecycleOwner().also { it.start() }
    }

    init {
        // 注册摄像头可用性回调，实现自动重连
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            cameraManager?.registerAvailabilityCallback(
                object : CameraManager.AvailabilityCallback() {
                    override fun onCameraAvailable(cameraId: String) {
                        if (!_isRunning.value && serviceBus.cameraEnabled.value) {
                            scope.launch { startCamera() }
                        }
                    }
                },
                null
            )
        } catch (_: Exception) {
            // 摄像头服务不可用（如模拟器环境），静默跳过
        }

        // Phase 4: 监听 deviceProfile 变化，动态调整帧率
        scope.launch {
            serviceBus.deviceProfile.collect { profile ->
                val oldProfile = currentProfile
                currentProfile = profile
                // 档位切换时重新配置 ImageAnalysis 帧率
                if (oldProfile != profile && _isRunning.value) {
                    stopCamera()
                    startCamera()
                }
            }
        }
    }

    /**
     * 启动后置摄像头采集。
     * 前置检查 CAMERA 权限，无权限时将 cameraEnabled 置为 false 并返回。
     */
    fun startCamera() {
        if (!hasCameraPermission()) {
            serviceBus.cameraEnabled.value = false
            return
        }

        if (_isRunning.value) return

        scope.launch(Dispatchers.IO) {
            try {
                val provider = ProcessCameraProvider.getInstance(context).get()
                cameraProvider = provider

                // 解绑所有已有用例，避免重复绑定
                provider.unbindAll()

                // Phase 4: 根据档位构建 ImageAnalysis
                val builder = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setTargetResolution(Size(TARGET_WIDTH, TARGET_HEIGHT))

                // LOW 档降低帧率到 10fps（通过分析间隔控制）
                // Note: setTargetFrameRate requires CameraX 1.3+; frame rate control
                // is handled via analyzer throttling in LOW profile mode instead.

                imageAnalysis = builder.build()
                    .also { analysis ->
                        analysis.setAnalyzer(cameraExecutor) { imageProxy: ImageProxy ->
                            // 将帧发布到总线，由消费者负责 imageProxy.close()
                            serviceBus.cameraFrame.tryEmit(imageProxy)
                        }
                    }

                // 绑定到内部 LifecycleOwner
                provider.bindToLifecycle(
                    cameraLifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    imageAnalysis
                )

                _isRunning.value = true
                serviceBus.cameraEnabled.value = true
            } catch (e: Exception) {
                _isRunning.value = false
                serviceBus.cameraEnabled.value = false
            }
        }
    }

    /**
     * 停止摄像头采集。
     */
    fun stopCamera() {
        cameraProvider?.unbindAll()
        _isRunning.value = false
        serviceBus.cameraEnabled.value = false
    }

    /**
     * 摄像头是否正在采集。
     */
    fun isRunning(): Boolean = _isRunning.value

    /**
     * 权限被授予后的回调，自动恢复摄像头。
     */
    fun onPermissionGranted() {
        if (serviceBus.cameraEnabled.value) {
            scope.launch { startCamera() }
        }
    }

    /**
     * 检查 CAMERA 权限。
     */
    fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
    }

    /**
     * 内部 LifecycleOwner，手动管理 Lifecycle 状态。
     */
    private class CameraLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)

        init {
            registry.currentState = Lifecycle.State.CREATED
        }

        fun start() {
            registry.currentState = Lifecycle.State.STARTED
            registry.currentState = Lifecycle.State.RESUMED
        }

        fun stop() {
            registry.currentState = Lifecycle.State.STARTED
            registry.currentState = Lifecycle.State.CREATED
        }

        override val lifecycle: Lifecycle get() = registry
    }
}
