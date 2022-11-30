package com.peoplellink.p2psdk

import org.webrtc.SdpObserver
import org.webrtc.SessionDescription

open class SimpleSdpObserver : SdpObserver {
    override fun onCreateSuccess(sessionDescription: SessionDescription) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(msg: String) {}
    override fun onSetFailure(msg: String) {}
}