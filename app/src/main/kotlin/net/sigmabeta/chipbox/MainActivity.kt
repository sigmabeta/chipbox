package net.sigmabeta.chipbox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.chipbox.feature.welcome.WelcomeViewModelBrain
import net.sigmabeta.chipbox.ui.ChipboxApp

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var brain: WelcomeViewModelBrain

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            brain.sendAction(SageAction.InitNoArgs)
        }

        setContent { ChipboxApp(brain) }
    }
}
