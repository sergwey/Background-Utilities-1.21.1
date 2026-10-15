package net.xlebupaksa.backutils.chat;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.xlebupaksa.backutils.data.MarkupUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * What a player writes in place of an item, in this mod's own chat line: the word a trigger names is taken
 * out of the message, and what that slot holds is drawn under the message on a line of its own, with the item
 * itself as a hover.
 *
 * <p><b>Why a component and not markup.</b> The plugin hands the client a real chat component whose hover is
 * a {@code show_item} event, and everything after that is the client's own work: it builds the item's tooltip
 * from the stack — the name and the lore — and draws it. None of that needs a mod on the client, which is
 * what separates this from the Inline syntax drawn elsewhere in this mod, and it is the whole reason the
 * substitution belongs in the chat line rather than in a client-side text renderer.
 *
 * <p><b>Why the item is not left in the sentence.</b> An item is a component of its own, and a component
 * between two stretches of text parts the line into two literals. Ember reveals a line one literal at a time,
 * each schedule counted from that literal's own first character, so words left either side of an item would be
 * typed by two reveals at once, each starting again at the item — the reveal a player sees restart. Taking the
 * item out of the sentence and drawing it underneath leaves one stretch of words for one reveal to type, and
 * the item whole where nothing can part it: its own words, its own tooltip, drawn as it stands rather than
 * typed out with the message.
 *
 * <p>The blanks the trigger stood between are stitched, so that a sentence keeps one space between its parts,
 * and a line that asks for an item and says nothing else is the item alone, with no empty line above it.
 *
 * <p><b>Why the trigger is marked and resolved apart.</b> A chat line is not the message alone. The message
 * is put into a profile's format, which is markup; the finished body may then be wrapped in Ember's typewriter
 * tag; and every written {@code \n} becomes a real break with the tags open at the break reopened under it.
 * Each of those rewrites the string, so the trigger is not replaced where it is found: it is <em>marked</em> in
 * the message, travels through all of it as characters nothing else looks at, and is resolved once the body is
 * final.
 *
 * <p>Marking the message rather than the whole body is deliberate, and is what the plugin does: it rewrites
 * what the player typed. A profile's format is the operator's own text, so a trigger written in a format is
 * drawn as it stands rather than becoming an item on every line that profile dresses.
 *
 * <p><b>Where the boundary is.</b> A trigger touching a letter or a digit is left as text, so no word is cut
 * in half by an item; a trigger inside a tag is left as the word it was typed as, because a tag is not text;
 * and a slot with nothing in it draws the game's own word for what is there, without a hover. Each of those is
 * reasoned at the method that makes it.
 */
public final class ShowItem {

    /**
     * One word and the slot it stands for, in the order the plugin replaces them. Written out rather than
     * derived from the slot's name, because the main hand is the one slot the plugin gives more than one word
     * to: {@code [item]}, {@code [i]} and {@code [hand]} all mean the held item.
     */
    private record Trigger(String token, EquipmentSlot slot) {}

    private static final List<Trigger> TRIGGERS = List.of(
            new Trigger("[item]", EquipmentSlot.MAINHAND),
            new Trigger("[i]", EquipmentSlot.MAINHAND),
            new Trigger("[hand]", EquipmentSlot.MAINHAND),
            new Trigger("[offhand]", EquipmentSlot.OFFHAND),
            new Trigger("[head]", EquipmentSlot.HEAD),
            new Trigger("[chest]", EquipmentSlot.CHEST),
            new Trigger("[legs]", EquipmentSlot.LEGS),
            new Trigger("[feet]", EquipmentSlot.FEET));

    /**
     * What stands for a trigger in a half-built line: a NUL followed by the trigger's index, the index
     * written as the control character above that number.
     *
     * <p>A control character because it has to survive string work that looks for markup, for spaces and for
     * breaks, and because the chat box refuses every character below a space — when it is typed and when it is
     * pasted, both through {@code StringUtil.filterText} — so a mark in a body is always one written here. The
     * index tells a mark from an unrelated NUL, and the pair never reaches a player, since {@link #compose} is
     * the only thing that reads it.
     *
     * <p>The index is a control character as well, rather than the digit it used to be, and that is what makes
     * the whole mark a thing no renderer draws: {@link MarkupUtil#drawsNothing} reads a control character as
     * nothing, which is what lets the wrapper carry the tags of a row that holds nothing but marks and tags to
     * the row that has words — see {@code MarkupWrap.carryTagOnlyRows}. A digit would be a drawn character,
     * and such a row would then read as a row with text on it.
     */
    private static final char MARK = '\u0000';

    /** The character the trigger at index 0 is written as; the ones above it are the triggers above it. */
    private static final char INDEX_BASE = '\u0001';

    private ShowItem() {}

    // ------------------------------------------------------------------
    // Marking the message
    // ------------------------------------------------------------------

    /**
     * {@return the message with every trigger replaced by a mark}
     *
     * <p>The words are matched exactly as the plugin matches them, case and all: {@code [ITEM]} is not a
     * trigger there and is not one here, and an unknown bracket such as {@code [item:diamond]} is ordinary
     * text. An untouched message is returned as it is, so a line that asks for no item is byte for byte the
     * line it was.
     */
    public static String mark(String message) {
        if (message == null || message.isEmpty() || message.indexOf('[') < 0) return message;

        StringBuilder marked = new StringBuilder(message.length() + 8);
        int i = 0;
        while (i < message.length()) {
            int index = message.charAt(i) == '[' ? triggerAt(message, i) : -1;
            if (index < 0 || !standsWhole(message, i, TRIGGERS.get(index).token().length())) {
                marked.append(message.charAt(i));
                i++;
                continue;
            }
            marked.append(MARK).append((char) (INDEX_BASE + index));
            i += TRIGGERS.get(index).token().length();
        }
        return marked.toString();
    }

    /** {@return the trigger written at this position, or -1 when none is} */
    private static int triggerAt(String message, int at) {
        for (int i = 0; i < TRIGGERS.size(); i++) {
            if (message.startsWith(TRIGGERS.get(i).token(), at)) return i;
        }
        return -1;
    }

    /**
     * {@return true when the trigger is not joined to a word on either side}
     *
     * <p>The plugin replaces the characters wherever they fall, which turns {@code x[item]y} into a word with
     * an item through the middle of it. A word does not survive being cut in half by a component, so a trigger
     * touching a letter or a digit is left as the text it was typed as. Everything else stands: punctuation,
     * markup and the ends of the message all read as a boundary, so {@code look,[item]!} is a trigger and
     * {@code [item]s} is not.
     */
    private static boolean standsWhole(String message, int at, int length) {
        int before = at - 1;
        int after = at + length;
        return (before < 0 || !Character.isLetterOrDigit(message.charAt(before)))
                && (after >= message.length() || !Character.isLetterOrDigit(message.charAt(after)));
    }

    // ------------------------------------------------------------------
    // Building the body
    // ------------------------------------------------------------------

    /**
     * {@return the body of a chat line as a component, each mark answered with what this player holds in the
     * slot it names}
     *
     * <p>A null player is the line with nobody behind it: there is no slot to read, so the words a message
     * asked an item for are drawn as the words they were, which is what the harnesses drive the composition
     * through and what stops a trigger being a crash when there is no player to ask.
     */
    public static Component compose(String body, ServerPlayer player) {
        if (player == null) return compose(body, (Function<EquipmentSlot, Component>) null);
        return compose(body, slot -> held(player.getItemBySlot(slot)));
    }

    /**
     * {@return the body of a line with every item taken out of its words and drawn under them}
     *
     * <p>The resolver is an argument so that the whole of it can be exercised without a server, which is the
     * part of this with rules of its own: which words are a trigger, where a tag ends, what stands on the
     * item's line, and what the blanks a trigger stood between become. A body that holds no mark is the
     * literal it always was, so a line that asks for no item draws exactly what it drew before; a null
     * resolver is a line with nothing to put an item in, so the words are written back and the body is that
     * literal too.
     *
     * <p>Several items are drawn together on the one line under the words, in the order they were asked for:
     * a message that names two hands has two things to show, and one line of them rather than two lines keeps
     * them together with the message they belong to.
     */
    public static Component compose(String body, Function<EquipmentSlot, Component> items) {
        if (body == null || body.isEmpty()) return Component.empty();
        // Nothing to resolve: the whole body is the one literal it has always been.
        if (body.indexOf(MARK) < 0) return Component.literal(body);
        // No resolver is nobody to read a slot from: the words are written back as they were typed.
        if (items == null) return Component.literal(unmarked(body));

        List<Component> asked = new ArrayList<>();
        StringBuilder words = new StringBuilder(body.length());
        int i = 0;
        while (i < body.length()) {
            char c = body.charAt(i);

            if (c == '<') {
                int end = body.indexOf('>', i + 1);
                if (end > 0 && isTag(body, i + 1, end)) {
                    // A mark inside a tag is not a trigger: an item put in one is a tag with a name in it
                    // rather than a tag, so the player's own word is written back and the tag is left whole.
                    words.append(unmarked(body.substring(i, end + 1)));
                    i = end + 1;
                    continue;
                }
                // A '<' with no tag after it is text, as the wrapper reads it too.
                words.append('<');
                i++;
                continue;
            }

            if (c == MARK) {
                int index = markIndex(body, i);
                if (index >= 0) {
                    asked.add(items.apply(TRIGGERS.get(index).slot()));
                    i = stitch(body, i + 2, words);
                    continue;
                }
                // A mark naming no trigger was not written here: every control character is stripped from
                // what a chat box takes, typed or pasted. It is dropped rather than drawn.
                i++;
                continue;
            }

            words.append(c);
            i++;
        }

        if (asked.isEmpty()) return Component.literal(words.toString());

        // The items stand under the words, on a line of their own. Words that draw nothing have no such line
        // to break: the tag characters of a format wrapped round an item and nothing else would be drawn as
        // the characters of the tags themselves, so the whole of what they hold goes and the items are the
        // line. A message that already ends in a break keeps it: the item line is the one under it, not one
        // further down.
        boolean said = !MarkupUtil.drawsNothing(words.toString());
        if (said && !MarkupUtil.strip(words.toString()).endsWith("\n")) words.append('\n');
        MutableComponent composed = Component.literal(said ? words.toString() : "");
        for (int n = 0; n < asked.size(); n++) {
            if (n > 0) composed.append(Component.literal(" "));
            composed.append(asked.get(n));
        }
        return composed;
    }

    /**
     * {@return where to read on, having written what stood between one trigger and the words around it}
     *
     * <p>The words either side of a trigger were written as one sentence with the trigger inside it, so taking
     * the trigger out leaves the blanks that separated it from them: two spaces where the sentence had one, or
     * a space before the comma the trigger stood in front of. The blanks before the trigger go, and the blanks
     * after it stand for the join — one space when they were a space, one break when they had a break, and
     * nothing at all when punctuation follows, which is what the trigger was standing in front of.
     *
     * <p>The one case with nothing to join: a trigger before any word, where a break the player wrote is
     * dropped with the blanks. That break was separating the item from the words after it, and the item's own
     * line is where it was leading; a break written <em>before</em> the trigger stands, because that one
     * separates the words from the item, and it is the line the item lands on rather than a blank one under
     * it. What counts as a word is what is drawn, not what is written: the tags of the format are in the words
     * already when the message's first trigger is reached, and they are not something to put a space or a
     * break after.
     */
    private static int stitch(String body, int at, StringBuilder words) {
        int end = words.length();
        boolean broke = false;
        while (end > 0 && Character.isWhitespace(words.charAt(end - 1))) {
            if (words.charAt(end - 1) == '\n') broke = true;
            end--;
        }
        words.setLength(end);

        int i = at;
        while (i < body.length() && Character.isWhitespace(body.charAt(i))) {
            if (body.charAt(i) == '\n') broke = true;
            i++;
        }
        if (!MarkupUtil.drawsNothing(words.toString()) && (broke || i > at)) {
            words.append(broke ? '\n' : ' ');
        }
        return i;
    }

    /** {@return the trigger the mark at this position names, or -1 when it names none} */
    private static int markIndex(String body, int at) {
        if (at + 1 >= body.length()) return -1;
        int index = body.charAt(at + 1) - INDEX_BASE;
        return index >= 0 && index < TRIGGERS.size() ? index : -1;
    }

    /**
     * {@return the text with every mark written back as the word it stood for}
     *
     * <p>For the one place a mark is not a trigger: inside a tag.
     */
    private static String unmarked(String text) {
        StringBuilder out = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            if (text.charAt(i) != MARK) {
                out.append(text.charAt(i));
                i++;
                continue;
            }
            int index = markIndex(text, i);
            if (index >= 0) {
                out.append(TRIGGERS.get(index).token());
                i += 2;
                continue;
            }
            i++;
        }
        return out.toString();
    }

    /**
     * {@return true when the text between two angle brackets is a tag}
     *
     * <p>The same reading as the wrapper's, deliberately: a tag the wrapper reopens at a line break is a tag
     * this leaves whole, so a mark written inside one is the word it was typed as in both. A name begins with a
     * letter, a digit or an underscore, and a {@code <} inside the brackets makes arithmetic rather than a tag,
     * so {@code 3 < 5} stays text.
     */
    private static boolean isTag(String text, int from, int to) {
        if (from >= to) return false;
        int index = text.charAt(from) == '/' ? from + 1 : from;
        if (index >= to) return false;
        char first = text.charAt(index);
        if (!Character.isLetterOrDigit(first) && first != '_') return false;
        int nested = text.indexOf('<', from);
        return nested < 0 || nested >= to;
    }

    // ------------------------------------------------------------------
    // The item
    // ------------------------------------------------------------------

    /**
     * {@return the words drawn for one slot, with the item itself as a hover}
     *
     * <p>The name is the stack's own hover name: the custom name when the item has one, and the game's own
     * translation key for the item when it has not. That is what makes this readable with no mod on the client
     * — the key is the game's, so the client translates it into its own language — and what carries a renamed
     * item's name to the reader with it.
     *
     * <p>The hover is the item and nothing else, as the plugin's is: neither the name nor the lore is written
     * into the line. The client builds that tooltip from the stack while the cursor is over the words, which is
     * why the lore is there on a client without this mod — and why the words on their own, in a chat log or a
     * screenshot, carry none of it.
     *
     * <p>An empty slot is drawn without a hover, because the plugin returns before it builds one. Its word is
     * the item the slot holds, which the game itself calls Air: the same word the plugin writes in English, and
     * the translated one everywhere else, with no key of this mod's for a client to be missing.
     */
    private static Component held(ItemStack stack) {
        MutableComponent drawn = Component.literal("[")
                .append(stack.getHoverName())
                .append(Component.literal("]"));
        if (stack.isEmpty()) return drawn;
        return drawn.withStyle(style -> style.withHoverEvent(
                new HoverEvent(HoverEvent.Action.SHOW_ITEM, new HoverEvent.ItemStackInfo(stack))));
    }
}
