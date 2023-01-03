package com.peoplellink.p2psdk

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.*
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.peoplellink.p2psdk.InstaSDK.audioMute
import com.peoplellink.p2psdk.InstaSDK.audioUnMute
import com.peoplellink.p2psdk.InstaSDK.connectServer
import com.peoplellink.p2psdk.InstaSDK.disconnect
import com.peoplellink.p2psdk.InstaSDK.getSentBytesStats
import com.peoplellink.p2psdk.InstaSDK.initialise
import com.peoplellink.p2psdk.InstaSDK.instaListener
import com.peoplellink.p2psdk.InstaSDK.leave
import com.peoplellink.p2psdk.InstaSDK.makeCall
import com.peoplellink.p2psdk.InstaSDK.setVideoResolution
import com.peoplellink.p2psdk.InstaSDK.startCall
import com.peoplellink.p2psdk.InstaSDK.switchCamera
import com.peoplellink.p2psdk.InstaSDK.videoMute
import com.peoplellink.p2psdk.InstaSDK.videoUnMute
import com.peoplellink.p2psdk.databinding.ActivityMainBinding
import java.lang.reflect.Method
import java.util.*


class MainActivity : AppCompatActivity(), InstaListener, View.OnClickListener {

    private lateinit var binding: ActivityMainBinding
    private var isAudioMuted: Boolean = false

    private var selfId: String? = null
    private var senderName: String? = null
    private var encounterID: String? = null
    private var trueOrFalse: String? = null
    private var audioManager: AudioManager? = null

    private val resolutionArray = arrayOf("240p", "360p", "480p", "720p", "1080p", "4k")
    private var isVideoMuted: Boolean = false
    private var audioDeviceInfoArray: ArrayList<AudioDeviceInfo> = arrayListOf()
    private val RECORDER_SAMPLERATE = 8000
    private val RECORDER_CHANNELS: Int = AudioFormat.CHANNEL_IN_MONO
    private val RECORDER_AUDIO_ENCODING: Int = AudioFormat.ENCODING_PCM_16BIT
    private var recorder: AudioRecord? = null

    private var bufferElements2Rec = 1024 // want to play 2048 (2K) since 2 bytes we use only 1024

    private var bytesPerElement = 2

    val audioInputDevicesList = ArrayList<String>()
    val audioDevicesList = ArrayList<String>()
    var bluetoothName = ""
    private lateinit var mHeadsetBroadcastReceiver: BTReceiver
    var isSocketConnected = false

    private val myTimer = Timer()

    @RequiresApi(Build.VERSION_CODES.P)
    override fun onCreate(savedInstanceState: Bundle?) {
        this.supportActionBar!!.hide()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        HeadsetReceiver.instance.register(this)
        mHeadsetBroadcastReceiver = BTReceiver()
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        audioManager!!.isSpeakerphoneOn = true
        audioManager?.mode = AudioManager.MODE_IN_CALL

        selfId = intent.getStringExtra("userId")
        senderName = intent.getStringExtra("senderName")
        encounterID = intent.getStringExtra("encounterID")
        trueOrFalse = intent.getStringExtra("trueOrFalse")
        val remoteUser = intent.getStringExtra("remoteUser")

        Log.d("TAG", "onCreate: $selfId $senderName $encounterID $trueOrFalse $remoteUser")



        onConnectSucceed()

        initialise(
            applicationContext, binding.LocalSurfaceView, binding.RemoteSurfaceView,
            localMirror = false,
            remoteMirror = true
        )
        instaListener(this)
        binding.call.setOnClickListener(this)

        binding.end.setOnClickListener {
            myTimer.cancel()
//            if (repeatFun().isActive) repeatFun().cancel()
            if (isSocketConnected) disconnect()
            else finish()
        }
        if (trueOrFalse == "true") {
            startCall(selfId, remoteUser)
        }

        binding.switchCamera.setOnClickListener { switchCamera() }

        binding.muteVideo.setOnClickListener { muteUnMuteVideo() }

        binding.muteAudio.setOnClickListener { muteUnMuteeAudio() }
//        binding.anwser.setOnClickListener { answerCall(selfId!!) }

//        binding.tiltVideo.setOnClickListener {
//            isVideoTilted = !isVideoTilted
//            tiltVideo(isVideoTilted)
//        }

        audioDevicesList.add("Default - Phone Speaker (Build-in)")
        audioDevicesList.add("Earphone Speaker(Build-in)")

        //Check if bluetooth connected
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        if (bluetoothManager.adapter.bondedDevices.size > 0) {
            for (d in bluetoothManager.adapter.bondedDevices) {
                if (isConnected(d)) {
                    val deviceName = d.name
                    bluetoothName = deviceName
                    audioDevicesList.add(deviceName)
                }
            }
        }

        HeadsetReceiver.instance.callback = object : HeadsetReceiver.HeadsetReceiverCallback {
            override fun onHeadsetConnected() {
                audioDevicesList.add("Wired Headset")
                if (!audioInputDevicesList.contains("Wired Headset - Microphone"))
                    audioInputDevicesList.add("Wired Headset - Microphone")

            }

            override fun onHeadsetDisconnected() {
                audioDevicesList.remove("Wired Headset")
                audioInputDevicesList.remove("Wired Headset - Microphone")
            }
        }
        BTReceiver.instance.register(this)

        BTReceiver.instance.callback = object : BTReceiver.BluetoothReceiverCallback {
            override fun onBluetoothConnected() {
                //Check if bluetooth connected
                if (ActivityCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return
                }
                if (bluetoothManager.adapter.bondedDevices.size > 0) {
                    for (d in bluetoothManager.adapter.bondedDevices) {
                        if (isConnected(d)) {
                            val deviceName = d.name
                            bluetoothName = deviceName
                            audioDevicesList.add(deviceName)

                        }
                    }
                }
                audioDeviceInfoArray.clear()
                audioInputDevicesList.clear()
                setInputDevices()
                Toast.makeText(this@MainActivity, "Bluetooth Connected", Toast.LENGTH_SHORT).show()

            }

            override fun onBluetoothDisconnected() {
                Toast.makeText(this@MainActivity, "Bluetooth Disconnected", Toast.LENGTH_SHORT)
                    .show()
                audioDevicesList.remove(bluetoothName)
                audioInputDevicesList.remove("Bluetooth Headset - Microphone")
            }
        }

        myTimer.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                getBandWidth()
            }
        }, 0, 5000) //put here time 1000 milliseconds=1 second


        val aa = ArrayAdapter(this, android.R.layout.simple_spinner_item, audioDevicesList)
        aa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.audioOutputSpinner.adapter = aa

        val resolutionAdapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_item, resolutionArray)
        resolutionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.resolutionSpinner.adapter = resolutionAdapter


        setInputDevices()

        val audioInputDevicesAdapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_item, audioInputDevicesList)
        audioInputDevicesAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.audioInputSpinner.adapter = audioInputDevicesAdapter

        recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            RECORDER_SAMPLERATE, RECORDER_CHANNELS,
            RECORDER_AUDIO_ENCODING, bufferElements2Rec * bytesPerElement
        )

        binding.audioInputSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>, view: View?, position: Int, id: Long
                ) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        recorder?.preferredDevice = audioDeviceInfoArray[position]
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }



        binding.resolutionSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    when (parent.getItemAtPosition(position).toString()) {
                        "240p" -> {
                            setVideoResolution(320, 240, 30)
                        }
                        "360p" -> {
                            setVideoResolution(480, 360, 30)
                        }
                        "480p" -> {
                            setVideoResolution(720, 480, 30)
                        }
                        "720p" -> {
                            setVideoResolution(1280, 720, 30)
                        }
                        "1080p" -> {
                            setVideoResolution(1920, 1080, 30)
                        }
                        else -> {
                            setVideoResolution(2560, 1440, 30)
                        }
                    }

                } // to close the onItemSelected

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

        binding.audioOutputSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    when (parent.getItemAtPosition(position).toString()) {
                        "Default - Phone Speaker (Build-in)" -> {
                            setSpeakerOn()
                        }
                        "Earphone Speaker(Build-in)" -> {
                            setHeadsetOn()
                        }
                        bluetoothName -> {
                            setBluetoothOn()
                        }
                        else -> {
                            setHeadphonesPlugged()
                        }
                    }

                } // to close the onItemSelected

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //  ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }

    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun setInputDevices() {

        val audioDevices = audioManager!!.getDevices(AudioManager.GET_DEVICES_INPUTS)

        for (deviceInfo in audioDevices) {

            Log.d("isHeadphonesPlugged", "isHeadphonesPlugged: " + deviceInfo.type)

            if (deviceInfo.type == AudioDeviceInfo.TYPE_BUILTIN_MIC) {
                if (!audioInputDevicesList.contains("Default - Microphone(Build-in)")) {
                    audioInputDevicesList.add("Default - Microphone(Build-in)")
                    audioDeviceInfoArray.add(deviceInfo)
                }
            }

            if (deviceInfo.type == AudioDeviceInfo.TYPE_WIRED_HEADSET) {
                audioInputDevicesList.add("Wired Headset - Microphone")
                audioDeviceInfoArray.add(deviceInfo)
            }
            if (deviceInfo.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                deviceInfo.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                deviceInfo.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
            ) {
                audioInputDevicesList.add("Bluetooth Headset - Microphone")
                audioDeviceInfoArray.add(deviceInfo)

            }

        }

    }

    private fun muteUnMuteeAudio() {
        if (isAudioMuted) {
            isAudioMuted = false
            binding.muteAudio.setImageResource(R.drawable.ic_baseline_mic_24)
            /*For call audio UnMute */
            audioUnMute()
        } else {
            isAudioMuted = true
            binding.muteAudio.setImageResource(R.drawable.ic_baseline_mic_off_24)
            /*For call audio Mute */
            audioMute()
        }

    }

    private fun muteUnMuteVideo() {

        if (isVideoMuted) {
            isVideoMuted = false
            binding.muteVideo.setImageResource(R.drawable.ic_baseline_videocam_24)
            /*For call video UnMute */
            videoUnMute()
        } else {
            isVideoMuted = true
            binding.muteVideo.setImageResource(R.drawable.ic_baseline_videocam_off_24)
            /*For call video Mute */
            videoMute()
        }
    }

    private fun onConnectSucceed() {

        val WS_SERVER = "https://testcdac-cdn.invc.vc/"
//https://testcdac-cdn.invc.vc/socket.io/?uid=12&projectId=testid&selfName=Sandeep&appName=eSanjeevani2.0&encounterUid=213&EIO=4&transport=polling&t=OLs04s1
        connectServer(
            WS_SERVER,
            selfId,
            "testid",
            senderName,
            encounterID, "eSanjeevani2.0",
            object : ActionCallBack {
                override fun onSuccess(message: String?) {
                    binding.connect.isEnabled = false
                    isSocketConnected = true
                    Log.d("onConnectSucceed", "onSuccess $message")
                }

                override fun onFailure(error: String?) {
                    binding.connect.isEnabled = true
                    isSocketConnected = false
                    Toast.makeText(this@MainActivity, "Connection failed", Toast.LENGTH_SHORT)
                        .show()
                    Log.d("onConnectSucceed", "onFailure $error")
                }
            })
    }

    override fun offerReceived(remoteId: String?) {}
    override fun onFinished() {
        finish()
    }

    override fun remoteUserDisconnected() {
        leave()
        finish()
    }

    fun setSpeakerOn() {
        audioManager?.mode = AudioManager.MODE_IN_CALL
        audioManager?.stopBluetoothSco()
        audioManager?.isBluetoothScoOn = false
        audioManager?.isSpeakerphoneOn = true
        audioManager?.isWiredHeadsetOn = false
    }

    fun setHeadsetOn() {
        audioManager?.mode = AudioManager.MODE_IN_CALL
        audioManager?.stopBluetoothSco()
        audioManager?.isBluetoothScoOn = false
        audioManager?.isSpeakerphoneOn = false
        audioManager?.isWiredHeadsetOn = false
    }

    fun setBluetoothOn() {
        audioManager?.mode = AudioManager.MODE_IN_CALL
        audioManager?.startBluetoothSco()
        audioManager?.isSpeakerphoneOn = false
        audioManager?.isBluetoothScoOn = true
        audioManager?.isWiredHeadsetOn = false
    }

    fun setHeadphonesPlugged() {
        audioManager?.mode = AudioManager.MODE_IN_CALL
        audioManager?.stopBluetoothSco()
        audioManager?.isWiredHeadsetOn = true
        audioManager?.isSpeakerphoneOn = false
        audioManager?.isBluetoothScoOn = false
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(
            mHeadsetBroadcastReceiver,
            IntentFilter(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
        )
        registerReceiver(
            mHeadsetBroadcastReceiver,
            IntentFilter(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED)
        )
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }

    private fun isConnected(device: BluetoothDevice): Boolean {
        return try {
            val m: Method = device.javaClass.getMethod("isConnected")
            m.invoke(device) as Boolean
        } catch (e: Exception) {
            throw IllegalStateException(e)
        }
    }

    override fun onClick(view: View) {

        if (view.id == R.id.call) {
            val destId = binding.destId.text.toString()
            makeCall(selfId, destId, object : ActionCallBack {

                override fun onSuccess(message: String?) {
                    Log.d("makeCall", "onSuccess$message")
                }

                override fun onFailure(error: String?) {
                    Log.d("makeCall", "onFailure$error")
                }
            })
        }

    }

    @RequiresApi(Build.VERSION_CODES.M)
    fun getBandWidth() {
        if (verifyAvailableNetwork(this)) {
            /*  val connectionManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
              val nc = connectionManager.getNetworkCapabilities(connectionManager.activeNetwork)
              val downSpeed = nc!!.linkDownstreamBandwidthKbps
              val upSpeed = nc.linkUpstreamBandwidthKbps*/
            val sentKB = getSentBytesStats(selfId)
            if (sentKB < 56) {
                isVideoMuted = false
                muteUnMuteVideo()
            } else if (!isVideoMuted) {
                videoUnMute()
            } else {
                videoMute()
            }
            Log.d("getBandWidth", "downSpeed: $sentKB")
        } else {
            videoMute()
        }
    }

    private fun verifyAvailableNetwork(activity: AppCompatActivity): Boolean {
        val connectivityManager =
            activity.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = connectivityManager.activeNetworkInfo
        return networkInfo != null && networkInfo.isConnected
    }

    override fun onDestroy() {
        super.onDestroy()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            HeadsetReceiver.instance.unregister(this)
        }
    }
}