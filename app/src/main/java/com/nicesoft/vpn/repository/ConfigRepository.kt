package com.nicesoft.vpn.repository

import com.nicesoft.vpn.database.Config
import com.nicesoft.vpn.database.ConfigDao

class ConfigRepository(private val configDao: ConfigDao) {

    suspend fun get(): Config {
        val config = configDao.get()
        if (config != null) return config
        return Config().also { configDao.insert(it) }
    }

    suspend fun update(config: Config) {
        configDao.update(config)
    }
}
