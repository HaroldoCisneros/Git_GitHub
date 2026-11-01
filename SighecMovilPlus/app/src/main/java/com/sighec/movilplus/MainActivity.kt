package com.sighec.movilplus

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.sighec.movilplus.ui.Navegacion
import com.sighec.movilplus.ui.theme.SighecTheme

/** FragmentActivity es requerida por BiometricPrompt (huella digital). */
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SighecTheme {
                Navegacion()
            }
        }
    }
}
