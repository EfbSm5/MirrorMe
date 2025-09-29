package com.example.castapp

import android.app.Application
import android.content.Context
import android.widget.Toast

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
            Toast.makeText(instance.applicationContext, text, duration).show()
        }
    }
}