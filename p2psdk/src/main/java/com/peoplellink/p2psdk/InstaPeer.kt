package com.peoplellink.p2psdk

import android.content.Context
import android.util.Log
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.webrtc.*
import org.webrtc.PeerConnection.*
import org.webrtc.PeerConnectionFactory.InitializationOptions
import java.net.URISyntaxException

class InstaPeer private constructor() {
    private var remoteUserId: String? = null
    private var meetingStartTime: String? = null
    private var meetingId: String? = null
    private var mVideoTrack: VideoTrack? = null
    private var mAudioTrack: AudioTrack? = null

    //FOR DISPOSE
    private var mPeerConnectionFactory: PeerConnectionFactory? = null
    private var mSurfaceTextureHelper: SurfaceTextureHelper? = null
    private var mVideoCapturer: VideoCapturer? = null
    private var mPeerConnection: PeerConnection? = null
    private var mLocalSurfaceView: SurfaceViewRenderer? = null
    private var mRemoteSurfaceView: SurfaceViewRenderer? = null
    private var stunUrl: String? = null
    private var udpUrl: String? = null
    private var tcpUrl: String? = null
    private var userName: String? = null
    private var credential: String? = null
    private var instaListener: InstaListener? = null
    private var finalBytes = 0
    private var kilobytes = 56
    private var mSocket: Socket? = null

    fun setListener(listener: InstaListener?) {
        instaListener = listener
    }


    fun connectServer(
        serverUrl: String?,
        selfId: String,
        projectId: String?,
        callBack: ActionCallBack
    ) {
        try {
            finalBytes = 0
            val mOptions = IO.Options()
            mOptions.query = "uid=$selfId&projectId=$projectId"
            mSocket = IO.socket(serverUrl, mOptions)
            mSocket!!.connect()
            callBack.onSuccess("Connected to server")
        } catch (e: URISyntaxException) {
            callBack.onFailure(e.message)
            throw RuntimeException(e)
        }

        mSocket!!.on("conf_response") { args ->

            Log.d("jsonMain", "conf_response" + args[0].toString())

            val mainJson = JSONObject(args[0].toString())
            when (mainJson.getString("type")) {
                "offer" -> {
                    remoteUserId = mainJson.getString("msgFrom")
                    meetingStartTime = mainJson.getString("meetingStartTime")
                    meetingId = mainJson.getString("meetingId")
                    onRemoteOfferReceived(mainJson)
                }
                "candidate" -> onRemoteCandidateReceived(mainJson)
                "incoming" -> {
                    makeCall(mainJson.getString("id"), mainJson.getString("msgFrom"),
                        object : ActionCallBack {
                            override fun onSuccess(message: String?) {
                                Log.d("makeCall", "onSuccess$message")
                            }

                            override fun onFailure(error: String?) {
                                Log.d("makeCall", "onFailure$error")
                            }
                        })
                }
                "answer" -> onRemoteAnswerReceived(mainJson)

                "closeMeeting" -> instaListener!!.remoteUserDisconnected()

            }

        }
    }

    fun initialise(
        context: Context, localView: SurfaceViewRenderer?,
        remoteView: SurfaceViewRenderer?, localMirror: Boolean, remoteMirror: Boolean
    ) {
        mLocalSurfaceView = localView
        mRemoteSurfaceView = remoteView
        val mRootEglBase = EglBase.create()
        mLocalSurfaceView!!.init(mRootEglBase.eglBaseContext, null)
        mLocalSurfaceView!!.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
        mLocalSurfaceView!!.setMirror(localMirror)
        mLocalSurfaceView!!.setEnableHardwareScaler(false)


        mRemoteSurfaceView!!.init(mRootEglBase.eglBaseContext, null)
        mRemoteSurfaceView!!.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
        mRemoteSurfaceView!!.setMirror(remoteMirror)
        mRemoteSurfaceView!!.setEnableHardwareScaler(true)
        mRemoteSurfaceView!!.setZOrderMediaOverlay(true)

        //CAN INITIALIZE SEPARATE
        mPeerConnectionFactory = createPeerConnectionFactory(context, mRootEglBase)
        Logging.enableLogToDebugOutput(Logging.Severity.LS_VERBOSE)
        mSurfaceTextureHelper =
            SurfaceTextureHelper.create("CaptureThread", mRootEglBase.eglBaseContext)
        val videoSource = mPeerConnectionFactory!!.createVideoSource(false)
        mVideoCapturer = createVideoCapturer(context)
        mVideoCapturer?.initialize(mSurfaceTextureHelper, context, videoSource.capturerObserver)
        mVideoTrack = mPeerConnectionFactory!!.createVideoTrack(VIDEO_TRACK_ID, videoSource)
        mVideoTrack?.setEnabled(true)
        mVideoTrack?.addSink(mLocalSurfaceView)
        val audioSource = mPeerConnectionFactory!!.createAudioSource(MediaConstraints())
        mAudioTrack = mPeerConnectionFactory!!.createAudioTrack(AUDIO_TRACK_ID, audioSource)
        mAudioTrack?.setEnabled(true)

        //TO START LOCAL CAPTURE
        mVideoCapturer!!.startCapture(VIDEO_RESOLUTION_WIDTH, VIDEO_RESOLUTION_HEIGHT, VIDEO_FPS)

    }

    fun setVideoResolution(width: Int, height: Int, ps: Int) {
        mVideoCapturer!!.changeCaptureFormat(width, height, ps)

    }

    private fun createPeerConnectionFactory(
        context: Context,
        mRootEglBase: EglBase
    ): PeerConnectionFactory {
        val encoderFactory: VideoEncoderFactory = DefaultVideoEncoderFactory(
            mRootEglBase.eglBaseContext,
            true,
            true
        )
        val decoderFactory: VideoDecoderFactory =
            DefaultVideoDecoderFactory(mRootEglBase.eglBaseContext)
        val initializationOptions = InitializationOptions
            .builder(context)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initializationOptions)
        val builder = PeerConnectionFactory.builder()
            .setVideoDecoderFactory(decoderFactory)
            .setVideoEncoderFactory(encoderFactory)
        builder.setOptions(null)
        return builder.createPeerConnectionFactory()
    }

    private fun createVideoCapturer(context: Context): VideoCapturer? {
        return if (Camera2Enumerator.isSupported(context)) {
            createCameraCapturer(Camera2Enumerator(context))
        } else {
            createCameraCapturer(Camera1Enumerator(true))
        }
    }

    private fun createCameraCapturer(enumerator: CameraEnumerator): VideoCapturer? {
        val deviceNames = enumerator.deviceNames
        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                val videoCapturer: VideoCapturer? = enumerator.createCapturer(deviceName, null)
                if (videoCapturer != null) return videoCapturer
            }
        }
        for (deviceName in deviceNames) {
            if (enumerator.isBackFacing(deviceName)) {
                val videoCapturer: VideoCapturer? = enumerator.createCapturer(deviceName, null)
                if (videoCapturer != null) return videoCapturer
            }
        }
        return null
    }

    fun tiltVideo(status: Boolean) {
        mLocalSurfaceView?.setMirror(status)
    }

    private fun createPeerConnection(): PeerConnection? {
        val iceServers: MutableList<IceServer> = ArrayList()
        if (stunUrl != null) {
            val stun = IceServer.builder(stunUrl).createIceServer()
            iceServers.add(stun)
        }
        if (udpUrl != null) {
            val udp = IceServer.builder(udpUrl).setUsername(userName).setPassword(credential)
                .createIceServer()
            iceServers.add(udp)
        }
        if (tcpUrl != null) {
            val tcp = IceServer.builder(tcpUrl).setUsername(userName).setPassword(credential)
                .createIceServer()
            iceServers.add(tcp)
        }
        val rtcConfig = RTCConfiguration(iceServers)
        rtcConfig.tcpCandidatePolicy = TcpCandidatePolicy.DISABLED
        rtcConfig.continualGatheringPolicy =
            ContinualGatheringPolicy.GATHER_CONTINUALLY
        rtcConfig.enableDtlsSrtp = true
        rtcConfig.enableCpuOveruseDetection = false
        //rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        val connection =
            mPeerConnectionFactory!!.createPeerConnection(rtcConfig, mPeerConnectionObserver)
                ?: return null
        val mediaStreamLabels = listOf("ARDAMS")
        connection.addTrack(mVideoTrack, mediaStreamLabels)
        connection.addTrack(mAudioTrack, mediaStreamLabels)
        return connection
    }

    private val mPeerConnectionObserver: Observer = object : Observer {
        override fun onSignalingChange(signalingState: SignalingState) {
            Log.d("testcase", "onSignalingChange: $signalingState")
        }

        override fun onIceConnectionChange(iceConnectionState: IceConnectionState) {
            Log.d("testcase", "onIceConnectionChange: $iceConnectionState")
        }

        override fun onIceConnectionReceivingChange(b: Boolean) {
            Log.d("testcase", "onIceConnectionChange: $b")
        }

        override fun onIceGatheringChange(iceGatheringState: IceGatheringState) {
            Log.d("testcase", "onIceGatheringChange: $iceGatheringState")
        }

        override fun onIceCandidate(iceCandidate: IceCandidate) {
            Log.d("testcase", "Obeserver onIceCandidate: $iceCandidate")
            try {
                val childObj = JSONObject()
                childObj.put("sdpMLineIndex", iceCandidate.sdpMLineIndex)
                childObj.put("sdpMid", iceCandidate.sdpMid)
                childObj.put("candidate", iceCandidate.sdp)
                val message = JSONObject()
                message.put("type", "candidate")
                message.put("id", remoteUserId)
                message.put("candidate", childObj)
                Log.d("testcase", "Obeserver onIceCandidate: $message")

                try {
                    send(message.toString())
                } catch (e: Exception) {
                    Log.d("testcase", "@@@@@@@@@@Exp " + e.message)
                }
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }

        override fun onIceCandidatesRemoved(iceCandidates: Array<IceCandidate>) {
            mPeerConnection!!.removeIceCandidates(iceCandidates)
        }

        override fun onAddStream(mediaStream: MediaStream) {
            Log.d("testcase", "onAddStream: " + mediaStream.videoTracks.size)
        }

        override fun onRemoveStream(mediaStream: MediaStream) {
            Log.d("testcase", "onRemoveStream")
        }

        override fun onDataChannel(dataChannel: DataChannel) {
            Log.d("testcase", "onDataChannel")
        }

        override fun onRenegotiationNeeded() {
            Log.d("testcase", "onRenegotiationNeeded")
        }

        override fun onAddTrack(rtpReceiver: RtpReceiver, mediaStreams: Array<MediaStream>) {
            val track = rtpReceiver.track()
            if (track is VideoTrack) {
                Log.d("testcase", "onAddVideoTrack")
                track.setEnabled(true)
                track.addSink(mRemoteSurfaceView)
            }
        }
    }

    private fun onRemoteOfferReceived(message: JSONObject) {
        Log.d("testcase", "offReceived $message")
        if (mPeerConnection == null) {
            mPeerConnection = createPeerConnection()
        }
        try {
            val description = message.getString("sdp")
            mPeerConnection!!.setRemoteDescription(
                SimpleSdpObserver(),
                SessionDescription(SessionDescription.Type.OFFER, description)
            )
            answerCall(message.getString("id"))
//                        instaListener!!.offerReceived(remoteId);
        } catch (e: JSONException) {
            e.printStackTrace()
            Log.d("testcase", "offReceived Exp " + e.message)
        }
    }

    private fun onRemoteAnswerReceived(message: JSONObject) {
        try {
            val description = message.getString("sdp")
            mPeerConnection!!.setRemoteDescription(
                SimpleSdpObserver(),
                SessionDescription(SessionDescription.Type.ANSWER, description)
            )
        } catch (e: JSONException) {
            e.printStackTrace()
            Log.d("testcase", "ansReceived Exp " + e.message)
        }
    }

    private fun onRemoteCandidateReceived(message: JSONObject) {
        try {
            val childJson = message.getJSONObject("candidate")
            val remoteIceCandidate = IceCandidate(
                childJson.getString("sdpMid"),
                childJson.getInt("sdpMLineIndex"),
                childJson.getString("candidate")
            )
            mPeerConnection!!.addIceCandidate(remoteIceCandidate)
        } catch (e: JSONException) {
            e.printStackTrace()
            Log.d("testcase", "candiReceived Exp " + e.message)
        }
    }

    fun makeCall(selfId: String?, remoteId: String?, callBack: ActionCallBack) {
        remoteUserId = remoteId
        if (mPeerConnection == null) {
            mPeerConnection = createPeerConnection()
        }
        val mediaConstraints = MediaConstraints()
        mediaConstraints.mandatory.add(
            MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true")
        )
        mediaConstraints.mandatory.add(
            MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true")
        )
        //不打开dtls无法和web端通信
        mediaConstraints.optional.add(
            MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true")
        )
        mPeerConnection!!.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sessionDescription: SessionDescription) {
                Log.i(
                    "testcase",
                    """Create local offer success: ${sessionDescription.description}""".trimIndent()
                )
                mPeerConnection!!.setLocalDescription(SimpleSdpObserver(), sessionDescription)
                val message = JSONObject()
                try {
                    message.put("id", remoteUserId)
                    message.put("type", "offer")
                    message.put("sdp", sessionDescription.description)
                    message.put("from", selfId)
                    //aaa
                    send(message.toString())

                    callBack.onSuccess("Offer sent success")
                    Log.d("conf_message", "onCreateSuccess: $message")
                } catch (e: JSONException) {
                    e.printStackTrace()
                    callBack.onFailure("Offer failed " + e.message)
                }
            }
        }, mediaConstraints)
    }

    fun answerCall(selfId: String) {
        if (mPeerConnection == null) {
            mPeerConnection = createPeerConnection()
        }
        val sdpMediaConstraints = MediaConstraints()
        Log.d("testcase", "Create answer ...")
        mPeerConnection!!.createAnswer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sessionDescription: SessionDescription) {
                Log.d("testcase", "Create answer success !")
                mPeerConnection!!.setLocalDescription(
                    SimpleSdpObserver(),
                    sessionDescription
                )
                val message = JSONObject()

                message.put("id", remoteUserId)
                message.put("type", "answer")
                message.put("sdp", sessionDescription.description)
                message.put("from", selfId)
                message.put("meetingId", meetingId)
                message.put("meetingStartTime", meetingStartTime)
                message.put("from", selfId)
                Log.d("answerCall", "onCreateSuccess: $message")
                send(message.toString())
            }
        }, sdpMediaConstraints)
    }

    fun disconnect() {
        val message = JSONObject()
        try {
            message.put("id", remoteUserId)
            message.put("type", "closeMeeting")
            send(message.toString())
        } catch (e: JSONException) {
            Log.d("testcase", "Disconnect " + e.message)
            e.printStackTrace()
        }
        leave()
    }

    fun leave() {
        try {
            mVideoCapturer!!.stopCapture()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
        if (mPeerConnection != null) {
            mPeerConnection!!.close()
            mPeerConnection = null
        }
        mLocalSurfaceView?.release()
        mRemoteSurfaceView?.release()
        mVideoCapturer?.dispose()
        mSurfaceTextureHelper?.dispose()
        PeerConnectionFactory.stopInternalTracingCapture()
        PeerConnectionFactory.shutdownInternalTracer()
        mPeerConnectionFactory?.dispose()
        if (mSocket != null) {
//            mSocket!!.close()
            mSocket!!.disconnect()
        }

        instaListener?.onFinished()
    }

    fun audioMute() {
        mAudioTrack!!.setEnabled(false)
    }

    fun audioUnMute() {
        mAudioTrack!!.setEnabled(true)
    }

    fun videoMute() {
        mVideoTrack!!.setEnabled(false)
    }

    fun videoUnMute() {
        mVideoTrack!!.setEnabled(true)
    }

    fun getSentBytesStats(selfId: String?): Int {

        mPeerConnection?.getStats { rtcStatsReport ->
            try {
                Log.d("RTCStatsReport", "bytesSent $rtcStatsReport")

                val json: String = ObjectMapper().writeValueAsString(rtcStatsReport.statsMap.values)
                val statsArray = JSONArray(json)
                var packetsLost = 0
                for (i in 0 until statsArray.length()) {
                    val statsObj = statsArray.getJSONObject(i)

                    if (statsObj.getString("id").contains("RTCInboundRTPVideoStream")) {
                        val membersObj = statsObj.getJSONObject("members")
                        packetsLost = membersObj.getInt("packetsLost")
                    }

                    if (statsObj.getString("id").contains("RTCOutboundRTPVideoStream")) {
                        val membersObj = statsObj.getJSONObject("members")
                        val bytesSent = membersObj.optString("bytesSent") ?: "0"
                        val framesPerSecond = membersObj.optString("framesPerSecond") ?: "0"

                        val bits = bytesSent.toInt() - finalBytes

                        val kilobytes = bits / 1000

                        finalBytes = bytesSent.toInt()
                        val stats = JSONObject()
                        stats.put("os", "Android")
                        stats.put("platform", "mobile_android")
                        stats.put("ipAddress", "")
                        stats.put("audioCodec", "audio/opus")
                        stats.put("videoCodec", "video/H264")
                        stats.put("bandwidthUsed", kilobytes)
                        stats.put("packetLost", packetsLost)
                        stats.put("frameRate", framesPerSecond.toFloat())
                        stats.put("browser", "")

                        val dict = JSONObject()
                        dict.put("type", "getStats")
                        dict.put("stats", stats)
                        dict.put("id", remoteUserId)
                        dict.put("from", selfId)
                        mSocket!!.emit("conf_message", dict.toString())
                        Log.d("RTCStatsReport", "statsForSend $dict")

                    }
                }
            } catch (e: JsonProcessingException) {
                e.printStackTrace()
                Log.e("RTCStatsReport", e.message.toString())
            }
        }

        return kilobytes
    }

    fun switchCamera() {
        if (mVideoCapturer != null) {
            if (mVideoCapturer is CameraVideoCapturer) {
                val cameraVideoCapturer = mVideoCapturer as CameraVideoCapturer
                cameraVideoCapturer.switchCamera(null)
            } else {
                // Will not switch camera, video capturer is not a camera
                Log.d("testcase", "No Possible")
            }
        }
    }

    fun send(msg: String?) {
        mSocket!!.emit("conf_message", msg)
    }

    companion object {
        private const val VIDEO_TRACK_ID = "ARDAMSv0"
        private const val AUDIO_TRACK_ID = "ARDAMSa0"
        private const val VIDEO_RESOLUTION_WIDTH = 320
        private const val VIDEO_RESOLUTION_HEIGHT = 240
        private const val VIDEO_FPS = 30

        @JvmStatic
        var instance: InstaPeer? = null
            get() {
                if (field == null) {
                    field = InstaPeer()
                }
                return field
            }
            private set
    }
}