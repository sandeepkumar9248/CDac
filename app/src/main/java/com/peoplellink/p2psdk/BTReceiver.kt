package com.peoplellink.p2psdk

import android.bluetooth.BluetoothHeadset
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.AUDIO_SERVICE
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.util.Log
import android.widget.Toast


class BTReceiver : BroadcastReceiver() {
    var state = 0 // instances
    var callback: BluetoothReceiverCallback? = null
    var audioManager: AudioManager? = null

    //region singleton
    private object HOLDER {

        val INSTANCE = BTReceiver()
    }

    companion object {
        val instance: BTReceiver by lazy { HOLDER.INSTANCE }
        private const val TAG = "BTReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("Z", "Received: Bluetooth")
        try {
            val extras = intent.extras
            if (extras != null) { //Do something
                audioManager = context.getSystemService(AUDIO_SERVICE) as AudioManager?
                val action = intent.action
//                Toast.makeText(context, action, Toast.LENGTH_LONG).show()
                val state: Int
                if (action == BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED) {
                    state = intent.getIntExtra(
                        BluetoothHeadset.EXTRA_STATE,
                        BluetoothHeadset.STATE_DISCONNECTED
                    )
                    Log.d(
                        Companion.TAG,
                        "\nAction = $action\nState = $state"
                    ) //$NON-NLS-1$ //$NON-NLS-2$
                    if (state == BluetoothHeadset.STATE_CONNECTED) {
                        setModeBluetooth()
                        callback?.onBluetoothConnected()
                    } else if (state == BluetoothHeadset.STATE_DISCONNECTED) {
                        // Calling stopVoiceRecognition always returns false here
                        // as it should since the headset is no longer connected.
                        setModeNormal()
                        callback?.onBluetoothDisconnected()
                        Log.d(Companion.TAG, "Headset disconnected") //$NON-NLS-1$
                    }
                } else  // audio
                {
                    state = intent.getIntExtra(
                        BluetoothHeadset.EXTRA_STATE,
                        BluetoothHeadset.STATE_AUDIO_DISCONNECTED
                    )
                    Log.d(
                        Companion.TAG,
                        "\nAction = $action\nState = $state"
                    ) //$NON-NLS-1$ //$NON-NLS-2$
                    if (state == BluetoothHeadset.STATE_AUDIO_CONNECTED) {
                        Log.d(Companion.TAG, "\nHeadset audio connected") //$NON-NLS-1$
                        callback?.onBluetoothConnected()
//                        setModeBluetooth()
                    } else if (state == BluetoothHeadset.STATE_AUDIO_DISCONNECTED) {
                        callback?.onBluetoothDisconnected()
//                        setModeNormal()
                        Log.d(Companion.TAG, "Headset audio disconnected") //$NON-NLS-1$
                    }
                }
            }
        } catch (e: Exception) {
            Log.d("Exception", "Exception $e")
        }
    }

    private fun setModeBluetooth() {
        try {
            audioManager!!.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager!!.startBluetoothSco()
            audioManager!!.isBluetoothScoOn = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setModeNormal() {
        try {
            audioManager!!.mode = AudioManager.MODE_NORMAL
            audioManager!!.stopBluetoothSco()
            audioManager!!.isBluetoothScoOn = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    fun register(context: Context) {
        val receiverFilter = IntentFilter(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
        context.registerReceiver(this, receiverFilter)
    }

    interface BluetoothReceiverCallback {
        fun onBluetoothConnected()
        fun onBluetoothDisconnected()
    }
}