package net.sigmabeta.chipbox.ui.util.transition.nonshared

class FadeOutDownTransition(stagger: Boolean, fragment: Boolean) : NonSharedTransition(stagger, fragment) {
    override fun getDistanceScaler() = 1
}
