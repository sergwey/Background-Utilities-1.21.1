package net.xlebupaksa.backutils.profile;

import net.minecraft.server.level.ServerPlayer;
import net.xlebupaksa.backutils.BackUtils;
import net.xlebupaksa.backutils.data.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ProfileLoader {

    public enum ReloadResult { UNCHANGED, UPDATED, RESET_TO_DEFAULT }

    public static final int PROFILE_PERMISSION_LEVEL = 2;

    private ProfileLoader() {}

    /** {@return the player's stored profile, or the default when they have none} */
    public static ProfileSnapshot load(String playerName) {
        BackUtilsData data = BackUtils.data();
        if (data == null) return ProfileSnapshot.DEFAULT;

        Optional<ActiveProfile> activeOpt = data.active().find(playerName);
        if (activeOpt.isEmpty()) return ProfileSnapshot.DEFAULT;

        ActiveProfile active = activeOpt.get();

        // A stale or foreign id must never be applied, so both lookups verify the profile's owner.
        ChatProfile chat = data.chats()
                .findById(active.chatProfileId())
                .filter(c -> playerName.equals(c.player()))
                .orElse(null);
        NameProfile name = active.nameProfileId() > 0
                ? data.names()
                        .findById(active.nameProfileId())
                        .filter(n -> playerName.equals(n.player()))
                        .orElse(null)
                : null;

        return new ProfileSnapshot(
                chat != null ? chat.id() : 0L,
                chat != null ? chat.name() : ProfileOptions.DEFAULT_PROFILE,
                chat != null ? chat.format() : ProfileSnapshot.DEFAULT.chatFormat(),
                chat != null ? chat.sound() : "",
                name != null ? name.id() : 0L,
                name != null ? name.name() : ProfileOptions.DEFAULT_PROFILE,
                name != null ? name.displayedName() : ProfileText.PLAYER
        );
    }

    /** {@return the choices to offer this player, and whether they may change them} */
    public static ProfileOptions optionsFor(ServerPlayer player) {
        BackUtilsData data = BackUtils.data();
        if (data == null) return ProfileOptions.EMPTY;

        String playerName = player.getName().getString();

        List<String> names = new ArrayList<>();
        names.add(ProfileOptions.DEFAULT_PROFILE);
        data.names().findByPlayer(playerName).forEach(profile -> names.add(profile.name()));

        List<String> chats = new ArrayList<>();
        chats.add(ProfileOptions.DEFAULT_PROFILE);
        data.chats().findByPlayer(playerName).forEach(profile -> chats.add(profile.name()));

        // Names stay behind the permission the /profile commands require, because they are how everyone else
        // reads the player; chat profiles are the player's own.
        return new ProfileOptions(names, chats,
                player.hasPermissions(PROFILE_PERMISSION_LEVEL), true);
    }

    /** Pushes the stored profile onto the player so their client can see it; both attachments are synced, which is what
     *  lets the chat overlay list and select profiles without databases on the client. */
    public static void push(ServerPlayer player, ProfileSnapshot snapshot) {
        player.setData(ModAttachments.PROFILE.get(), snapshot);
        player.setData(ModAttachments.OPTIONS.get(), optionsFor(player));
        NameApplier.apply(player);
    }

    public static ReloadResult reload(ServerPlayer player) {
        // The attachment is registered with a default, so it never answers null.
        ProfileSnapshot current = player.getData(ModAttachments.PROFILE.get());
        ProfileSnapshot fresh = load(player.getName().getString());

        ReloadResult result = current.matches(fresh)
                ? ReloadResult.UNCHANGED
                : fresh.equals(ProfileSnapshot.DEFAULT) ? ReloadResult.RESET_TO_DEFAULT : ReloadResult.UPDATED;

        // Always push, even when the active profile did not change: the set of profiles to choose from may have.
        push(player, fresh);
        return result;
    }

    /** {@return the name to write into the roleplay log for this player} — the roleplay name rather than the account
     *  name, with colour stripped because the log is plain white; other formatting, notably the underline, is kept. */
    public static String logName(String playerName) {
        String displayed = ProfileText.resolve(load(playerName).displayedName(), playerName);
        return MarkupUtil.stripColour(displayed);
    }

    /** {@return this player's own chat profiles, in the order the editor lists them} */
    public static List<ChatProfile> chatProfilesOf(String playerName) {
        BackUtilsData data = BackUtils.data();
        if (data == null) return List.of();
        return data.chats().findByPlayer(playerName);
    }

    /** {@return this player's own name profiles, in the order the menu lists them} */
    public static List<NameProfile> nameProfilesOf(String playerName) {
        BackUtilsData data = BackUtils.data();
        if (data == null) return List.of();
        return data.names().findByPlayer(playerName);
    }

    /** {@return the id of the player's active chat profile, or 0 when they have none} */
    public static long activeChatId(String playerName) {
        BackUtilsData data = BackUtils.data();
        if (data == null) return 0L;
        return data.active().find(playerName).map(ActiveProfile::chatProfileId).orElse(0L);
    }

    /** {@return the id of the player's active name profile, or 0 when they have none} */
    public static long activeNameId(String playerName) {
        BackUtilsData data = BackUtils.data();
        if (data == null) return 0L;
        return data.active().find(playerName).map(ActiveProfile::nameProfileId).orElse(0L);
    }

    /** {@return the format a player's messages are wrapped in, or "" for the plain default} — read from storage, because
     *  this is asked on the server while the message is being sent. */
    public static ChatProfile activeChat(String playerName) {
        BackUtilsData data = BackUtils.data();
        if (data == null) return null;

        long id = activeChatId(playerName);
        if (id <= 0L) return null;
        return data.chats().findById(id).filter(c -> playerName.equals(c.player())).orElse(null);
    }
}
