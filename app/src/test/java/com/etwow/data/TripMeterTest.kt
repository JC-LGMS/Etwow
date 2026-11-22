package com.etwow.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TripMeterTest {

    @Test
    fun `premiere lecture sans odometre precedent ne change rien`() {
        assertEquals(0.0, TripMeter.next(0.0, null, 931.9), 0.0001)
        assertEquals(12.3, TripMeter.next(12.3, null, 931.9), 0.0001)
    }

    @Test
    fun `les ecarts positifs s ajoutent`() {
        assertEquals(0.3, TripMeter.next(0.0, 931.9, 932.2), 0.0001)
        assertEquals(12.8, TripMeter.next(12.3, 931.9, 932.4), 0.0001)
    }

    @Test
    fun `un odometre identique ou qui recule ne retire rien`() {
        assertEquals(12.3, TripMeter.next(12.3, 931.9, 931.9), 0.0001)
        assertEquals(12.3, TripMeter.next(12.3, 931.9, 0.0), 0.0001)
    }
}
