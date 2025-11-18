package com.jediterm.core.typeahead

interface Debouncer {
    fun call()

    fun terminateCall()
}