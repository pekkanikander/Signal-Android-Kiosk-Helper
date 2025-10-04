package fi.iki.pnr.kioskhelper

import android.app.Activity
import android.content.Intent
import android.os.Bundle

class HomeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        // Minimal HOME surface. If kiosk applied, ensure Signal is foregrounded; else finish.
        // MVP: do nothing special; finish if not applied to hand control back.
        finish()
    }
}
