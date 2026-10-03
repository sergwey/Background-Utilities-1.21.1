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
import net.xlebupaksa.backutils.data.NameProfile;
import net.xlebupaksa.backutils.data.ProfileOptions;
import net.xlebupaksa.backutils.profile.ProfileLoader;
import net.xlebupaksa.backutils.profile.ProfileMarkup;
import net.xlebupaksa.backutils.profile.ProfileSound;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The background menu's profile traffic: every player's profiles, seen from outside.
 *
 * <p>Unlike {@link ProfileNetwork}, this carries a target and is answered only for
 * {@link AdminNetwork#REQUIRED_LEVEL} or above; everything else is checked on the server, and the target
 * must resolve to a player the server knows. The tag allow-list is deliberately not applied - see
 * {@link ProfileMarkup#validateStaff(String, boolean)} - though the shape of a staff-written format is
 * still enforced.
 */
@EventBusSubscriber(modid = BackUtils.MOD_ID)
@SuppressWarnings("unused") // entry points: the game bus and the loader call these
public final class AdminProfileNetwork {

    private AdminProfileNetwork() {}

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(BackUtils.MOD_ID).optional();

        registrar.playToServer(
                AdminProfileRequestPayload.TYPE,
                AdminProfileRequestPayload.STREAM_CODEC,
                AdminProfileNetwork::onRequest);

        registrar.playToServer(
                AdminProfileEditPayload.TYPE,
                AdminProfileEditPayload.STREAM_CODEC,
                AdminProfileNetwork::onEdit);

        registrar.playToClient(
                AdminPlayerListPayload.TYPE,
                AdminPlayerListPayload.STREAM_CODEC,
                AdminProfileNetwork::onPlayers);

        registrar.playToClient(
                AdminProfileListPayload.TYPE,
                AdminProfileListPayload.STREAM_CODEC,
                AdminProfileNetwork::onProfiles);

        registrar.playToClient(
                AdminProfileFeedbackPayload.TYPE,
                AdminProfileFeedbackPayload.STREAM_CODEC,
                AdminProfileNetwork::onFeedback);
    }

    // The client's side

    /** Asks for the player list; one request answers for both sub-tabs, which share the same tables. */
    public static void requestPlayers() {
        PacketDistributor.sendToServer(new AdminProfileRequestPayload(false, ""));
    }

    public static void requestProfiles(boolean names, String player) {
        PacketDistributor.sendToServer(new AdminProfileRequestPayload(names, player));
    }

    /** Sends one change; the server decides whether it happens. */
    public static void send(AdminProfileEditPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    private static void onPlayers(AdminPlayerListPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof net.minecraft.client.player.LocalPlayer)) return;
        AdminProfileCache.setPlayers(payload.names(), payload.players());
    }

    private static void onProfiles(AdminProfileListPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof net.minecraft.client.player.LocalPlayer)) return;
        AdminProfileCache.setProfiles(payload.names(), payload.player(), payload.rows(),
                payload.sounds());
    }

    private static void onFeedback(AdminProfileFeedbackPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof net.minecraft.client.player.LocalPlayer)) return;
        AdminProfileFeedbackCache.push(payload.ok(), payload.names(), payload.player(),
                payload.message());
    }

    // The server's side

    private static void onRequest(AdminProfileRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer admin)) return;
        if (!AdminNetwork.mayAdminister(admin)) return;

        BackUtilsData data = BackUtils.data();
        if (data == null) return;

        if (payload.player().isBlank()) {
            sendPlayers(admin, data);
            return;
        }

        String target = resolveTarget(admin, data, payload.player());
        if (target == null) {
            feedback(admin, false, payload.names(), payload.player(),
                    "No player called '" + payload.player() + "' here вЂ— online, or with a profile.");
            return;
        }
        sendProfiles(admin, data, payload.names(), target);
    }

    private static void onEdit(AdminProfileEditPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer admin)) return;

        // Before anything else, before its own fields are read: the target is why this payload exists.
        if (!AdminNetwork.mayAdminister(admin)) {
            feedback(admin, false, payload.names(), payload.player(),
                    "You are not allowed to edit other players' profiles.");
            return;
        }

        BackUtilsData data = BackUtils.data();
        if (data == null) {
            feedback(admin, false, payload.names(), payload.player(),
                    "Profile storage is unavailable вЂ— see the server log.");
            return;
        }

        try {
            String target = resolveTarget(admin, data, payload.player());
            if (target == null) {
                feedback(admin, false, payload.names(), payload.player(),
                        "No player called '" + payload.player() + "' here вЂ— online, or with a "
                                + "profile.");
                return;
            }

            String problem = switch (payload.operation()) {
                case AdminProfileEditPayload.CREATE -> create(data, payload, target);
                case AdminProfileEditPayload.UPDATE -> update(data, payload, target);
                case AdminProfileEditPayload.DELETE -> delete(data, payload, target);
                case AdminProfileEditPayload.USE -> use(data, payload, target);
                default -> "Unknown profile request.";
            };

            if (problem != null) {
                feedback(admin, false, payload.names(), target, problem);
                return;
            }

            // Only now: a refused change must not have pushed anything to the player's client.
            pushToTarget(admin, target);
            feedback(admin, true, payload.names(), target, done(payload, target));

            // Re-sent rather than patched: the server decided what happened, and the counts have changed.
            sendProfiles(admin, data, payload.names(), target);
            sendPlayers(admin, data);
        } catch (Exception e) {
            BackUtils.LOGGER.error("Could not apply an admin profile change for {}",
                    admin.getName().getString(), e);
            feedback(admin, false, payload.names(), payload.player(),
                    "Something went wrong on the server; see its log.");
        }
    }

    // The four changes

    /** {@return null when the profile was stored, otherwise what to tell the administrator} */
    static String create(BackUtilsData data, AdminProfileEditPayload payload,
                         String target) {
        ProfileMarkup.Result name = ProfileMarkup.validateName(payload.name());
        if (!name.ok()) return name.error();
        String profile = name.value();

        ProfileMarkup.Result format = formatOf(payload);
        if (!format.ok()) return format.error();

        // No cap here, deliberately: the limit stops a player filling the database from their own
        // chat screen, while staff are trusted with the same operation through /profile.
        if (payload.names()) {
            if (data.names().findByPlayerAndName(target, profile).isPresent()) {
                return target + " already has a name profile called '" + profile + "'.";
            }
            data.names().create(profile, format.value(), target);
            return null;
        }

        if (data.chats().findByPlayerAndName(target, profile).isPresent()) {
            return target + " already has a chat profile called '" + profile + "'.";
        }
        String sound = soundProblem(payload);
        if (sound != null) return sound;

        data.chats().create(profile, format.value(), storedSound(payload), target);
        return null;
    }

    /**
     * {@return null when the profile was changed, otherwise what to tell the administrator; every
     * check runs before anything is written, so a refused format cannot leave a rename behind}
     */
    static String update(BackUtilsData data, AdminProfileEditPayload payload,
                                 String target) {
        String current = trim(payload.name());
        if (!exists(data, payload.names(), target, current)) {
            return target + " has no " + kind(payload) + " profile called '" + current + "'.";
        }

        ProfileMarkup.Result wanted = ProfileMarkup.validateName(payload.newName());
        if (!wanted.ok()) return wanted.error();
        String finalName = wanted.value();

        ProfileMarkup.Result format = formatOf(payload);
        if (!format.ok()) return format.error();

        // A name profile has no sound, and "" is a valid chat sound, so the check is skipped for names.
        if (!payload.names()) {
            String sound = soundProblem(payload);
            if (sound != null) return sound;
        }

        if (!finalName.equals(current)) {
            if (exists(data, payload.names(), target, finalName)) {
                return target + " already has a " + kind(payload) + " profile called '"
                        + finalName + "'.";
            }
            if (payload.names()) {
                data.names().rename(target, current, finalName);
            } else {
                data.chats().rename(target, current, finalName);
            }
        }

        if (payload.names()) {
            data.names().update(finalName, format.value(), target);
        } else {
            data.chats().update(finalName, format.value(), storedSound(payload), target);
        }
        return null;
    }

    /** {@return null when the profile was removed, otherwise what to tell the administrator} */
    static String delete(BackUtilsData data, AdminProfileEditPayload payload,
                                 String target) {
        String name = trim(payload.name());

        if (payload.names()) {
            Optional<NameProfile> profile = data.names().findByPlayerAndName(target, name);
            if (profile.isEmpty()) {
                return target + " has no name profile called '" + name + "'.";
            }
            long id = profile.get().id();
            data.names().delete(name, target);
            // By row id, so it is cleared by hand: a dangling id would come back if the id were reused.
            if (ProfileLoader.activeNameId(target) == id) {
                data.active().clearActiveName(target);
            }
            return null;
        }

        Optional<ChatProfile> profile = data.chats().findByPlayerAndName(target, name);
        if (profile.isEmpty()) {
            return target + " has no chat profile called '" + name + "'.";
        }
        long id = profile.get().id();
        data.chats().delete(name, target);
        if (ProfileLoader.activeChatId(target) == id) {
            data.active().clearActiveChat(target);
        }
        return null;
    }

    /** {@return null when the profile is now in use, otherwise what to tell the administrator} */
    static String use(BackUtilsData data, AdminProfileEditPayload payload, String target) {
        String name = trim(payload.name());

        if (ProfileOptions.DEFAULT_PROFILE.equalsIgnoreCase(name)) {
            if (payload.names()) {
                data.active().clearActiveName(target);
            } else {
                data.active().clearActiveChat(target);
            }
            return null;
        }

        if (payload.names()) {
            Optional<NameProfile> profile = data.names().findByPlayerAndName(target, name);
            if (profile.isEmpty()) {
                return target + " has no name profile called '" + name + "'.";
            }
            data.active().setActiveName(target, profile.get().id());
            return null;
        }

        Optional<ChatProfile> profile = data.chats().findByPlayerAndName(target, name);
        if (profile.isEmpty()) {
            return target + " has no chat profile called '" + name + "'.";
        }
        data.active().setActiveChat(target, profile.get().id());
        return null;
    }

    // Building the two answers

    /** Sends the player list for both kinds: everyone online, plus players with a profile of that kind. */
    private static void sendPlayers(ServerPlayer admin, BackUtilsData data) {
        Map<String, Integer> chatCounts = caseInsensitive(data.chats().countsByPlayer());
        Map<String, Integer> nameCounts = caseInsensitive(data.names().countsByPlayer());

        List<String> online = new ArrayList<>();
        if (admin.getServer() != null) {
            for (ServerPlayer player : admin.getServer().getPlayerList().getPlayers()) {
                online.add(player.getName().getString());
            }
        }
        online.sort(String.CASE_INSENSITIVE_ORDER);

        for (boolean names : new boolean[] {false, true}) {
            List<AdminPlayerListPayload.Player> players = new ArrayList<>();
            Set<String> added = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

            for (String name : online) {
                if (added.add(name)) {
                    players.add(new AdminPlayerListPayload.Player(name, true,
                            count(chatCounts, name), count(nameCounts, name)));
                }
            }

            // Offline players come last, and only those with a profile of the kind being listed.
            Set<String> withProfiles = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            withProfiles.addAll(names ? nameCounts.keySet() : chatCounts.keySet());
            for (String name : withProfiles) {
                if (added.add(name)) {
                    players.add(new AdminPlayerListPayload.Player(name, false,
                            count(chatCounts, name), count(nameCounts, name)));
                }
            }

            PacketDistributor.sendToPlayer(admin, new AdminPlayerListPayload(names, players));
        }
    }

    /** Sends one player's profiles, of one kind, with the sound palette. */
    private static void sendProfiles(ServerPlayer admin, BackUtilsData data, boolean names,
                                     String player) {
        List<ProfileListPayload.Row> rows = new ArrayList<>();

        if (names) {
            long active = ProfileLoader.activeNameId(player);
            for (NameProfile profile : data.names().findByPlayer(player)) {
                rows.add(new ProfileListPayload.Row(profile.name(), profile.displayedName(), "",
                        active > 0L && profile.id() == active));
            }
        } else {
            long active = ProfileLoader.activeChatId(player);
            for (ChatProfile profile : data.chats().findByPlayer(player)) {
                rows.add(new ProfileListPayload.Row(profile.name(), profile.format(),
                        profile.sound(), active > 0L && profile.id() == active));
            }
        }

        PacketDistributor.sendToPlayer(admin, new AdminProfileListPayload(names, player, rows,
                BackUtilsConfig.getProfileSounds()));
    }

    // Helpers

    /**
     * {@return the format to store, checked the way a staff-written format is; advanced mode sends the
     * finished format, otherwise the server composes it from the controls, so a client cannot hand over
     * a format that does not match the toggles it displayed}
     */
    private static ProfileMarkup.Result formatOf(AdminProfileEditPayload payload) {
        boolean names = payload.names();
        if (payload.advanced()) return ProfileMarkup.validateStaff(payload.inner(), names);

        return ProfileMarkup.composeStaff(payload.colour(), payload.bold(), payload.italic(),
                payload.underline(), payload.strikethrough(), payload.inner(), names);
    }

    /** {@return null when the chosen sound is usable, otherwise the reason} */
    private static String soundProblem(AdminProfileEditPayload payload) {
        return ProfileSound.checkStaff(payload.sound());
    }

    private static String storedSound(AdminProfileEditPayload payload) {
        return ProfileSound.normaliseStaff(payload.sound(), BackUtilsConfig.getProfileSounds());
    }

    private static boolean exists(BackUtilsData data, boolean names, String player, String name) {
        return names
                ? data.names().findByPlayerAndName(player, name).isPresent()
                : data.chats().findByPlayerAndName(player, name).isPresent();
    }

    /**
     * {@return the name to write under, or null when there is no such player; an online player's own
     * name wins, then a name already stored with a profile, then the server's cache of players it has
     * seen, so an arbitrary string cannot become a player}
     */
    private static String resolveTarget(ServerPlayer admin, BackUtilsData data, String raw) {
        String wanted = trim(raw);
        if (!isPlayerName(wanted)) return null;

        ServerPlayer online = admin.getServer() == null ? null
                : admin.getServer().getPlayerList().getPlayerByName(wanted);
        if (online != null) return online.getName().getString();

        for (String stored : data.chats().countsByPlayer().keySet()) {
            if (stored.equalsIgnoreCase(wanted)) return stored;
        }
        for (String stored : data.names().countsByPlayer().keySet()) {
            if (stored.equalsIgnoreCase(wanted)) return stored;
        }

        return admin.getServer() != null
                && admin.getServer().getProfileCache().get(wanted).isPresent() ? wanted : null;
    }

    /** {@return true for something shaped like an account name} */
    private static boolean isPlayerName(String value) {
        if (value.isEmpty() || value.length() > 16) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_') return false;
        }
        return true;
    }

    /** Pushes the change onto the player it was made for, when they are online. */
    private static void pushToTarget(ServerPlayer admin, String target) {
        ServerPlayer online = admin.getServer() == null ? null
                : admin.getServer().getPlayerList().getPlayerByName(target);
        if (online != null) ProfileLoader.reload(online);
    }

    /** {@return what to say after a change that happened} */
    private static String done(AdminProfileEditPayload payload, String target) {
        String name = trim(payload.newName());
        return switch (payload.operation()) {
            case AdminProfileEditPayload.CREATE -> "Saved '" + name + "' for " + target + ".";
            case AdminProfileEditPayload.UPDATE -> "Updated '" + name + "' for " + target + ".";
            case AdminProfileEditPayload.DELETE -> "Deleted '" + trim(payload.name()) + "' from "
                    + target + ".";
            case AdminProfileEditPayload.USE -> ProfileOptions.DEFAULT_PROFILE
                    .equalsIgnoreCase(name)
                    ? target + " is back on the plain " + kind(payload) + "."
                    : target + " is now using '" + name + "'.";
            default -> "Done.";
        };
    }

    private static String kind(AdminProfileEditPayload payload) {
        return payload.names() ? "name" : "chat";
    }

    /** {@return the counts, keyed the way a Minecraft name is compared} */
    private static Map<String, Integer> caseInsensitive(Map<String, Integer> counts) {
        Map<String, Integer> copy = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        copy.putAll(counts);
        return copy;
    }

    private static int count(Map<String, Integer> counts, String player) {
        return counts.getOrDefault(player, 0);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    /** Answers the change: to the server log, and to the window that asked, never to chat. */
    private static void feedback(ServerPlayer admin, boolean ok, boolean names, String player,
                                 String message) {
        String who = admin.getName().getString();
        // A refusal always says something: an empty message would leave a window with no message at all.
        String said = message == null || message.isBlank()
                ? "The change was refused, without a reason." : message;

        if (ok) {
            BackUtils.LOGGER.info("[admin] {}: {}", who, said);
        } else {
            BackUtils.LOGGER.info("[admin] refused for {}: {}", who, said);
        }

        if (canReceive(admin)) {
            PacketDistributor.sendToPlayer(admin, new AdminProfileFeedbackPayload(ok, names,
                    player == null ? "" : player, said));
        }
    }

    /** {@return true when this client negotiated the admin profile channel} */
    public static boolean canReceive(ServerPlayer player) {
        return OptionalChannels.negotiated(player, AdminProfileRequestPayload.TYPE);
    }
}
