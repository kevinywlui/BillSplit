package com.kevinywlui.billsplit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.kevinywlui.billsplit.navigation.AppNavigation
import com.kevinywlui.billsplit.ui.theme.BillSplitTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BillSplitTheme {
                AppNavigation()
            }
        }
    }
}
