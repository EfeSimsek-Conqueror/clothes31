package com.fitrater.app.util

import com.fitrater.app.data.model.ClosetItem

data class EditPieceRequest(
    val item: ClosetItem,
    val referenceUrl: String?,
)

/** Simple singleton to pass an edit request across nav destinations. */
object EditRequestBus {
    @Volatile private var pending: EditPieceRequest? = null

    fun set(item: ClosetItem, referenceUrl: String?) {
        pending = EditPieceRequest(item, referenceUrl)
    }

    /** Read the pending request without clearing. */
    fun peek(): EditPieceRequest? = pending

    /** Read and clear the pending request. */
    fun consume(): EditPieceRequest? {
        val r = pending
        pending = null
        return r
    }
}
