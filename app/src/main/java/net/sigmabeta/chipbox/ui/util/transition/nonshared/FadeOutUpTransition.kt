package net.sigmabeta.chipbox.ui.util.transition.nonshared

class FadeOutUpTransition(stagger: Boolean, fragment: Boolean) : NonSharedTransition(stagger, fragment) {
    override fun getDistanceScaler() = -1
}
