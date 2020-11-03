package com.markus1002.autumnity.core.registry;

import com.markus1002.autumnity.core.Reference;

import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionBrewing;
import net.minecraft.potion.Potions;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public class AutumnityPotions
{
	public static final DeferredRegister<Potion> POTIONS = DeferredRegister.create(ForgeRegistries.POTION_TYPES, Reference.MOD_ID);
	
	public static final RegistryObject<Potion> EXTENSION = POTIONS.register("extension", () -> new Potion("extension", new EffectInstance(AutumnityEffects.EXTENSION.get(), 1)));
	
	public static void registerBrewingRecipes()
	{
		PotionBrewing.addMix(Potions.AWKWARD, AutumnityBlocks.SNAIL_SLIME.get().asItem(), EXTENSION.get());
	}
}