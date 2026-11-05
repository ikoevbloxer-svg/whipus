package com.crackthewhip;

import com.crackthewhip.network.ModNetwork;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(CrackTheWhip.MODID)
public class CrackTheWhip {
    public static final String MODID = "crackthewhip";

    public CrackTheWhip(IEventBus modBus) {
        ModRegistry.register(modBus);
        modBus.addListener(ModNetwork::register);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
