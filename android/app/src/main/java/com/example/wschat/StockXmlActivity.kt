package com.example.wschat

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.feature.stock.presentation.ui.xml.StockXmlFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class StockXmlActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stock_xml)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, StockXmlFragment.newInstance())
                .commit()
        }
    }
}
