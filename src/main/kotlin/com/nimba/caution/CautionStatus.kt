package com.nimba.caution

/** A caution's lifecycle — same DRAFT/FINAL shape as the PV: editable while drafted, immutable and snapshotted once finalized. */
enum class CautionStatus {
    DRAFT,
    FINAL,
}
