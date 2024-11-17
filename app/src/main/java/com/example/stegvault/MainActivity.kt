package com.example.stegvault

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        val cardExifSteg:CardView=findViewById(R.id.cardExifSteg)
        val cardLsbSteg:CardView=findViewById(R.id.cardLsbSteg)

        cardExifSteg.setOnClickListener {
            val intent= Intent(this,EXIFStegnography::class.java)
            startActivity(intent)
        }
        cardLsbSteg.setOnClickListener {
            val intent= Intent(this,LSBStegnography::class.java)
            startActivity(intent)
        }

    }
}