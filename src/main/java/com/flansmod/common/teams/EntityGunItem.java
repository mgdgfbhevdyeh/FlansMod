package com.flansmod.common.teams;

import java.util.ArrayList;
import java.util.List;

import com.flansmod.common.EntityCustomItem;
import com.flansmod.common.PlayerHandler;
import com.flansmod.common.guns.GunType;
import com.flansmod.common.guns.ItemBullet;
import com.flansmod.common.guns.ItemGun;
import com.flansmod.common.guns.ItemShootable;
import com.flansmod.common.guns.ShootableType;

import net.minecraft.block.Block;
import net.minecraft.entity.MoverType;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.item.ItemExpireEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;

public class EntityGunItem extends EntityCustomItem
{
	
	public List<ItemStack> ammoStacks;
	private boolean teamsModEnabled = false;
	private int age;
	
	public EntityGunItem(World w)
	{
		super(w);
	}
	
	public EntityGunItem(EntityItem entity)
	{
		super(entity.world, entity.posX, entity.posY, entity.posZ, entity.getEntityItem().copy());
		setSize(1F, 1F);
		ammoStacks = new ArrayList<ItemStack>();
	}

	public EntityGunItem(World w, double x, double y, double z, ItemStack stack, List<ItemStack> stacks) 
	{
		super(w, x, y, z, stack);
		setSize(1F, 1F);
		ammoStacks = new ArrayList<ItemStack>();
		for(ItemStack ammoStack : stacks)
		{
			if(ammoStack != null && ammoStack.getItem() != null && (ammoStack.getItem() instanceof ItemBullet))
				ammoStacks.add(ammoStack);
		}
		teamsModEnabled = true;
	}	
	
	public EntityGunItem(World w, double x, double y, double z)
	{
		super(w, x, y, z);
	}
	
	@Override
	public boolean canBeCollidedWith()
	{
		return true;
	}
	
	@Override
	protected boolean canTriggerWalking()
	{
		return true;
	}
	
	@Override
	public AxisAlignedBB getEntityBoundingBox()
	{
		return null;
	}
	
	@Override
	public void onUpdate()
	{
		onEntityUpdate();
		if(getEntityItem() == null || getEntityItem().getItem() == null || !(getEntityItem().getItem() instanceof ItemGun))
			setDead();
		
		if(!world.isRemote && ammoStacks == null)
			setDead();

        prevPosX = posX;
        prevPosY = posY;
        prevPosZ = posZ;
        motionY -= 0.03999999910593033D;
        if(getEntityBoundingBox() != null)
        {
        	pushOutOfBlocks(posX, (getEntityBoundingBox().minY + getEntityBoundingBox().maxY) / 2.0D, posZ); //PushOutOfBlocks
        }
        move(MoverType.SELF, motionX, motionY, motionZ);

        float var2 = 0.98F;

        if (onGround)
        {
            var2 = 0.58800006F;
            Block block = world.getBlockState(new BlockPos(MathHelper.floor(posX), MathHelper.floor(getEntityBoundingBox().minY) - 1, MathHelper.floor(posZ))).getBlock();

            if (block != null)
            {
                var2 = block.slipperiness * 0.98F;
            }
        }

        motionX *= var2;
        motionY *= 0.9800000190734863D;
        motionZ *= var2;

        if (onGround)
        {
            motionY *= -0.5D;
        }

        ++age;

        //TODO
        ItemStack item = getEntityItem();//getDataWatcher().getWatchableObjectItemStack(10);

        if(!world.isRemote && age >= lifespan){
            if(item != null){   
                ItemExpireEvent event = new ItemExpireEvent(this, (item.getItem() == null ? 6000 : item.getItem().getEntityLifespan(item, world)));
                if (MinecraftForge.EVENT_BUS.post(event)){
                    lifespan += event.getExtraLife();
                }
                else{
                    setDead();
                }
            }
            else{
                setDead();
            }
        }

        if(item != null && item.getCount() <= 0){
            setDead();
        }
        
		//Temporary fire glitch fix
		if(world.isRemote){
			extinguish();
		}
	}
	
	@Override
	public boolean attackEntityFrom(DamageSource par1DamageSource, float par2){
	 	return true;
	}

	@Override
	public void onCollideWithPlayer(EntityPlayer player)
	{
		if (!world.isRemote)
		{
			if(ammoStacks != null && ammoStacks.size() > 0)
			{
				for(int i = 0; i < player.inventory.getSizeInventory(); i++)
				{
					ItemStack stack = player.inventory.getStackInSlot(i);
					if(stack != null && stack.getItem() != null && stack.getItem() instanceof ItemGun)
					{
						GunType type = ((ItemGun)stack.getItem()).getInfoType();
						for(int j = ammoStacks.size() - 1; j >= 0; j--)
						{
							ItemStack ammoStack = ammoStacks.get(j);
							if(type.isAmmo(((ItemShootable)ammoStack.getItem()).type))
							{
								if(player.inventory.addItemStackToInventory(ammoStack))
								{
									FMLCommonHandler.instance().firePlayerItemPickupEvent(player, this);
									//TODO playSound("random.pop", 0.2F, ((rand.nextFloat() - rand.nextFloat()) * 0.7F + 1.0F) * 2.0F);
									ammoStacks.remove(j);
								}
							}
						}
						if(ammoStacks.size() == 0)
							setDead();
					}
				}
			}
			else if(!teamsModEnabled)
				super.onCollideWithPlayer(player);
		}
	}

	@Override //TODO
	public boolean processInitialInteract(EntityPlayer player, EnumHand hand) //interact
	{
		if(world.isRemote)
			return true;
		EntityItemPickupEvent event = new EntityItemPickupEvent(player, this);
		//TODO fsu implementation? 
		//TeamsManager.getInstance().playerLoot(event);
		if(!event.isCanceled())
		{
			ItemStack currentItem = player.getHeldItemMainhand();
			if(currentItem != null && currentItem.getItem() instanceof ItemGun)
			{
				GunType gunType = ((ItemGun)currentItem.getItem()).getInfoType();
				List<ItemStack> newAmmoStacks = new ArrayList<ItemStack>();
				for(int i = 0; i < player.inventory.getSizeInventory(); i++)
				{
					ItemStack stack = player.inventory.getStackInSlot(i);
					if(stack != null && stack.getItem() instanceof ItemShootable)
					{
						ShootableType bulletType = ((ItemShootable)stack.getItem()).type;
						if(gunType.isAmmo(bulletType))
						{
							newAmmoStacks.add(stack.copy());
							player.inventory.setInventorySlotContents(i, null);
						}
					}
				}
				EntityGunItem newGunItem = new EntityGunItem(world, posX, posY, posZ, currentItem.copy(), newAmmoStacks);
				world.spawnEntity(newGunItem);
				player.inventory.setInventorySlotContents(player.inventory.currentItem, getEntityItem());
				for(ItemStack stack : ammoStacks)
				{
					player.inventory.addItemStackToInventory(stack);
				}
				setDead();
				PlayerHandler.getPlayerData(player).shootClickDelay = 10;
				PlayerHandler.getPlayerData(player).isShootingRight = false;
				return true;
			}
		}
		return false;
	}

	@Override //TODO check
	public boolean canBeAttackedWithItem()
	{
		return true;
	}

	@Override
	public boolean isBurning()
	{
		return false;
	}
}