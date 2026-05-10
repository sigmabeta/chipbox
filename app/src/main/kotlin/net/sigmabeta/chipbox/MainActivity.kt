package net.sigmabeta.chipbox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import net.sigmabeta.chipbox.appui.ChipboxAppUi
import net.sigmabeta.sage.ui.StringProvider

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var stringProvider: StringProvider

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { ChipboxAppUi(stringProvider) }
    }
}
