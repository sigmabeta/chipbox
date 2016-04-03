package net.sigmabeta.chipbox.ui.util.transition.nonshared

class FadeOutDownTransition(stagger: Boolean) : NonSharedTransition(stagger) {
    override fun getDistanceScaler() = 1
}
