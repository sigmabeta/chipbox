package net.sigmabeta.chipbox.ui.util.transition.nonshared

class FadeInFromAboveTransition(stagger: Boolean) : NonSharedTransition(stagger) {
    override fun getDistanceScaler() = -1
}
