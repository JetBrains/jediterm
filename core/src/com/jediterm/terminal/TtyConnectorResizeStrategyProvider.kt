package com.jediterm.terminal

/**
 * Implement this interface by your [TtyConnector] to provide the resize strategy.
 */
interface TtyConnectorResizeStrategyProvider {
  val resizeStrategy: TtyConnectorResizeStrategy
}

/**
 * Determines the strategy of calling [TtyConnector.resize] when terminal grid size changes.
 */
enum class TtyConnectorResizeStrategy {
  /**
   * Resize the [TtyConnector] immediately together with [com.jediterm.terminal.model.TerminalTextBuffer] resize.
   */
  IMMEDIATE,

  /**
   * Resize of [TtyConnector] will be done some time after [com.jediterm.terminal.model.TerminalTextBuffer] resize.
   */
  POSTPONED,
}