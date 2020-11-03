package com.markus1002.autumnity.common.entity.passive;

import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import com.markus1002.autumnity.core.other.AutumnityCriteriaTriggers;
import com.markus1002.autumnity.core.other.AutumnityTags;
import com.markus1002.autumnity.core.registry.AutumnityBlocks;
import com.markus1002.autumnity.core.registry.AutumnityEntities;
import com.markus1002.autumnity.core.registry.AutumnityItems;
import com.markus1002.autumnity.core.registry.AutumnitySoundEvents;

import net.minecraft.block.BlockState;
import net.minecraft.entity.AgeableEntity;
import net.minecraft.entity.EntitySize;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.Pose;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.AttributeModifierMap;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.ai.controller.LookController;
import net.minecraft.entity.ai.controller.MovementController;
import net.minecraft.entity.ai.goal.BreedGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAtGoal;
import net.minecraft.entity.ai.goal.LookRandomlyGoal;
import net.minecraft.entity.ai.goal.TemptGoal;
import net.minecraft.entity.ai.goal.WaterAvoidingRandomWalkingGoal;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.MooshroomEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.IPacket;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.particles.ItemParticleData;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Hand;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.network.NetworkHooks;

public class SnailEntity extends AnimalEntity
{
	private static final UUID HIDING_ARMOR_BONUS_ID = UUID.fromString("73BF0604-4235-4D4C-8A74-6A633E526E24");
	private static final AttributeModifier HIDING_ARMOR_BONUS_MODIFIER = new AttributeModifier(HIDING_ARMOR_BONUS_ID, "Hiding armor bonus", 20.0D, AttributeModifier.Operation.ADDITION);
	private static final DataParameter<Integer> EATING_TIME = EntityDataManager.createKey(SnailEntity.class, DataSerializers.VARINT);
	private static final DataParameter<Boolean> HIDING = EntityDataManager.createKey(SnailEntity.class, DataSerializers.BOOLEAN);
	private int hidingTime = 0;
	private int slimeAmount = 0;

	private float hideTicks;
	private float prevHideTicks;

	private int shakeTicks;
	private int prevShakeTicks;

	private boolean canBreed = true;

	private static final Predicate<LivingEntity> ENEMY_MATCHER = (livingentity) -> {
		if (livingentity == null)
		{
			return false;
		}
		else if (livingentity.getItemStackFromSlot(EquipmentSlotType.CHEST) != null && livingentity.getItemStackFromSlot(EquipmentSlotType.CHEST).getItem() == AutumnityItems.SNAIL_SHELL_CHESTPLATE.get())
		{
			return false;
		}
		else if (livingentity instanceof PlayerEntity)
		{
			return !livingentity.isSneaking() && !livingentity.isSpectator() && !((PlayerEntity)livingentity).isCreative();
		}
		else
		{
			return !(livingentity instanceof SnailEntity) && !(livingentity instanceof MooshroomEntity);
		}
	};

	public SnailEntity(EntityType<? extends SnailEntity> type, World worldIn)
	{
		super(type, worldIn);
		this.lookController = new SnailEntity.LookHelperController();
		this.moveController = new SnailEntity.MoveHelperController();
	}

	@Override
	protected void registerGoals()
	{
		this.goalSelector.addGoal(0, new SnailEntity.HideGoal());
		this.goalSelector.addGoal(1, new SnailEntity.GetOutOfShellGoal());
		this.goalSelector.addGoal(2, new BreedGoal(this, 0.5D));
		this.goalSelector.addGoal(3, new SnailEntity.FollowFoodGoal());
		this.goalSelector.addGoal(4, new SnailEntity.EatMushroomsGoal());
		this.goalSelector.addGoal(5, new SnailEntity.EatMooshroomMushroomsGoal());
		this.goalSelector.addGoal(6, new WaterAvoidingRandomWalkingGoal(this, 0.5D));
		this.goalSelector.addGoal(7, new SnailEntity.WatchGoal());
		this.goalSelector.addGoal(8, new LookRandomlyGoal(this));
	}

	public static AttributeModifierMap.MutableAttribute registerAttributes()
	{
		return MobEntity.func_233666_p_()
				.createMutableAttribute(Attributes.MAX_HEALTH, 18.0D)
				.createMutableAttribute(Attributes.MOVEMENT_SPEED, 0.25D)
				.createMutableAttribute(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
	}

	@Override
	protected float getStandingEyeHeight(Pose poseIn, EntitySize sizeIn)
	{
		return sizeIn.height * 0.5F;
	}

	@Override
	public ItemStack getPickedResult(RayTraceResult target)
	{
		return new ItemStack(AutumnityItems.SNAIL_SPAWN_EGG.get());
	}

	@Nullable
	@Override
	protected SoundEvent getDeathSound()
	{
		return AutumnitySoundEvents.ENTITY_SNAIL_HURT.get();
	}

	@Nullable
	@Override
	protected SoundEvent getHurtSound(DamageSource damageSourceIn)
	{
		return AutumnitySoundEvents.ENTITY_SNAIL_HURT.get();
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState blockIn)
	{
		this.playSound(AutumnitySoundEvents.ENTITY_SNAIL_STEP.get(), 0.3F, 1.0F);
	}

	@Override
	public void tick()
	{
		super.tick();

		if (this.world.isRemote)
		{
			this.prevHideTicks = this.hideTicks;
			if (this.getHiding())
			{
				this.hideTicks = MathHelper.clamp(this.hideTicks + 1, 0, 3);
			}
			else
			{
				this.hideTicks = MathHelper.clamp(this.hideTicks - 0.5F, 0, 3);
			}

			this.prevShakeTicks = this.shakeTicks;
			if (this.shakeTicks > 0)
			{
				this.shakeTicks = MathHelper.clamp(this.shakeTicks - 1, 0, 20);
			}
			else
			{
				this.shakeTicks = MathHelper.clamp(this.shakeTicks + 1, -20, 0);
			}	
		}
	}

	@Override
	public void livingTick()
	{
		if (!this.canMove() || this.isMovementBlocked())
		{
			this.isJumping = false;
			this.moveStrafing = 0.0F;
			this.moveForward = 0.0F;
		}

		super.livingTick();

		if (this.isEating())
		{
			this.eat();
			if (!this.world.isRemote)
			{
				this.setEatingTime(this.getEatingTime() - 1);
			}
		}

		if (!this.world.isRemote && this.isAlive())
		{
			ItemStack itemstack = this.getItemStackFromSlot(EquipmentSlotType.MAINHAND);
			if (!itemstack.isEmpty())
			{
				if (this.isFoodItem(itemstack))
				{
					if (!this.isEating())
					{
						this.setSlimeAmount(this.rand.nextInt(3) + 5);

						if (Ingredient.fromTag(AutumnityTags.SNAIL_GLOWING_FOODS).test(itemstack))
						{
							this.addPotionEffect(new EffectInstance(Effects.GLOWING, 200, 0));
						}
						if (Ingredient.fromTag(AutumnityTags.SNAIL_SPEEDING_FOODS).test(itemstack))
						{
							this.addPotionEffect(new EffectInstance(Effects.SPEED, 320, 2));
						}

						Item item = itemstack.getItem();
						ItemStack itemstack1 = itemstack.onItemUseFinish(this.world, this);
						if (!itemstack1.isEmpty())
						{
							if (itemstack1.getItem() != item)
							{
								this.setItemStackToSlot(EquipmentSlotType.MAINHAND, itemstack1);
								this.spitOutItem();
							}
							else
							{
								this.setItemStackToSlot(EquipmentSlotType.MAINHAND, ItemStack.EMPTY);
							}
						}
					}
				}
				else
				{
					this.spitOutItem();
				}
			}

			if (this.getSlimeAmount() > 0 && net.minecraftforge.event.ForgeEventFactory.getMobGriefingEvent(this.world, this))
			{
				int i = MathHelper.floor(this.getPosX());
				int j = MathHelper.floor(this.getPosY());
				int k = MathHelper.floor(this.getPosZ());
				BlockState blockstate = AutumnityBlocks.SNAIL_SLIME.get().getDefaultState();

				for(int l = 0; l < 4; ++l)
				{
					i = MathHelper.floor(this.getPosX() + (double)((float)(l % 2 * 2 - 1) * 0.25F));
					j = MathHelper.floor(this.getPosY());
					k = MathHelper.floor(this.getPosZ() + (double)((float)(l / 2 % 2 * 2 - 1) * 0.25F));
					BlockPos blockpos = new BlockPos(i, j, k);
					if (this.getSlimeAmount() > 0 && this.world.isAirBlock(blockpos) && blockstate.isValidPosition(this.world, blockpos))
					{
						this.world.setBlockState(blockpos, blockstate);
						this.setSlimeAmount(this.getSlimeAmount() - 1);
					}
				}
			}

			if (this.hidingTime > 0)
			{
				this.hidingTime--;
			}
		}
	}

	public void eat()
	{
		if ((this.getEatingTime() + 1) % 12 == 0 && !this.getItemStackFromSlot(EquipmentSlotType.MAINHAND).isEmpty())
		{
			this.playSound(AutumnitySoundEvents.ENTITY_SNAIL_EAT.get(), 0.25F + 0.5F * (float)this.rand.nextInt(2), (this.rand.nextFloat() - this.rand.nextFloat()) * 0.2F + 1.0F);

			for(int i = 0; i < 6; ++i)
			{
				Vector3d vector3d = new Vector3d(((double)this.rand.nextFloat() - 0.5D) * 0.1D, Math.random() * 0.1D + 0.1D, ((double)this.rand.nextFloat() - 0.5D) * 0.1D);
				vector3d = vector3d.rotatePitch(-this.rotationPitch * ((float)Math.PI / 180F));
				vector3d = vector3d.rotateYaw(-this.rotationYaw * ((float)Math.PI / 180F));
				double d0 = (double)(-this.rand.nextFloat()) * 0.2D;
				Vector3d vector3d1 = new Vector3d(((double)this.rand.nextFloat() - 0.5D) * 0.2D, d0, 0.8D + ((double)this.rand.nextFloat() - 0.5D) * 0.2D);
				vector3d1 = vector3d1.rotateYaw(-this.renderYawOffset * ((float)Math.PI / 180F));
				vector3d1 = vector3d1.add(this.getPosX(), this.getPosY() + (double)this.getEyeHeight(), this.getPosZ());
				this.world.addParticle(new ItemParticleData(ParticleTypes.ITEM, this.getItemStackFromSlot(EquipmentSlotType.MAINHAND)), vector3d1.x, vector3d1.y, vector3d1.z, vector3d.x, vector3d.y + 0.05D, vector3d.z);
			}
		}
	}

	@Override
	protected void onGrowingAdult()
	{
		super.onGrowingAdult();
		if (!this.isChild() && this.world.getGameRules().getBoolean(GameRules.DO_MOB_LOOT))
		{
			this.entityDropItem(AutumnityItems.SNAIL_SHELL_PIECE.get(), 1);
		}
	}

	@Override
	public ActionResultType func_230254_b_(PlayerEntity player, Hand hand)
	{
		if (!this.getHiding() && !this.isEating())
		{
			ItemStack itemstack = player.getHeldItem(hand);
			if (!itemstack.isEmpty() && this.getItemStackFromSlot(EquipmentSlotType.MAINHAND).isEmpty())
			{
				if (this.isFoodItem(itemstack))
				{
					if (!this.isChild() && this.getSlimeAmount() <= 0)
					{
						if (!this.world.isRemote)
						{
							ItemStack itemstack1 = itemstack.copy();
							itemstack1.setCount(1);
							this.setItemStackToSlot(EquipmentSlotType.MAINHAND, itemstack1);
							this.setEatingTime(192);
							AutumnityCriteriaTriggers.FEED_SNAIL.trigger((ServerPlayerEntity) player, itemstack1); 
						}
						this.consumeItemFromStack(player, itemstack);
						return ActionResultType.SUCCESS;
					}
				}
				else if (this.isSnailBreedingItem(itemstack))
				{
					boolean flag = false;

					if (this.getGrowingAge() == 0 && !this.isChild() && this.canBreed())
					{
						if (!this.world.isRemote)
						{
							this.setInLove(player);
						}
						flag = true;
					}
					else if (this.isChild())
					{
						this.ageUp((int)((float)(-this.getGrowingAge() / 20) * 0.1F), true);
						flag = true;
					}

					if (flag)
					{
						if (!this.world.isRemote)
						{
							ItemStack itemstack1 = itemstack.onItemUseFinish(this.world, this);
							if (!player.abilities.isCreativeMode && !itemstack1.isEmpty())
							{
								player.setHeldItem(hand, itemstack1);
							}
						}

						return ActionResultType.SUCCESS;
					}
				}
			}
		}

		this.canBreed = false;
		ActionResultType result = super.func_230254_b_(player, hand);
		this.canBreed = true;
		return result;
	}

	@Override
	public boolean attackEntityFrom(DamageSource source, float amount)
	{
		if (this.isInvulnerableTo(source))
		{
			return false;
		}
		else
		{
			boolean flag = super.attackEntityFrom(source, amount);

			if (!this.world.isRemote)
			{
				if (!this.isAIDisabled())
				{
					this.hidingTime = 160 + rand.nextInt(200);
					this.setHiding(true);
				}
				this.spitOutItem();
			}
			else
			{
				this.shakeTicks = this.rand.nextInt(2) == 0 ? -10 : 10;
			}

			return flag;
		}
	}

	private void spitOutItem()
	{
		ItemStack itemstack = this.getItemStackFromSlot(EquipmentSlotType.MAINHAND);
		if (!itemstack.isEmpty())
		{
			if (!this.world.isRemote)
			{
				ItemEntity itementity = new ItemEntity(this.world, this.getPosX() + this.getLookVec().x, this.getPosY() + this.getEyeHeight(), this.getPosZ() + this.getLookVec().z, itemstack);
				itementity.setPickupDelay(40);
				itementity.setThrowerId(this.getUniqueID());
				this.world.addEntity(itementity);
				this.setItemStackToSlot(EquipmentSlotType.MAINHAND, ItemStack.EMPTY);
				this.setEatingTime(0);
			}
		}
	}

	public int getEatingTime()
	{
		return this.dataManager.get(EATING_TIME);
	}

	public void setEatingTime(int eatingTimeIn)
	{
		this.dataManager.set(EATING_TIME, eatingTimeIn);
	}

	public boolean isEating()
	{
		return this.getEatingTime() > 0;
	}

	public boolean getHiding()
	{
		return this.dataManager.get(HIDING);
	}

	public void setHiding(boolean hiding)
	{
		if (hiding)
		{
			this.dataManager.set(HIDING, true);
		}
		else
		{
			this.dataManager.set(HIDING, false);
		}

		if (!this.world.isRemote)
		{
			this.getAttribute(Attributes.ARMOR).removeModifier(HIDING_ARMOR_BONUS_MODIFIER);
			if (hiding)
			{
				this.getAttribute(Attributes.ARMOR).applyNonPersistentModifier(HIDING_ARMOR_BONUS_MODIFIER);
			}
		}
	}

	public boolean canMove()
	{
		return !this.getHiding() && !this.isEating();
	}

	private int getSlimeAmount()
	{
		return this.slimeAmount;
	}

	private void setSlimeAmount(int slimeAmountIn)
	{
		this.slimeAmount = slimeAmountIn;
	}

	@OnlyIn(Dist.CLIENT)
	public float getHidingAnimationScale(float partialTicks)
	{
		return MathHelper.lerp(partialTicks, this.prevHideTicks, this.hideTicks) / 3.0F;
	}

	@OnlyIn(Dist.CLIENT)
	public float getHideTicks()
	{
		return this.hideTicks;
	}

	@OnlyIn(Dist.CLIENT)
	public float getShakingAnimationScale(float partialTicks)
	{
		return MathHelper.lerp(partialTicks, this.prevShakeTicks, this.shakeTicks) / 10.0F;
	}

	@OnlyIn(Dist.CLIENT)
	public float getShakeTicks()
	{
		return this.shakeTicks;
	}

	@Override
	protected void registerData()
	{
		super.registerData();
		this.dataManager.register(EATING_TIME, 0);
		this.dataManager.register(HIDING, false);
	}

	@Override
	public void writeAdditional(CompoundNBT compound)
	{
		super.writeAdditional(compound);
		compound.putInt("SlimeAmount", this.getSlimeAmount());
		compound.putBoolean("Hiding", this.getHiding());
	}

	@Override
	public void readAdditional(CompoundNBT compound)
	{
		super.readAdditional(compound);
		this.setSlimeAmount(compound.getInt("SlimeAmount"));
		this.setHiding(compound.getBoolean("Hiding"));
	}

	private boolean isFoodItem(ItemStack stack)
	{
		return Ingredient.fromTag(AutumnityTags.SNAIL_FOODS).test(stack);
	}

	@Override
	public boolean isBreedingItem(ItemStack stack)
	{
		return this.canBreed ? this.isSnailBreedingItem(stack) : false;
	}

	private boolean isSnailBreedingItem(ItemStack stack)
	{
		return Ingredient.fromTag(AutumnityTags.SNAIL_BREEDING_ITEMS).test(stack);
	}

	@Override
	public AgeableEntity createChild(AgeableEntity ageable)
	{
		return AutumnityEntities.SNAIL.get().create(this.world);
	}

	@Override
	public IPacket<?> createSpawnPacket()
	{
		return NetworkHooks.getEntitySpawningPacket(this);
	}

	@Override
	protected float determineNextStepDistance()
	{
		return this.distanceWalkedOnStepModified + 0.15F;
	}

	public class HideGoal extends Goal
	{
		public HideGoal()
		{
			super();
		}

		public boolean shouldExecute()
		{
			if (!SnailEntity.this.getHiding() && !SnailEntity.this.isEating())
			{
				for(LivingEntity livingentity : SnailEntity.this.world.getEntitiesWithinAABB(LivingEntity.class, SnailEntity.this.getBoundingBox().grow(0.3D), ENEMY_MATCHER))
				{
					if (livingentity.isAlive())
					{
						return true;
					}
				}
			}

			return false;
		}

		public void startExecuting()
		{
			SnailEntity.this.hidingTime = 100 + rand.nextInt(100);
			SnailEntity.this.setHiding(true);
		}

		public boolean shouldContinueExecuting()
		{
			return false;
		}
	}

	public class GetOutOfShellGoal extends Goal
	{
		public GetOutOfShellGoal()
		{
			super();
		}

		public boolean shouldExecute()
		{
			return SnailEntity.this.getRevengeTarget() == null && SnailEntity.this.hidingTime <= 0;
		}

		public void startExecuting()
		{
			SnailEntity.this.setHiding(false);
		}

		public boolean shouldContinueExecuting()
		{
			return false;
		}
	}

	class FollowFoodGoal extends TemptGoal
	{
		public FollowFoodGoal()
		{
			super(SnailEntity.this, 0.5F, false, null);
		}

		public boolean shouldExecute()
		{
			return !SnailEntity.this.canMove() ? false : super.shouldExecute();
		}

		protected boolean isTempting(ItemStack stack)
		{
			return Ingredient.fromTag(AutumnityTags.SNAIL_TEMPTATION_ITEMS).test(stack);
		}
	}

	class WatchGoal extends LookAtGoal
	{
		public WatchGoal()
		{
			super(SnailEntity.this, PlayerEntity.class, 6.0F);
		}

		public boolean shouldExecute()
		{
			return super.shouldExecute() && !SnailEntity.this.getHiding();
		}

		public boolean shouldContinueExecuting()
		{
			return super.shouldContinueExecuting() && !SnailEntity.this.getHiding();
		}
	}

	class EatMushroomsGoal extends Goal
	{
		private double mushroomX;
		private double mushroomY;
		private double mushroomZ;

		public EatMushroomsGoal()
		{
			super();
			this.setMutexFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		public boolean shouldExecute()
		{
			if (SnailEntity.this.getRNG().nextInt(20) != 0)
			{
				return false;
			}
			else
			{
				return !SnailEntity.this.isChild() && SnailEntity.this.canMove() && SnailEntity.this.getSlimeAmount() <= 0 && net.minecraftforge.event.ForgeEventFactory.getMobGriefingEvent(SnailEntity.this.world, SnailEntity.this) && this.canMoveToMushroom();
			}
		}

		protected boolean canMoveToMushroom()
		{
			Vector3d vec3d = this.findMushroom();
			if (vec3d == null)
			{
				return false;
			}
			else
			{
				this.mushroomX = vec3d.x;
				this.mushroomY = vec3d.y;
				this.mushroomZ = vec3d.z;
				return true;
			}
		}

		public boolean shouldContinueExecuting()
		{
			return !SnailEntity.this.getNavigator().noPath();
		}

		public void startExecuting()
		{
			SnailEntity.this.getNavigator().tryMoveToXYZ(this.mushroomX, this.mushroomY, this.mushroomZ, 0.5D);
		}

		@Nullable
		protected Vector3d findMushroom()
		{
			Random random = SnailEntity.this.getRNG();
			BlockPos blockpos = new BlockPos(SnailEntity.this.getPosX(), SnailEntity.this.getBoundingBox().minY, SnailEntity.this.getPosZ());

			for(int i = 0; i < 10; ++i)
			{
				BlockPos blockpos1 = blockpos.add(random.nextInt(20) - 10, random.nextInt(6) - 3, random.nextInt(20) - 10);
				if (this.isBlockMushroom(blockpos1))
				{
					return new Vector3d((double)blockpos1.getX(), (double)blockpos1.getY(), (double)blockpos1.getZ());
				}
			}

			return null;
		}

		public void tick()
		{
			if (!SnailEntity.this.isChild() && SnailEntity.this.canMove() && SnailEntity.this.getSlimeAmount() <= 0)
			{
				BlockPos blockpos = SnailEntity.this.getPosition();

				if (this.isBlockMushroom(blockpos))
				{
					if (net.minecraftforge.event.ForgeEventFactory.getMobGriefingEvent(SnailEntity.this.world, SnailEntity.this))
					{
						SnailEntity.this.setItemStackToSlot(EquipmentSlotType.MAINHAND, new ItemStack(SnailEntity.this.world.getBlockState(blockpos).getBlock().asItem(), 1));
						SnailEntity.this.setEatingTime(192);
						SnailEntity.this.world.destroyBlock(blockpos, false);
					}
				}
			}
		}

		private boolean isBlockMushroom(BlockPos pos)
		{
			return AutumnityTags.SNAIL_BLOCK_FOODS.contains(SnailEntity.this.world.getBlockState(pos).getBlock());
		}
	}

	public class EatMooshroomMushroomsGoal extends Goal
	{
		private MooshroomEntity targetMooshroom;
		private int delayCounter;

		public EatMooshroomMushroomsGoal()
		{
			super();
		}

		public boolean shouldExecute()
		{
			if (!SnailEntity.this.isChild() && SnailEntity.this.canMove() && SnailEntity.this.getSlimeAmount() <= 0)
			{
				List<MooshroomEntity> list = SnailEntity.this.world.getEntitiesWithinAABB(MooshroomEntity.class, SnailEntity.this.getBoundingBox().grow(8.0D, 4.0D, 8.0D));
				MooshroomEntity mooshroom = null;
				double d0 = Double.MAX_VALUE;

				for(MooshroomEntity mooshroom1 : list)
				{
					if (mooshroom1.getGrowingAge() >= 0)
					{
						double d1 = SnailEntity.this.getDistanceSq(mooshroom1);
						if (!(d1 > d0))
						{
							d0 = d1;
							mooshroom = mooshroom1;
						}
					}
				}

				if (mooshroom == null)
				{
					return false;
				}
				else
				{
					this.targetMooshroom = mooshroom;
					return true;
				}
			}
			else
			{
				return false;
			}
		}

		public boolean shouldContinueExecuting()
		{
			if (!this.targetMooshroom.isAlive())
			{
				return false;
			}
			else if (!SnailEntity.this.canMove())
			{
				return false;
			}
			else if (SnailEntity.this.getSlimeAmount() > 0)
			{
				return false;
			}
			else
			{
				double d0 = this.targetMooshroom.getDistanceSq(SnailEntity.this);
				return !(d0 > 256.0D);
			}
		}

		public void startExecuting()
		{
			this.delayCounter = 0;
		}

		public void resetTask()
		{
			this.targetMooshroom = null;
		}

		public void tick()
		{
			if (--this.delayCounter <= 0)
			{
				this.delayCounter = 10;
				SnailEntity.this.getNavigator().tryMoveToEntityLiving(this.targetMooshroom, 0.5D);
			}

			if (this.targetMooshroom != null && this.targetMooshroom.isAlive())
			{
				double d0 = this.targetMooshroom.getDistanceSq(SnailEntity.this);
				if (d0 < 2.0D)
				{
					if (this.targetMooshroom.getMooshroomType() == MooshroomEntity.Type.BROWN)
					{
						SnailEntity.this.setItemStackToSlot(EquipmentSlotType.MAINHAND, new ItemStack(Items.BROWN_MUSHROOM, 1));
					}
					else
					{
						SnailEntity.this.setItemStackToSlot(EquipmentSlotType.MAINHAND, new ItemStack(Items.RED_MUSHROOM, 1));
					}

					SnailEntity.this.setEatingTime(192);
					this.targetMooshroom.attackEntityFrom(DamageSource.causeMobDamage(SnailEntity.this), 0.0F);
				}
			}
		}
	}

	public class LookHelperController extends LookController
	{
		public LookHelperController()
		{
			super(SnailEntity.this);
		}

		public void tick()
		{
			if (!SnailEntity.this.getHiding())
			{
				super.tick();
			}
		}
	}

	class MoveHelperController extends MovementController
	{
		public MoveHelperController()
		{
			super(SnailEntity.this);
		}

		public void tick()
		{
			if (SnailEntity.this.canMove())
			{
				super.tick();
			}
		}
	}
}