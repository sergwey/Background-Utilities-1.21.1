package net.xlebupaksa.backutils.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.xlebupaksa.backutils.BackUtils;
import net.xlebupaksa.backutils.BackUtilsConfig;
import net.xlebupaksa.backutils.data.BackUtilsData;
import net.xlebupaksa.backutils.data.ChatProfile;
import net.xlebupaksa.backutils.data.ProfileOptions;
import net.xlebupaksa.backutils.profile.ProfileLoader;
import net.xlebupaksa.backutils.profile.ProfileMarkup;
import net.xlebupaksa.backutils.profile.ProfileSound;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Both ends of the profile traffic.
 *
 * <p>Every check happens on the server, on the way in: the sender is read from the connection,
 * so no payload can name somebody else, and nothing arriving in {@link ProfileEditPayload} is
 * believed. The editor's copy of the checks is a courtesy. Registered optional like the log and
 * zone traffic, so a client without these payloads still connects and has no profile tab.
 */
@EventBusSubscriber(modid = BackUtils.MOD_ID)
@SuppressWarnings("unused") // entry points: the game bus and the loader call these
public final class ProfileNetwork {

    private ProfileNetwork() {}

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(BackUtils.MOD_ID).optional();

        registrar.playToServer(
                ProfileEditPayload.TYPE,
                ProfileEditPayload.STREAM_CODEC,
                ProfileNetwork::onEdit);

        registrar.playToServer(
                ProfileListRequestPayload.TYPE,
                ProfileListRequestPayload.STREAM_CODEC,
                ProfileNetwork::onListRequest);

        registrar.playToClient(
                ProfileListPayload.TYPE,
                ProfileListPayload.STREAM_CODEC,
                ProfileNetwork::onList);

        registrar.playToClient(
                ProfileFeedbackPayload.TYPE,
                ProfileFeedbackPayload.STREAM_CODEC,
                ProfileNetwork::onFeedback);
    }

    /** Asks the server for this player's profiles rather than reusing the last fetch. */
    public static void requestList() {
        PacketDistributor.sendToServer(new ProfileListRequestPayload());
    }

    /** Sends one change. The server decides whether it happens. */
    public static void send(ProfileEditPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    private static void onList(ProfileListPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof net.minecraft.client.player.LocalPlayer)) return;
        ProfileListCache.set(payload.rows(), payload.sounds());
    }

    private static void onFeedback(ProfileFeedbackPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof net.minecraft.client.player.LocalPlayer)) return;
        ProfileFeedbackCache.push(payload.ok(), payload.message());
    }

    private static void onListRequest(ProfileListRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        sendList(player);
    }

    private static void onEdit(ProfileEditPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;

        try {
            BackUtilsData data = BackUtils.data();
            if (data == null) {
                reply(player, payload.name(), false,
                        "Profile storage is unavailable — see the server log.");
                return;
            }

            switch (payload.kind()) {
                case ProfileEditPayload.CREATE -> create(player, payload, data);
                case ProfileEditPayload.UPDATE -> update(player, payload, data);
                case ProfileEditPayload.DELETE -> delete(player, payload, data);
                case ProfileEditPayload.USE -> use(player, payload, data);
                default -> reply(player, payload.name(), false, "Unknown profile request.");
            }
        } catch (Exception e) {
            BackUtils.LOGGER.error("Could not apply a profile change from {}",
                    player.getName().getString(), e);
            reply(player, payload.name(), false,
                    "Something went wrong on the server; see its log.");
        }
    }

    private static void create(ServerPlayer player, ProfileEditPayload payload, BackUtilsData data) {
        String playerName = player.getName().getString();
        boolean staff = isStaff(player);

        ProfileMarkup.Result name = ProfileMarkup.validateName(payload.name());
        if (!name.ok()) {
            reply(player, payload.name(), false, name.error());
            return;
        }
        if (data.chats().findByPlayerAndName(playerName, name.value()).isPresent()) {
            reply(player, name.value(), false,
                    "You already have a profile called '" + name.value() + "'.");
            return;
        }
        // The cap stops a player filling the database from their own menu; an operator's window
        // has no cap, so refusing here would only send them elsewhere to do the same thing.
        if (!staff
                && data.chats().findByPlayer(playerName).size() >= ProfileMarkup.MAX_PROFILES_PER_PLAYER) {
            reply(player, name.value(), false, "You already have "
                    + ProfileMarkup.MAX_PROFILES_PER_PLAYER + " profiles — the limit.");
            return;
        }

        ProfileMarkup.Result format = formatOf(payload, staff);
        if (!format.ok()) {
            reply(player, name.value(), false, format.error());
            return;
        }
        String sound = resolveSound(player, payload, name.value(), staff);
        if (sound == null) return;

        data.chats().create(name.value(), format.value(), sound, playerName);
        ProfileLoader.reload(player);
        reply(player, name.value(), true, "Saved '" + name.value() + "'.");
        sendList(player);
    }

    private static void update(ServerPlayer player, ProfileEditPayload payload, BackUtilsData data) {
        String playerName = player.getName().getString();
        String name = payload.name() == null ? "" : payload.name().trim();
        boolean staff = isStaff(player);

        if (data.chats().findByPlayerAndName(playerName, name).isEmpty()) {
            reply(player, name, false, "You have no profile called '" + name + "'.");
            return;
        }

        // The rename happens first, so everything below works with the name the profile will
        // have; the row id does not change, so an active profile stays active across it.
        ProfileMarkup.Result wanted = ProfileMarkup.validateName(payload.newName());
        if (!wanted.ok()) {
            reply(player, name, false, wanted.error());
            return;
        }
        String finalName = wanted.value();
        if (!finalName.equals(name)) {
            if (data.chats().findByPlayerAndName(playerName, finalName).isPresent()) {
                reply(player, name, false,
                        "You already have a profile called '" + finalName + "'.");
                return;
            }
            data.chats().rename(playerName, name, finalName);
        }

        ProfileMarkup.Result format = formatOf(payload, staff);
        if (!format.ok()) {
            reply(player, finalName, false, format.error());
            return;
        }
        String sound = resolveSound(player, payload, finalName, staff);
        if (sound == null) return;

        data.chats().update(finalName, format.value(), sound, playerName);
        ProfileLoader.reload(player);
        reply(player, finalName, true, "Updated '" + finalName + "'.");
        sendList(player);
    }

    private static void delete(ServerPlayer player, ProfileEditPayload payload, BackUtilsData data) {
        String playerName = player.getName().getString();
        String name = payload.name() == null ? "" : payload.name().trim();

        Optional<ChatProfile> profile = data.chats().findByPlayerAndName(playerName, name);
        if (profile.isEmpty()) {
            reply(player, name, false, "You have no profile called '" + name + "'.");
            return;
        }

        long id = profile.get().id();
        data.chats().delete(name, playerName);
        if (ProfileLoader.activeChatId(playerName) == id) {
            data.active().clearActiveChat(playerName);
        }

        ProfileLoader.reload(player);
        reply(player, name, true, "Deleted '" + name + "'.");
        sendList(player);
    }

    private static void use(ServerPlayer player, ProfileEditPayload payload, BackUtilsData data) {
        String playerName = player.getName().getString();
        String name = payload.name() == null ? "" : payload.name().trim();

        if (ProfileOptions.DEFAULT_PROFILE.equalsIgnoreCase(name)) {
            data.active().clearActiveChat(playerName);
            ProfileLoader.reload(player);
            reply(player, ProfileOptions.DEFAULT_PROFILE, true,
                    "Back to the plain chat format.");
            sendList(player);
            return;
        }

        Optional<ChatProfile> profile = data.chats().findByPlayerAndName(playerName, name);
        if (profile.isEmpty()) {
            reply(player, name, false, "You have no profile called '" + name + "'.");
            return;
        }

        data.active().setActiveChat(playerName, profile.get().id());
        ProfileLoader.reload(player);
        reply(player, name, true, "Now using '" + name + "'.");
        sendList(player);
    }

    /**
     * {@return the format to store, checked}
     *
     * <p>Advanced mode sends the finished format, otherwise the server composes it from the
     * controls. A player's goes through the allow-list, an operator's only its shape, per
     * {@link ProfileMarkup#validateStaff}.
     */
    private static ProfileMarkup.Result formatOf(ProfileEditPayload payload, boolean staff) {
        if (staff) {
            return payload.advanced()
                    ? ProfileMarkup.validateStaff(payload.inner(), false)
                    : ProfileMarkup.composeStaff(payload.colour(), payload.bold(), payload.italic(),
                            payload.underline(), payload.strikethrough(), payload.inner(), false);
        }
        if (payload.advanced()) return ProfileMarkup.validate(payload.inner());
        return ProfileMarkup.compose(payload.colour(), payload.bold(), payload.italic(),
                payload.underline(), payload.strikethrough(), payload.inner());
    }

    /** {@return the sound to store, or null when the choice was refused} */
    private static String resolveSound(ServerPlayer player, ProfileEditPayload payload, String name,
                                       boolean staff) {
        // The palette stops a player picking a sound that is startling or far too long; an
        // operator's markup can reach any sound id anyway, so theirs only has to be a sound.
        if (staff) {
            String problem = ProfileSound.checkStaff(payload.sound());
            if (problem != null) {
                reply(player, name, false, problem);
                return null;
            }
            return ProfileSound.normaliseStaff(payload.sound(), BackUtilsConfig.getProfileSounds());
        }

        List<String> palette = BackUtilsConfig.getProfileSounds();
        String problem = ProfileSound.check(payload.sound(), palette);
        if (problem != null) {
            reply(player, name, false, problem);
            return null;
        }
        return ProfileSound.normalise(payload.sound(), palette);
    }

    /** {@return true when this player may write formats without the players' restrictions} */
    private static boolean isStaff(ServerPlayer player) {
        return player.hasPermissions(ProfileLoader.PROFILE_PERMISSION_LEVEL);
    }

    public static void sendList(ServerPlayer player) {
        if (!canReceive(player)) return;

        String playerName = player.getName().getString();
        long activeId = ProfileLoader.activeChatId(playerName);

        List<ProfileListPayload.Row> rows = new ArrayList<>();
        for (ChatProfile profile : ProfileLoader.chatProfilesOf(playerName)) {
            rows.add(new ProfileListPayload.Row(
                    profile.name(),
                    profile.format(),
                    profile.sound(),
                    activeId > 0L && profile.id() == activeId));
        }

        PacketDistributor.sendToPlayer(player,
                new ProfileListPayload(rows, BackUtilsConfig.getProfileSounds()));
    }

    /** Answers the change: to the server log, and to the window that asked, never to chat. */
    private static void reply(ServerPlayer player, String name, boolean ok, String message) {
        // A refusal always says something: an empty one leaves a window showing nothing.
        String said = message == null || message.isBlank()
                ? "The change was refused, without a reason." : message;

        if (ok) {
            BackUtils.LOGGER.info("[profile] {}: {}", player.getName().getString(), said);
        } else {
            BackUtils.LOGGER.info("[profile] refused for {}: {}",
                    player.getName().getString(), said);
        }
        if (canReceive(player)) {
            PacketDistributor.sendToPlayer(player,
                    new ProfileFeedbackPayload(ok, name == null ? "" : name, said));
        }
    }

    /** {@return true when this client negotiated the profile channel} */
    public static boolean canReceive(ServerPlayer player) {
        return OptionalChannels.negotiated(player, ProfileListPayload.TYPE);
    }
}
