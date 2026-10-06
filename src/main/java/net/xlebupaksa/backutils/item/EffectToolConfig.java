package net.xlebupaksa.backutils.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;
import java.util.Locale;

/**
 * Everything one effect tool is set to, held as a data component on the item.
 *
 * <p>The record is the border: nothing outside may build one that is out of range, so the codec,
 * the configuration screen and the drawing code all see values they can use without checking them
 * again. The codec sanitises on the way in as well, because an item written by another build — or
 * edited by hand — is not something this one may trust.
 *
 * <p>The two sentinels share the value {@code -1}: {@link #INFINITE_LIFETIME} where a lifetime is
 * expected and {@link #PLAYER_REACH} where a distance is. Each field reads the one that belongs to
 * it, so the same number means two different things without ever being ambiguous.
 */
public record EffectToolConfig(
        Mode mode,
        AutoRotate autoRotate,
        int lifetimeTicks,
        int maxDistance,
        String effectPath,
        Triplet scale,
        Triplet rotation,
        Triplet offset,
        boolean forceDeath,
        int delayTicks) {

    /** The shortest and longest lifetime a tool may ask for, in ticks. */
    public static final int MIN_LIFETIME = 1;
    public static final int MAX_LIFETIME = 9_999_999;
    /** Written in place of a lifetime: the effect is never removed by the tool. */
    public static final int INFINITE_LIFETIME = -1;

    public static final int MIN_DISTANCE = 0;
    public static final int MAX_DISTANCE = 100;
    /** Written in place of a distance: the effect lands where the player can reach. */
    public static final int PLAYER_REACH = -1;

    public static final int MIN_DELAY = 0;
    public static final int MAX_DELAY = 72_000;
    /**
     * The longest an effect path may be. Room for any id an effect could have, and no room for a
     * component that carries a document into every client the item is ever shown to.
     */
    public static final int MAX_PATH_LENGTH = 256;
    /** How far an offset may move an effect from where it was aimed, up or down. */
    public static final int MAX_OFFSET = 100;
    public static final int MIN_ROTATION = -360;
    public static final int MAX_ROTATION = 360;

    private static final Codec<Double> FINITE_DOUBLE = Codec.DOUBLE
            .validate(value -> Double.isFinite(value)
                    ? DataResult.success(value)
                    : DataResult.error(
                            () -> "An effect tool value must be a finite number, not " + value));

    private static final Codec<Triplet> TRIPLET_CODEC = FINITE_DOUBLE.listOf(3, 3)
            .xmap(Triplet::from, Triplet::values);

    public static final Codec<EffectToolConfig> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Mode.CODEC.fieldOf("mode").forGetter(EffectToolConfig::mode),
                    // Optional rather than required: a tool configured by an earlier build carries no
                    // such field, and a required one would refuse to decode the whole component,
                    // which would quietly turn every tool an operator already holds blank again.
                    AutoRotate.CODEC.optionalFieldOf("auto_rotate", AutoRotate.DEFAULT)
                            .forGetter(EffectToolConfig::autoRotate),
                    Codec.INT.fieldOf("lifetime").forGetter(EffectToolConfig::lifetimeTicks),
                    Codec.INT.fieldOf("distance").forGetter(EffectToolConfig::maxDistance),
                    Codec.STRING.fieldOf("path").forGetter(EffectToolConfig::effectPath),
                    TRIPLET_CODEC.fieldOf("scale").forGetter(EffectToolConfig::scale),
                    TRIPLET_CODEC.fieldOf("rotation").forGetter(EffectToolConfig::rotation),
                    TRIPLET_CODEC.fieldOf("offset").forGetter(EffectToolConfig::offset),
                    Codec.BOOL.fieldOf("force_death").forGetter(EffectToolConfig::forceDeath),
                    Codec.INT.fieldOf("delay").forGetter(EffectToolConfig::delayTicks))
            .apply(instance, EffectToolConfig::of));

    /**
     * Derived rather than written out: a component's stream codec is only reached through the codec
     * it is built from, so a hand-written one could drift out of step with the stored shape.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, EffectToolConfig> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(CODEC);

    /** The tool as it is given out: nothing chosen, and to be configured before it is used. */
    public static final EffectToolConfig BLANK = new EffectToolConfig(
            Mode.NONE, AutoRotate.DEFAULT, INFINITE_LIFETIME, PLAYER_REACH, "",
            Triplet.ONE, Triplet.ZERO, Triplet.ZERO, false, MIN_DELAY);

    /**
     * {@return the configuration with every value inside its range, and no auto-rotation chosen}
     *
     * <p>The shorter way in for the many callers that have no opinion about auto-rotation: a screen
     * writing a tool it did not touch, and a test building one to ask about something else. It fills
     * the field with {@link AutoRotate#DEFAULT} rather than {@code NONE}, because a stored none is
     * not the same tool as a stored default once an auto-rotate mode reads it.
     */
    public static EffectToolConfig of(Mode mode, int lifetimeTicks, int maxDistance,
                                      String effectPath,
                                      Triplet scale, Triplet rotation, Triplet offset,
                                      boolean forceDeath, int delayTicks) {
        return of(mode, AutoRotate.DEFAULT, lifetimeTicks, maxDistance, effectPath, scale, rotation,
                offset, forceDeath, delayTicks);
    }

    /**
     * {@return the configuration with every value inside its range}
     *
     * <p>The one path that builds a configuration, so a stored or hand-edited value is corrected
     * once, here, rather than at every use.
     */
    public static EffectToolConfig of(Mode mode, AutoRotate autoRotate, int lifetimeTicks,
                                      int maxDistance,
                                      String effectPath,
                                      Triplet scale, Triplet rotation, Triplet offset,
                                      boolean forceDeath, int delayTicks) {
        return new EffectToolConfig(
                mode == null ? Mode.NONE : mode,
                autoRotate == null ? AutoRotate.DEFAULT : autoRotate,
                clampLifetime(lifetimeTicks),
                clampDistance(maxDistance),
                clampPath(effectPath),
                // One rather than nothing: an effect at no scale is an effect nobody can see, so zero
                // is the one value a scale may not default to. It stays a value an operator can type,
                // which is why the default is filled in here rather than zero being read as unset.
                scale == null ? Triplet.ONE : scale.sanitised(),
                rotation == null ? Triplet.ZERO : new Triplet(
                        clamp(rotation.x(), (double) MIN_ROTATION, (double) MAX_ROTATION),
                        clamp(rotation.y(), (double) MIN_ROTATION, (double) MAX_ROTATION),
                        clamp(rotation.z(), (double) MIN_ROTATION, (double) MAX_ROTATION)),
                offset == null ? Triplet.ZERO : new Triplet(
                        clamp(offset.x(), (double) -MAX_OFFSET, (double) MAX_OFFSET),
                        clamp(offset.y(), (double) -MAX_OFFSET, (double) MAX_OFFSET),
                        clamp(offset.z(), (double) -MAX_OFFSET, (double) MAX_OFFSET)),
                forceDeath,
                clamp(delayTicks, MIN_DELAY, MAX_DELAY));
    }

    /** {@return this configuration, with any stored value that is out of range corrected} */
    public EffectToolConfig sanitised() {
        return of(mode, autoRotate, lifetimeTicks, maxDistance, effectPath, scale, rotation, offset,
                forceDeath, delayTicks);
    }

    /** {@return true when the effect is never removed rather than living for a number of ticks} */
    public boolean isInfinite() {
        return lifetimeTicks == INFINITE_LIFETIME;
    }

    /** {@return true when the effect lands within the player's reach rather than at a distance} */
    public boolean usesPlayerReach() {
        return maxDistance == PLAYER_REACH;
    }

    /** {@return true until a mode has been chosen}, which is how a freshly given tool reads */
    public boolean isBlank() {
        return mode == Mode.NONE;
    }

    /** {@return true when the effect is rotated at all}, which is what the renderer asks about */
    public boolean isRotated() {
        return !rotation.equals(Triplet.ZERO);
    }

    public EffectToolConfig withMode(Mode newMode) {
        return of(newMode, autoRotate, lifetimeTicks, maxDistance, effectPath, scale, rotation,
                offset, forceDeath, delayTicks);
    }

    /**
     * {@return this configuration with another auto-rotation chosen}
     *
     * <p>{@link AutoRotate#NONE} is a value this accepts: it is what a tool that attaches without
     * auto-rotation stores, and what keeps the mode and the rotation independently editable.
     */
    public EffectToolConfig withAutoRotate(AutoRotate newAutoRotate) {
        return of(mode, newAutoRotate, lifetimeTicks, maxDistance, effectPath, scale, rotation,
                offset, forceDeath, delayTicks);
    }

    public EffectToolConfig withLifetime(int ticks) {
        return of(mode, autoRotate, ticks, maxDistance, effectPath, scale, rotation, offset,
                forceDeath, delayTicks);
    }

    public EffectToolConfig withDistance(int distance) {
        return of(mode, autoRotate, lifetimeTicks, distance, effectPath, scale, rotation, offset,
                forceDeath, delayTicks);
    }

    public EffectToolConfig withEffectPath(String path) {
        return of(mode, autoRotate, lifetimeTicks, maxDistance, path, scale, rotation, offset,
                forceDeath, delayTicks);
    }

    public EffectToolConfig withScale(Triplet newScale) {
        return of(mode, autoRotate, lifetimeTicks, maxDistance, effectPath, newScale, rotation,
                offset, forceDeath, delayTicks);
    }

    public EffectToolConfig withRotation(Triplet newRotation) {
        return of(mode, autoRotate, lifetimeTicks, maxDistance, effectPath, scale, newRotation,
                offset, forceDeath, delayTicks);
    }

    public EffectToolConfig withOffset(Triplet newOffset) {
        return of(mode, autoRotate, lifetimeTicks, maxDistance, effectPath, scale, rotation,
                newOffset, forceDeath, delayTicks);
    }

    public EffectToolConfig withForceDeath(boolean death) {
        return of(mode, autoRotate, lifetimeTicks, maxDistance, effectPath, scale, rotation, offset,
                death, delayTicks);
    }

    public EffectToolConfig withDelay(int ticks) {
        return of(mode, autoRotate, lifetimeTicks, maxDistance, effectPath, scale, rotation, offset,
                forceDeath, ticks);
    }

    /**
     * {@return these settings as the text a database column can hold}
     *
     * <p>The component's own codec writes them rather than a hand-written format, so a setting added
     * to the tool is stored without this needing to change, and a stored tool can never be read back
     * as something the codec would have refused.
     */
    public String toStoredText() {
        return CODEC.encodeStart(NbtOps.INSTANCE, this)
                .result()
                .map(Tag::toString)
                .orElse("");
    }

    /**
     * {@return the settings a stored text describes}, or {@link #BLANK} when it describes nothing
     *
     * <p>A row that cannot be read gives the blank tool rather than throwing: one unreadable effect
     * must not be able to stop a server from starting, and the blank tool places nothing, so a bad
     * row draws no effect rather than a wrong one.
     */
    public static EffectToolConfig fromStoredText(String text) {
        if (text == null || text.isBlank()) return BLANK;
        try {
            CompoundTag tag = TagParser.parseTag(text);
            return CODEC.parse(NbtOps.INSTANCE, tag).result().orElse(BLANK);
        } catch (Exception e) {
            return BLANK;
        }
    }

    private static int clampLifetime(int ticks) {
        return ticks == INFINITE_LIFETIME
                ? INFINITE_LIFETIME : clamp(ticks, MIN_LIFETIME, MAX_LIFETIME);
    }

    private static int clampDistance(int distance) {
        return distance == PLAYER_REACH
                ? PLAYER_REACH : clamp(distance, MIN_DISTANCE, MAX_DISTANCE);
    }

    /** {@return the path without the space a paste brings, cut to the length a path may have} */
    private static String clampPath(String path) {
        if (path == null) return "";
        String trimmed = path.trim();
        return trimmed.length() <= MAX_PATH_LENGTH
                ? trimmed : trimmed.substring(0, MAX_PATH_LENGTH);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Which of the tool's ways of choosing a point is in use.
     *
     * <p>{@link #NONE} is not one of the spec's modes: it is how a tool that has just been
     * given out says that nothing has been chosen yet.
     */
    public enum Mode {

        NONE("backutils.effect_tool.mode.none"),
        BLOCK("backutils.effect_tool.mode.block"),
        BLOCK_SIDE("backutils.effect_tool.mode.block_side"),
        ENTITY_AUTO_ROTATE("backutils.effect_tool.mode.entity_auto_rotate"),
        ENTITY("backutils.effect_tool.mode.entity"),
        SELF_AUTO_ROTATE("backutils.effect_tool.mode.self_auto_rotate"),
        SELF("backutils.effect_tool.mode.self"),
        ACCURATE("backutils.effect_tool.mode.accurate");

        /** The order the selector offers them in, which is the spec's own. */
        public static final List<Mode> ALL = List.of(NONE, BLOCK, BLOCK_SIDE, ENTITY_AUTO_ROTATE,
                ENTITY, SELF_AUTO_ROTATE, SELF, ACCURATE);

        /**
         * Stored by name rather than by ordinal: an ordinal would silently change meaning the day a
         * mode is inserted in the middle of this list, and every existing tool would follow it.
         */
        public static final Codec<Mode> CODEC = Codec.STRING.comapFlatMap(Mode::byName,
                mode -> mode.name().toLowerCase(Locale.ROOT));

        private final String key;

        Mode(String key) {
            this.key = key;
        }

        /** {@return the translation key this mode is named by}, which its selector entry draws */
        public String key() {
            return key;
        }

        /**
         * {@return the mode a stored name stands for}, failing at a name it does not know
         *
         * <p>A mode is written down under two different vocabularies and they are not
         * interchangeable. <b>{@link #name}</b> is what a world holds, written by {@link #CODEC} and
         * read back by this method. <b>{@link #key}</b> is what a screen shows, and what a selector
         * hands back when one of its entries is picked.
         *
         * <p>Reading one as the other fails <em>quietly</em>: it is not a crash and not a wrong value
         * but a lookup that answers nothing, so the caller's fallback is what gets stored. That is
         * how a mode dropdown came to store {@link #NONE} whatever was picked from it, which left a
         * tool with no preview and nothing to place — so the two lookups are kept apart and named
         * for the vocabulary they take.
         */
        public static DataResult<Mode> byName(String name) {
            for (Mode mode : values()) {
                if (mode.name().equalsIgnoreCase(name)) return DataResult.success(mode);
            }
            return DataResult.error(() -> "Unknown effect tool mode '" + name + "'");
        }

        /** {@return the mode a screen's translation key stands for}, failing at a key it does not know */
        public static DataResult<Mode> byKey(String key) {
            for (Mode mode : values()) {
                if (mode.key.equals(key)) return DataResult.success(mode);
            }
            return DataResult.error(() -> "Unknown effect tool mode key '" + key + "'");
        }
    }

    /**
     * How an attached effect is turned as the world moves on: the parameter the spec's auto-rotate
     * modes are set with.
     *
     * <p>Our own type rather than Photon's, and named for Photon's values because it is the argument
     * Photon is finally handed. The integration is a reflection-backed seam that is only loaded when
     * Photon is present, so a tool whose mode names one of these has to be able to say which one it
     * means without Photon's classes on the classpath at all.
     *
     * <p>Stored by name, as the mode is, so reordering this list cannot change what a world holds.
     */
    public enum AutoRotate {

        /** No auto-rotation: the effect keeps the rotation it was given and stays there. */
        NONE("backutils.effect_tool.autorotate.none"),
        /** Turns to face the way the effect's own owner is facing. */
        FORWARD("backutils.effect_tool.autorotate.forward"),
        /** Turns to look at whoever is nearest, which is how a beam follows a player. */
        LOOK("backutils.effect_tool.autorotate.look"),
        /** Turns about its own x axis, which is what the configured rotation is a rotation in. */
        XROT("backutils.effect_tool.autorotate.xrot");

        /**
         * What a tool that has chosen nothing auto-rotates by, and what
         * {@code ENTITY_AUTO_ROTATE} is promoted to when it is handed a stored {@link #NONE}: the
         * spec gives that mode no none, and a mode that quietly did nothing is worse than a
         * default an operator can see and change.
         */
        public static final AutoRotate DEFAULT = XROT;

        /** The order the selector offers them in: none first, then the three real ones. */
        public static final List<AutoRotate> ALL = List.of(NONE, FORWARD, LOOK, XROT);

        /** Stored by name, for the same reason the mode is. */
        public static final Codec<AutoRotate> CODEC = Codec.STRING.comapFlatMap(AutoRotate::byName,
                autoRotate -> autoRotate.name().toLowerCase(Locale.ROOT));

        private final String key;

        AutoRotate(String key) {
            this.key = key;
        }

        /** {@return the translation key this value is named by}, which its selector entry draws */
        public String key() {
            return key;
        }

        /**
         * {@return the value a stored name stands for}, failing at a name it does not know
         *
         * <p>Reads {@link #NONE} back as itself rather than promoting it: what a world holds is one
         * thing, and what a mode finally attaches with is another. The promotion belongs to the mode
         * and lives with the rest of the placement arithmetic.
         *
         * <p>Names are what a world holds and keys are what a screen shows, as {@link Mode#byName}
         * sets out at length; the two are read apart here for the same reason.
         */
        public static DataResult<AutoRotate> byName(String name) {
            for (AutoRotate autoRotate : values()) {
                if (autoRotate.name().equalsIgnoreCase(name)) return DataResult.success(autoRotate);
            }
            return DataResult.error(() -> "Unknown effect tool auto-rotation '" + name + "'");
        }

        /** {@return the value a screen's translation key stands for}, failing at a key it does not know */
        public static DataResult<AutoRotate> byKey(String key) {
            for (AutoRotate autoRotate : values()) {
                if (autoRotate.key.equals(key)) return DataResult.success(autoRotate);
            }
            return DataResult.error(() -> "Unknown effect tool auto-rotation key '" + key + "'");
        }
    }

    /**
     * Three numbers of the same kind: a scale, a rotation in degrees, or an offset in blocks.
     *
     * <p>Its own type rather than three fields, so the screen can offer one row of three boxes and
     * a test can compare a whole row at once.
     */
    public record Triplet(double x, double y, double z) {

        public static final Triplet ZERO = new Triplet(0.0D, 0.0D, 0.0D);

        /**
         * The value a scale starts at, which no other triplet does.
         *
         * <p>A rotation and an offset of nothing are both real and useful; a scale of nothing is an
         * effect too small to see, so a scale has to begin at its own size.
         */
        public static final Triplet ONE = new Triplet(1.0D, 1.0D, 1.0D);

        /** {@return the triplet a decoded list stands for}, padding a short one with zeroes */
        public static Triplet from(List<Double> values) {
            return new Triplet(number(values, 0), number(values, 1), number(values, 2));
        }

        /** {@return a scale whose three values are usable}, a scale having no natural range */
        public Triplet sanitised() {
            return new Triplet(finite(x), finite(y), finite(z));
        }

        public List<Double> values() {
            return List.of(x, y, z);
        }

        /** {@return the three values as one line}, which is how the boxes beside them read */
        public String asText() {
            return number(x) + " " + number(y) + " " + number(z);
        }

        private static double number(List<Double> values, int index) {
            Double value = index < values.size() ? values.get(index) : null;
            return value == null ? 0.0D : value;
        }

        /** {@return the value, or zero when it is not a number an effect can be scaled by} */
        private static double finite(double value) {
            return Double.isFinite(value) ? value : 0.0D;
        }

        private static String number(double value) {
            if (value == Math.floor(value) && Math.abs(value) < 1.0e9D) {
                return String.valueOf((long) value);
            }
            String formatted = String.format(Locale.ROOT, "%.3f", value);
            while (formatted.endsWith("0")) {
                formatted = formatted.substring(0, formatted.length() - 1);
            }
            return formatted.endsWith(".")
                    ? formatted.substring(0, formatted.length() - 1) : formatted;
        }
    }
}
