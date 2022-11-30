package com.peoplellink.p2psdk

interface ActionCallBack {
    fun onSuccess(message: String?)
    fun onFailure(error: String?)
}