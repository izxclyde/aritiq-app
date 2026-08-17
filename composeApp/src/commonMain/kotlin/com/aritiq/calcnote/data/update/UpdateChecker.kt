package com.aritiq.calcnote.data.update

data class UpdateInfo(val versionCode: Int, val downloadUrl: String)

expect fun checkForUpdate(): UpdateInfo?

expect fun downloadAndInstallUpdate(context: Any, update: UpdateInfo): Boolean

expect fun canRequestPackageInstalls(context: Any): Boolean

expect fun openUnknownAppSources(context: Any)