package com.tapweb.data.repository

import com.tapweb.data.db.WebSiteDao
import com.tapweb.data.db.WebSiteEntity
import com.tapweb.data.model.OpenMode
import kotlinx.coroutines.flow.Flow

class WebSiteRepository(private val dao: WebSiteDao) {

    fun getAll(): Flow<List<WebSiteEntity>> = dao.getAll()

    suspend fun getById(id: Long): WebSiteEntity? = dao.getById(id)

    suspend fun add(url: String, title: String, faviconUrl: String?, openMode: OpenMode): Long {
        val maxOrder = dao.getMaxSortOrder() ?: -1
        return dao.insert(
            WebSiteEntity(
                url = url,
                title = title,
                faviconUrl = faviconUrl,
                openMode = openMode,
                sortOrder = maxOrder + 1
            )
        )
    }

    suspend fun addWithIcon(
        url: String, title: String, faviconUrl: String?, openMode: OpenMode,
        iconType: com.tapweb.data.model.IconType, customIconPath: String?
    ): Long {
        val maxOrder = dao.getMaxSortOrder() ?: -1
        return dao.insert(
            WebSiteEntity(
                url = url,
                title = title,
                faviconUrl = faviconUrl,
                openMode = openMode,
                sortOrder = maxOrder + 1,
                iconType = iconType,
                customIconPath = customIconPath
            )
        )
    }

    suspend fun update(website: WebSiteEntity) = dao.update(website)

    suspend fun delete(website: WebSiteEntity) = dao.delete(website)

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    suspend fun getNextId(): Long = dao.getNextId()
}
