package app.expressiveclock.overlay

/** Unknown visibility is deliberately not evidence of immersive fullscreen. */
internal fun shouldHideIslandForFullscreen(statusBarVisible: Boolean?, navigationBarVisible: Boolean?,
    locked: Boolean, calibrationPreview: Boolean): Boolean =
    !locked && !calibrationPreview && statusBarVisible == false && navigationBarVisible == false
