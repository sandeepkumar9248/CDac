package com.peoplellink.p2psdk

import android.content.Context
import com.peoplellink.p2psdk.InstaPeer.Companion.instance
import org.webrtc.SurfaceViewRenderer

object InstaSDK {
    fun connectServer(
        serverUrl: String?,
        selfId: String?,
        projectId: String?,
        selfName: String?,
        encounterUid: String?,
        appName: String?,
        callBack: ActionCallBack?
    ) {
        instance!!.connectServer(serverUrl, selfId!!, projectId, selfName, encounterUid,appName, callBack!!)
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

    fun startCall(selfId: String?,remoteUser:String?) {
        instance!!.startCall(selfId,remoteUser)
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

    fun getSentBytesStats(selfId: String?): Int {
        return instance!!.getSentBytesStats(selfId)
    }

    fun switchCamera() {
        instance!!.switchCamera()
    }
}