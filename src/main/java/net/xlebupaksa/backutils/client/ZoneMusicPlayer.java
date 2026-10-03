package net.xlebupaksa.backutils.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.xlebupaksa.backutils.BackUtils;
import net.xlebupaksa.backutils.network.ZoneMusicPayload;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Plays the music for the zones this client is standing in. Looping is applied by the engine itself:
 * a {@link SoundInstance} with {@code looping = true} and the default zero delay loops natively, with
 * no re-trigger and no gap. {@code canStartSilent()} is required rather than cosmetic, because these
 * loops start at volume zero and fade up.
 *
 * <p>Playback is non-positional, so the zone is heard at its full volume anywhere inside it. The
 * instance is a {@link TickableSoundInstance}, which the engine ticks by itself: calling
 * {@code queueTickingSound} as well would run every fade at double speed.
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = BackUtils.MOD_ID, value = Dist.CLIENT)
@SuppressWarnings("unused") // entry points: the game bus and the loader call these
public final class ZoneMusicPlayer {

    /** The loops currently held, keyed by zone id. */
    private static final Map<Long, Loop> ACTIVE = new HashMap<>();

    /** Sound ids already reported as missing, so a bad id complains once rather than forever. */
    private static final Set<String> REPORTED = new HashSet<>();

    private ZoneMusicPlayer() {}

    /** Client-side handler for {@link ZoneMusicPayload}. */
    public static void apply(ZoneMusicPayload payload) {
        if (payload.stop()) {
            stop(payload.zoneId(), ZoneMusicPayload.Fade.OUT_TICKS);
        } else {
            play(payload);
        }
    }

    private static void play(ZoneMusicPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();

        ResourceLocation location = ResourceLocation.tryParse(payload.sound());
        if (location == null) {
            report(payload.sound(), "is not a valid sound id");
            return;
        }

        // Only this side can check this: sounds.json is client-side, so whether an id exists —
        // especially one added by a resourcepack — is only knowable here.
        if (minecraft.getSoundManager().getSoundEvent(location) == null) {
            report(location.toString(),
                    "has no sound on this client. A resourcepack may be missing.");
            return;
        }

        Loop existing = ACTIVE.get(payload.zoneId());
        if (existing != null) {
            // Already playing this zone: retune rather than restart, so an edited zone does not
            // stutter.
            existing.retarget(payload.volume(), payload.pitch(), ZoneMusicPayload.Fade.IN_TICKS);
            return;
        }

        Loop loop = new Loop(payload.zoneId(), location, sourceByName(payload.source()));
        loop.retarget(payload.volume(), payload.pitch(), ZoneMusicPayload.Fade.IN_TICKS);
        ACTIVE.put(payload.zoneId(), loop);

        // play() registers the tickable instance and starts the fade from silence.
        minecraft.getSoundManager().play(loop);
    }

    private static void stop(long zoneId, int fadeOutTicks) {
        Loop loop = ACTIVE.remove(zoneId);
        if (loop != null) loop.fadeOut(fadeOutTicks);
    }

    /** Silences everything. Called on disconnect, where the held loops are gone anyway. */
    public static void clear() {
        for (Loop loop : ACTIVE.values()) {
            loop.fadeOut(1);
        }
        ACTIVE.clear();
        REPORTED.clear();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    private static void report(String sound, String problem) {
        if (!REPORTED.add(sound)) return;
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        player.displayClientMessage(
                Component.literal("§7[Zone music] " + sound + " " + problem), false);
    }

    /** {@return the named sound channel, or {@code music} if the name is not one}, since the channel
     * decides which volume slider the music obeys. */
    private static SoundSource sourceByName(String name) {
        if (name != null) {
            for (SoundSource source : SoundSource.values()) {
                if (source.getName().equalsIgnoreCase(name)) return source;
            }
        }
        return SoundSource.MUSIC;
    }

    /** One zone's loop: the engine re-reads a ticking instance's volume every tick, so mutating the
     * field in {@link #tick()} is all a fade needs. */
    private static final class Loop extends AbstractSoundInstance implements TickableSoundInstance {

        private final long zoneId;
        private float target;
        private float step;
        private float pitch;
        private boolean stopped;

        Loop(long zoneId, ResourceLocation location, SoundSource source) {
            super(location, source, SoundInstance.createUnseededRandom());
            this.zoneId = zoneId;
            this.looping = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.relative = true;
            this.volume = 0.0F;
            this.pitch = 1.0F;
        }

        void retarget(float volume, float pitch, int fadeInTicks) {
            this.target = Math.max(0.0F, volume);
            this.pitch = pitch;
            this.stopped = false;
            float difference = this.target - this.volume;
            this.step = Math.abs(difference) / Math.max(1, fadeInTicks) * Math.signum(difference);
            if (this.step == 0.0F) this.volume = this.target;
        }

        void fadeOut(int fadeOutTicks) {
            this.target = 0.0F;
            this.step = -Math.max(0.0F, this.volume) / Math.max(1, fadeOutTicks);
            if (this.step == 0.0F) {
                this.volume = 0.0F;
                this.stopped = true;
            }
        }

        @Override
        public void tick() {
            if (stopped) return;

            float next = volume + step;
            boolean arrived = step > 0 ? next >= target : next <= target;

            if (arrived) {
                volume = target;
                step = 0.0F;
                if (target <= 0.0F) {
                    // Stop explicitly rather than waiting to be reaped, so the channel is released
                    // at the moment the fade ends.
                    Minecraft.getInstance().getSoundManager().stop(this);
                    stopped = true;
                }
            } else {
                volume = next;
            }
        }

        @Override
        public boolean isStopped() {
            return stopped;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public boolean isLooping() {
            return true;
        }

        long zoneId() {
            return zoneId;
        }
    }
}
