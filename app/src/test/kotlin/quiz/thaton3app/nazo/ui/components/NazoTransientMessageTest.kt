package quiz.thaton3app.nazo.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NazoTransientMessageTest {

    @Test
    fun firstMessageBecomesTheRenderedExitValue() {
        assertEquals("Saved", retainLastTransientValue("Saved", retained = null))
    }

    @Test
    fun clearingCurrentMessageKeepsItsContentForTheExit() {
        assertEquals("Saved", retainLastTransientValue(current = null, retained = "Saved"))
    }

    @Test
    fun aReplacementMessageWinsImmediately() {
        assertEquals("Failed", retainLastTransientValue(current = "Failed", retained = "Saved"))
    }

    @Test
    fun emptyHostStaysEmpty() {
        assertNull(retainLastTransientValue<String>(current = null, retained = null))
    }
}
