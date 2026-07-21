package com.nimba.caution

/** Replaces a draft caution's field answers wholesale; 409 once the caution is FINAL. */
data class UpdateCautionCommand(
    val content: Map<String, String>,
)
