package com.nimba.identity

/**
 * A signatory's civility, as it is printed on a generated document. Confirmed once
 * on a user's profile (when opting in as a signatory) or on a standalone [com.nimba.signatory]
 * record, then resolved automatically wherever that signatory is picked, instead of
 * retyped by hand on every dossier.
 */
enum class Civility {
    MONSIEUR,
    MADAME,
}
