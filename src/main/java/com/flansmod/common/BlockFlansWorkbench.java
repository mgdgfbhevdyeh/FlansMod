package com.flansmod.common;

import com.flansmod.common.util.CTabs;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class BlockFlansWorkbench extends Block{
	
	public static final PropertyInteger TYPE = PropertyInteger.create("type", 0, 2);
	
    public BlockFlansWorkbench(){
        super(Material.IRON);
        setHardness(3F);
        setResistance(6F);
        setCreativeTab(CTabs.vehicles);
        setDefaultState(blockState.getBaseState().withProperty(TYPE, Integer.valueOf(0)));
    }
    
    @Override
    public void getSubBlocks(Item item, CreativeTabs tab, NonNullList<ItemStack> par3List)
    {
    	if(tab == CTabs.vehicles)
    		par3List.add(new ItemStack(item, 1, 0));
    	else if(tab == CTabs.weapons)
    		par3List.add(new ItemStack(item, 1, 1));
    	else if(tab == CTabs.parts)
    		par3List.add(new ItemStack(item, 1, 2));
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ)
    {
    	switch(((Integer)world.getBlockState(pos).getValue(TYPE)).intValue())
    	{
    	case 0 : if(world.isRemote) player.openGui(FlansMod.INSTANCE, 0, world, pos.getX(), pos.getY(), pos.getZ()); break;
    	case 1 : if(!world.isRemote) player.openGui(FlansMod.INSTANCE, 2, world, pos.getX(), pos.getY(), pos.getZ()); break; 
    	}    	
		return true;
    }
    

    
    @Override
    protected BlockStateContainer createBlockState()
    {
        return new BlockStateContainer(this, new IProperty[] {TYPE});
    }
    
    @Override
    public IBlockState getStateFromMeta(int meta)
    {
        return this.getDefaultState().withProperty(TYPE, Integer.valueOf(meta));
    }
    
    @Override
    public int getMetaFromState(IBlockState state)
    {
        return ((Integer)state.getValue(TYPE)).intValue();
    }
    
    @Override
    public int damageDropped(IBlockState state)
    {
        return ((Integer)state.getValue(TYPE)).intValue();
    }

}
