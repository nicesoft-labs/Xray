package com.nicesoft.vpn.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nicesoft.vpn.Xray
import com.nicesoft.vpn.database.Link
import kotlinx.coroutines.launch

class LinkViewModel(application: Application) : AndroidViewModel(application) {

    private val linkRepository by lazy { getApplication<Xray>().linkRepository }

    val tabs = linkRepository.tabs
    val links = linkRepository.all

    suspend fun activeLinks(): List<Link> {
        return linkRepository.activeLinks()
    }

    fun insert(link: Link) = viewModelScope.launch {
        linkRepository.insert(link)
    }

    fun update(link: Link) = viewModelScope.launch {
        linkRepository.update(link)
    }

    fun delete(link: Link) = viewModelScope.launch {
        linkRepository.delete(link)
    }
}
