package com.aritiq.calcnote.data.update

/**
 * Release tags are `v<major>.<minor>.<patch>-<build>`, where the build suffix is the git commit
 * count and doubles as the Android `versionCode`.
 *
 * Update detection compares that integer against the installed build, so a tag without a trailing
 * number (a hand-written `v1.0.0`, a `v0.2.0-rc.1` prerelease) means "cannot tell" and must return
 * null. Guessing 0 there would make every tagged release look like an upgrade.
 */
fun buildNumberFromTag(tag: String): Int? = tag.substringAfterLast('-', "").toIntOrNull()
