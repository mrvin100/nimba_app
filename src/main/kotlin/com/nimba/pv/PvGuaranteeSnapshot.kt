package com.nimba.pv

import com.nimba.guarantee.GuaranteeKind

/** One guarantee as frozen on a finalized PV — just the two printed columns, no attachments. */
data class PvGuaranteeSnapshot(
    val kind: GuaranteeKind,
    val description: String,
)
