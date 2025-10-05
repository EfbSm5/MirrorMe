package com.example.castapp

import android.app.Application
import android.content.Context
import android.widget.Toast
import android.os.Handler
import android.os.Looper

class CastApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        private lateinit var instance: CastApp

        fun getContext(): Context {
            return instance.applicationContext
        }

        fun showMsg(text: String, duration: Int = Toast.LENGTH_SHORT) {
            val ctx = instance.applicationContext
            if (Looper.myLooper() == Looper.getMainLooper()) {
                Toast.makeText(ctx, text, duration).show()
            } else {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(ctx, text, duration).show()
                }
            }
        }
    }
}