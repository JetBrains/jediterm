package ai.rever.bossterm.core.typeahead

import ai.rever.bossterm.core.util.Ascii
import org.jetbrains.annotations.Contract
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import java.util.Map
import java.util.concurrent.TimeUnit
import kotlin.concurrent.Volatile
import kotlin.math.max
import kotlin.math.min

class TerminalTypeAheadManager(private val myTerminalModel: TypeAheadTerminalModel) {
    private var myClearPredictionsDebouncer: Debouncer? = null
    private val myPredictions: MutableList<TypeAheadPrediction?> = ArrayList<TypeAheadPrediction?>()
    private val myLatencyStatistics = LatencyStatistics()

    // if false, predictions will still be generated for latency statistics but won't be displayed
    private var myIsShowingPredictions = false

    // if true, new predictions will only be generated if the user isn't typing for a certain amount of time
    @Volatile
    private var myOutOfSyncDetected = false
    private var myLastTypedTime: Long = 0

    // guards the terminal prompt. All predictions that try to move the cursor beyond leftmost cursor position are tentative
    private var myLeftMostCursorPosition: Int? = null
    private var myIsNotPasswordPrompt = false
    private var myLastSuccessfulPrediction: TypeAheadPrediction? = null

    fun onTerminalStateChanged() {
        if (!myTerminalModel.isTypeAheadEnabled || myOutOfSyncDetected) return

        myTerminalModel.lock()
        try {
            if (myTerminalModel.isUsingAlternateBuffer) {
                resetState()
                return
            }
            val lineWithCursorX = myTerminalModel.currentLineWithCursor

            if (!myPredictions.isEmpty()) {
                updateLeftMostCursorPosition(lineWithCursorX.myCursorX)
                myClearPredictionsDebouncer?.call()
            }

            val lastPrediction = myLastSuccessfulPrediction
            if (lastPrediction != null && lineWithCursorX == lastPrediction.myPredictedLineWithCursorX) {
                return
            }

            val removedPredictions = ArrayList<TypeAheadPrediction>()
            while (!myPredictions.isEmpty() && lineWithCursorX != myPredictions.get(0)?.myPredictedLineWithCursorX) {
                myPredictions.removeAt(0)?.let { removedPredictions.add(it) }
            }

            if (myPredictions.isEmpty()) {
                myOutOfSyncDetected = true
                resetState()
            } else {
                myLastSuccessfulPrediction = myPredictions.removeAt(0)
                myLastSuccessfulPrediction?.let { removedPredictions.add(it) }
                for (prediction in removedPredictions) {
                    myLatencyStatistics.adjustLatency(prediction)

                    if (prediction is CharacterPrediction) {
                        myIsNotPasswordPrompt = true
                    }
                }
                applyPredictions()
            }
        } finally {
            myTerminalModel.unlock()
        }
    }

    fun onKeyEvent(keyEvent: TypeAheadEvent) {
        if (!myTerminalModel.isTypeAheadEnabled) return
        myTerminalModel.lock()
        try {
            if (myTerminalModel.isUsingAlternateBuffer) {
                resetState()
                return
            }

            val lineWithCursorX = myTerminalModel.currentLineWithCursor

            val prevTypedTime = myLastTypedTime
            myLastTypedTime = System.nanoTime()

            val autoSyncDelay: Long
            if (myLatencyStatistics.sampleSize >= LATENCY_MIN_SAMPLES_TO_TURN_ON) {
                autoSyncDelay = min(myLatencyStatistics.maxLatency, MAX_TERMINAL_DELAY)
            } else {
                autoSyncDelay = MAX_TERMINAL_DELAY
            }

            val hasTypedRecently = System.nanoTime() - prevTypedTime < autoSyncDelay
            if (hasTypedRecently) {
                if (myOutOfSyncDetected) {
                    return
                }
            } else {
                myOutOfSyncDetected = false
            }
            reevaluatePredictorState(hasTypedRecently)

            updateLeftMostCursorPosition(lineWithCursorX.myCursorX)

            if (myPredictions.isEmpty()) {
                myClearPredictionsDebouncer?.call() // start a timer that will clear predictions
            }
            val prediction = createPrediction(lineWithCursorX, keyEvent)
            myPredictions.add(prediction)
            applyPredictions()

            LOG.debug("Created " + keyEvent.myEventType + " prediction")
        } finally {
            myTerminalModel.unlock()
        }
    }

    fun onResize() {
        if (!myTerminalModel.isTypeAheadEnabled) return

        myTerminalModel.lock()
        try {
            resetState()
        } finally {
            myTerminalModel.unlock()
        }
    }

    val cursorX: Int
        get() {
            myTerminalModel.lock()
            try {
                if (myTerminalModel.isUsingAlternateBuffer && !myPredictions.isEmpty()) {
                    // otherwise, it will misreport cursor position
                    resetState()
                }

                val predictions =
                    this.visiblePredictions

                val cursorX =
                    if (predictions.isEmpty()) myTerminalModel.currentLineWithCursor.myCursorX else predictions.get(
                        predictions.size - 1
                    )?.myPredictedLineWithCursorX?.myCursorX ?: myTerminalModel.currentLineWithCursor.myCursorX
                return cursorX + 1
            } finally {
                myTerminalModel.unlock()
            }
        }

    fun debounce() {
        myTerminalModel.lock()
        try {
            if (!myPredictions.isEmpty()) {
                LOG.debug("Debounce")
                resetState()
            }
        } finally {
            myTerminalModel.unlock()
        }
    }

    fun setClearPredictionsDebouncer(clearPredictionsDebouncer: Debouncer) {
        myClearPredictionsDebouncer = clearPredictionsDebouncer
    }

    class TypeAheadEvent {
        enum class EventType {
            Character,
            Backspace,
            AltBackspace,
            LeftArrow,
            RightArrow,
            AltLeftArrow,
            AltRightArrow,
            Delete,
            Home,
            End,
            Unknown,
        }

        var myEventType: EventType

        // if event is Character it will hold character
        var characterOrNull: Char? = null
            private set

        constructor(eventType: EventType) {
            myEventType = eventType
        }

        constructor(eventType: EventType, ch: Char) {
            myEventType = eventType
            this.characterOrNull = ch
        }

        private class Sequence {
            private val mySequence: ByteArray?

            internal constructor(vararg bytesAsInt: Int) {
                mySequence = makeCode(*bytesAsInt)
            }

            internal constructor(sequence: ByteArray?) {
                mySequence = sequence
            }

            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other !is Sequence) return false
                return mySequence.contentEquals(other.mySequence)
            }

            override fun hashCode(): Int {
                return mySequence.contentHashCode()
            }

            companion object {
                // CharUtils.makeCode
                private fun makeCode(vararg bytesAsInt: Int): ByteArray {
                    val bytes = ByteArray(bytesAsInt.size)
                    var i = 0
                    for (byteAsInt in bytesAsInt) {
                        bytes[i] = byteAsInt.toByte()
                        i++
                    }
                    return bytes
                }
            }
        }

        companion object {
            // @see ai.rever.bossterm.terminal.TerminalKeyEncoder
            @JvmStatic
            fun fromByteArray(byteArray: ByteArray): MutableList<TypeAheadEvent> {
                if (byteArray.size == 0) {
                    return mutableListOf<TypeAheadEvent>()
                }
                val stringRepresentation = String(byteArray)
                if (isPrintableUnicode(stringRepresentation.get(0))) {
                    return fromString(stringRepresentation)
                }

                return mutableListOf<TypeAheadEvent>(fromSequence(byteArray))
            }

            fun fromChar(ch: Char): TypeAheadEvent {
                if (isPrintableUnicode(ch)) {
                    return TypeAheadEvent(EventType.Character, ch)
                } else {
                    return TypeAheadEvent(EventType.Unknown)
                }
            }

            @JvmStatic
            fun fromString(string: String): MutableList<TypeAheadEvent> {
                if (string.isEmpty()) {
                    return mutableListOf<TypeAheadEvent>()
                }

                if (!isPrintableUnicode(string.get(0))) {
                    return mutableListOf<TypeAheadEvent>(fromSequence(string.toByteArray()))
                }

                val events = ArrayList<TypeAheadEvent>()
                for (ch in string.toCharArray()) {
                    val event: TypeAheadEvent = fromChar(ch)
                    events.add(event)
                    if (event.myEventType == EventType.Unknown) break
                }

                return events
            }

            /**
             * copied from com.intellij.openapi.util.text.StringUtil
             */
            @Contract(pure = true)
            private fun isPrintableUnicode(c: Char): Boolean {
                val t = Character.getType(c)
                return t != Character.UNASSIGNED.toInt() && t != Character.LINE_SEPARATOR.toInt() && t != Character.PARAGRAPH_SEPARATOR.toInt() && t != Character.CONTROL.toInt() && t != Character.FORMAT.toInt() && t != Character.PRIVATE_USE.toInt() && t != Character.SURROGATE.toInt()
            }

            private fun fromSequence(byteArray: ByteArray?): TypeAheadEvent {
                return TerminalTypeAheadManager.TypeAheadEvent(
                    sequenceToEventType.getOrDefault(
                      Sequence(
                          byteArray
                      ), ai.rever.bossterm.core.typeahead.TerminalTypeAheadManager.TypeAheadEvent.EventType.Unknown
                    ) ?: ai.rever.bossterm.core.typeahead.TerminalTypeAheadManager.TypeAheadEvent.EventType.Unknown
                )
            }

            private val sequenceToEventType: MutableMap<Sequence?, EventType?> = Map.ofEntries<Sequence?, EventType?>(
                Map.entry<Sequence?, EventType?>(
                    Sequence(Ascii.ESC.code, '['.code, '3'.code, '~'.code),
                    EventType.Delete
                ),
                Map.entry<Sequence?, EventType?>(Sequence(Ascii.DEL.code), EventType.Backspace),
                Map.entry<Sequence?, EventType?>(Sequence(Ascii.ESC.code, Ascii.DEL.code), EventType.AltBackspace),
                Map.entry<Sequence?, EventType?>(Sequence(Ascii.ESC.code, 'O'.code, 'D'.code), EventType.LeftArrow),
                Map.entry<Sequence?, EventType?>(Sequence(Ascii.ESC.code, '['.code, 'D'.code), EventType.LeftArrow),
                Map.entry<Sequence?, EventType?>(Sequence(Ascii.ESC.code, 'O'.code, 'C'.code), EventType.RightArrow),
                Map.entry<Sequence?, EventType?>(Sequence(Ascii.ESC.code, '['.code, 'C'.code), EventType.RightArrow),
                Map.entry<Sequence?, EventType?>(Sequence(Ascii.ESC.code, 'b'.code), EventType.AltLeftArrow),
                Map.entry<Sequence?, EventType?>(
                    Sequence(
                        Ascii.ESC.code,
                        '['.code,
                        '1'.code,
                        ';'.code,
                        '3'.code,
                        'D'.code
                    ), EventType.AltLeftArrow
                ),  // It's ctrl+left arrow, but behaves just the same
                Map.entry<Sequence?, EventType?>(
                    Sequence(
                        Ascii.ESC.code,
                        '['.code,
                        '1'.code,
                        ';'.code,
                        '5'.code,
                        'D'.code
                    ), EventType.AltLeftArrow
                ),
                Map.entry<Sequence?, EventType?>(Sequence(Ascii.ESC.code, 'f'.code), EventType.AltRightArrow),
                Map.entry<Sequence?, EventType?>(
                    Sequence(
                        Ascii.ESC.code,
                        '['.code,
                        '1'.code,
                        ';'.code,
                        '3'.code,
                        'C'.code
                    ), EventType.AltRightArrow
                ),  // It's ctrl+right arrow, but behaves just the same
                Map.entry<Sequence?, EventType?>(
                    Sequence(
                        Ascii.ESC.code,
                        '['.code,
                        '1'.code,
                        ';'.code,
                        '5'.code,
                        'C'.code
                    ), EventType.AltRightArrow
                ),
                Map.entry<Sequence?, EventType?>(Sequence(Ascii.ESC.code, '['.code, 'H'.code), EventType.Home),
                Map.entry<Sequence?, EventType?>(Sequence(Ascii.ESC.code, 'O'.code, 'H'.code), EventType.Home),
                Map.entry<Sequence?, EventType?>(Sequence(1), EventType.Home),  // ctrl + a
                Map.entry<Sequence?, EventType?>(Sequence(Ascii.ESC.code, '['.code, 'F'.code), EventType.End),
                Map.entry<Sequence?, EventType?>(Sequence(Ascii.ESC.code, 'O'.code, 'F'.code), EventType.End),
                Map.entry<Sequence?, EventType?>(Sequence(5), EventType.End) // ctrl + e
            )
        }
    }

    internal class LatencyStatistics {
        private val myLatencies = LinkedList<Long?>()

        fun adjustLatency(prediction: TypeAheadPrediction) {
            myLatencies.add(System.nanoTime() - prediction.myCreatedTime)

            if (myLatencies.size > LATENCY_BUFFER_SIZE) {
                myLatencies.removeFirst()
            }
        }

        val latencyMedian: Long
            get() {
                check(!myLatencies.isEmpty()) { "Tried to calculate latency with sample size of 0" }

                val sortedLatencies =
                    myLatencies.filterNotNull().sorted().toLongArray()

                if (sortedLatencies.size % 2 == 0) {
                    return (sortedLatencies[sortedLatencies.size / 2 - 1] + sortedLatencies[sortedLatencies.size / 2]) / 2
                } else {
                    return sortedLatencies[sortedLatencies.size / 2]
                }
            }

        val maxLatency: Long
            get() {
                check(!myLatencies.isEmpty()) { "Tried to get max latency with sample size of 0" }

                return Collections.max(myLatencies.filterNotNull())
            }

        val sampleSize: Int
            get() = myLatencies.size

        companion object {
            private const val LATENCY_BUFFER_SIZE = 30
        }
    }

    private val lastPrediction: TypeAheadPrediction?
        get() = if (myPredictions.isEmpty()) null else myPredictions.get(myPredictions.size - 1)

    private val visiblePredictions: MutableList<out TypeAheadPrediction?>
        get() {
            var lastVisiblePredictionIndex = 0
            while (lastVisiblePredictionIndex < myPredictions.size
                && myPredictions.get(lastVisiblePredictionIndex)?.myIsNotTentative == true
            ) {
                lastVisiblePredictionIndex++
            }
            lastVisiblePredictionIndex--

            return if (lastVisiblePredictionIndex >= 0) myPredictions.subList(
                0,
                lastVisiblePredictionIndex + 1
            ) else mutableListOf<TypeAheadPrediction>()
        }

    private fun updateLeftMostCursorPosition(cursorX: Int) {
        val leftMost = myLeftMostCursorPosition
        myLeftMostCursorPosition = if (leftMost == null) {
            cursorX
        } else {
            min(leftMost, cursorX)
        }
    }

    private fun resetState() {
        myTerminalModel.clearPredictions()
        myPredictions.clear()
        myLeftMostCursorPosition = null
        myLastSuccessfulPrediction = null
        myIsNotPasswordPrompt = false
        myClearPredictionsDebouncer?.terminateCall()
    }

    private fun reevaluatePredictorState(hasTypedRecently: Boolean) {
        if (!myTerminalModel.isTypeAheadEnabled) {
            myIsShowingPredictions = false
        } else if (myLatencyStatistics.sampleSize >= LATENCY_MIN_SAMPLES_TO_TURN_ON) {
            val latency = myLatencyStatistics.latencyMedian

            if (latency >= myTerminalModel.latencyThreshold) {
                myIsShowingPredictions = true
            } else if (latency < myTerminalModel.latencyThreshold * LATENCY_TOGGLE_OFF_THRESHOLD && !hasTypedRecently) {
                myIsShowingPredictions = false
            }
        }
    }

    private fun applyPredictions() {
        val predictions =
            this.visiblePredictions
        myTerminalModel.clearPredictions()
        for (prediction in predictions) {
            val predictedCursorX = prediction?.myPredictedLineWithCursorX?.myCursorX ?: continue
            if (prediction is CharacterPrediction) {
                myTerminalModel.insertCharacter(prediction.myCharacter, predictedCursorX - 1)
                myTerminalModel.moveCursor(predictedCursorX)
            } else if (prediction is BackspacePrediction) {
                myTerminalModel.moveCursor(predictedCursorX)
                myTerminalModel.removeCharacters(predictedCursorX, prediction.myAmount)
            } else if (prediction is CursorMovePrediction) {
                myTerminalModel.moveCursor(predictedCursorX)
            } else if (prediction is DeletePrediction) {
                myTerminalModel.removeCharacters(predictedCursorX, 1)
            } else {
                throw IllegalStateException("Unsupported prediction type")
            }
        }
        myTerminalModel.forceRedraw()
    }

    private fun createPrediction(
        initialLineWithCursorX: TypeAheadTerminalModel.LineWithCursorX,
        keyEvent: TypeAheadEvent
    ): TypeAheadPrediction {
        if (this.lastPrediction is HardBoundary) {
            return HardBoundary()
        }

        val newLineWCursorX: TypeAheadTerminalModel.LineWithCursorX?
        val lastPrediction =
            this.lastPrediction
        if (lastPrediction != null) {
            newLineWCursorX = lastPrediction.myPredictedLineWithCursorX.copy()
        } else {
            newLineWCursorX = initialLineWithCursorX.copy()
        }

        when (keyEvent.myEventType) {
            TypeAheadEvent.EventType.Character -> {
                if (newLineWCursorX.myCursorX >= myTerminalModel.terminalWidth) {
                    return HardBoundary()
                }

                val hasCharacterPredictions = myPredictions.stream()
                    .anyMatch { prediction: TypeAheadPrediction? -> prediction is CharacterPrediction }

                val ch = keyEvent.characterOrNull
                checkNotNull(ch) { "KeyEvent type is Character but keyEvent.myCharacter == null" }

                if (newLineWCursorX.myLineText.length < newLineWCursorX.myCursorX) {
                    newLineWCursorX.myLineText.append(" ".repeat(newLineWCursorX.myCursorX - newLineWCursorX.myLineText.length))
                }
                newLineWCursorX.myLineText.insert(newLineWCursorX.myCursorX, ch)
                newLineWCursorX.myCursorX++

                if (newLineWCursorX.myLineText.length > myTerminalModel.terminalWidth) {
                    newLineWCursorX.myLineText.delete(
                        myTerminalModel.terminalWidth,
                        newLineWCursorX.myLineText.length
                    )
                }

                return CharacterPrediction(
                    newLineWCursorX, ch,
                    (myIsNotPasswordPrompt || hasCharacterPredictions) && myIsShowingPredictions
                )
            }

            TypeAheadEvent.EventType.Backspace -> {
                if (newLineWCursorX.myCursorX == 0) {
                    return HardBoundary()
                }

                newLineWCursorX.myCursorX--
                if (newLineWCursorX.myCursorX < newLineWCursorX.myLineText.length) {
                    newLineWCursorX.myLineText.deleteCharAt(newLineWCursorX.myCursorX)
                }
                return BackspacePrediction(
                    newLineWCursorX, 1,
                    myLeftMostCursorPosition?.let { it <= newLineWCursorX.myCursorX } == true && myIsShowingPredictions
                )
            }

            TypeAheadEvent.EventType.AltBackspace -> {
                val oldCursorX = newLineWCursorX.myCursorX
              myTerminalModel.shellType?.let { newLineWCursorX.moveToWordBoundary(false, it) }

                if (newLineWCursorX.myCursorX < 0) {
                    return HardBoundary()
                }
                val amount = oldCursorX - newLineWCursorX.myCursorX

                if (newLineWCursorX.myCursorX < newLineWCursorX.myLineText.length) {
                    newLineWCursorX.myLineText.delete(
                        newLineWCursorX.myCursorX,
                        min(oldCursorX, newLineWCursorX.myLineText.length)
                    )
                }
                return BackspacePrediction(
                    newLineWCursorX, amount,
                    myLeftMostCursorPosition?.let { it <= newLineWCursorX.myCursorX } == true && myIsShowingPredictions
                )
            }

            TypeAheadEvent.EventType.LeftArrow, TypeAheadEvent.EventType.RightArrow -> {
                val amount = if (keyEvent.myEventType == TypeAheadEvent.EventType.RightArrow) 1 else -1
                newLineWCursorX.myCursorX += amount

                if (newLineWCursorX.myCursorX < 0 || (newLineWCursorX.myCursorX
                            >= max(newLineWCursorX.myLineText.length + 1, myTerminalModel.terminalWidth))
                ) {
                    return HardBoundary()
                }

                return CursorMovePrediction(
                    newLineWCursorX, amount,
                    myLeftMostCursorPosition?.let { it <= newLineWCursorX.myCursorX } == true && newLineWCursorX.myCursorX <= newLineWCursorX.myLineText.length && myIsShowingPredictions
                )
            }

            TypeAheadEvent.EventType.AltLeftArrow, TypeAheadEvent.EventType.AltRightArrow -> {
                val oldCursorX = newLineWCursorX.myCursorX
              myTerminalModel.shellType?.let {
                newLineWCursorX.moveToWordBoundary(
                  keyEvent.myEventType == TypeAheadEvent.EventType.AltRightArrow,
                  it
                )
              }

                if (newLineWCursorX.myCursorX < 0 || (newLineWCursorX.myCursorX
                            >= max(newLineWCursorX.myLineText.length + 1, myTerminalModel.terminalWidth))
                ) {
                    return HardBoundary()
                }
                val amount = newLineWCursorX.myCursorX - oldCursorX

                return CursorMovePrediction(
                    newLineWCursorX, amount,
                    myLeftMostCursorPosition?.let { it <= newLineWCursorX.myCursorX } == true && newLineWCursorX.myCursorX <= newLineWCursorX.myLineText.length && myIsShowingPredictions
                )
            }

            TypeAheadEvent.EventType.Delete -> {
                if (newLineWCursorX.myCursorX < newLineWCursorX.myLineText.length) {
                    newLineWCursorX.myLineText.deleteCharAt(newLineWCursorX.myCursorX)
                }
                return DeletePrediction(newLineWCursorX, myIsShowingPredictions)
            }

            TypeAheadEvent.EventType.Home -> {
                val leftMost = myLeftMostCursorPosition ?: return HardBoundary()
                val amount = leftMost - newLineWCursorX.myCursorX
                newLineWCursorX.myCursorX = leftMost
                return CursorMovePrediction(newLineWCursorX, amount, myIsShowingPredictions)
            }

            TypeAheadEvent.EventType.End -> {
                var newCursorPosition = newLineWCursorX.myLineText.length
                if (newCursorPosition == myTerminalModel.terminalWidth) {
                    newCursorPosition--
                }
                val amount = newCursorPosition - newLineWCursorX.myCursorX
                newLineWCursorX.myCursorX = newLineWCursorX.myLineText.length
                return CursorMovePrediction(newLineWCursorX, amount, myIsShowingPredictions)
            }

            TypeAheadEvent.EventType.Unknown -> return HardBoundary()
        }
    }

    abstract class TypeAheadPrediction(
        val myPredictedLineWithCursorX: TypeAheadTerminalModel.LineWithCursorX,
        val myIsNotTentative: Boolean
    ) {
        val myCreatedTime: Long

        init {
            myCreatedTime = System.nanoTime()
        }
    }

    private class HardBoundary :
        TypeAheadPrediction(TypeAheadTerminalModel.LineWithCursorX(StringBuffer(), -100), false)

    private class CharacterPrediction(
        predictedLineWithCursorX: TypeAheadTerminalModel.LineWithCursorX,
        val myCharacter: Char,
        isNotTentative: Boolean
    ) : TypeAheadPrediction(predictedLineWithCursorX, isNotTentative)

    private class BackspacePrediction(
        predictedLineWithCursorX: TypeAheadTerminalModel.LineWithCursorX,
        val myAmount: Int,
        isNotTentative: Boolean
    ) : TypeAheadPrediction(predictedLineWithCursorX, isNotTentative)

    private class DeletePrediction(
        predictedLineWithCursorX: TypeAheadTerminalModel.LineWithCursorX,
        isNotTentative: Boolean
    ) : TypeAheadPrediction(predictedLineWithCursorX, isNotTentative)

    private class CursorMovePrediction(
        predictedLineWithCursorX: TypeAheadTerminalModel.LineWithCursorX,
        val myAmount: Int,
        isNotTentative: Boolean
    ) : TypeAheadPrediction(predictedLineWithCursorX, isNotTentative)

    companion object {
        val MAX_TERMINAL_DELAY: Long = TimeUnit.MILLISECONDS.toNanos(1500)
        private const val LATENCY_MIN_SAMPLES_TO_TURN_ON = 2
        private const val LATENCY_TOGGLE_OFF_THRESHOLD = 0.5

        private val LOG: Logger = LoggerFactory.getLogger(TerminalTypeAheadManager::class.java)
    }
}
