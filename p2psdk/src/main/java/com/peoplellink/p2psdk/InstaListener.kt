package com.peoplellink.p2psdk

interface InstaListener {
    fun offerReceived(remoteId: String?)
    fun onFinished()
    fun remoteUserDisconnected()
}