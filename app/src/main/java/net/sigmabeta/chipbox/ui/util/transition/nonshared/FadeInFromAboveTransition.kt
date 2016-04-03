package net.sigmabeta.chipbox.ui.util.transition.nonshared

class FadeInFromAboveTransition(stagger: Boolean, fragment: Boolean) : NonSharedTransition(stagger, fragment) {
    override fun getDistanceScaler() = -1
}
