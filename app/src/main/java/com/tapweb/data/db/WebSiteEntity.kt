package com.tapweb.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tapweb.data.model.IconType
import com.tapweb.data.model.OpenMode

@Entity(tableName = "websites")
data class WebSiteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val url: String,
    val title: String,
    val faviconUrl: String? = null,
    val openMode: OpenMode = OpenMode.WEBVIEW,
    val createdAt: Long = System.currentTimeMillis(),
    val sortOrder: Int = 0,
    val hasShortcut: Boolean = false,
    val iconType: IconType = IconType.AUTO,
    val customIconPath: String? = null
)
