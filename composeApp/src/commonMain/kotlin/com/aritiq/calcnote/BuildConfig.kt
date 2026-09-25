package com.aritiq.calcnote

expect fun appVersion(): String

// Update checks hit GitHub Releases and prompt the user, which is noise in dev and blocks E2E runs.
expect fun isDebugBuild(): Boolean
