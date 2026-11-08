package com.retroengine.app

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.retro_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        val root = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(64, 64, 64, 64)
            addView(
                logo,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
        setContentView(root)
    }
}
