package net.xlebupaksa.backutils.data;

import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.xlebupaksa.backutils.BackUtils;

import java.util.function.Supplier;

public final class ModAttachments {

    public static final DeferredRegister<AttachmentType<?>> REGISTRY =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, BackUtils.MOD_ID);

    public static final Supplier<AttachmentType<ProfileSnapshot>> PROFILE =
            REGISTRY.register("profile_snapshot", () ->
                    AttachmentType.builder(() -> ProfileSnapshot.DEFAULT)
                            .serialize(ProfileSnapshot.CODEC)
                            .sync(ProfileSnapshot.STREAM_CODEC)
                            .copyOnDeath()
                            .build()
            );

    /**
     * The choices the owning client may offer, derived from the server databases. Not serialized
     * and not copied on death: it is re-pushed on login and on respawn by {@code ProfileLoader}.
     */
    public static final Supplier<AttachmentType<ProfileOptions>> OPTIONS =
            REGISTRY.register("profile_options", () ->
                    AttachmentType.builder(() -> ProfileOptions.EMPTY)
                            .sync(ProfileOptions.STREAM_CODEC)
                            .build()
            );

    private ModAttachments() {}
}