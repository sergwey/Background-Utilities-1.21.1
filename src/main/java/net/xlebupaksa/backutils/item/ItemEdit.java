package net.xlebupaksa.backutils.item;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.tysontheember.emberstextapi.immersivemessages.api.MarkupParser;
import net.tysontheember.emberstextapi.immersivemessages.api.TextSpan;
import net.tysontheember.emberstextapi.immersivemessages.effects.Effect;
import net.xlebupaksa.backutils.BackUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * The three things this mod lets an operator change about an item, and the arithmetic of changing
 * them.
 *
 * <p><b>They are not NBT.</b> On 1.21 a name, a lore and an item's attributes are data components —
 * {@code minecraft:custom_name}, {@code minecraft:lore} and {@code minecraft:attribute_modifiers} —
 * and the word NBT belongs to the mod this feature is modelled on rather than to this version of the
 * game.
 *
 * <p>Everything here answers without the game running: the lists are lists, the markup is parsed by
 * the library, and the one conversion that needs a registry — an attribute id to the attribute
 * itself — is deliberately left to the side that has one. That is what lets the arithmetic be tested
 * at all, and the arithmetic is where a wrong answer is silent: a lore line that moves to the wrong
 * place or an attribute that is added twice looks like a working editor until somebody reads the
 * item.
 */
public final class ItemEdit {

    private ItemEdit() {}

    // ------------------------------------------------------------------
    // The name and the lore: Ember markup in, a component out
    // ------------------------------------------------------------------

    /**
     * One run of text and the style it is shown in — what Ember's markup is made of, and what a
     * Minecraft {@link Style} is, said in a way this mod can hold and compare.
     *
     * <p>{@code null} colour and {@code null} font mean the item's own, which is not the same as
     * white and not the same as the default font: an item styled with neither keeps whatever the
     * client would have used, and writing a default over it would be this mod changing something
     * nobody asked it to change.
     */
    public record Styled(String content, TextColor colour, boolean bold, boolean italic,
                         boolean underline, boolean strikethrough, boolean obfuscated,
                         ResourceLocation font) {

        /** {@return this run as a component}, which is what an item's name and lore are made of */
        public Component toComponent() {
            Style style = Style.EMPTY
                    .withColor(colour)
                    .withBold(flag(bold))
                    .withItalic(flag(italic))
                    .withUnderlined(flag(underline))
                    .withStrikethrough(flag(strikethrough))
                    .withObfuscated(flag(obfuscated))
                    .withFont(font);
            return Component.literal(content == null ? "" : content).withStyle(style);
        }

        /** {@return the switch as the style wants it}: nothing said is not the same as said false. */
        private static Boolean flag(boolean on) {
            return on ? Boolean.TRUE : null;
        }
    }

    /** The colour every line this editor writes starts from, which is the one the game gives text with none. */
    private static final int DEFAULT_COLOUR = 0xFFFFFF;

    /**
     * The style every line this editor writes starts from: white, and not italic.
     *
     * <p>Neither of those is what the game would do by itself. An item that has been renamed has its name drawn
     * in its rarity's colour and in italic, and a line of lore in dark purple italic — the game merges both of
     * those into the line before it draws it — so a name typed here with nothing said about its colour would
     * come out yellow on an uncommon item and leaning to the right on every item. This is set on the component
     * itself, which is deeper than anything the library can do: the game's own style is applied <em>under</em>
     * this one, and a value said here wins.
     *
     * <p>It is a floor and not a ceiling. A colour chosen in the picker, a weight, a slant written by hand: the
     * markup is read over this, so what an operator says about their own text is what their text wears.
     */
    public static final Style DEFAULT_STYLE = Style.EMPTY
            .withColor(DEFAULT_COLOUR)
            .withItalic(false);

    /**
     * {@return one line of an item as the component the item is given}
     *
     * <p><b>The markup is carried as the text of the component</b>, and not parsed into styled runs. That is
     * what makes an effect possible at all: a colour and a weight are things a component can hold, but an
     * animation is not — the library turns an effect into an object as it reads the markup, and the only place
     * it can do that is while something is being drawn. So the text travels as it was typed, the library's own
     * hook on a literal parses it as the game draws it, and a rainbow is a rainbow. A line with no markup in it
     * is a plain literal and is drawn as one.
     *
     * <p>The cost is worth writing down: on a client without the library the tags are drawn as the text they
     * are, because nothing there parses them. That is the same trade the profile names already make, and the
     * only one available — a component that a client without the library can read is a component with no
     * effects in it.
     */
    public static Component line(String markup) {
        // Written in the form the library draws before it is handed over, so that a colour typed the other way
        // — which the library reads and then does not apply to a literal — arrives as the colour it says.
        return Component.literal(effectColours(markup)).withStyle(DEFAULT_STYLE);
    }

    /**
     * {@return the component an Ember markup string describes}
     *
     * <p>What a line of the item <em>says</em>, read the way the renderer reads it: no effects survive this,
     * because they are not text, and this is the reading a preview measures its characters in rather than the
     * reading an item is given. {@link #line} is the latter.
     */
    public static Component component(String markup) {
        return component(spans(markup));
    }

    /** {@return the component a list of runs makes, in the order they are given} */
    public static Component component(List<Styled> spans) {
        MutableComponent out = Component.empty();
        for (Styled span : spans) out.append(span.toComponent());
        return out;
    }

    /**
     * {@return the runs an Ember markup string describes}
     *
     * <p>The only code in this class that knows the library exists. Everything it does is copy fields
     * across, so that the conversion above is the thing under test rather than the parser: a parser
     * that changes shape should break this method and nothing else.
     *
     * <p>A string the parser refuses is shown as the text it is. An operator who typed something
     * unparseable meant to name their item that, and a name that vanishes because a bracket was left
     * open is worse than a name that is not styled.
     */
    public static List<Styled> spans(String markup) {
        if (markup == null || markup.isEmpty()) return List.of();
        try {
            List<Styled> out = new ArrayList<>();
            for (TextSpan span : MarkupParser.parse(markup)) {
                out.add(new Styled(span.getContent(), span.getColor(), truth(span.getBold()),
                        truth(span.getItalic()), truth(span.getUnderline()),
                        truth(span.getStrikethrough()), truth(span.getObfuscated()), span.getFont()));
            }
            return out;
        } catch (RuntimeException unparseable) {
            return List.of(new Styled(markup, null, false, false, false, false, false, null));
        }
    }

    /**
     * {@return whether the library can read this markup}
     *
     * <p>Asked before markup is handed to the renderer. A literal component is parsed by the library as it
     * is drawn, so a string the parser refuses would be refused while the game is drawing — and the one
     * place markup is written by hand is a text field, where half-typed tags are the normal state of
     * things.
     */
    public static boolean reads(String markup) {
        if (markup == null || markup.isEmpty()) return true;
        try {
            MarkupParser.parse(markup);
            return true;
        } catch (RuntimeException refused) {
            return false;
        }
    }

    private static boolean truth(Boolean flag) {
        return Boolean.TRUE.equals(flag);
    }

    // ------------------------------------------------------------------
    // Selecting part of a name or a lore line, and formatting it
    // ------------------------------------------------------------------

    /**
     * One field after an edit, and the run of it that is still the words that were edited.
     *
     * <p>The run comes back because the buttons are pressed one after another on the same words: a
     * button takes the focus away from the field, and a field that has lost the focus has lost its
     * selection, so whoever pressed the button has to be told where those words are now.
     */
    public record Edited(String text, int from, int to) { }

    /**
     * {@return the text with a pair of tags round one run of its characters}
     *
     * <p>The run is in the characters of the text as it stands, tags and all, which is what a text field's
     * own selection is, and what {@link #shownRange} turns a selection made in the preview into. The words
     * keep the place they had, moved along by whatever tags the line grew, so that a second button wraps
     * the same words rather than the end of the line.
     *
     * <p>Putting a pair on words that already wear one is not what a button does — a button asks
     * {@link #wearsAll} first, and takes the style off instead — but it is what this does, because it is the
     * plain half of that: the tag is added and nothing is decided.
     */
    public static Edited wrapped(String text, int from, int to, String open, String close) {
        Tag pair = Tag.pair(open, close);
        return reformat(text, from, to, (atom, stack) -> with(stack, pair));
    }

    /**
     * {@return the text with a pair of tags put round one run of its characters, or taken off it}
     *
     * <p>Which of the two it is, is decided before this is asked — see {@link #wearsAll} — and decided once for
     * the whole selection rather than run by run, because a selection can cover a bold line and a plain one and
     * an operator pressing a button means one thing by it. Half a button pressed — the bold words unbolded and
     * the plain ones bolded — is a state nobody asked for and nobody can predict from the button.
     *
     * <p>Taking a style off is by name and not by spelling: a run bolded as {@code <b>} is bold, and a button
     * that could not see that would leave the weight on and open a second pair beside it.
     */
    public static Edited unwrapped(String text, int from, int to, String open) {
        Tag pair = Tag.of(open);
        return reformat(text, from, to, (atom, stack) -> without(stack, pair));
    }

    /**
     * {@return whether every character of every one of these runs is drawn under this tag}
     *
     * <p>The question a button asks once, about every part of a selection that falls on a line of the item: a
     * selection that is bold on one line and plain on the next does not wear bold, so the button that writes
     * bold puts it on rather than taking half of it off.
     */
    public static boolean wearsAll(List<Run> runs, String open) {
        Tag tag = Tag.of(open);
        boolean any = false;
        for (Run run : runs) {
            if (run.from() >= run.to()) continue;
            any = true;
            if (!wears(run.line(), run.from(), run.to(), tag)) return false;
        }
        return any;
    }

    /**
     * One line of the item and the run of the characters it shows that a selection covers on it.
     *
     * <p>What a button is pressed on. A selection runs across the lines of an item, and each line takes its own
     * part of it: the first from where the drag began, the last to where it ended, and the lines between whole.
     * The line's own text is carried rather than an index into a list of them, because the caller is the one
     * that knows which line it is holding.
     */
    public record Run(String line, int from, int to) { }

    /**
     * {@return whether every character of one run of the text is drawn under this tag}
     *
     * <p>Asked of the runs the way everything else here is, and only of the runs that have characters: a tag
     * that stands on its own is drawn rather than being text, and a selection that reaches over one has not
     * thereby put its characters in the style.
     */
    private static boolean wears(String text, int from, int to, Tag tag) {
        String source = text == null ? "" : text;
        int start = Math.max(0, Math.min(Math.min(from, to), source.length()));
        int end = Math.max(start, Math.min(Math.max(from, to), source.length()));
        if (start == end) return false;

        boolean any = false;
        for (Piece piece : pieces(source)) {
            if (piece.atom()) continue;
            int a = Math.max(piece.start(), start);
            int b = Math.min(piece.end(), end);
            if (b <= a) continue;
            any = true;
            if (!holds(piece.stack(), tag)) return false;
        }
        return any;
    }

    /** {@return the tags without this one}, which is what taking a style off a run of words means */
    private static List<Tag> without(List<Tag> stack, Tag tag) {
        List<Tag> out = new ArrayList<>();
        for (Tag existing : stack) {
            if (!sameTag(existing, tag)) out.add(existing);
        }
        return out.isEmpty() ? List.of() : List.copyOf(out);
    }

    /** {@return whether one of these tags is the tag being looked for}, under any of the names it is written */
    private static boolean holds(List<Tag> stack, Tag tag) {
        for (Tag existing : stack) {
            if (sameTag(existing, tag)) return true;
        }
        return false;
    }

    /**
     * {@return whether two tags are the same style}, whatever they are called
     *
     * <p>The library reads a handful of styles under two names each — {@code <b>} and {@code <bold>},
     * {@code <i>} and {@code <italic>} — and both are what the game draws. A button that asked only after the
     * name it writes itself would not see the other one: pressed on words bolded as {@code <b>}, it would put
     * a second weight on them and then take that one off again, which is a button that does nothing. The colour
     * is not here, because a colour is set by the picker rather than switched — see {@link #isColour}.
     */
    private static boolean sameTag(Tag one, Tag other) {
        String name = one.name();
        String wanted = other.name();
        if (name == null || wanted == null) return false;
        if (name.equalsIgnoreCase(wanted)) return true;
        return switch (wanted.toLowerCase()) {
            case "bold" -> name.equalsIgnoreCase("b");
            case "italic" -> name.equalsIgnoreCase("i");
            case "underline" -> name.equalsIgnoreCase("u");
            case "strikethrough" -> name.equalsIgnoreCase("s");
            case "obfuscated" -> name.equalsIgnoreCase("obf");
            default -> false;
        };
    }

    /**
     * {@return the text with the tags taken out of one run of its characters}
     *
     * <p>What the clear button does, and the run it answers with is the words that are left. Every tag goes
     * and not only the ones that come in pairs: a run that should read as plain text has to lose what was
     * over it rather than be nested in more, and a tag that stands on its own — the self-closing form the
     * library draws an item or a mob with — goes with the rest.
     *
     * <p>What is <b>not</b> a tag stays: a name reading {@code <3}, or a bracket left open. That is the
     * library's own reading of the line rather than a guess, and it is the difference between a button that
     * tidies a name and one that eats it.
     *
     * <p>The game's own colour codes — a section sign and a letter — are left where they are. They are read
     * by the game inside the text rather than by the library as tags, this mod writes its formatting as
     * tags, and a button that removed characters nobody asked about would be doing more than it says.
     */
    public static Edited untagged(String text, int from, int to) {
        String source = text == null ? "" : text;
        int start = Math.max(0, Math.min(Math.min(from, to), source.length()));
        int end = Math.max(start, Math.min(Math.max(from, to), source.length()));
        // Nothing selected is nothing to do, which is not the same as an empty pair put in: clearing takes
        // formatting away and never adds any.
        if (start == end) return new Edited(source, start, end);
        return reformat(source, start, end, (atom, stack) -> atom ? null : List.of());
    }

    /**
     * {@return the text with a colour round one run of its characters}, replacing any colour already there
     *
     * <p>A colour is <b>set</b> rather than added, which is the difference between a colour control and a
     * button that writes a tag. The picker is dragged across its square rather than pressed once, so a
     * colour that was added would leave one pair of tags per shade the drag passed through, with the first
     * of them doing nothing.
     *
     * <p>Set on the characters that were chosen and on no others. A run inside a longer coloured line keeps
     * the colour its neighbours have, so colouring three words of a red line blue leaves the red on either
     * side of them — which is what choosing a colour for a selection means, and what a colour written round
     * the whole line could not say.
     *
     * <p>An effect that colours — a rainbow, a gradient — is left alone. It is not a colour this picker set,
     * and a flat colour written over somebody's effect would be this button throwing away the shape of what
     * it did not understand.
     */
    public static Edited recoloured(String text, int from, int to, String colour) {
        // The effect form of the colour, which is the form the library draws: see effectColours.
        Tag pair = Tag.pair("<color col=" + colour + ">", "</color>");
        return reformat(text, from, to, (atom, stack) -> {
            List<Tag> out = new ArrayList<>();
            boolean set = false;
            for (Tag tag : stack) {
                if (!isColourName(tag.name())) {
                    out.add(tag);
                } else if (!set) {
                    // Where the old colour stood rather than beside it: the pair already round the words is
                    // the one being set, and a second pair inside it is a colour that does nothing.
                    out.add(pair);
                    set = true;
                }
            }
            // Outermost when the run had no colour at all, which is the shape a colour is written in
            // everywhere else in this mod.
            if (!set) out.add(0, pair);
            return List.copyOf(out);
        });
    }

    // ------------------------------------------------------------------
    // The shape of a line: its characters, its tags, and how the tags nest
    // ------------------------------------------------------------------

    /**
     * One tag as a line holds it: the literal that opens it, the literal that closes it, and its name.
     *
     * <p>The literals are kept rather than the tag being rebuilt from its name, because a tag carries more
     * than a name. {@code <grad from=FF0000 to=00FF00>} is not {@code <grad>}, and a rewrite that rebuilt a
     * tag from its name would quietly turn somebody's gradient into a flat colour of the same shape.
     */
    private record Tag(String open, String close, String name) {

        /** {@return the tag a pair of literals makes}, taking its name from the opening one */
        static Tag pair(String open, String close) {
            return new Tag(open, close, nameOf(open));
        }

        /** {@return the tag an opening literal makes}, closed by the name it turned out to hold */
        static Tag of(String open) {
            String name = nameOf(open);
            return new Tag(open, "</" + name + ">", name);
        }

        /** {@return the name an opening literal is written with}, which is what two tags are compared by */
        private static String nameOf(String open) {
            Found found = tagAt(open, 0);
            return found == null ? "" : found.name();
        }
    }

    /**
     * One run of a line: the characters between two tags, or a tag that stands on its own.
     *
     * <p>Runs rather than characters, because an edit decides which tags a stretch of text is under and the
     * answer is the same for every character of a run.
     *
     * @param atom whether this run is a tag rather than characters. The self-closing form is one: the
     *             library reads it as a span of its own and draws what it names — an item, a mob — and it
     *             has no characters of its own, so it is carried whole and never cut in half
     */
    private record Piece(int start, int end, List<Tag> stack, boolean atom) { }

    /**
     * What an edit does to the tags over one run of a line.
     *
     * <p>Answering with no tags is what clearing a run does; answering with nothing at all — null — is what
     * takes the run out of the line, which only a tag standing on its own can be. The run being a tag is
     * passed in as well as its tags, because those two are what an edit is allowed to look at.
     */
    private interface Change {
        List<Tag> apply(boolean atom, List<Tag> stack);
    }

    /**
     * {@return a line after an edit of one run of its characters}, and the run as it stands afterwards
     *
     * <p>This is the whole of how the buttons and the picker work, and it is what keeps formatting from
     * stacking wrongly. The line is taken apart into the tags it has and the characters between them, the
     * edit is made to the tags over the chosen characters, and the line is written back out from that. A
     * pair of tags spliced into the markup instead — which is what this used to do — lands wherever the
     * chosen characters happen to fall, and a pair written across a closing tag is read by the library as a
     * style that begins on one word and ends on another: the words in between come out in a formatting
     * nobody asked for.
     *
     * <p>An empty selection puts the pair in empty and leaves the line's own characters alone, because that
     * is how a tag is typed round nothing: the operator's next keystroke in the field lands between the two.
     */
    private static Edited reformat(String text, int from, int to, Change change) {
        String source = text == null ? "" : text;
        int start = Math.max(0, Math.min(Math.min(from, to), source.length()));
        int end = Math.max(start, Math.min(Math.max(from, to), source.length()));

        List<Piece> out = new ArrayList<>();
        if (start == end) {
            boolean placed = false;
            for (Piece piece : pieces(source)) {
                if (!placed && piece.start() >= start) {
                    out.add(blank(start, piece.stack(), change));
                    placed = true;
                } else if (!placed && !piece.atom() && start < piece.end()) {
                    // The caret is inside this run, so the run is cut and the pair goes between the two halves
                    // with the caret in them. The halves are the same run and stay under the same tags.
                    out.add(new Piece(piece.start(), start, piece.stack(), false));
                    out.add(blank(start, piece.stack(), change));
                    out.add(new Piece(start, piece.end(), piece.stack(), false));
                    placed = true;
                    continue;
                }
                out.add(piece);
            }
            if (!placed) out.add(blank(start, List.of(), change));
            return write(out, source, start, end);
        }

        for (Piece piece : pieces(source)) {
            // The ends of the selection cut the runs they fall inside, because that is where the tags over the
            // characters change. A run that is a tag of its own is never cut: it has no characters, and half
            // of it is not markup.
            List<Integer> cuts = new ArrayList<>();
            cuts.add(piece.start());
            if (!piece.atom() && start > piece.start() && start < piece.end()) cuts.add(start);
            if (!piece.atom() && end > piece.start() && end < piece.end()) cuts.add(end);
            cuts.add(piece.end());

            for (int i = 0; i + 1 < cuts.size(); i++) {
                int a = cuts.get(i);
                int b = cuts.get(i + 1);
                // Both ends of the run inside the selection, so that a run the selection reaches only part of
                // — which the cuts above have already divided — is not styled past the words chosen.
                if (a < start || b > end) {
                    out.add(new Piece(a, b, piece.stack(), piece.atom()));
                    continue;
                }
                List<Tag> wanted = change.apply(piece.atom(), piece.stack());
                if (wanted != null) out.add(new Piece(a, b, wanted, piece.atom()));
            }
        }
        return write(out, source, start, end);
    }

    /**
     * {@return the empty run an edit puts where nothing was selected}, under the tags that stood there
     *
     * <p>The change is asked about a run that is not a tag standing on its own, because an empty selection is
     * a pair of tags round nothing: it is never the tag itself that is being taken out of the line.
     */
    private static Piece blank(int at, List<Tag> stack, Change change) {
        return new Piece(at, at, change.apply(false, stack), false);
    }

    /**
     * {@return a line taken apart into its runs and the tags open over each of them}
     *
     * <p>Only the characters make a run: a tag with no characters under it is not written back at all. A pair
     * round nothing is not formatting, and keeping one costs every edit that crosses it a pair of tags opened
     * and closed again over no words — which is the wall of tags an operator complained about, made by the
     * editor rather than by them.
     *
     * <p>The stack is pushed and popped the way the library does it, which is blindly. Ember's parser pops
     * whatever is on top of its stack for any closing tag, whatever the tag is called, so a model that
     * matched names would read a line differently from the renderer that draws it. A closing tag with
     * nothing open is dropped, which is what the library does with that as well.
     */
    private static List<Piece> pieces(String source) {
        List<Piece> out = new ArrayList<>();
        List<Tag> stack = new ArrayList<>();
        int text = 0;
        int at = 0;
        while (at < source.length()) {
            Found found = tagAt(source, at);
            if (found == null) {
                at++;
                continue;
            }
            if (at > text) out.add(new Piece(text, at, List.copyOf(stack), false));
            if (found.selfClosing()) {
                // A span of its own rather than a tag over anything: it does not change what follows it, and
                // it is written back exactly where it stands.
                out.add(new Piece(at, found.end(), List.copyOf(stack), true));
            } else if (found.closing()) {
                if (!stack.isEmpty()) stack.remove(stack.size() - 1);
            } else {
                stack.add(new Tag(source.substring(at, found.end()), "</" + found.name() + ">", found.name()));
            }
            at = found.end();
            text = at;
        }
        if (source.length() > text) out.add(new Piece(text, source.length(), List.copyOf(stack), false));
        return out;
    }

    /**
     * {@return the line written out from its runs}, and where the two ends of the edit ended up
     *
     * <p>Written from the runs rather than by splice, which is what makes the answer valid formatting: a tag
     * is closed exactly where the run under it ends and opened exactly where the next run under it begins,
     * so every pair written is balanced and the tags that were already in the line keep their nesting. Only
     * the difference between one run's tags and the next is written, so a line whose words are already under
     * the tags they should be comes back with its characters in the order they were in.
     *
     * <p>Written back through this, a line the library was misreading — a tag closed across another, an
     * angle bracket left open — comes out saying what it said and meaning what it meant, which is the point
     * of doing it this way rather than patching the text where the words are.
     *
     * <p>The two answers are where the ends of the edit stand in what was written, because the caller has a
     * selection to put back on the words: a field that lost its selection to a button has to be told where
     * those words are now, and they moved by the length of every tag written before them.
     */
    private static Edited write(List<Piece> pieces, String source, int from, int to) {
        StringBuilder out = new StringBuilder();
        List<Tag> open = List.of();
        int atFrom = -1;
        int atTo = -1;
        int tail = 0;

        for (Piece piece : pieces) {
            int shared = common(open, piece.stack());
            for (int i = open.size() - 1; i >= shared; i--) out.append(open.get(i).close());
            for (int i = shared; i < piece.stack().size(); i++) {
                // Written the way the library draws it rather than the way it was found — see drawnTag — because
                // a colour the library reads and does not draw is a colour this editor has written for nothing.
                out.append(drawnTag(piece.stack().get(i).open()));
            }
            open = piece.stack();

            if (atFrom < 0 && piece.start() >= from) atFrom = out.length();
            out.append(source, piece.start(), piece.end());
            tail = out.length();
            if (piece.end() <= to) atTo = out.length();
        }

        for (int i = open.size() - 1; i >= 0; i--) out.append(open.get(i).close());
        if (atFrom < 0) atFrom = tail;
        if (atTo < 0) atTo = tail;
        return new Edited(out.toString(), atFrom, Math.max(atTo, atFrom));
    }

    /** {@return how many of the tags over two runs are the same ones in the same order} */
    private static int common(List<Tag> a, List<Tag> b) {
        int shared = 0;
        while (shared < a.size() && shared < b.size() && a.get(shared).equals(b.get(shared))) shared++;
        return shared;
    }

    /** {@return the tags with this one added inside them}, or the same tags when it is already one of them */
    private static List<Tag> with(List<Tag> stack, Tag tag) {
        if (stack.contains(tag)) return stack;
        List<Tag> out = new ArrayList<>(stack);
        out.add(tag);
        return List.copyOf(out);
    }

    /**
     * {@return the markup with every colour written the way the library draws it}
     *
     * <p>What {@link #line} is built from, and what {@link #write} does to each tag it writes. The library reads
     * a colour in two forms and draws only one of them: {@code <color value=FF0000>} sets the span's colour,
     * and a span's colour is applied to a component, which is not what an item's text is any more — an item's
     * text is a literal, and the library's own reader for a literal applies weight, slant, lines, fonts and
     * effects, and does not apply the span's colour. {@code <color col=FF0000>} is read as an <em>effect</em>
     * instead, and effects are applied. So a colour written the other way is a colour written for nothing: this
     * is the form that draws.
     */
    public static String effectColours(String markup) {
        String source = markup == null ? "" : markup;
        if (source.indexOf('<') < 0 || source.indexOf('>') < 0) return source;

        StringBuilder out = new StringBuilder();
        int at = 0;
        while (at < source.length()) {
            Found found = tagAt(source, at);
            if (found == null) {
                out.append(source.charAt(at));
                at++;
                continue;
            }
            out.append(drawnTag(source.substring(at, found.end())));
            at = found.end();
        }
        return out.toString();
    }

    /**
     * {@return one tag written the way the library draws it}, which for a colour means as an effect
     *
     * <p>Any other tag is given back exactly as it was found: this rewrites what the library would silently
     * not draw, and nothing else. A colour that already names its colour in the drawing form is left as it is,
     * so a line that has been through this once reads the same the second time.
     */
    private static String drawnTag(String literal) {
        Found found = tagAt(literal, 0);
        if (found == null || found.closing() || !isColourName(found.name())) return literal;

        String attributes = literal.substring(1 + found.name().length(),
                literal.length() - (found.selfClosing() ? 2 : 1));
        // The form the library draws, first; then the two names it reads but does not draw. A bare word is a
        // value to the library — `value` is what it calls the first thing that is not a name — so it is read
        // here as one too.
        String value = attribute(attributes, "col", "c");
        if (value == null) value = attribute(attributes, "value", "color");
        if (value == null) return literal;
        return "<color col=" + value + ">";
    }

    /** {@return the value of the first of these attributes that is written}, or null when none of them is */
    private static String attribute(String attributes, String... names) {
        for (String name : names) {
            for (String[] written : attributes(attributes)) {
                if (written[1] != null && written[0].equalsIgnoreCase(name)) return written[1];
            }
        }
        return null;
    }

    /**
     * {@return the attributes of a tag}, as pairs of a name and the value it was given
     *
     * <p>The grammar is the library's own, as it is in {@link #tagAt}: a name, and a value that may be bare or
     * quoted in either kind of quote. The first word with no name on it is the value — the library's reading,
     * where the first bare thing it finds is filed under {@code value} — which is why a value of null is
     * answered for it under that name.
     */
    private static List<String[]> attributes(String attributes) {
        List<String[]> out = new ArrayList<>();
        int n = attributes.length();
        int i = 0;
        boolean first = true;
        while (i < n) {
            while (i < n && space(attributes.charAt(i))) i++;
            if (i >= n) break;

            if (!letter(attributes.charAt(i))) {
                // Not an attribute: the bare value the library calls `value`, and then it stops reading.
                if (first) out.add(new String[]{"value", word(attributes, i)});
                break;
            }

            int nameStart = i;
            while (i < n && attrChar(attributes.charAt(i))) i++;
            String name = attributes.substring(nameStart, i);
            String value = null;
            if (i < n && (attributes.charAt(i) == '=' || attributes.charAt(i) == ':')) {
                i++;
                if (i < n && (attributes.charAt(i) == '"' || attributes.charAt(i) == '\'')) {
                    char quote = attributes.charAt(i);
                    int close = attributes.indexOf(quote, i + 1);
                    if (close < 0) break;
                    value = attributes.substring(i + 1, close);
                    i = close + 1;
                } else {
                    int valueStart = i;
                    while (i < n && !space(attributes.charAt(i))
                            && attributes.charAt(i) != '>' && attributes.charAt(i) != '/') i++;
                    value = attributes.substring(valueStart, i);
                }
            }
            // The first thing written with no name on it is the value, whatever it looks like: that is the
            // library's own reading, and it is why `<color red>` is a colour and `<color red blue>` is not.
            if (value == null && first) out.add(new String[]{"value", name});
            else out.add(new String[]{name, value});
            first = false;
        }
        return out;
    }

    /** {@return one bare word of a tag's attributes}, which is as far as the library reads a bare value */
    private static String word(String attributes, int at) {
        int end = at;
        while (end < attributes.length() && !space(attributes.charAt(end))
                && attributes.charAt(end) != '>' && attributes.charAt(end) != '/') end++;
        return attributes.substring(at, end);
    }

    /** {@return whether a tag with this name is one that sets a colour} */
    private static boolean isColourName(String name) {
        return "color".equalsIgnoreCase(name) || "c".equalsIgnoreCase(name);
    }

    /**
     * {@return the characters an item shows for this markup}, which is what a preview draws
     *
     * <p>The tags are not text, so a selection made by dragging over what is drawn is measured in these
     * characters and not in the markup's. Asking the component rather than taking the tags out here is
     * also the only way to be certain which parts of the markup really are invisible: the parser decides
     * that, and it is the same parser whose answer is drawn.
     */
    public static String shown(String markup) {
        return component(markup).getString();
    }

    // ------------------------------------------------------------------
    // Reading an item's own text back into the editor
    // ------------------------------------------------------------------

    /**
     * {@return the run of the markup that covers one run of the characters it shows}
     *
     * <p>What turns a selection dragged in the preview into something a field can be edited with. The
     * end is the end of the last character selected rather than the start of the one after it, because
     * the one after it is on the far side of whatever tags lie between them, and a run that swallowed a
     * closing tag would wrap the new pair around the old one's remains.
     *
     * <p>Only the characters that were selected are named, and not the tags that reach over the ends of
     * the run: those are not characters anybody pointed at, and a field that grew a tag the operator
     * never touched is a field that rewrote what it was not asked to. A run that crosses a style boundary
     * therefore comes back with tags on both sides of the crossing, and it is {@link #reformat} that reads
     * it as the styles it names and writes the line out again with them balanced.
     */
    public static int[] shownRange(String markup, String shown, int from, int to) {
        String source = markup == null ? "" : markup;
        String text = shown == null ? "" : shown;
        int[] map = offsets(source, text);

        int start = Math.max(0, Math.min(Math.min(from, to), text.length()));
        int end = Math.max(start, Math.min(Math.max(from, to), text.length()));
        if (end <= start) {
            int at = map[Math.min(start, map.length - 1)];
            return new int[]{at, at};
        }

        int lastIndex = end - 1;
        // A character outside the basic plane is two chars of the string and one of the text, so the run
        // has to end after the whole of it: half a surrogate is not a character.
        if (Character.isLowSurrogate(text.charAt(lastIndex)) && lastIndex > 0) lastIndex--;
        int last = map[lastIndex] + Character.charCount(text.codePointAt(lastIndex));
        return new int[]{map[start], last};
    }

    /**
     * {@return the markup of one run of the characters a line shows}, with the tags over that run written
     *         round it
     *
     * <p>What a preview needs and a slice of the markup cannot give. The characters of one drawn row are the
     * ones to draw, but the colour and the weight they are drawn in were written round the whole line and
     * reach over the row's ends — cutting the markup at the characters throws away exactly those tags, which
     * is how a name written as {@code <color value=F73636>asdas</color>} came out white: the colour was round
     * every character of it and round none of the row that was drawn.
     *
     * <p>Written through the same runs an edit is written through, so what comes back is the run and the tags
     * it is under, balanced and in the order it had them: a row that begins inside a colour and a weight is
     * given both, and a row that ends inside them closes both.
     */
    public static String marked(String markup, int from, int to) {
        String source = markup == null ? "" : markup;
        int start = Math.max(0, Math.min(Math.min(from, to), source.length()));
        int end = Math.max(start, Math.min(Math.max(from, to), source.length()));

        List<Piece> out = new ArrayList<>();
        for (Piece piece : pieces(source)) {
            // The ends of the run cut the pieces they fall inside, and only the pieces the run covers are
            // kept: what is written is the run, with whatever was over it written over it again.
            List<Integer> cuts = new ArrayList<>();
            cuts.add(piece.start());
            if (!piece.atom() && start > piece.start() && start < piece.end()) cuts.add(start);
            if (!piece.atom() && end > piece.start() && end < piece.end()) cuts.add(end);
            cuts.add(piece.end());

            for (int i = 0; i + 1 < cuts.size(); i++) {
                int a = cuts.get(i);
                int b = cuts.get(i + 1);
                if (a >= start && b <= end) out.add(new Piece(a, b, piece.stack(), piece.atom()));
            }
        }
        return write(out, source, start, end).text();
    }

    /**
     * {@return where each character of the shown text is in the markup}, one index per character and one
     *         more for the end of the text
     *
     * <p>Walked rather than parsed, and in this order deliberately: the next character of the markup that
     * is the character being looked for is the one that is drawn, so a {@code <} is only treated as a tag
     * when there is nothing to match first. That is what keeps markup the parser refused — where the
     * brackets are shown as themselves — from being read as tags and eaten by the selection.
     */
    private static int[] offsets(String markup, String shown) {
        int[] map = new int[shown.length() + 1];
        int at = 0;
        for (int i = 0; i < shown.length(); i++) {
            char wanted = shown.charAt(i);
            while (at < markup.length() && markup.charAt(at) != wanted) {
                Found found = tagAt(markup, at);
                at = found == null ? at + 1 : found.end();
            }
            map[i] = Math.min(at, markup.length());
            if (at < markup.length()) at++;
        }
        map[shown.length()] = markup.length();
        return map;
    }

    // ------------------------------------------------------------------
    // Reading a tag out of a line, by the library's own grammar
    // ------------------------------------------------------------------

    /**
     * One tag found in a line: where it ends, what it is called, and which of the three forms it is.
     *
     * <p>Three forms, because the library has three: an opening tag, a closing one, and the self-closing
     * form it reads as a span of its own. That last one is how a glyph — an item, a mob — is put in the
     * middle of a line, and it is the reason the other two cannot be told apart from the spelling of a name.
     */
    private record Found(int end, String name, boolean closing, boolean selfClosing) { }

    /**
     * {@return the tag that begins here}, or null when what is here is somebody's text
     *
     * <p>The grammar is the library's own, written out rather than approached, because this is the line
     * between what an item shows and what it does not. A bracket the library reads as a tag is invisible and
     * has to be stepped over; one it does not is a character somebody typed and has to be counted. The first
     * version of this counted anything between brackets with a word in it, so a line reading
     * {@code <bold extra!>} — which the library draws, exclamation mark and all — was measured as a tag, and
     * every character after it in the preview came out selected by the wrong letter.
     *
     * <p>Written out by hand because the library's pattern is private to it, and because the question is
     * asked of every character of a line while a selection is being dragged: a parser that answered about a
     * whole line would have to be run again for each of them. The three shapes it accepts are a name and
     * nothing else, a name and a closing slash, and a name and any number of {@code key=value} attributes,
     * with a value that is bare, quoted in single quotes, or quoted in double ones.
     */
    private static Found tagAt(String text, int at) {
        int n = text.length();
        if (at < 0 || at >= n || text.charAt(at) != '<') return null;

        int i = at + 1;
        boolean closing = i < n && text.charAt(i) == '/';
        if (closing) i++;

        int first = i;
        while (i < n && nameChar(text.charAt(i))) i++;
        if (i == first) return null;
        String name = text.substring(first, i);

        // Zero or more attributes, read greedily: an attribute that does not parse ends the list rather than
        // the tag, because the library's pattern can match a prefix of them and a name with a stray word
        // after it is not a tag at all.
        while (true) {
            int spaces = i;
            while (spaces < n && space(text.charAt(spaces))) spaces++;
            if (spaces == i || spaces >= n || !letter(text.charAt(spaces))) break;

            int end = spaces + 1;
            while (end < n && attrChar(text.charAt(end))) end++;
            if (end < n && (text.charAt(end) == '=' || text.charAt(end) == ':')) {
                int value = end + 1;
                if (value < n && (text.charAt(value) == '"' || text.charAt(value) == '\'')) {
                    int quoted = text.indexOf(text.charAt(value), value + 1);
                    if (quoted < 0) break;
                    end = quoted + 1;
                } else {
                    int bare = value;
                    while (bare < n && !space(text.charAt(bare))
                            && text.charAt(bare) != '>' && text.charAt(bare) != '/') bare++;
                    if (bare == value) break;
                    end = bare;
                }
            }
            i = end;
        }

        boolean selfClosing = i < n && text.charAt(i) == '/';
        if (selfClosing) i++;
        if (i >= n || text.charAt(i) != '>') return null;
        return new Found(i + 1, name, closing, selfClosing);
    }

    /** {@return whether this may stand anywhere in a tag's name}, which is the library's own character set */
    private static boolean nameChar(char c) {
        return letter(c) || digit(c) || c == '_';
    }

    /** {@return whether this may stand after the first character of an attribute's name} */
    private static boolean attrChar(char c) {
        return letter(c) || digit(c);
    }

    private static boolean letter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private static boolean digit(char c) {
        return c >= '0' && c <= '9';
    }

    /** {@return whether this is one of the six characters the library's whitespace means} */
    private static boolean space(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\u000B' || c == '\f' || c == '\r';
    }

    /**
     * {@return the markup that describes a component}, for reading an item's own name and lore back
     *         into the editor
     *
     * <p>Without this the editor is lossy in the worst way: it reads an existing name as its text, so
     * an operator who opens an item and presses apply without changing anything restyles it. The
     * round trip is what this method is for, and the harness checks it as a round trip rather than as a
     * string, because a conversion that is right about the text and wrong about the colour looks
     * correct until somebody reads the item.
     *
     * <p>A component that is not plain text — a translation, a keybind, a score — is written whole, as
     * the game reads it, and not taken apart: its text is resolved by the client and there is no markup
     * for something this mod cannot see. Its children are not walked afterwards, or the same text would
     * be written twice.
     *
     * <p>A font is <b>not</b> written back. The tag for one is not a tag this mod has ever written and
     * could not be confirmed from the library's own sources, and a tag the parser does not know is
     * shown literally: an item named {@code <font=...>Sword} is a worse failure than an item that lost
     * its typeface.
     */
    public static String markup(Component component) {
        if (component == null) return "";
        StringBuilder out = new StringBuilder();
        append(out, component);
        return out.toString();
    }

    private static void append(StringBuilder out, Component component) {
        if (!(component.getContents() instanceof PlainTextContents plain)) {
            out.append(tagged(component.getString(), component.getStyle()));
            return;
        }
        if (!plain.text().isEmpty()) out.append(tagged(plain.text(), component.getStyle()));
        for (Component sibling : component.getSiblings()) append(out, sibling);
    }

    /**
     * {@return one run of text with the tags its style asks for around it}
     *
     * <p>{@code value=} rather than the {@code col=} the profile markup writes, and the difference is
     * not a matter of taste. {@code col=} becomes an <em>effect</em>: the library's own note in
     * {@link net.xlebupaksa.backutils.profile.ProfileMarkup} says that form is the one the library
     * renders, and the harness found the other half of it — a {@code col=} tag leaves the span's colour
     * empty, because the colour is applied when it is drawn rather than carried by the text. An item's
     * name is drawn by the game from its style, so the colour has to be <b>in the span</b>, which is
     * what {@code value=} produces. Writing the profile's form here would round-trip a name to text
     * with no colour at all.
     *
     * <p>The hex form rather than a colour name, because a name is not a palette entry: the colour came
     * from an item, not from a list of colours this mod offers, and any of the sixteen million values
     * has to survive.
     *
     * <p>What this editor says by default is not written back. White is the colour a line starts from and an
     * italic that says no is how the game's own slant is kept off it, so an item this editor has written comes
     * back to the field as the markup it was given — without a {@code <color value=FFFFFF>} round every line,
     * which would otherwise grow by one pair every time an item was opened and applied.
     */
    private static String tagged(String text, Style style) {
        StringBuilder open = new StringBuilder();
        StringBuilder close = new StringBuilder();

        if (style.getColor() != null && style.getColor().getValue() != DEFAULT_COLOUR) {
            open.append("<color col=").append(hex(style.getColor())).append('>');
            close.insert(0, "</color>");
        }
        if (Boolean.TRUE.equals(style.isBold())) tag(open, close, "bold");
        if (Boolean.TRUE.equals(style.isItalic())) tag(open, close, "italic");
        if (Boolean.TRUE.equals(style.isUnderlined())) tag(open, close, "underline");
        if (Boolean.TRUE.equals(style.isStrikethrough())) tag(open, close, "strikethrough");
        if (Boolean.TRUE.equals(style.isObfuscated())) tag(open, close, "obfuscated");

        if (open.isEmpty()) return text;
        return open + text + close;
    }

    private static void tag(StringBuilder open, StringBuilder close, String name) {
        open.append('<').append(name).append('>');
        close.insert(0, "</" + name + ">");
    }

    /**
     * {@return a colour as the hex the library reads}, which is what an item's colour has to be
     *
     * <p>Six digits and no {@code #}: that is the form the library's own examples are written in
     * ({@code <color value=FF8800>}), and the form it is known to read. The hash makes a string that looks
     * like a colour to this mod and to nothing else, which is the worst kind of wrong — it parses, and then
     * the item is drawn in white.
     */
    static String hex(TextColor colour) {
        return String.format("%06X", colour.getValue() & 0xFFFFFF);
    }

    // ------------------------------------------------------------------
    // The attributes
    // ------------------------------------------------------------------

    /**
     * One attribute the item grants, as the editor holds it.
     *
     * <p>Keyed by the attribute's own id rather than by the attribute, because the id is what a screen
     * holds and what a message can carry: the game object behind it is a registry entry, and a list
     * that needed the registry to be built could not be tested or sent. The slot is part of the entry
     * rather than of the item, which is how 1.21 grants attributes.
     */
    public record Granted(ResourceLocation attribute, double amount,
                          AttributeModifier.Operation operation, EquipmentSlotGroup slot) {

        /** {@return the modifier id for one attribute}, derived so that two do not collide */
        public static ResourceLocation id(ResourceLocation attribute) {
            return ResourceLocation.fromNamespaceAndPath(BackUtils.MOD_ID,
                    attribute.getPath().replace('/', '_'));
        }
    }

    /**
     * {@return the attributes with this one added, or with the one it replaces}
     *
     * <p>Replaced rather than added beside: a second modifier for the same attribute with the same id
     * is not two bonuses, and an editor that let one attribute be listed twice would let an operator
     * set the same thing twice with no way to tell which of the two the game had applied.
     *
     * <p>Replaced <b>where it stands</b>, which is the difference between an edit and a jump: the screen
     * is a list, and an operator changing the amount on the third line would find it at the bottom
     * afterwards, having lost the line they were working on.
     */
    public static List<Granted> putAttribute(List<Granted> attributes, Granted granted) {
        List<Granted> out = new ArrayList<>();
        boolean replaced = false;
        for (Granted existing : attributes == null ? List.<Granted>of() : attributes) {
            if (existing.attribute().equals(granted.attribute())) {
                out.add(granted);
                replaced = true;
                continue;
            }
            out.add(existing);
        }
        // Only one that was not there already goes to the end, which is where a list grows.
        if (!replaced) out.add(granted);
        return List.copyOf(out);
    }

    /** {@return the attributes without this one}, or the same attributes when it is not there */
    public static List<Granted> removeAttribute(List<Granted> attributes, ResourceLocation attribute) {
        List<Granted> out = new ArrayList<>();
        for (Granted existing : attributes == null ? List.<Granted>of() : attributes) {
            if (existing.attribute().equals(attribute)) continue;
            out.add(existing);
        }
        return List.copyOf(out);
    }

    /** {@return how many of these attributes the item would grant}, for a caption that says so */
    public static int count(List<Granted> attributes) {
        return attributes == null ? 0 : attributes.size();
    }
}
