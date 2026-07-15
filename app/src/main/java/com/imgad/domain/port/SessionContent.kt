package com.imgad.domain.port

import com.imgad.domain.model.Asset
import com.imgad.domain.model.Message

data class SessionContent(
    val messages: List<Message>,
    val assetsByMessage: Map<String, List<Asset>>,
    val title: String = "",
)
