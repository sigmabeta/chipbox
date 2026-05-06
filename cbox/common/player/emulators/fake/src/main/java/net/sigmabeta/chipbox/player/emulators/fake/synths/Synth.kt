package net.sigmabeta.chipbox.player.emulators.fake.synths

interface Synth {
    fun generate(timeMillis: Double, frequency: Double, amplitude: Double): Short
}