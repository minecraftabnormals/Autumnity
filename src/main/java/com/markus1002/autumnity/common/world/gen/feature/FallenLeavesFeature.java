package com.markus1002.autumnity.common.world.gen.feature;

import java.util.Random;

import com.markus1002.autumnity.core.registry.AutumnityBlocks;
import com.mojang.serialization.Codec;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ISeedReader;
import net.minecraft.world.gen.ChunkGenerator;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.NoFeatureConfig;
import net.minecraft.world.gen.feature.structure.StructureManager;

public class FallenLeavesFeature extends Feature<NoFeatureConfig>
{
	public FallenLeavesFeature(Codec<NoFeatureConfig> config)
	{
		super(config);
	}

	public boolean func_230362_a_(ISeedReader worldIn, StructureManager manager, ChunkGenerator generator, Random rand, BlockPos pos, NoFeatureConfig config)
	{
		int i = 0;
		
		int j = rand.nextInt(3);
		BlockState blockstate = j == 0 ? AutumnityBlocks.YELLOW_MAPLE_LEAF_CARPET.get().getDefaultState() : AutumnityBlocks.ORANGE_MAPLE_LEAF_CARPET.get().getDefaultState();

		for(int x = -3; x <= 3; ++x)
		{
			for(int z = -3; z <= 3; ++z)
			{
				if (Math.abs(x) < 2 || Math.abs(z) < 2) 
				{
					for(int y = -3; y <= 3; ++y)
					{
						BlockPos blockpos = pos.add(x, y, z);
						if (rand.nextInt(3) > 0 && worldIn.isAirBlock(blockpos) && blockpos.getY() < worldIn.getHeight() && worldIn.getBlockState(blockpos.down()).getBlock() == Blocks.GRASS_BLOCK)
						{
							worldIn.setBlockState(blockpos, blockstate, 2);
							++i;
						}
					}
				}
			}
		}

		return i > 0;
	}
}