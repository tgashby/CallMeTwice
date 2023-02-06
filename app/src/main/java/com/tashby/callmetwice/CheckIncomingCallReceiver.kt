package com.tashby.callmetwice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager

var previousRingVolume: Int = -1
var ringVolumeSet: Boolean = false

class CheckIncomingCallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            TelephonyManager.ACTION_PHONE_STATE_CHANGED -> {
                val stateStr = intent.extras!!.getString(TelephonyManager.EXTRA_STATE)
                if (stateStr == TelephonyManager.EXTRA_STATE_RINGING) {
                    var number = intent.extras!!.getString(TelephonyManager.EXTRA_INCOMING_NUMBER)
                    if (number != null) {
                        if (!ringVolumeSet) {
                            val audioManager: AudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                            previousRingVolume = audioManager.ringerMode
                            ringVolumeSet = true
                        }
                        number = PhoneNumberUtils.normalizeNumber(number)
                        MainActivity.checkPhoneNumber(context, number)
                        /*
                        -- Read phone number
                        -- Check phone number against whitelist
                        -- Check phone number against call log
                        -- Change ring volume
                        -- Change ring volume back to before
                        Add numbers to whitelist
                         */
                    }
                } else if (stateStr == TelephonyManager.EXTRA_STATE_IDLE) {
                    if (ringVolumeSet) {
                        ringVolumeSet = false

                        val audioManager: AudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                        audioManager.ringerMode = previousRingVolume
                    }
                }
            }
        }
    }
}