package com.aritiq.calcnote.data.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Ponytail: this parse is the only thing standing between a new release and a silently disabled
 * update prompt. It lives in commonMain (not inside the androidMain `actual`) purely so it can be
 * tested — the format is enforced by .github/workflows/release.yml, not by the type system.
 */
class ReleaseVersionTest {

    @Test fun extracts_build_number_from_tag() {
        assertEquals(55, buildNumberFromTag("v0.1.0-55"))
        assertEquals(101, buildNumberFromTag("v0.2.0-101"))
    }

    @Test fun extracts_build_number_after_bumped_base() {
        // The base moves with each release; the build suffix must still win.
        assertEquals(54, buildNumberFromTag("v0.1.1-54"))
        assertEquals(60, buildNumberFromTag("v0.2.0-60"))
    }

    @Test fun tag_without_build_number_is_unknown_not_zero() {
        assertNull(buildNumberFromTag("v1.0.0"))
        assertNull(buildNumberFromTag("garbage"))
    }

    @Test fun prerelease_tag_is_unknown() {
        assertNull(buildNumberFromTag("v0.2.0-rc.1"))
        assertNull(buildNumberFromTag("v0.2.0-1.2"))
    }
}
