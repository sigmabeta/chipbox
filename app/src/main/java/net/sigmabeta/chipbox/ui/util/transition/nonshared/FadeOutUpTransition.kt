package net.sigmabeta.chipbox.ui.util.transition.nonshared

class FadeOutUpTransition(stagger: Boolean) : NonSharedTransition(stagger) {
    override fun getDistanceScaler() = -1
}
