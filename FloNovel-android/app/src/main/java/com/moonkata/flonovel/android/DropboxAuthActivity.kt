package com.moonkata.flonovel.android

import android.app.Activity
import android.os.Bundle
import com.moonkata.flonovel.android.data.sync.DropboxAuthSession

/**
 * Catches the `db-<app key>://1/connect` redirect the browser sends back after Dropbox consent.
 *
 * It has no UI: it hands the URI to [DropboxAuthSession] and finishes, which drops the user straight
 * back onto whatever screen started the flow. That works because the browser launches this into the
 * app's own task, leaving `MainActivity` underneath.
 *
 * `singleTop` keeps a second redirect (a double-tap on the consent button) from stacking a duplicate
 * copy on top of the first.
 */
class DropboxAuthActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DropboxAuthSession.onRedirect(intent?.data)
        finish()
    }
}
