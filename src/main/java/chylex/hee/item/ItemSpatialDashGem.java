package chylex.hee.item;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import chylex.hee.entity.projectile.EntityProjectileSpatialDash;
import chylex.hee.mechanics.enhancements.EnhancementHandler;
import chylex.hee.mechanics.enhancements.types.SpatialDashGemEnhancements;
import chylex.hee.system.util.MathUtil;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class ItemSpatialDashGem extends ItemAbstractEnergyAcceptor{
	@Override
	public int getMaxDamage(ItemStack is){
		return EnhancementHandler.hasEnhancement(is,SpatialDashGemEnhancements.CAPACITY) ? MathUtil.ceil(1.5F*super.getMaxDamage(is)) : super.getMaxDamage(is);
	}
	
	@Override
	public boolean canAcceptEnergy(ItemStack is){
		return is.getItemDamage() > 0;
	}

	@Override
	public void onEnergyAccepted(ItemStack is){
		is.setItemDamage(is.getItemDamage()-3);
	}

	@Override
	public int getEnergyPerUse(ItemStack is){
		return 1;
	}
	
	@Override
	public void onUpdate(ItemStack is, World world, Entity entity, int slot, boolean isHeld){
		if (is.stackTagCompound != null && is.stackTagCompound.hasKey("cooldown")){
			byte cooldown = is.stackTagCompound.getByte("cooldown");
			
			if (--cooldown <= 0)is.stackTagCompound.removeTag("cooldown");
			else is.stackTagCompound.setByte("cooldown",cooldown);
		}
		
		super.onUpdate(is,world,entity,slot,isHeld);
	}
	
	@Override
	public ItemStack onItemRightClick(ItemStack is, World world, EntityPlayer player){
		if (is.getItemDamage() < getMaxDamage() && (is.stackTagCompound == null || !is.stackTagCompound.hasKey("cooldown"))){
			if (!world.isRemote){
				is.damageItem(getEnergyPerUse(is),player);
				world.spawnEntityInWorld(new EntityProjectileSpatialDash(world,player,EnhancementHandler.getEnhancements(is)));

				if (is.stackTagCompound == null)is.stackTagCompound = new NBTTagCompound();
				is.stackTagCompound.setByte("cooldown",(byte)25);
			}
			else world.playSound(player.posX,player.posY,player.posZ,"hardcoreenderexpansion:player.random.spatialdash",0.8F,0.9F,false);
		}
		
		return is;
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	public boolean hasEffect(ItemStack is, int pass){
		return is.getItemDamage() == 1 || super.hasEffect(is,pass);
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	public void addInformation(ItemStack is, EntityPlayer player, List textLines, boolean showAdvancedInfo){
		EnhancementHandler.appendEnhancementNames(is,textLines);
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	public int getColorFromItemStack(ItemStack is, int pass){
		return is.stackTagCompound != null && is.stackTagCompound.hasKey("cooldown") ? (192<<16|192<<8|192) : super.getColorFromItemStack(is,pass);
	}
}
