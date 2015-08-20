package chylex.hee.system.abstractions.damage;
import java.util.Arrays;
import java.util.List;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnchantmentProtection;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.util.DamageSource;
import net.minecraft.world.EnumDifficulty;
import chylex.hee.packets.PacketPipeline;
import chylex.hee.packets.client.C07AddPlayerVelocity;
import chylex.hee.system.util.DragonUtil;
import chylex.hee.system.util.MathUtil;

@FunctionalInterface
public interface IDamageModifier{
	public static final IDamageModifier
		peacefulExclusion = (amount, target, source, postProcessors) -> target.worldObj.difficultySetting == EnumDifficulty.PEACEFUL ? 0F : amount,
		
		difficultyScaling = (amount, target, source, postProcessors) -> {
			switch(target.worldObj.difficultySetting){
				case PEACEFUL: return amount*0.4F;
				case EASY: return amount*0.7F;
				default: return amount;
				case HARD: return amount*1.4F;
			}
		},
		
		armorProtection = (amount, target, source, postProcessors) -> {
			int armor = target instanceof EntityLivingBase ? ((EntityLivingBase)target).getTotalArmorValue() : 0;
			
			if (armor > 0){
				postProcessors.add(finalAmount -> {
					if (target instanceof EntityPlayer){
						ItemStack[] armorIS = Arrays.stream(((EntityPlayer)target).inventory.armorInventory).filter(is -> is != null && is.getItem() instanceof ItemArmor).toArray(ItemStack[]::new);
						
						if (armorIS.length > 0){
							int damage = MathUtil.ceil(finalAmount*0.33F);
							armorIS[target.worldObj.rand.nextInt(armorIS.length)].damageItem(damage,(EntityPlayer)target);
							armorIS[target.worldObj.rand.nextInt(armorIS.length)].damageItem(damage,(EntityPlayer)target);
						}
					}
				});
			}
			
			return amount*((26F-Math.min(20,armor))/26F);
		},
		
		magicDamage = (amount, target, source, postProcessors) -> {
			source.setMagicDamage();
			return amount;
		},
		
		enchantmentProtection = (amount, target, source, postProcessors) -> {
			if (target instanceof EntityPlayer){
				int maxValue = 0;
				
				for(int attempt = 0; attempt < 3; attempt++){
					maxValue = Math.max(maxValue,EnchantmentHelper.getEnchantmentModifierDamage(((EntityPlayer)target).inventory.armorInventory,source));
				}
				
				if (maxValue > 0){
					amount *= 1F-((float)Math.pow(Math.min(25,maxValue),1.1D)/44F);
					
					ItemStack[] enchArmor = Arrays.stream(((EntityPlayer)target).inventory.armorInventory).filter(is -> {
						return is != null && EnchantmentHelper.getEnchantments(is).keySet().stream().anyMatch(enchID -> Enchantment.enchantmentsList[(int)enchID] instanceof EnchantmentProtection);
					}).toArray(ItemStack[]::new);
					
					if (enchArmor.length > 0){
						enchArmor[target.worldObj.rand.nextInt(enchArmor.length)].damageItem(1,(EntityPlayer)target);
					}
				}
			}
			
			return amount;
		},
		
		potionProtection = (amount, target, source, postProcessors) -> {
			if (target instanceof EntityLivingBase && ((EntityLivingBase)target).isPotionActive(Potion.resistance)){
				amount *= 1F-0.15F*Math.min(((EntityLivingBase)target).getActivePotionEffect(Potion.resistance).getAmplifier()+1,5);
			}
			
			return amount;
		},
		
		nudityDanger = (amount, target, source, postProcessors) -> {
			int count = 4;
			
			if (target instanceof EntityPlayer){
				count = (int)Arrays.stream(((EntityPlayer)target).inventory.armorInventory).filter(is -> is != null && is.getItem() instanceof ItemArmor).count();
			}
			else if (target instanceof EntityLiving){
				count = (int)Arrays.stream(((EntityLiving)target).getLastActiveItems()).filter(is -> is != null && is.getItem() instanceof ItemArmor).count();
			}
			
			switch(count){
				case 3: return amount*1.3F;
				case 2: return amount*1.7F;
				case 1: return amount*2.3F;
				case 0: return amount*3.2F;
				default: return amount;
			}
		},
		
		thorns = (amount, target, source, postProcessors) -> {
			return amount; // TODO
		};
	
	public static IDamageModifier rapidDamage(final int decreaseBy){
		return (amount, target, source, postProcessors) -> {
			if (target instanceof EntityLivingBase){
				postProcessors.add(finalAmount -> {
					target.hurtResistantTime = ((EntityLivingBase)target).maxHurtResistantTime-decreaseBy;
				});
			}
			
			return amount;
		};
	}
	
	static final AttributeModifier noKnockback = new AttributeModifier("HEE NoKnockbackTemp",1D,0);
	
	public static IDamageModifier overrideKnockback(final float multiplier){
		return (amount, target, source, postProcessors) -> {
			Entity sourceEntity = source.getEntity();
			
			if (sourceEntity != null && target instanceof EntityLivingBase){
				((EntityLivingBase)target).getEntityAttribute(SharedMonsterAttributes.knockbackResistance).applyModifier(noKnockback);
				
				postProcessors.add(finalAmount -> {
					((EntityLivingBase)target).getEntityAttribute(SharedMonsterAttributes.knockbackResistance).removeModifier(noKnockback);
					
					double[] vec = DragonUtil.getNormalizedVector(target.posX-sourceEntity.posX,target.posZ-sourceEntity.posZ);
					target.addVelocity(vec[0]*0.5D*multiplier,0.4D+0.1D*multiplier,vec[1]*0.5D*multiplier);
					if (target instanceof EntityPlayer)PacketPipeline.sendToPlayer((EntityPlayer)target,new C07AddPlayerVelocity(vec[0]*0.5D*multiplier,0.4D+0.1D*multiplier,vec[1]*0.5D*multiplier));
					
					sourceEntity.motionX *= 0.6D;
					sourceEntity.motionZ *= 0.6D;
				});
			}
			
			return amount;
		};
	}
	
	float modify(float amount, Entity target, DamageSource source, List<IDamagePostProcessor> postProcessors);
}
