package com.example.messages.data.sms

/** Bound nesting before giving an imported backup to Android's recursive JSON parser. */
internal fun checkBackupJsonDepth(json: String) {
    var depth = 0
    var quoted = false
    var escaped = false
    var structures = 0
    var separators = 0
    for (char in json) {
        if (quoted) {
            when {
                escaped -> escaped = false
                char == '\\' -> escaped = true
                char == '"' -> quoted = false
            }
            continue
        }
        when (char) {
            '"' -> quoted = true
            '{', '[' -> {
                depth++
                structures++
                require(depth <= 16 && structures <= 100_000) { "This backup is too complex." }
            }
            '}', ']' -> {
                depth--
                require(depth >= 0) { "This backup contains invalid JSON." }
            }
            ',', ':' -> {
                separators++
                require(separators <= 1_500_000) { "This backup is too complex." }
            }
            // Android also accepts comments and alternate separators. DEG backups use standard JSON.
            '\'', '/', '#', ';', '=' -> error("This backup contains invalid JSON.")
        }
    }
    require(!quoted && depth == 0) { "This backup contains invalid JSON." }
}
