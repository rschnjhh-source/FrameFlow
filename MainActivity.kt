package com.frameflow.app

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.*
import java.io.*
import java.net.URL
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var chooseButton: Button
    private lateinit var startButton: Button
    private lateinit var fpsGroup: RadioGroup
    private lateinit var smoothGroup: RadioGroup
    private lateinit var resolution: Spinner
    private var input: Uri? = null
    private var targetFps = 60
    private var running = false

    private val ffmpegUrl = "https://github.com/hzw1199/Android-FFmpeg-Prebuilt/raw/main/ffmpeg-8.1.1/bin/ffmpeg"
    private val pickCode = 7

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "FrameFlow"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 44, 32, 28)
        }

        val titleView = TextView(this).apply {
            text = "FrameFlow"
            textSize = 30f
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val subtitle = TextView(this).apply {
            text = "محوّل الفريمات والسلاسة"
            textSize = 18f
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 8, 0, 24)
        }
        root.addView(titleView)
        root.addView(subtitle)

        chooseButton = Button(this).apply {
            text = "🎬  اختيار الفيديو"
            setOnClickListener { chooseVideo() }
        }
        root.addView(chooseButton)

        root.addView(label("معدل الإطارات النهائي"))
        fpsGroup = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL; gravity = Gravity.CENTER }
        val r60 = RadioButton(this).apply { text = "60 FPS"; id = 600; isChecked = true }
        val r120 = RadioButton(this).apply { text = "120 FPS"; id = 1200 }
        fpsGroup.addView(r60); fpsGroup.addView(r120)
        fpsGroup.setOnCheckedChangeListener { _, id -> targetFps = if (id == 1200) 120 else 60 }
        root.addView(fpsGroup)

        root.addView(label("مستوى السلاسة"))
        smoothGroup = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL; gravity = Gravity.CENTER }
        val normal = RadioButton(this).apply { text = "عادي"; id = 1; isChecked = true }
        val strong = RadioButton(this).apply { text = "قوي"; id = 2 }
        val max = RadioButton(this).apply { text = "أقصى"; id = 3 }
        smoothGroup.addView(normal); smoothGroup.addView(strong); smoothGroup.addView(max)
        root.addView(smoothGroup)

        root.addView(label("الدقة"))
        resolution = Spinner(this)
        resolution.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            arrayOf("نفس دقة الفيديو الأصلي", "1080p", "720p"))
        root.addView(resolution)

        startButton = Button(this).apply {
            text = "⚡  ابدأ التحويل"
            isEnabled = false
            setOnClickListener { convert() }
        }
        root.addView(startButton)

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; progress = 0
        }
        root.addView(progress)

        status = TextView(this).apply {
            text = "جاهز — اختر فيديو للبدء"
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, 18, 0, 0)
        }
        root.addView(status)

        val note = TextView(this).apply {
            text = "ملاحظة: 120 FPS هنا يتم إنشاؤه باستيفاء فريمات بالحركة، وليس مجرد تغيير رقم الـFPS."
            textSize = 12f
            setPadding(0, 22, 0, 0)
        }
        root.addView(note)

        setContentView(root)
        thread { ensureFfmpeg() }
    }

    private fun label(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 16f
        setPadding(0, 20, 0, 6)
    }

    private fun chooseVideo() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "video/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }, pickCode)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == pickCode && resultCode == RESULT_OK) {
            input = data?.data
            try { input?.let { contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } } catch (_: Exception) {}
            status.text = "تم اختيار الفيديو ✓"
            updateStartState()
        }
    }

    private fun updateStartState() {
        startButton.isEnabled = input != null && File(filesDir, "ffmpeg").canExecute() && !running
    }

    private fun ensureFfmpeg() {
        val file = File(filesDir, "ffmpeg")
        if (file.exists() && file.length() > 1_000_000) {
            runOnUiThread { status.text = "محرك التحويل جاهز ✓"; updateStartState() }
            return
        }
        runOnUiThread { status.text = "أول تشغيل فقط: جاري تجهيز محرك التحويل…" }
        try {
            URL(ffmpegUrl).openStream().use { input -> FileOutputStream(file).use { output -> input.copyTo(output) } }
            file.setExecutable(true)
            runOnUiThread { status.text = "المحرك جاهز ✓"; updateStartState() }
        } catch (e: Exception) {
            runOnUiThread { status.text = "تعذر تجهيز المحرك. تحقق من الإنترنت ثم أعد فتح التطبيق." }
        }
    }

    private fun convert() {
        val uri = input ?: return
        val ffmpeg = File(filesDir, "ffmpeg")
        if (!ffmpeg.exists()) return

        running = true
        chooseButton.isEnabled = false
        startButton.isEnabled = false
        progress.progress = 0
        status.text = "جاري التحويل…"

        thread {
            val inputFile = File(cacheDir, "frameflow_input_${System.currentTimeMillis()}.mp4")
            try {
                contentResolver.openInputStream(uri)?.use { source -> FileOutputStream(inputFile).use { dest -> source.copyTo(dest) } }
                    ?: throw IOException("تعذر فتح الفيديو")

                val outputFile = File(cacheDir, "frameflow_output_${System.currentTimeMillis()}.mp4")
                val quality = when (smoothGroup.checkedRadioButtonId) {
                    2 -> "minterpolate=fps=$targetFps:mi_mode=mci:mc_mode=aobmc:me_mode=bidir:vsbmc=1"
                    3 -> "minterpolate=fps=$targetFps:mi_mode=mci:mc_mode=aobmc:me_mode=bidir:vsbmc=1:scd=fdiff"
                    else -> "minterpolate=fps=$targetFps:mi_mode=mci:mc_mode=aobmc:me_mode=bidir"
                }

                val filters = ArrayList<String>()
                filters.add(quality)
                when (resolution.selectedItemPosition) {
                    1 -> filters.add("scale=-2:1080")
                    2 -> filters.add("scale=-2:720")
                }

                val cmd = arrayOf(
                    ffmpeg.absolutePath, "-y", "-i", inputFile.absolutePath,
                    "-vf", filters.joinToString(","),
                    "-c:v", "h264_mediacodec", "-b:v", "12M",
                    "-c:a", "aac", "-b:a", "192k", "-movflags", "+faststart",
                    outputFile.absolutePath
                )

                val process = ProcessBuilder(*cmd).redirectErrorStream(true).start()
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val text = line ?: continue
                    val percent = parseProgress(text)
                    if (percent >= 0) runOnUiThread { progress.progress = percent }
                    if (text.contains("time=")) runOnUiThread { status.text = "جاري إنشاء فيديو $targetFps FPS… $percent%" }
                }
                val code = process.waitFor()
                if (code == 0 && outputFile.exists()) {
                    val savedUri = saveToGallery(outputFile)
                    runOnUiThread {
                        progress.progress = 100
                        status.text = if (savedUri != null) "تم الحفظ بالاستوديو ✓\nFrameFlow_${targetFps}FPS.mp4" else "تم التحويل ✓ لكن تعذر الحفظ بالاستوديو"
                        running = false; chooseButton.isEnabled = true; updateStartState()
                    }
                } else {
                    throw IOException("رمز التحويل: $code")
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "صار خطأ أثناء التحويل:\n${e.message ?: "خطأ غير معروف"}"
                    running = false; chooseButton.isEnabled = true; updateStartState()
                }
            } finally {
                inputFile.delete()
            }
        }
    }

    private fun parseProgress(line: String): Int {
        val durationMatch = Regex("Duration: (\\d+):(\\d+):(\\d+\\.?\\d*)").find(line)
        if (durationMatch != null) return progress.progress
        val timeMatch = Regex("time=(\\d+):(\\d+):(\\d+\\.?\\d*)").find(line) ?: return -1
        val h = timeMatch.groupValues[1].toDoubleOrNull() ?: 0.0
        val m = timeMatch.groupValues[2].toDoubleOrNull() ?: 0.0
        val s = timeMatch.groupValues[3].toDoubleOrNull() ?: 0.0
        // Without reliable duration parsing from FFmpeg logs, show smooth activity rather than a false exact percentage.
        return minOf(99, maxOf(1, ((h * 3600 + m * 60 + s) * 2).toInt()))
    }

    private fun saveToGallery(file: File): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "FrameFlow_${targetFps}FPS_${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/FrameFlow")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        return try {
            contentResolver.openOutputStream(uri)?.use { out -> FileInputStream(file).use { input -> input.copyTo(out) } }
            values.clear(); values.put(MediaStore.Video.Media.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)
            uri
        } catch (e: Exception) {
            contentResolver.delete(uri, null, null); null
        }
    }
}
