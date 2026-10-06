package net.xlebupaksa.backutils.network;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.xlebupaksa.backutils.BackUtils;
import net.xlebupaksa.backutils.item.ItemEdit;

import java.util.ArrayList;
import java.util.List;

/**
 * One client saying what the item it is looking at should become.
 *
 * <p><b>The whole edit, not a change to it.</b> An editor holds a name, a lore and a list of
 * attributes, and what an operator presses "apply" on is the state they have been looking at. Sending
 * the difference would mean a protocol that can arrive out of order, be applied twice, or describe a
 * change to something that is no longer there — and none of that can happen to a message that says
 * what the item is.
 *
 * <p><b>And a message says which of the three it is about.</b> Saying what the item is only works if
 * the whole of it is known, and a screen built one section at a time does not know the parts it cannot
 * show: a name edit sent as a whole item would be an edit that deletes the lore, because an empty lore
 * means "no lore" and not "I did not look". The two flags are what tell those apart, and they are why
 * the sections could be built in turn without the first one being destructive.
 *
 * <p>Markup travels as text and is parsed by the server, which is fewer bytes and the right side of
 * the wire to be holding the component: a client that invents one has changed nothing that matters.
 *
 * <p>Every count is bounded, and a count outside its bound <b>refuses the message rather than clamping
 * it</b>: the counts are what the rest of the message is made of, so reading four lines of a message
 * that announced six would leave the rest of it being read as something else.
 */
public record ItemEditPayload(EffectToolConfigPayload.Where where, int containerId, int slot,
                              String name, List<String> lore, List<ItemEdit.Granted> attributes,
                              boolean withLore, boolean withAttributes)
        implements CustomPacketPayload {

    /** How long a name may be, and how long one line of lore may be. */
    private static final int MAX_TEXT = 512;

    /** How many lore lines and how many attributes one edit may carry. */
    private static final int MAX_LINES = 64;
    private static final int MAX_ATTRIBUTES = 32;

    /** How long the name of an operation or a slot may be. */
    private static final int MAX_WORD = 32;

    public static final CustomPacketPayload.Type<ItemEditPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(BackUtils.MOD_ID, "item_edit"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ItemEditPayload> STREAM_CODEC =
            StreamCodec.of(ItemEditPayload::write, ItemEditPayload::read);

    /**
     * {@return a message that changes all three parts of an item}
     *
     * <p>What the editor sends now that it shows all three. The flags that say which parts a message is
     * about stay, because they are the reason the three sections could be built one at a time without
     * the first two being destructive — and a screen that shows less than all three has to say so.
     */
    public static ItemEditPayload full(EffectToolConfigPayload.Where where, int containerId, int slot,
                                       String name, List<String> lore,
                                       List<ItemEdit.Granted> attributes) {
        return new ItemEditPayload(where, containerId, slot, name, lore, attributes, true, true);
    }

    private static void write(RegistryFriendlyByteBuf buf, ItemEditPayload payload) {
        buf.writeVarInt(payload.where() == null ? 0 : payload.where().ordinal());
        buf.writeVarInt(payload.containerId());
        buf.writeVarInt(payload.slot());
        buf.writeUtf(payload.name() == null ? "" : payload.name(), MAX_TEXT);
        buf.writeBoolean(payload.withLore());
        buf.writeBoolean(payload.withAttributes());

        List<String> lore = payload.lore() == null ? List.of() : payload.lore();
        buf.writeVarInt(Math.min(lore.size(), MAX_LINES));
        for (int i = 0; i < Math.min(lore.size(), MAX_LINES); i++) {
            String line = lore.get(i);
            buf.writeUtf(line == null ? "" : line, MAX_TEXT);
        }

        List<ItemEdit.Granted> attributes = payload.attributes() == null
                ? List.of() : payload.attributes();
        int count = Math.min(attributes.size(), MAX_ATTRIBUTES);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            ItemEdit.Granted granted = attributes.get(i);
            buf.writeResourceLocation(granted.attribute());
            buf.writeDouble(granted.amount());
            buf.writeUtf(granted.operation().name(), MAX_WORD);
            buf.writeUtf(granted.slot().name(), MAX_WORD);
        }
    }

    private static ItemEditPayload read(RegistryFriendlyByteBuf buf) {
        EffectToolConfigPayload.Where where = EffectToolConfigPayload.Where.byId(buf.readVarInt());
        int containerId = buf.readVarInt();
        int slot = buf.readVarInt();
        String name = buf.readUtf(MAX_TEXT);
        boolean withLore = buf.readBoolean();
        boolean withAttributes = buf.readBoolean();

        int lines = buf.readVarInt();
        if (lines < 0 || lines > MAX_LINES) {
            throw new DecoderException("An item edit announced " + lines + " lines of lore");
        }
        List<String> lore = new ArrayList<>(lines);
        for (int i = 0; i < lines; i++) lore.add(buf.readUtf(MAX_TEXT));

        int count = buf.readVarInt();
        if (count < 0 || count > MAX_ATTRIBUTES) {
            throw new DecoderException("An item edit announced " + count + " attributes");
        }
        List<ItemEdit.Granted> attributes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ResourceLocation attribute = buf.readResourceLocation();
            double amount = buf.readDouble();
            attributes.add(new ItemEdit.Granted(attribute, amount,
                    operation(buf.readUtf(MAX_WORD)), group(buf.readUtf(MAX_WORD))));
        }

        return new ItemEditPayload(where, containerId, slot, name, lore, attributes, withLore,
                withAttributes);
    }

    /**
     * {@return the operation a name stands for}, reading one this game does not have as adding a value
     *
     * <p>By name rather than by the number the enum happens to sit at: a number means a different
     * operation the moment the game inserts one before it, and a name that means nothing is at least
     * visibly a name that meant nothing.
     */
    private static AttributeModifier.Operation operation(String name) {
        try {
            return AttributeModifier.Operation.valueOf(name);
        } catch (IllegalArgumentException unknown) {
            return AttributeModifier.Operation.ADD_VALUE;
        }
    }

    /** {@return the slot group a name stands for}, read the same way and for the same reason */
    private static EquipmentSlotGroup group(String name) {
        try {
            return EquipmentSlotGroup.valueOf(name);
        } catch (IllegalArgumentException unknown) {
            return EquipmentSlotGroup.ANY;
        }
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
