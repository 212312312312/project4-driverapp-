package com.taxiapp.driver

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity

class ActivityDistributionInfo : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_distribution_info)

        findViewById<View>(R.id.btnBack).setOnClickListener {
            finish()
        }
    }
}