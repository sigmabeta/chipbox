package net.sigmabeta.chipbox.player.generator.fake.synths

interface Synth {
    fun generate(timeMillis: Double, frequency: Double, amplitude: Double): Short
}