package com.nicesoft.vpn.repository

import com.nicesoft.vpn.database.Link
import com.nicesoft.vpn.database.LinkDao

class LinkRepository(private val linkDao: LinkDao) {

    val all = linkDao.all()
    val tabs = linkDao.tabs()

    suspend fun activeLinks(): List<Link> {
        return linkDao.activeLinks()
    }

    suspend fun insert(link: Link) {
        linkDao.insert(link)
    }

    suspend fun update(link: Link) {
        linkDao.update(link)
    }

    suspend fun delete(link: Link) {
        linkDao.delete(link)
    }
}
