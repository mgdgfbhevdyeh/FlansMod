package com.flansmod.client;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

import com.flansmod.api.IControllable;
import com.flansmod.client.gui.GuiDriveableController;
import com.flansmod.client.model.GunAnimations;
import com.flansmod.common.FlansMod;
import com.flansmod.common.driveables.mechas.EntityMecha;
import com.flansmod.common.guns.AttachmentType;
import com.flansmod.common.guns.EntityBullet;
import com.flansmod.common.guns.GunType;
import com.flansmod.common.guns.IScope;
import com.flansmod.common.guns.ItemGun;
import com.flansmod.common.types.InfoType;
import com.flansmod.common.util.Ticker;
import com.flansmod.common.util.Util;
import com.flansmod.common.vector.Vector3i;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHandSide;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.EnumSkyBlock;
import net.minecraftforge.fml.client.FMLClientHandler;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

public class FlansModClient extends FlansMod
{
	//Plane / Vehicle control handling
	/** Whether the player has received the vehicle tutorial text */
	public static boolean doneTutorial = false;
	/** Whether the player is in mouse control mode */
	public static boolean controlModeMouse = true;
	/** A delayer on the mouse control switch */
	public static int controlModeSwitchTimer = 20;
		
	//Recoil variables
	/** The recoil applied to the player view by shooting */
	public static float playerRecoil;
	/** The amount of compensation to apply to the recoil in order to bring it back to normal */
	public static float antiRecoil;
	
	//Gun animations
	/** Gun animation variables for each entity holding a gun. Currently only applicable to the player */
	public static HashMap<EntityLivingBase, GunAnimations> gunAnimationsRight = new HashMap<EntityLivingBase, GunAnimations>(), gunAnimationsLeft = new HashMap<EntityLivingBase, GunAnimations>();
	
	//Scope variables
	/** A delayer on the scope button to avoid repeat presses */
	public static int scopeTime;
	/** The scope that is currently being looked down */
	public static IScope currentScope = null;
	/** The transition variable for zooming in / out with a smoother. 0 = unscoped, 1 = scoped */
	public static float zoomProgress = 0F, lastZoomProgress = 0F;
	/** The zoom level of the last scope used, for transitioning out of being scoped, even after the scope is forgotten */
	public static float lastZoomLevel = 1F, lastFOVZoomLevel = 1F;

	//Variables to hold the state of some settings so that after being hacked for scopes, they may be restored
	/** The player's mouse sensitivity setting, as it was before being hacked by my mod */
	public static float originalMouseSensitivity = 0.5F;
	/** The player's original FOV */
	public static float originalFOV = 90F;
	/** The original third person mode, before being hacked */
	public static int originalThirdPerson = 0;
	
	/** Whether the player is in a plane or not */
	public static boolean inPlane = false;
	
	/** When a round ends, the teams score GUI is locked for this length of time */
	public static int teamsScoreGUILock = 0;	
	
	public static int hitMarkerTime = 0;
		
	public static ArrayList<Vector3i> blockLightOverrides = new ArrayList<Vector3i>();
	public static int lightOverrideRefreshRate = 5;
	
	public void load()
	{		
		Util.log("Loading Flan's mod client side.");

	}
	
	//private static final ResourceLocation zombieSkin = new ResourceLocation("flansmod", "skins/zombie.png");

	public static void tick(){
		if (minecraft.player == null || minecraft.world == null)
			return;
		
		if(minecraft.player.getRidingEntity() instanceof IControllable && minecraft.currentScreen == null)
			minecraft.displayGuiScreen(new GuiDriveableController((IControllable)minecraft.player.getRidingEntity()));
		
		//Teams GUI lock at end of rounds
		/*if(teamsScoreGUILock > 0){
			teamsScoreGUILock--;
			if(minecraft.currentScreen == null){
				minecraft.displayGuiScreen(new GuiTeamScores());
			}
		}*/
		
		// Guns
		if(scopeTime > 0)
			scopeTime--;
		if (playerRecoil > 0)
			playerRecoil *= 0.8F;
		if(hitMarkerTime > 0)
			hitMarkerTime--;
		minecraft.player.rotationPitch -= playerRecoil;
		antiRecoil += playerRecoil;

		minecraft.player.rotationPitch += antiRecoil * 0.2F;
		antiRecoil *= 0.8F;
		
		//Update gun animations for the gun in hand
		for(GunAnimations g : gunAnimationsRight.values())
		{
			g.update();
		}		
		for(GunAnimations g : gunAnimationsLeft.values())
		{
			g.update();
		}		
		
		for(Object obj : minecraft.world.playerEntities)
		{
			EntityPlayer player = (EntityPlayer)obj;
			ItemStack currentItem = player.getHeldItemMainhand();
			if(currentItem != null && currentItem.getItem() instanceof ItemGun)
			{
				/*if(player == minecraft.player && minecraft.gameSettings.thirdPersonView == 0)
					player.clearItemInUse();
				else
				{
					player.setItemInUse(currentItem, 100);
				}*/
				//TODO
			}
		}

		//If the currently held item is not a gun or is the wrong gun, unscope
		Item itemInHand = null;
		ItemStack itemstackInHand = minecraft.player.inventory.getCurrentItem();
		if (itemstackInHand != null)
			itemInHand = itemstackInHand.getItem();
		if (currentScope != null)
		{
			//GameSettings gameSettings = FMLClientHandler.instance().getClient().gameSettings;
			
			// If we've opened a GUI page, or we switched weapons, close the current scope
			if(FMLClientHandler.instance().getClient().currentScreen != null 
			|| itemInHand == null 
			|| !(itemInHand instanceof ItemGun)
			|| ((ItemGun)itemInHand).getInfoType().getCurrentScope(itemstackInHand) != currentScope)
			{
				currentScope = null;
				minecraft.gameSettings.fovSetting = originalFOV;
				minecraft.gameSettings.mouseSensitivity = originalMouseSensitivity;
				minecraft.gameSettings.thirdPersonView = originalThirdPerson;
			}
		}

		//Calculate new zoom variables
		lastZoomProgress = zoomProgress;
		if(currentScope == null)
		{
			zoomProgress *= 0.66F;
		}
		else
		{
			zoomProgress = 1F - (1F - zoomProgress) * 0.66F; 
		}
		
		if(minecraft.player.getRidingEntity() instanceof IControllable){
			inPlane = true;	
			try{
				ObfuscationReflectionHelper.setPrivateValue(EntityRenderer.class, minecraft.entityRenderer, ((IControllable)minecraft.player.getRidingEntity()).getCameraDistance(), "thirdPersonDistance", "q", "field_78490_B");
			}
			catch (Exception e){
				Util.log("I forgot to update obfuscated reflection D:");
				throw new RuntimeException(e);
			}		
		}
		else if(inPlane){
			try{
				ObfuscationReflectionHelper.setPrivateValue(EntityRenderer.class, minecraft.entityRenderer, 4.0F, "thirdPersonDistance", "q", "field_78490_B");
			}
			catch (Exception e){
				Util.log("I forgot to update obfuscated reflection D:");
				throw new RuntimeException(e);
			}	
			inPlane = false;
		}
		if (controlModeSwitchTimer > 0)
			controlModeSwitchTimer--;
	}
	
	public static void SetScope(IScope scope)
	{
		GameSettings gameSettings = FMLClientHandler.instance().getClient().gameSettings;
		
		if(scopeTime <= 0 && FMLClientHandler.instance().getClient().currentScreen == null)
		{
			if(currentScope == null)
			{
				currentScope = scope;
				lastZoomLevel = scope.getZoomFactor();
				lastFOVZoomLevel = scope.getFOVFactor();
				float f = originalMouseSensitivity = gameSettings.mouseSensitivity;
				gameSettings.mouseSensitivity = f / (float) Math.sqrt(scope.getZoomFactor());
				originalThirdPerson = gameSettings.thirdPersonView;
				gameSettings.thirdPersonView = 0;
				originalFOV = gameSettings.fovSetting;
			}
			else
			{
				currentScope = null;
				gameSettings.mouseSensitivity = originalMouseSensitivity;
				gameSettings.thirdPersonView = originalThirdPerson;
				gameSettings.fovSetting = originalFOV;
			}
			scopeTime = 10;
		}
	}
	
	public static void UpdateCameraZoom(float smoothing)
	{
		//If the zoom has changed sufficiently, update it
		if(Math.abs(zoomProgress - lastZoomProgress) > 0.0001F)
		{
			float actualZoomProgress = lastZoomProgress + (zoomProgress - lastZoomProgress) * smoothing;
			float botchedZoomProgress = zoomProgress > 0.8F ? 1F : 0F;
			double zoomLevel = botchedZoomProgress * lastZoomLevel + (1 - botchedZoomProgress);
			float FOVZoomLevel = actualZoomProgress * lastFOVZoomLevel + (1 - actualZoomProgress);
			if(Math.abs(zoomLevel - 1F) < 0.01F)
				zoomLevel = 1.0D;
			
			float zoomToApply = Math.max(FOVZoomLevel, (float)zoomLevel);
			minecraft.gameSettings.fovSetting = (((originalFOV * 40 + 70) / zoomToApply) - 70) / 40;
		}
	}
		
	private boolean checkFileExists(File file)
	{
		if(!file.exists())
		{
			try
			{ 
				file.createNewFile();
			}
			catch(Exception e)
			{
				Util.log("Failed to create file");
				Util.log(file.getAbsolutePath());
			}
			return false;
		}	
		return true;
	}

	public static boolean flipControlMode(){
		if(controlModeSwitchTimer > 0){
			return false;
		}
		controlModeMouse = !controlModeMouse;
		FMLClientHandler.instance().getClient().displayGuiScreen(controlModeMouse ? new GuiDriveableController((IControllable)FMLClientHandler.instance().getClient().player.getRidingEntity()) : null);
		controlModeSwitchTimer = 40;
		return true;
	}
	
	public static void reloadModels(boolean reloadSkins)
	{
		for(InfoType type : InfoType.infoTypes.values())
		{
			type.reloadModel();
		}
		if(reloadSkins)
			proxy.forceReload();
	}
	
	public static Minecraft minecraft = FMLClientHandler.instance().getClient();
	
	public static EnumParticleTypes getParticleType(String s)
	{
		if(s.equals("hugeexplosion")) 		return EnumParticleTypes.EXPLOSION_HUGE;
		else if(s.equals("largeexplode"))	return EnumParticleTypes.EXPLOSION_LARGE;
		else if(s.equals("explode"))		return EnumParticleTypes.EXPLOSION_NORMAL;
		else if(s.equals("fireworksSpark"))	return EnumParticleTypes.FIREWORKS_SPARK;
		else if(s.equals("bubble"))			return EnumParticleTypes.WATER_BUBBLE;
		else if(s.equals("splash"))			return EnumParticleTypes.WATER_SPLASH;
		else if(s.equals("wake"))			return EnumParticleTypes.WATER_WAKE;
		else if(s.equals("drop"))			return EnumParticleTypes.WATER_DROP;
		else if(s.equals("suspended"))		return EnumParticleTypes.SUSPENDED;
		else if(s.equals("depthsuspend"))	return EnumParticleTypes.SUSPENDED_DEPTH;
		else if(s.equals("townaura"))		return EnumParticleTypes.TOWN_AURA;
		else if(s.equals("crit"))			return EnumParticleTypes.CRIT;
		else if(s.equals("magicCrit"))		return EnumParticleTypes.CRIT_MAGIC;
		else if(s.equals("smoke"))			return EnumParticleTypes.SMOKE_NORMAL;
		else if(s.equals("largesmoke"))		return EnumParticleTypes.SMOKE_LARGE;
		else if(s.equals("spell"))			return EnumParticleTypes.SPELL;
		else if(s.equals("instantSpell"))	return EnumParticleTypes.SPELL_INSTANT;
		else if(s.equals("mobSpell"))		return EnumParticleTypes.SPELL_MOB;
		else if(s.equals("mobSpellAmbient"))return EnumParticleTypes.SPELL_MOB_AMBIENT;
		else if(s.equals("witchMagic"))		return EnumParticleTypes.SPELL_WITCH;
		else if(s.equals("dripWater"))		return EnumParticleTypes.DRIP_WATER;
		else if(s.equals("dripLava"))		return EnumParticleTypes.DRIP_LAVA;
		else if(s.equals("angryVillager"))	return EnumParticleTypes.VILLAGER_ANGRY;
		else if(s.equals("happyVillager"))	return EnumParticleTypes.VILLAGER_HAPPY;
		else if(s.equals("note"))			return EnumParticleTypes.NOTE;
		else if(s.equals("portal"))			return EnumParticleTypes.PORTAL;
		else if(s.equals("enchantmenttable"))return EnumParticleTypes.ENCHANTMENT_TABLE;
		else if(s.equals("flame"))			return EnumParticleTypes.FLAME;
		else if(s.equals("lava"))			return EnumParticleTypes.LAVA;
		else if(s.equals("footstep"))		return EnumParticleTypes.FOOTSTEP;
		else if(s.equals("cloud"))			return EnumParticleTypes.CLOUD;
		else if(s.equals("reddust"))		return EnumParticleTypes.REDSTONE;
		else if(s.equals("snowballpoof"))	return EnumParticleTypes.SNOWBALL;
		else if(s.equals("snowshovel"))		return EnumParticleTypes.SNOW_SHOVEL;
		else if(s.equals("slime"))			return EnumParticleTypes.SLIME;
		else if(s.equals("heart"))			return EnumParticleTypes.HEART;
		else if(s.equals("barrier"))		return EnumParticleTypes.BARRIER;
		
		return EnumParticleTypes.WATER_BUBBLE;
	}
	
	/*@SideOnly(Side.CLIENT)
	public static EntityFX getParticle(String s, World w, double x, double y, double z)
	{
		Minecraft mc = Minecraft.getMinecraft();
		//return mc.renderGlobal.doSpawnParticle(s, x, y, z, 0.01D, 0.01D, 0.01D);
		
        int particleID = 0;
        int[] data = new int[0];
              
		if(s.equals("hugeexplosion")) 		particleID = EnumParticleTypes.EXPLOSION_HUGE.getParticleID();
		else if(s.equals("largeexplode"))	particleID = EnumParticleTypes.EXPLOSION_LARGE.getParticleID();
		else if(s.equals("explode"))		particleID = EnumParticleTypes.EXPLOSION_NORMAL.getParticleID();
		else if(s.equals("fireworksSpark"))	particleID = EnumParticleTypes.FIREWORKS_SPARK.getParticleID();
		else if(s.equals("bubble"))			particleID = EnumParticleTypes.WATER_BUBBLE.getParticleID();
		else if(s.equals("splash"))			particleID = EnumParticleTypes.WATER_SPLASH.getParticleID();
		else if(s.equals("wake"))			particleID = EnumParticleTypes.WATER_WAKE.getParticleID();
		else if(s.equals("drop"))			particleID = EnumParticleTypes.WATER_DROP.getParticleID();
		else if(s.equals("suspended"))		particleID = EnumParticleTypes.SUSPENDED.getParticleID();
		else if(s.equals("depthsuspend"))	particleID = EnumParticleTypes.SUSPENDED_DEPTH.getParticleID();
		else if(s.equals("townaura"))		particleID = EnumParticleTypes.TOWN_AURA.getParticleID();
		else if(s.equals("crit"))			particleID = EnumParticleTypes.CRIT.getParticleID();
		else if(s.equals("magicCrit"))		particleID = EnumParticleTypes.CRIT_MAGIC.getParticleID();
		else if(s.equals("smoke"))			particleID = EnumParticleTypes.SMOKE_NORMAL.getParticleID();
		else if(s.equals("largesmoke"))		particleID = EnumParticleTypes.SMOKE_LARGE.getParticleID();
		else if(s.equals("spell"))			particleID = EnumParticleTypes.SPELL.getParticleID();
		else if(s.equals("instantSpell"))	particleID = EnumParticleTypes.SPELL_INSTANT.getParticleID();
		else if(s.equals("mobSpell"))		particleID = EnumParticleTypes.SPELL_MOB.getParticleID();
		else if(s.equals("mobSpellAmbient"))particleID = EnumParticleTypes.SPELL_MOB_AMBIENT.getParticleID();
		else if(s.equals("witchMagic"))		particleID = EnumParticleTypes.SPELL_WITCH.getParticleID();
		else if(s.equals("dripWater"))		particleID = EnumParticleTypes.DRIP_WATER.getParticleID();
		else if(s.equals("dripLava"))		particleID = EnumParticleTypes.DRIP_LAVA.getParticleID();
		else if(s.equals("angryVillager"))	particleID = EnumParticleTypes.VILLAGER_ANGRY.getParticleID();
		else if(s.equals("happyVillager"))	particleID = EnumParticleTypes.VILLAGER_HAPPY.getParticleID();
		else if(s.equals("note"))			particleID = EnumParticleTypes.NOTE.getParticleID();
		else if(s.equals("portal"))			particleID = EnumParticleTypes.PORTAL.getParticleID();
		else if(s.equals("enchantmenttable"))particleID = EnumParticleTypes.ENCHANTMENT_TABLE.getParticleID();
		else if(s.equals("flame"))			particleID = EnumParticleTypes.FLAME.getParticleID();
		else if(s.equals("lava"))			particleID = EnumParticleTypes.LAVA.getParticleID();
		else if(s.equals("footstep"))		particleID = EnumParticleTypes.FOOTSTEP.getParticleID();
		else if(s.equals("cloud"))			particleID = EnumParticleTypes.CLOUD.getParticleID();
		else if(s.equals("reddust"))		particleID = EnumParticleTypes.REDSTONE.getParticleID();
		else if(s.equals("snowballpoof"))	particleID = EnumParticleTypes.SNOWBALL.getParticleID();
		else if(s.equals("snowshovel"))		particleID = EnumParticleTypes.SNOW_SHOVEL.getParticleID();
		else if(s.equals("slime"))			particleID = EnumParticleTypes.SLIME.getParticleID();
		else if(s.equals("heart"))			particleID = EnumParticleTypes.HEART.getParticleID();
		else if(s.equals("barrier"))		particleID = EnumParticleTypes.BARRIER.getParticleID();
        else if(s.contains("_"))
        {
            int k;
            String[] split = s.split("_", 3);
            
            

            if(split[0].equals("iconcrack"))
            {
                data = new int[] { Item.getIdFromItem(InfoType.getRecipeElement(split[1],0).getItem()) };
                particleID = EnumParticleTypes.ITEM_CRACK.getParticleID();
            }
            else
            {
            	data = new int[] { Block.getIdFromBlock(Block.getBlockFromItem(InfoType.getRecipeElement(split[1],0).getItem())) };

                if(split[0].equals("blockcrack"))
                {
                	 particleID = EnumParticleTypes.BLOCK_CRACK.getParticleID();
                }
                else if(split[0].equals("blockdust"))
                {
                	 particleID = EnumParticleTypes.BLOCK_DUST.getParticleID();
                }
            }
        }

        
        EntityFX fx = mc.effectRenderer.spawnEffectParticle(particleID, x, y, z, 0D, 0D, 0D, data);
        
		if(mc.gameSettings.fancyGraphics)
			fx.renderDistanceWeight = 200D;
		
		return fx;
	}*/

	private static void addGunAnimations(EntityLivingBase living, EnumHandSide hand, GunAnimations animations)
	{
		if (hand == EnumHandSide.LEFT)
		{
			gunAnimationsLeft.put(living, animations);
		}
		else
		{
			gunAnimationsRight.put(living, animations);
		}
	}

	public static GunAnimations getGunAnimations(EntityLivingBase living, EnumHandSide hand)
	{
		GunAnimations result = hand == EnumHandSide.LEFT ? gunAnimationsLeft.get(living) : gunAnimationsRight.get(living);
		if (result == null)
		{
			result = new GunAnimations();
			addGunAnimations(living, hand, result);
		}
		return result;
	}
	
	public static void AddHitMarker()
	{
		hitMarkerTime = 20;
	}
	
	/** Handle flashlight block light override */	
	public static void UpdateFlashlights(Minecraft mc)
	{
		//Handle lighting from flashlights and glowing bullets
		if(Ticker.tick % lightOverrideRefreshRate == 0 && mc.world != null)
		{
			//Check graphics setting and adjust refresh rate
			lightOverrideRefreshRate = mc.gameSettings.fancyGraphics ? 10 : 20;
			
			//Reset old light values
			for(Vector3i v : blockLightOverrides)
			{
				mc.world.getLightFromNeighborsFor(EnumSkyBlock.BLOCK, new BlockPos(v.x, v.y, v.z));
			}
			//Clear the list
			blockLightOverrides.clear();
			
			//Find all flashlights
			for(Object obj : mc.world.playerEntities)
			{
				EntityPlayer player = (EntityPlayer)obj;
				ItemStack currentHeldItem = player.getHeldItemMainhand();
				if(currentHeldItem != null && currentHeldItem.getItem() instanceof ItemGun)
				{
					GunType type = ((ItemGun)currentHeldItem.getItem()).getInfoType();
					AttachmentType grip = type.getGrip(currentHeldItem);
					if(grip != null && grip.flashlight)
					{
						for(int i = 0; i < 2; i++)
						{
							RayTraceResult ray = player.rayTrace(grip.flashlightRange / 2F * (i + 1), 1F);
							if(ray != null)
							{
								int x = ray.getBlockPos().getX();
								int y = ray.getBlockPos().getY();
								int z = ray.getBlockPos().getZ();
								EnumFacing side = ray.sideHit;
								switch(side)
								{
								case DOWN : y--; break;
								case UP : y++; break;
								case NORTH : z--; break;
								case SOUTH : z++; break;
								case WEST : x--; break;
								case EAST : x++; break;
								}
								blockLightOverrides.add(new Vector3i(x, y, z));
								mc.world.setLightFor(EnumSkyBlock.BLOCK, new BlockPos(x, y, z), 12);
								mc.world.getLightFromNeighborsFor(EnumSkyBlock.BLOCK, new BlockPos(x, y + 1, z));
								mc.world.getLightFromNeighborsFor(EnumSkyBlock.BLOCK, new BlockPos(x, y - 1, z));
								mc.world.getLightFromNeighborsFor(EnumSkyBlock.BLOCK, new BlockPos(x + 1, y, z));
								mc.world.getLightFromNeighborsFor(EnumSkyBlock.BLOCK, new BlockPos(x - 1, y, z));
								mc.world.getLightFromNeighborsFor(EnumSkyBlock.BLOCK, new BlockPos(x, y, z + 1));
								mc.world.getLightFromNeighborsFor(EnumSkyBlock.BLOCK, new BlockPos(x, y, z - 1));
							}
						}
					}
				}
			}
			
			for(Object obj : mc.world.loadedEntityList)
			{
				if(obj instanceof EntityBullet)
				{
					EntityBullet bullet = (EntityBullet)obj;
					if(!bullet.isDead && bullet.type.hasLight)
					{
						int x = MathHelper.floor(bullet.posX);
						int y = MathHelper.floor(bullet.posY);
						int z = MathHelper.floor(bullet.posZ);
						blockLightOverrides.add(new Vector3i(x, y, z));
						mc.world.setLightFor(EnumSkyBlock.BLOCK, new BlockPos(x, y, z), 15);
						mc.world.getLightFromNeighborsFor(EnumSkyBlock.BLOCK, new BlockPos(x, y + 1, z));
						mc.world.getLightFromNeighborsFor(EnumSkyBlock.BLOCK, new BlockPos(x, y - 1, z));
						mc.world.getLightFromNeighborsFor(EnumSkyBlock.BLOCK, new BlockPos(x + 1, y, z));
						mc.world.getLightFromNeighborsFor(EnumSkyBlock.BLOCK, new BlockPos(x - 1, y, z));
						mc.world.getLightFromNeighborsFor(EnumSkyBlock.BLOCK, new BlockPos(x, y, z + 1));
						mc.world.getLightFromNeighborsFor(EnumSkyBlock.BLOCK, new BlockPos(x, y, z - 1));
					}
				}
				else if(obj instanceof EntityMecha)
				{
					EntityMecha mecha = (EntityMecha)obj;
					int x = MathHelper.floor(mecha.posX);
					int y = MathHelper.floor(mecha.posY);
					int z = MathHelper.floor(mecha.posZ);
					if(mecha.lightLevel() > 0)
					{
						blockLightOverrides.add(new Vector3i(x, y, z));
						mc.world.setLightFor(EnumSkyBlock.BLOCK, new BlockPos(x, y, z), Math.max(mc.world.getLightFor(EnumSkyBlock.BLOCK, new BlockPos(x, y, z)), mecha.lightLevel()));
						mc.world.getLightFromNeighborsFor(EnumSkyBlock.BLOCK, new BlockPos(x, y + 1, z));
						mc.world.getLightFromNeighborsFor(EnumSkyBlock.BLOCK, new BlockPos(x, y - 1, z));
						mc.world.getLightFromNeighborsFor(EnumSkyBlock.BLOCK, new BlockPos(x + 1, y, z));
						mc.world.getLightFromNeighborsFor(EnumSkyBlock.BLOCK, new BlockPos(x - 1, y, z));
						mc.world.getLightFromNeighborsFor(EnumSkyBlock.BLOCK, new BlockPos(x, y, z + 1));
						mc.world.getLightFromNeighborsFor(EnumSkyBlock.BLOCK, new BlockPos(x, y, z - 1));
					}
					if(mecha.forceDark())
					{
						for(int i = -3; i <= 3; i++)
						{
							for(int j = -3; j <= 3; j++)
							{
								for(int k = -3; k <= 3; k++)
								{
									int xd = i + x;
									int yd = j + y;
									int zd = k + z;
									blockLightOverrides.add(new Vector3i(xd, yd, zd));
									mc.world.setLightFor(EnumSkyBlock.SKY, new BlockPos(xd, yd, zd), Math.abs(i) + Math.abs(j) + Math.abs(k));
								}
							}
						}
					}
				}
			}
		}
	}
}
