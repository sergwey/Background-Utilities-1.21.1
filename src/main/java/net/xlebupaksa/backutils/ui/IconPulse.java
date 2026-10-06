package net.xlebupaksa.backutils.ui;

/**
 * The fade the alert icon is drawn with.
 *
 * <p>Why the fade is not left to the sprite's frames: the GUI sprite shader is
 * {@code position_tex_color}, whose fragment stage discards anything under alpha 0.1 <i>before</i>
 * the colour modulator is applied. A fade carried by the frames therefore ends in a cut rather than
 * reaching zero, and it is also the one channel a hand-drawn animation is least able to control —
 * every animated GUI sprite the game itself ships is fully opaque in every frame, and carries its
 * animation in its colour.
 *
 * <p>The modulator, by contrast, is applied after that test, so a fade expressed here slides all the
 * way down without being clipped. It is a cosine rather than a triangle so that the fade eases at
 * both ends, which is what stops a pulse reading as a blink.
 */
final class IconPulse {

    private IconPulse() {}

    /**
     * {@return the alpha to draw an icon with, between {@code floor} and one}
     *
     * @param seconds how long a whole fade down and back takes; zero or less means no fade, and the
     *                icon is drawn as its own frames have it
     * @param floor   how dim the icon is allowed to get — never zero, so that an alert is always
     *                visible on screen, which is what the icon is for
     */
    static float alpha(long nanos, double seconds, float floor) {
        if (seconds <= 0.0D) return 1.0F;

        float lowest = Math.max(0.0F, Math.min(1.0F, floor));
        double phase = (nanos / 1.0e9D) % seconds / seconds;
        // One at the start, the floor halfway through, one again at the end.
        double wave = 0.5D + 0.5D * Math.cos(phase * 2.0D * Math.PI);
        return (float) (lowest + (1.0D - lowest) * wave);
    }
}
