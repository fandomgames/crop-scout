package com.farrah.sproutvision.analyze

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the SAM literal-scale note in the Spark prompt: without it a
 * healthy full tray (SAM ~15-20%) reads as sparse. The wording is pinned
 * exactly — prompt contracts with the model change deliberately, and the
 * 15-25% band must track fresh golden runs if measurement ever changes.
 */
class MetaAnalyzerTest {

    @Test
    fun `scale note states the literal band and grower-scale reporting`() {
        assertEquals(
            "SAM counts literal leaf pixels: a full healthy microgreen tray measures " +
                "about 15-25% (golden-tray calibration), so never read a low SAM number " +
                "as sparse on its own. Judge health against this measured scale, and report " +
                "coveragePercent as grower-perceived canopy (0-100), reconciling the photo " +
                "with the measurement.",
            samScaleNote()
        )
    }
}
