package net.sigmabeta.chipbox.player.emulators.fake.models

private const val FREQ_C = 16.35f
private const val FREQ_CSHARP = 17.32f
private const val FREQ_D = 18.35f
private const val FREQ_DSHARP = 19.45f
private const val FREQ_E = 20.60f
private const val FREQ_F = 21.83f
private const val FREQ_FSHARP = 23.12f
private const val FREQ_G = 24.50f
private const val FREQ_GSHARP = 25.96f
private const val FREQ_A = 27.50f
private const val FREQ_ASHARP = 29.14f
private const val FREQ_B = 30.87f

enum class PitchClass(val frequency: Float) {
    C(FREQ_C),
    CSHARP(FREQ_CSHARP),
    D(FREQ_D),
    DSHARP(FREQ_DSHARP),
    E(FREQ_E),
    F(FREQ_F),
    FSHARP(FREQ_FSHARP),
    G(FREQ_G),
    GSHARP(FREQ_GSHARP),
    A(FREQ_A),
    ASHARP(FREQ_ASHARP),
    B(FREQ_B)
}
