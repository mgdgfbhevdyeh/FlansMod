package com.flansmod.common;

import com.flansmod.common.util.CTabs;

import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

public class ItemBlockManyNames extends ItemBlock{
	
	public ItemBlockManyNames(Block b)
	{
		super(b);
		setHasSubtypes(true);
		setRegistryName(b.getRegistryName());
		setUnlocalizedName(b.getRegistryName().toString());
	}
	
	@Override
	public String getUnlocalizedName(ItemStack stack)
	{
		return super.getUnlocalizedName() + "." + stack.getItemDamage();
	}
	
	@Override
	public int getMetadata(int par1) 
	{
		return par1;
	}
	
	@Override
	public CreativeTabs[] getCreativeTabs()
	{
		return new CreativeTabs[]{ CTabs.vehicles, CTabs.weapons, CTabs.parts };
	}
}
