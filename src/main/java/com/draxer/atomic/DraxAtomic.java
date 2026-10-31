package com.draxer.atomic;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Drax Atomic: una espada de slime que, al mantener clic derecho, carga y desata una
 * explosión atómica centrada en quien la empuña.
 */
@Mod(DraxAtomic.MOD_ID)
public final class DraxAtomic {
    public static final String MOD_ID = "draxatomic";
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);

    public static final DeferredHolder<Item, Item> SLIME_SWORD = ITEMS.registerItem("slime_sword",
            properties -> new SlimeSwordItem(properties.sword(ToolMaterial.NETHERITE, 4.0F, -2.4F)
                    .fireResistant().rarity(Rarity.EPIC)));

    public DraxAtomic(IEventBus modEventBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, AtomicConfig.SPEC);
        ITEMS.register(modEventBus);
        Mana.init(modEventBus);
        SlimeSuit.init();
        Cult.init(modEventBus);
        Vampires.init(modEventBus);
        ShadowGarden.init(modEventBus);
        Mitsugoshi.init(modEventBus);
        Cities.init(modEventBus);
        Magic.init();
        modEventBus.addListener(DraxAtomic::addToTabs);
    }

    private static void addToTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(SLIME_SWORD.value());
            event.accept(SlimeSuit.HOOD.value());
            event.accept(SlimeSuit.COAT.value());
            event.accept(SlimeSuit.PANTS.value());
            event.accept(SlimeSuit.BOOTS.value());
            event.accept(ShadowGarden.EMBLEM.value());
            event.accept(Cult.FRAGMENT.value());
            event.accept(Cult.SEAL.value());
            event.accept(Cult.HEART.value());
            event.accept(Vampires.BLOOD_VIAL.value());
            event.accept(Vampires.CHALICE.value());
            event.accept(Vampires.CRIMSON_BLOOD.value());
            event.accept(Vampires.QUEEN_BLOOD.value());
        }
        if (event.getTabKey() == CreativeModeTabs.FOOD_AND_DRINKS) {
            event.accept(Mitsugoshi.CHOCOLATE.value());
            event.accept(Mitsugoshi.COFFEE.value());
            event.accept(Mitsugoshi.SHAMPOO.value());
        }
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(Mitsugoshi.COUNTER_ITEM.value());
        }
    }
}
