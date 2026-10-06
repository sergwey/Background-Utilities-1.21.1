package net.xlebupaksa.backutils.network;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.xlebupaksa.backutils.BackUtils;
import net.xlebupaksa.backutils.item.ItemEdit;

import java.util.ArrayList;
import java.util.List;

/**
 * Where an item edit becomes an item.
 *
 * <p>The server's side of the editor, and the whole of it: a name, a lore and a list of attributes,
 * written onto the stack in the slot the client named. <b>It is deliberately usable with no screen at
 * all</b> — the screen is the next thing built and this is what it will be built on, because a feature
 * that cannot be exercised without its own GUI can only be tested by looking at it.
 *
 * <p>Three components, because that is what these three things are on 1.21:
 * {@code minecraft:custom_name}, {@code minecraft:lore} and {@code minecraft:attribute_modifiers}. An
 * edit that leaves one of them empty <b>removes it</b> rather than writing an empty one: an item with
 * no custom name is not an item whose name is nothing, and the two are drawn differently.
 */
@EventBusSubscriber(modid = BackUtils.MOD_ID)
public final class ItemEditorNetwork {

    /**
     * What an operator needs to be to edit somebody's item.
     *
     * <p>The same level every other administrative feature of this mod asks for, written out here
     * rather than reached for across features: the two are the same policy and not the same decision.
     */
    private static final int REQUIRED_LEVEL = 2;

    private ItemEditorNetwork() {}

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(BackUtils.MOD_ID).optional();
        registrar.playToServer(
                ItemEditPayload.TYPE,
                ItemEditPayload.STREAM_CODEC,
                ItemEditorNetwork::onEdit);
    }

    /** Sends one edit. The server decides whether it happens. */
    public static void send(ItemEditPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    /**
     * Writes one edit onto the item the player addressed.
     *
     * <p>Operators only, and only operators: the client is not offered the screen unless the server
     * has already told it it may run this mod's commands, and this is the gate that actually holds.
     * The creative allowance this started with is gone — an item editor is a power over somebody
     * else's item, and being in creative is not a reason to have it.
     */
    private static void onEdit(ItemEditPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!player.hasPermissions(REQUIRED_LEVEL)) {
            refuse(player);
            return;
        }

        Slot slot = AddressedSlots.of(player, payload.where(), payload.containerId(), payload.slot());
        if (slot == null || !slot.hasItem()) {
            refuse(player);
            return;
        }

        apply(slot.getItem(), payload);
        slot.setChanged();
        player.displayClientMessage(Component.translatable("backutils.item_editor.applied"), true);
    }

    /** Writes the edits onto one stack, leaving out every part the message is not about. */
    private static void apply(ItemStack stack, ItemEditPayload payload) {
        String name = payload.name();
        if (name == null || name.isBlank()) {
            stack.remove(DataComponents.CUSTOM_NAME);
        } else {
            stack.set(DataComponents.CUSTOM_NAME, ItemEdit.component(name));
        }

        // Only when the message says so: a screen that cannot show the lore has not said the item
        // should have none, and an edit that took it away would be one nobody asked for.
        if (payload.withLore()) {
            List<String> lore = payload.lore();
            if (lore == null || lore.isEmpty()) {
                stack.remove(DataComponents.LORE);
            } else {
                List<Component> lines = new ArrayList<>();
                for (String line : lore) lines.add(ItemEdit.component(line));
                stack.set(DataComponents.LORE, new ItemLore(lines));
            }
        }

        if (payload.withAttributes()) {
            if (payload.attributes().isEmpty()) {
                stack.remove(DataComponents.ATTRIBUTE_MODIFIERS);
            } else {
                stack.set(DataComponents.ATTRIBUTE_MODIFIERS, modifiers(payload.attributes()));
            }
        }
    }

    /**
     * {@return the attribute modifiers one edit describes}
     *
     * <p>An attribute this game does not have is <b>left out rather than invented</b>: the id came from
     * a client, and a client can name anything. What it cannot do is put an attribute into a registry,
     * so the list is built from what this server actually has and the rest is dropped.
     */
    private static ItemAttributeModifiers modifiers(List<ItemEdit.Granted> granted) {
        List<ItemAttributeModifiers.Entry> entries = new ArrayList<>();
        for (ItemEdit.Granted one : granted) {
            Holder<Attribute> attribute = BuiltInRegistries.ATTRIBUTE.getHolder(one.attribute())
                    .orElse(null);
            if (attribute == null) continue;
            entries.add(new ItemAttributeModifiers.Entry(attribute,
                    new AttributeModifier(ItemEdit.Granted.id(one.attribute()), one.amount(),
                            one.operation()),
                    one.slot() == null ? EquipmentSlotGroup.ANY : one.slot()));
        }
        return new ItemAttributeModifiers(entries, true);
    }

    private static void refuse(ServerPlayer player) {
        player.displayClientMessage(Component.translatable("backutils.item_editor.refused"), false);
    }
}
