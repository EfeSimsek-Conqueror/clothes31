package com.fitrater.app

import android.app.Application
import com.fitrater.app.data.Supa
import com.fitrater.app.data.billing.RcBilling

class FitraterApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Configure RevenueCat as early as possible. Identification with the Supabase
        // user id happens later in MainActivity when the session becomes Authenticated.
        RcBilling.init(this, Supa.RC_ANDROID_API_KEY)
    }
}
