package com.peoplellink.p2psdk

import android.content.Context
import com.peoplellink.p2psdk.InstaPeer.Companion.instance
import org.webrtc.SurfaceViewRenderer

object InstaSDK {
    fun connectServer(
        serverUrl: String?,
        selfId: String?,
        projectId: String?,
        callBack: ActionCallBack?
    ) {
        instance!!.connectServer(serverUrl, selfId!!, projectId, callBack!!)
    }

    fun instaListener(instaListener: InstaListener?) {
        instance!!.setListener(instaListener)
    }

    fun initialise(
        context: Context?,
        localView: SurfaceViewRenderer?,
        remoteView: SurfaceViewRenderer?,
        localMirror: Boolean,
        remoteMirror: Boolean
    ) {
        instance!!.initialise(context!!, localView, remoteView, localMirror, remoteMirror)
    }

    fun setVideoResolution(width: Int, height: Int, ps: Int) {
        instance!!.setVideoResolution(width, height, ps)
    }

    fun makeCall(selfId: String?, remoteId: String?, callBack: ActionCallBack?) {
        instance!!.makeCall(selfId, remoteId, callBack!!)
    }

    fun answerCall(selfId: String) {
        instance!!.answerCall(selfId)
    }

    fun disconnect() {
        instance!!.disconnect()
    }

    fun leave() {
        instance!!.leave()
    }

    fun audioMute() {
        instance!!.audioMute()
    }

    fun audioUnMute() {
        instance!!.audioUnMute()
    }

    fun videoMute() {
        instance!!.videoMute()
    }

    fun videoUnMute() {
        instance!!.videoUnMute()
    }

    fun getSentBytesStats(): Int {
        return instance!!.getSentBytesStats()
    }

    fun tiltVideo(status: Boolean) {
        instance!!.tiltVideo(status)
    }

    fun switchCamera() {
        instance!!.switchCamera()
    } /*public static void call(Context context, SurfaceViewRenderer localView, SurfaceViewRenderer remoteView, String serverUrl, String selfId, String remoteId) {
        InstaPeer.getInstance().connectServer(context, localView, remoteView, serverUrl, selfId, remoteId);
    }*/
}