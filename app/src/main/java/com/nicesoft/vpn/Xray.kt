package com.nicesoft.vpn

import android.app.Application
import com.nicesoft.vpn.database.XrayDatabase
import com.nicesoft.vpn.repository.ConfigRepository
import com.nicesoft.vpn.repository.LinkRepository
import com.nicesoft.vpn.repository.ProfileRepository

class Xray : Application() {

    private val xrayDatabase by lazy { XrayDatabase.ref(this) }
    val configRepository by lazy { ConfigRepository(xrayDatabase.configDao()) }
    val linkRepository by lazy { LinkRepository(xrayDatabase.linkDao()) }
    val profileRepository by lazy { ProfileRepository(xrayDatabase.profileDao()) }
}
