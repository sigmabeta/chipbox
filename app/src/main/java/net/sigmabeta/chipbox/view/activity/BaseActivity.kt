package net.sigmabeta.chipbox.view.activity

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.widget.Toast
import net.sigmabeta.chipbox.ChipboxApplication
import net.sigmabeta.chipbox.util.logError

public abstract class BaseActivity : AppCompatActivity() {
    /**
     * Calls the superclass constructor, and then automatically
     * requests an injection of the Activity's dependencies.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ChipboxApplication.appComponent == null) {
            logError("[DaggerActivity] AppComponent null.")
        }
        inject()
    }

    fun showToastMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    /**
     * Must be overridden to request the activity's dependencies
     * and do any other necessary setup.
     */
    protected abstract fun inject()
}