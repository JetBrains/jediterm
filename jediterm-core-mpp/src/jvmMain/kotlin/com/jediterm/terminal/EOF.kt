package com.jediterm.terminal

import java.io.IOException

/**
 * Exception thrown when end of terminal data stream is reached.
 */
class EOF : IOException {
    constructor() : super("End of terminal data stream")

    constructor(message: String?) : super(message)
}
