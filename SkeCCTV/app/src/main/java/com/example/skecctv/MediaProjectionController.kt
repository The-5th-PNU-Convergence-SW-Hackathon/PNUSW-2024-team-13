package com.example.skecctv

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import io.reactivex.rxjava3.functions.Action
import java.text.SimpleDateFormat
import java.util.*

object MediaProjectionController {

    const val mediaScreenRecord = 101

    private var projectionManager: MediaProjectionManager? = null
    private var projectionRecord: MediaProjection? = null
    private var virtualDisplayRecord: VirtualDisplay? = null
    private var mediaRecorder: MediaRecorder? = null

    private var width = 0
    private var height = 0

    private var prevIntentData: Intent? = null
    private var prevResultCode = 0

    private var startRecordCompletedAction: Action? = null

    private var fileDescriptor: ParcelFileDescriptor? = null

    var isRecording = MutableLiveData(false)

    fun screenRecording(activity: Activity, action: Action?) {
        startRecordCompletedAction = action

        if (prevIntentData != null) {
            // If you have received permission even once, proceed without requesting permission
            getMediaProjectionRecord(activity, prevResultCode, prevIntentData)
        } else {
            // permission request
            Log.e("flg", "permission")
            projectionManager = activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            activity.startActivityForResult(projectionManager?.createScreenCaptureIntent(), mediaScreenRecord)
        }
    }

    fun getMediaProjectionRecord(activity: Activity, resultCode: Int, intentData: Intent?) {
        projectionRecord = projectionManager?.getMediaProjection(resultCode, intentData!!)

        if (projectionRecord != null) {
            prevIntentData = intentData
            prevResultCode = resultCode

            // Create virtualDisplay
            createVirtualDisplayRecord(activity)

            // MediaRecorder Start
            if (virtualDisplayRecord != null) {
                Log.e("flg", "record started!")
                startRecording(activity)
            }
            else {
                Log.e("bug", "virtual is null")
            }
        }
        else {
            Log.e("bug", "projection is null")
        }
    }

    private fun createVirtualDisplayRecord(activity: Activity) {
        val metrics = activity.resources?.displayMetrics!!
        val density = metrics.densityDpi
        val flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR

        width = metrics.widthPixels
        height = metrics.heightPixels

        // MediaRecorder Prepare
        mediaRecorder = MediaRecorder()
        prepareRecording(activity)

        // MediaRecorder Surface rendering
        virtualDisplayRecord = projectionRecord?.createVirtualDisplay(
            "screenRecord", width, height, density, flags,
            mediaRecorder?.surface, null, null
        )
    }

    fun createFile(activity: Activity): ParcelFileDescriptor? {

        val contentValues = ContentValues()
        val currentTime = Date(System.currentTimeMillis())
        val currentTimeStamp = SimpleDateFormat("yyyyMMddHHmmss", Locale.KOREA).format(currentTime)

        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, "Recorded$currentTimeStamp.mp4")
        contentValues.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        contentValues.put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis())

        val contentResolver = activity.contentResolver
        val collectionUri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)

        return contentResolver.openFileDescriptor(collectionUri!!, "w")
    }

    private fun prepareRecording(activity: Activity) {

        fileDescriptor = createFile(activity) // Create file to save

        mediaRecorder?.apply {

            setOutputFile(fileDescriptor?.fileDescriptor)
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setVideoEncodingBitRate(5 * 1024 * 1000)
            setVideoFrameRate(30)
            setVideoEncoder(MediaRecorder.VideoEncoder.DEFAULT)
            setVideoSize(width, height)
            prepare()
        }
    }

    private fun startRecording(activity: Activity) {
        if (mediaRecorder != null) {
            try {
                mediaRecorder?.start()

                isRecording.value = true

                startRecordCompletedAction?.run()

                Toast.makeText(activity, "screenRecording...", Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
                System.err.println("[MediaProjection] start error : $e")
            }
        }
        else {
            Log.e("bug", "no media recorder at start")
        }
    }

    fun getFilePathFromUri(context: Context, uri: Uri): String? {
        val projection = arrayOf(MediaStore.Video.Media.DATA)
        val cursor: Cursor? = context.contentResolver.query(uri, projection, null, null, null)
        val column_index = cursor?.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
        cursor?.moveToFirst()
        return cursor?.getString(column_index ?: -1)
    }

    // 파일 경로를 로그에 출력하는 함수
    fun logFilePath(activity: Activity) {
        val uri = MediaProjectionController.createFile(activity)?.let {
            val collectionUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(MediaStore.Video.Media._ID)
            val selection = "${MediaStore.Video.Media.DATA} = ?"
            val selectionArgs = arrayOf(it.fileDescriptor.toString())
            val cursor = activity.contentResolver.query(collectionUri, projection, selection, selectionArgs, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
                    Uri.withAppendedPath(collectionUri, id.toString())
                } else {
                    null
                }
            }
        }
        uri?.let {
            val filePath = getFilePathFromUri(activity, it)
            Log.e("where", filePath!!)
        }
    }


    fun stopRecording(activity: Activity, action: Action?) {
        if (mediaRecorder != null) {
            try {
                mediaRecorder?.stop()
                mediaRecorder?.reset()

                virtualDisplayRecord?.release()

                projectionRecord?.stop()

                isRecording.value = false

                fileDescriptor?.close()

                action?.run()

                // 파일 경로를 로그에 출력
                logFilePath(activity)

                Toast.makeText(activity, "stopRecording, File has been saved.", Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
                System.err.println("[MediaProjection] start error : $e")
            }
        }
        else {
            Log.e("bug", "no media recorder at stop")
        }
    }
}