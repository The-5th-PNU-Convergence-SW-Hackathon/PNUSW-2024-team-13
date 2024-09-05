package com.example.skecctv

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.skecctv.databinding.ActivityMainBinding
import android.os.Build
import androidx.core.app.ActivityCompat
import android.Manifest


class MainActivity : AppCompatActivity() {
    private var isRecording = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val binding= ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        var change = 1
        val camView = binding.cam
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ), 300
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val serviceIntent = Intent(this, MainService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        camView.settings.apply {
            this.setSupportMultipleWindows(false) // 새창 띄우기 허용
            this.setSupportZoom(true) // 화면 확대 허용
            this.loadWithOverviewMode = true // html의 컨텐츠가 웹뷰보다 클 경우 스크린 크기에 맞게 조정
            this.useWideViewPort = true // html의 viewport 메타 태그 지원
            this.builtInZoomControls = false // 화면 확대/축소 허용
            this.displayZoomControls = false
            this.layoutAlgorithm = WebSettings.LayoutAlgorithm.SINGLE_COLUMN // 컨텐츠 사이즈 맞추기
            this.cacheMode = WebSettings.LOAD_NO_CACHE // 브라우저 캐쉬 허용
            this.domStorageEnabled = true // 로컬 저장 허용
            this.databaseEnabled = true
            this.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
        camView.webViewClient = WebViewClient()

        camView.loadUrl("http://192.168.0.18:5000/video_feed")

        binding.change.setOnClickListener {
            change *= -1
            if(change == 1) {
                Toast.makeText(this, "privacy mode off", Toast.LENGTH_SHORT).show()
                camView.loadUrl("http://192.168.0.18:5000/video_feed")
            }
            else {
                Toast.makeText(this, "privacy mode on", Toast.LENGTH_SHORT).show()
                camView.loadUrl("http://192.168.0.18:5000/skeleton_feed")
            }
        }

        binding.record.setOnClickListener {
            isRecording = !isRecording
            if (isRecording) {
                MediaProjectionController.screenRecording(this) {
                    MainApplication.updateNotification(this, "Recording", "Start")
                }
                Toast.makeText(this, "record start", Toast.LENGTH_SHORT).show()
            } else {
                MediaProjectionController.stopRecording(this) {
                    MainApplication.updateNotification(this, "Recording", "Stop")
                }
                Toast.makeText(this, "record end", Toast.LENGTH_SHORT).show()
            }
        }

        binding.emer.setOnClickListener {
            Toast.makeText(this, "call action", Toast.LENGTH_SHORT).show()
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:01064173891"))
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            }
            else {
                Toast.makeText(this, "call failed", Toast.LENGTH_SHORT).show()
            }
        }

        //setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val serviceIntent = Intent(this, MainService::class.java)
        stopService(serviceIntent)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            MediaProjectionController.mediaScreenRecord -> {
                MediaProjectionController.getMediaProjectionRecord(this, resultCode, data)
            }
        }
    }
}