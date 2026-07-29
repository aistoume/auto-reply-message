package solutions.aicon.zuiti

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Invisible relay: the bubble was tapped but this session has no
 * MediaProjection yet. Pop the system consent over whatever app the user
 * is in (translucent theme keeps it visible underneath), hand the grant
 * to the service, and have it run the capture the user asked for.
 */
class ProjectionConsentActivity : ComponentActivity() {

    private val launcher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                startService(
                    Intent(this, BubbleService::class.java)
                        .setAction(BubbleService.ACTION_GRANT_AND_CAPTURE)
                        .putExtra(BubbleService.EXTRA_RESULT_CODE, result.resultCode)
                        .putExtra(BubbleService.EXTRA_RESULT_DATA, result.data)
                )
            } else {
                Toast.makeText(this, getString(R.string.consent_needed), Toast.LENGTH_SHORT).show()
            }
            finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        launcher.launch(mgr.createScreenCaptureIntent())
    }
}
