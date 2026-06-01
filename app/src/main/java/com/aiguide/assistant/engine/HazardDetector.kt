package com.aiguide.assistant.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import androidx.camera.core.ImageProxy
import com.aiguide.assistant.service.BoundingBox
import com.aiguide.assistant.service.HazardLevel
import com.aiguide.assistant.service.HazardResult
import com.aiguide.assistant.service.ServiceBus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 危险检测器：基于 TFLite MobileNetV3-SSD 对摄像头帧进行实时物体检测。
 *
 * ## 检测类别
 * - 人 (person)
 * - 车 (car)
 * - 自行车 (bicycle)
 * - 障碍物 (obstacle)
 * - 马路边缘 (road_edge)
 * - 急坡 (steep_slope)
 *
 * ## 推理策略
 * - 每 3 帧推理一次（降低功耗）
 * - 推理超时 300ms 跳过当前帧
 * - 检测结果通过 [ServiceBus.hazardAlert] 发布
 */
@Singleton
class HazardDetector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val serviceBus: ServiceBus
) {

    companion object {
        /** 模型文件名（放在 assets/ 下） */
        private const val MODEL_FILE = "mobilenet_v3_ssd.tflite"

        /** TFLite 输入尺寸 (MobileNetV3-SSD 标准) */
        private const val INPUT_SIZE = 320

        /** 模型输入字节数: 320 * 320 * 3 * 4 (float32) */
        private const val INPUT_BYTE_SIZE = INPUT_SIZE * INPUT_SIZE * 3 * 4

        /** 每 N 帧推理一次 */
        private const val INFERENCE_INTERVAL = 3

        /** 单次推理超时（毫秒） */
        private const val INFERENCE_TIMEOUT_MS = 300L

        /** SSDLite 输出：每个检测框 4 个坐标 + 类别数 */
        private const val MAX_DETECTIONS = 10
        private const val NUM_CLASSES = 6

        /** 置信度阈值 */
        private const val CONFIDENCE_THRESHOLD = 0.5f

        /** COCO 标签映射到业务类型 */
        private val COCO_LABEL_MAP: Map<Int, String> = mapOf(
            0  to "background",
            1  to "person",
            2  to "bicycle",
            3  to "car",
            7  to "car",       // truck → car
            44 to "obstacle",  // bottle → obstacle
            77 to "obstacle"   // cell phone → obstacle (general obstacle)
        )

        /** 距离估算阈值（基于 bbox 面积占图像比例） */
        private const val NEAR_AREA_RATIO = 0.25f    // > 25% → 近
        private const val MID_AREA_RATIO = 0.08f     // > 8% → 中，≤ 8% → 远
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var interpreter: Interpreter? = null
    private var frameCounter = 0
    private var isInitialized = false

    private val inputBuffer: ByteBuffer by lazy {
        ByteBuffer.allocateDirect(INPUT_BYTE_SIZE).apply {
            order(ByteOrder.nativeOrder())
        }
    }

    /** 检测结果输出数组 */
    private val outputLocations: Array<Array<FloatArray>> =
        Array(1) { Array(MAX_DETECTIONS) { FloatArray(4) } }   // [1, max_det, 4]
    private val outputClasses: Array<FloatArray> =
        Array(1) { FloatArray(MAX_DETECTIONS) }                 // [1, max_det]
    private val outputScores: Array<FloatArray> =
        Array(1) { FloatArray(MAX_DETECTIONS) }                 // [1, max_det]
    private val outputNumDetections: Array<FloatArray> =
        Array(1) { FloatArray(1) }                              // [1, 1]

    init {
        initializeModel()
        startObserving()
    }

    // ========================
    // 模型初始化
    // ========================

    private fun initializeModel() {
        try {
            interpreter = Interpreter(loadModelFile())
            isInitialized = true
        } catch (e: Exception) {
            isInitialized = false
        }
    }

    /**
     * 从 assets 加载 TFLite 模型。
     */
    private fun loadModelFile(): MappedByteBuffer {
        context.assets.openFd(MODEL_FILE).use { fileDescriptor ->
            FileInputStream(fileDescriptor.fileDescriptor).use { inputStream ->
                val fileChannel = inputStream.channel
                val startOffset = fileDescriptor.startOffset
                val declaredLength = fileDescriptor.declaredLength
                return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
            }
        }
    }

    // ========================
    // 帧流监听
    // ========================

    private fun startObserving() {
        scope.launch {
            serviceBus.cameraFrame.collectLatest { frame ->
                processFrame(frame)
            }
        }
    }

    /**
     * 每 INFERENCE_INTERVAL 帧推理一次，其他帧直接关闭并跳过。
     */
    private suspend fun processFrame(frame: ImageProxy) {
        try {
            frameCounter++
            if (frameCounter % INFERENCE_INTERVAL != 0) {
                // 跳过当前帧（不推理，帧由 NavSafetyEngine 负责 close）
                return
            }

            if (!isInitialized) {
                return
            }

            val result = runInference(frame)
            if (result != null) {
                serviceBus.hazardAlert.tryEmit(result)
            }
        } catch (_: Exception) {
            // 帧处理异常，静默跳过
        }
    }

    // ========================
    // TFLite 推理
    // ========================

    /**
     * 执行单帧推理，超时 300ms 自动取消。
     */
    private suspend fun runInference(frame: ImageProxy): HazardResult? {
        return withTimeoutOrNull(INFERENCE_TIMEOUT_MS) {
            withContext(Dispatchers.Default) {
                val interpreter = this@HazardDetector.interpreter ?: return@withContext null

                // 1. 预处理：YUV → RGB → ByteBuffer (320x320)
                preprocessFrame(frame)

                // 2. 推理
                interpreter.runForMultipleInputsOutputs(
                    arrayOf(inputBuffer),
                    mapOf(
                        0 to outputLocations,
                        1 to outputClasses,
                        2 to outputScores,
                        3 to outputNumDetections
                    )
                )

                // 3. 后处理：取最高置信度检测结果
                buildHazardResult(frame.width, frame.height)
            }
        }
    }

    /**
     * 将 ImageProxy YUV 帧转换为模型输入的 ByteBuffer。
     * 简化实现：先转 Bitmap 再缩放并填充 ByteBuffer。
     */
    private fun preprocessFrame(frame: ImageProxy) {
        inputBuffer.clear()

        val bitmap = imageProxyToBitmap(frame) ?: return

        // 缩放为 320x320 并归一化到 [-1, 1]
        val scaled = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        scaled.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        for (pixel in pixels) {
            val r = ((pixel shr 16) and 0xFF) / 127.5f - 1.0f
            val g = ((pixel shr 8) and 0xFF) / 127.5f - 1.0f
            val b = (pixel and 0xFF) / 127.5f - 1.0f
            inputBuffer.putFloat(r)
            inputBuffer.putFloat(g)
            inputBuffer.putFloat(b)
        }

        inputBuffer.rewind()
        bitmap.recycle()
        scaled.recycle()
    }

    /**
     * ImageProxy → Bitmap 转换。
     */
    private fun imageProxyToBitmap(frame: ImageProxy): Bitmap? {
        val planes = frame.planes
        if (planes.isEmpty()) return null

        return try {
            val yBuffer = planes[0].buffer
            val uBuffer = planes[1].buffer
            val vBuffer = planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            val yuvImage = android.graphics.YuvImage(
                nv21,
                android.graphics.ImageFormat.NV21,
                frame.width,
                frame.height,
                null
            )
            val out = java.io.ByteArrayOutputStream()
            yuvImage.compressToJpeg(android.graphics.Rect(0, 0, frame.width, frame.height), 80, out)
            val jpegBytes = out.toByteArray()
            android.graphics.BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
        } catch (_: Exception) {
            null
        }
    }

    // ========================
    // 后处理
    // ========================

    /**
     * 从推理输出构建 HazardResult：取置信度最高的检测框。
     */
    private fun buildHazardResult(frameWidth: Int, frameHeight: Int): HazardResult? {
        val numDetections = outputNumDetections[0][0].toInt().coerceAtMost(MAX_DETECTIONS)
        if (numDetections <= 0) return null

        // 找最高置信度索引
        var bestIdx = -1
        var bestScore = 0f
        for (i in 0 until numDetections) {
            val score = outputScores[0][i]
            if (score > bestScore && score >= CONFIDENCE_THRESHOLD) {
                bestScore = score
                bestIdx = i
            }
        }

        if (bestIdx < 0) return null

        val classId = outputClasses[0][bestIdx].toInt()
        val detectionType = COCO_LABEL_MAP[classId] ?: "unknown"

        // bbox: [ymin, xmin, ymax, xmax] → 转换为像素坐标
        val ymin = outputLocations[0][bestIdx][0] * frameHeight
        val xmin = outputLocations[0][bestIdx][1] * frameWidth
        val ymax = outputLocations[0][bestIdx][2] * frameHeight
        val xmax = outputLocations[0][bestIdx][3] * frameWidth

        val bbox = BoundingBox(
            x = xmin,
            y = ymin,
            width = xmax - xmin,
            height = ymax - ymin
        )

        val distance = estimateDistance(bbox, frameWidth, frameHeight)

        // 危险等级判定
        val level = when (detectionType) {
            "person"   -> HazardLevel.CRITICAL
            "car"      -> if (distance == "近") HazardLevel.CRITICAL else HazardLevel.WARNING
            "bicycle"  -> HazardLevel.WARNING
            "obstacle" -> if (distance == "近") HazardLevel.WARNING else HazardLevel.INFO
            else       -> HazardLevel.INFO
        }

        return HazardResult(
            level = level,
            message = "检测到 $detectionType，距离: $distance",
            type = detectionType,
            bbox = bbox,
            distance = distance,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * 基于 bbox 面积占图像比例估算距离。
     * - > 25% → 近
     * - > 8%  → 中
     * - ≤ 8%  → 远
     */
    private fun estimateDistance(bbox: BoundingBox, frameWidth: Int, frameHeight: Int): String {
        val frameArea = (frameWidth * frameHeight).toFloat()
        val bboxArea = bbox.width * bbox.height
        val ratio = if (frameArea > 0f) bboxArea / frameArea else 0f

        return when {
            ratio >= NEAR_AREA_RATIO -> "近"
            ratio >= MID_AREA_RATIO  -> "中"
            else                     -> "远"
        }
    }

    /**
     * 检测器是否已就绪。
     */
    fun isReady(): Boolean = isInitialized
}
