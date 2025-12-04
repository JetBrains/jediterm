package ai.rever.bossterm.terminal

import ai.rever.bossterm.terminal.model.CharBuffer

open class StyledTextConsumerAdapter : StyledTextConsumer {
    override fun consume(x: Int, y: Int, style: TextStyle, characters: CharBuffer, startRow: Int) {
        // to override
    }

    override fun consumeNul(x: Int, y: Int, nulIndex: Int, style: TextStyle, characters: CharBuffer, startRow: Int) {
        // to override
    }

    override fun consumeQueue(x: Int, y: Int, nulIndex: Int, startRow: Int) {
        // to override
    }
}
