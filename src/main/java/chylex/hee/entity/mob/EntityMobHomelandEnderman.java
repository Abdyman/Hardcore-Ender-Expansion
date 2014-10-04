package chylex.hee.entity.mob;
import java.util.List;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSourceIndirect;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeHooks;
import chylex.hee.HardcoreEnderExpansion;
import chylex.hee.entity.mob.util.IEndermanRenderer;
import chylex.hee.mechanics.misc.HomelandEndermen;
import chylex.hee.mechanics.misc.HomelandEndermen.EndermanTask;
import chylex.hee.mechanics.misc.HomelandEndermen.HomelandRole;
import chylex.hee.mechanics.misc.HomelandEndermen.OvertakeGroupRole;
import chylex.hee.system.logging.Log;

public class EntityMobHomelandEnderman extends EntityMob implements IEndermanRenderer{
	private HomelandRole homelandRole;
	private long groupId = -1;
	private OvertakeGroupRole overtakeGroupRole;
	
	private EndermanTask currentTask;
	private int currentTaskTimer;
	private Object currentTaskData;
	
	private byte stareTimer;
	
	public EntityMobHomelandEnderman(World world){
		super(world);
		setSize(0.6F,2.9F);
        stepHeight = 1.0F;
	}
	
	@Override
	protected void entityInit(){
        super.entityInit();
        dataWatcher.addObject(16,Byte.valueOf((byte)0));
        dataWatcher.addObjectByDataType(17,5);
        dataWatcher.addObject(18,Byte.valueOf((byte)0));
    }
	
	@Override
	protected void applyEntityAttributes(){
		super.applyEntityAttributes();
		getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(40D);
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(0.3D);
        getEntityAttribute(SharedMonsterAttributes.attackDamage).setBaseValue(7D);
		updateAttributes();
	}
	
	private void updateAttributes(){
		if (homelandRole == null)return;
		
		switch(homelandRole){
			case ISLAND_LEADERS:
				getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(70D);
				getEntityAttribute(SharedMonsterAttributes.attackDamage).setBaseValue(12D);
				break;
				
			case GUARD:
				getEntityAttribute(SharedMonsterAttributes.attackDamage).setBaseValue(15D);
				break;
				
			case BUSINESSMAN:
				getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(0.35D);
				break;
		}
	}
	
	@Override
	public void onLivingUpdate(){
		if (worldObj.isRemote){
			refreshRoles();
			
			int chance = 1+(int)Math.floor(HardcoreEnderExpansion.proxy.getClientSidePlayer().getDistanceToEntity(this)/12F);
			
			if (rand.nextInt(chance) == 0){
				float colFactor = rand.nextFloat()*0.6F+0.4F;
				HardcoreEnderExpansion.fx.portalColor(worldObj,posX+(rand.nextDouble()-0.5D)*width,posY+rand.nextDouble()*height-0.25D,posZ+(rand.nextDouble()-0.5D)*width,(rand.nextDouble()-0.5D)*2D,-rand.nextDouble(),(rand.nextDouble()-0.5D)*2D,colFactor*0.9F,colFactor*0.3F,colFactor);
				
				if (homelandRole != null && rand.nextInt(3) == 0){
					HardcoreEnderExpansion.fx.portalColor(worldObj,posX+(rand.nextDouble()-0.5D)*width,posY+rand.nextDouble()*height-0.25D,posZ+(rand.nextDouble()-0.5D)*width,(rand.nextDouble()-0.5D)*2D,-rand.nextDouble(),(rand.nextDouble()-0.5D)*2D,homelandRole.red,homelandRole.green,homelandRole.blue);
				}
				
				if (overtakeGroupRole != null && rand.nextInt(7) == 0){
					HardcoreEnderExpansion.fx.portalColor(worldObj,posX+(rand.nextDouble()-0.5D)*width,posY+rand.nextDouble()*height-0.25D,posZ+(rand.nextDouble()-0.5D)*width,(rand.nextDouble()-0.5D)*2D,-rand.nextDouble(),(rand.nextDouble()-0.5D)*2D,0.3F,0.3F,0.3F);
				}
			}
		}
		else{
			if (isWet())attackEntityFrom(DamageSource.drown,1F);
			
			if (isWet() || isBurning()){
				setTarget(null);
				setScreaming(false);
			}
			
			long overtakeGroup = HomelandEndermen.getOvertakeGroup(this);
			
			if (overtakeGroup == -1){ // calm situation
				if (currentTask != EndermanTask.NONE){ // has a task
					--currentTaskTimer;
					
					if (currentTask == EndermanTask.LISTEN_TO_RECRUITER || currentTask == EndermanTask.RECRUIT_TO_GROUP){
						moveForward = 0F;
						moveStrafing = 0F;
						
						if (currentTaskTimer == 0 && currentTask == EndermanTask.RECRUIT_TO_GROUP){
							int chance = 50, reportChance = 10;
							EntityMobHomelandEnderman target = (EntityMobHomelandEnderman)currentTaskData;
							
							switch(target.homelandRole){
								case WORKER: chance = 30; break;
								case GUARD: chance = 15; reportChance = 35; break;
								case INTELLIGENCE: chance = 80; reportChance = 25; break;
								case BUSINESSMAN: chance = 40; reportChance = 18; break;
							}
							
							if (rand.nextInt(100) < chance){
								target.setGroupMember(groupId,OvertakeGroupRole.getRandomMember(rand));
								System.out.println("Recruiting successful!");
							}
							else if (rand.nextInt(100) < reportChance){
								List<EntityMobHomelandEnderman> guards = HomelandEndermen.getByHomelandRole(this,HomelandRole.GUARD);
								System.out.println("Guards alerted!");
								
								for(int a = 0, amt = Math.max(3,(int)Math.round(guards.size()*0.3D)); a < amt; a++){
									EntityMobHomelandEnderman guard = guards.get(rand.nextInt(guards.size()));
									guard.setTarget(this);
									guard.setScreaming(true);
								}
							}
							
							for(int attempt = 0; attempt < 50; attempt++){
								if (teleportRandomly())break;
							}
						}
					}
					
					if (currentTaskTimer <= 0){
						currentTask = EndermanTask.NONE;
						currentTaskData = null;
					}
				}
				else{
					if (groupId != -1 && rand.nextInt(200) == 0){
						List<EntityMobHomelandEnderman> total = HomelandEndermen.getAll(this);
						int groupAmt = HomelandEndermen.getInSameGroup(this).size();
						int totalAmt = total.size();
						
						if (totalAmt > 8 && (rand.nextInt(5) <= 2 || groupAmt < (totalAmt>>2)+rand.nextInt(totalAmt>>3)-rand.nextInt(4))){
							for(int attempt = 0; attempt < 5; attempt++){
								EntityMobHomelandEnderman enderman = total.get(rand.nextInt(totalAmt));
								
								if (enderman == this || enderman.groupId != -1 || !enderman.onGround || enderman.homelandRole == HomelandRole.ISLAND_LEADERS || enderman.getDistanceToEntity(this) > 80D)continue;
								if (groupAmt > 3 && ((enderman.homelandRole == HomelandRole.GUARD || enderman.homelandRole == HomelandRole.WORKER) && rand.nextInt(5) != 0))continue;
								
								currentTask = EndermanTask.RECRUIT_TO_GROUP;
								currentTaskData = enderman;
								teleportTo(enderman.posX+(rand.nextDouble()-0.5D)*2D,enderman.posY,enderman.posZ+(rand.nextDouble()-0.5D)*2D);
								enderman.currentTask = EndermanTask.LISTEN_TO_RECRUITER;
								enderman.currentTaskTimer = currentTaskTimer = 20+rand.nextInt(60);
								System.out.println("Trying to recruit at "+posX+","+posY+","+posZ);
							}
						}
					}
				}
			}
			else if (overtakeGroup == groupId){ // overtaking group
				
			}
			else{ // different group - join them or be against them
				
			}
		}
		
		isJumping = false;

		if (entityToAttack != null)faceEntity(entityToAttack,100F,100F);
		
		super.onLivingUpdate();
	}
	
	@Override
	protected Entity findPlayerToAttack(){
		if (!shouldActHostile() && teleportRandomly())return null;
		
		EntityPlayer closestPlayer = worldObj.getClosestPlayerToEntity(this,64D);
		
		if (closestPlayer != null){
			if (isPlayerStaringIntoEyes(closestPlayer)){
				if (stareTimer == 0)worldObj.playSoundEffect(closestPlayer.posX,closestPlayer.posY,closestPlayer.posZ,"mob.endermen.stare",1F,1F);
				else if (stareTimer++ == 5){
					stareTimer = 0;
					setScreaming(true);
					return closestPlayer;
				}
			}
			else stareTimer = 0;
		}
		
		return null;
	}
	
	@Override
	public boolean attackEntityFrom(DamageSource source, float amount){
		if (isEntityInvulnerable())return false;
		
		if (source.getEntity() instanceof EntityPlayer || source.getEntity() instanceof EntityMobHomelandEnderman){
			setTarget(source.getEntity());
			setScreaming(true);
		}
		else if (source instanceof EntityDamageSourceIndirect){
			for(int attempt = 0; attempt < 64; attempt++){
				if (teleportRandomly()){
					setTarget(null);
					setScreaming(false);
					return true;
				}
			}
		}
		
		return super.attackEntityFrom(source,amount);
	}

	@Override
	public void writeEntityToNBT(NBTTagCompound nbt){
		super.writeEntityToNBT(nbt);
		nbt.setByte("homelandRole",(byte)homelandRole.ordinal());
		nbt.setLong("groupId",groupId);
		if (groupId != -1)nbt.setByte("groupRole",(byte)overtakeGroupRole.ordinal());
		
		ItemStack carrying = getCarrying();
		if (carrying != null)nbt.setTag("carrying",carrying.writeToNBT(new NBTTagCompound()));
	}

	@Override
	public void readEntityFromNBT(NBTTagCompound nbt){
		super.readEntityFromNBT(nbt);
		
		byte homelandRoleId = nbt.getByte("homelandRole");
		homelandRole = homelandRoleId >= 0 && homelandRoleId < HomelandRole.values.length ? HomelandRole.values[homelandRoleId] : null;
		
		if ((groupId = nbt.getLong("groupId")) != -1){
			byte groupRoleId = nbt.getByte("groupRole");
			overtakeGroupRole = groupRoleId >= 0 && groupRoleId < OvertakeGroupRole.values.length ? OvertakeGroupRole.values[groupRoleId] : null;
		}
		
		if (nbt.hasKey("carrying"))setCarrying(ItemStack.loadItemStackFromNBT(nbt.getCompoundTag("carrying")));
		
		if (homelandRole == null || (groupId != -1 && overtakeGroupRole == null))setDead();
		else if (!worldObj.isRemote)refreshRoles();
	}
	
	@Override
	public void setWorld(World world){
		super.setWorld(world);
		refreshRoles();
	}
	
	@Override
	protected void despawnEntity(){}
	
	// LOGIC HANDLING
	
	private boolean canTeleport(){
		return !HomelandEndermen.isOvertakeHappening(this);
	}
	
	private boolean shouldActHostile(){
		if (homelandRole == HomelandRole.ISLAND_LEADERS || (groupId != -1 && rand.nextInt(5) != 0) || HomelandEndermen.isOvertakeHappening(this)){
			return false;
		}
		else return rand.nextInt(4) != 0;
	}
	
	// GETTERS, SETTERS AND DATA WATCHER
	
	public void refreshRoles(){
		if (worldObj == null)return;
		
		if (!worldObj.isRemote){
			int data = (homelandRole.ordinal() & 0b1111) << 4;
			if (overtakeGroupRole != null)data |= ((overtakeGroupRole.ordinal()+1) & 0b1111);
			dataWatcher.updateObject(18,Byte.valueOf((byte)data));
			setCustomNameTag(homelandRole.name()+" / "+(overtakeGroupRole == null ? "null" : overtakeGroupRole.name()));
		}
		else{
			byte data = dataWatcher.getWatchableObjectByte(18);
			homelandRole = HomelandRole.values[(data >> 4) & 0b1111];
			if ((data & 0b1111) > 0)overtakeGroupRole = OvertakeGroupRole.values[data & 0b1111];
		}
	}
	
	public void setHomelandRole(HomelandRole role){
		this.homelandRole = role;
		refreshRoles();
		updateAttributes();
	}
	
	public HomelandRole getHomelandRole(){
		return homelandRole;
	}
	
	public long setNewGroupLeader(){
		if (homelandRole == HomelandRole.ISLAND_LEADERS)return -1L;
		this.overtakeGroupRole = OvertakeGroupRole.LEADER;
		return this.groupId = UUID.randomUUID().getLeastSignificantBits();
	}
	
	public void setGroupMember(long groupId, OvertakeGroupRole role){
		this.groupId = groupId;
		this.overtakeGroupRole = role;
		refreshRoles();
	}
	
	public OvertakeGroupRole getGroupRole(){
		return overtakeGroupRole;
	}
	
	public boolean isInSameGroup(EntityMobHomelandEnderman enderman){
		return groupId == enderman.groupId;
	}
	
	public void setCarrying(ItemStack is){
		dataWatcher.updateObject(17,is);
	}

	@Override
	public boolean isCarrying(){
		return getCarrying() != null;
	}
	
	@Override
	public ItemStack getCarrying(){
		return dataWatcher.getWatchableObjectItemStack(17);
	}
	
	public void setScreaming(boolean isScreaming){
		dataWatcher.updateObject(16,Byte.valueOf((byte)(isScreaming ? 1 : 0)));
	}

	@Override
	public boolean isScreaming(){
		return dataWatcher.getWatchableObjectByte(16) == 1;
	}
	
	// ENDERMAN METHODS
	
	private boolean teleportRandomly(){
		return teleportTo(posX+(rand.nextDouble()-0.5D)*64D,posY+(rand.nextInt(64)-32),posZ+(rand.nextDouble()-0.5D)*64D);
	}
	
	private boolean teleportRandomly(double maxDist){
		return teleportTo(posX+(rand.nextDouble()-0.5D)*maxDist,posY+(rand.nextInt((int)maxDist)-maxDist*0.5D),posZ+(rand.nextDouble()-0.5D)*maxDist);
	}

	private boolean teleportToEntity(Entity entity){
		Vec3 vec = Vec3.createVectorHelper(posX-entity.posX,boundingBox.minY+(height/2F)-entity.posY+entity.getEyeHeight(),posZ-entity.posZ).normalize();
		double newX = posX+(rand.nextDouble()-0.5D)*8D-vec.xCoord*16D;
		double newY = posY+(rand.nextInt(16)-8)-vec.yCoord*16D;
		double newZ = posZ+(rand.nextDouble()-0.5D)*8D-vec.zCoord*16D;
		return this.teleportTo(newX,newY,newZ);
	}

	private boolean teleportTo(double x, double y, double z){
		if (!canTeleport())return false;
		
		double oldX = posX, oldY = posY, oldZ = posZ;
		posX = x;
		posY = y;
		posZ = y;
		
		boolean hasTeleported = false;
		int ix = MathHelper.floor_double(posX), iy = MathHelper.floor_double(posY), iz = MathHelper.floor_double(posZ);

		if (worldObj.blockExists(ix,iy,iz)){
			boolean foundTopBlock = false;

			while(!foundTopBlock && iy > 0){
				if (worldObj.getBlock(ix,iy-1,iz).getMaterial().blocksMovement())foundTopBlock = true;
				else{
					--posY;
					--iy;
				}
			}

			if (foundTopBlock){
				setPosition(posX,posY,posZ);

				if (worldObj.getCollidingBoundingBoxes(this,boundingBox).isEmpty() && !worldObj.isAnyLiquid(boundingBox)){
					hasTeleported = true;
				}
			}
		}

		if (!hasTeleported){
			setPosition(oldX,oldY,oldZ);
			return false;
		}
		else{
			for(int a = 0, particleAmt = 128; a < particleAmt; a++){
				double linePosition = a/(particleAmt-1D);
				double particleX = oldX+(posX-oldX)*linePosition+(rand.nextDouble()-0.5D)*width*2D;
				double particleY = oldY+(posY-oldY)*linePosition+rand.nextDouble()*height;
				double particleZ = oldZ+(posZ-oldZ)*linePosition+(rand.nextDouble()-0.5D)*width*2D;
				worldObj.spawnParticle("portal",particleX,particleY,particleZ,(rand.nextFloat()-0.5F)*0.2F,(rand.nextFloat()-0.5F)*0.2F,(rand.nextFloat()-0.5F)*0.2F);
			}

			worldObj.playSoundEffect(oldX,oldY,oldZ,"mob.endermen.portal",1F,1F);
			playSound("mob.endermen.portal",1F,1F);
			return true;
		}
	}
	
	private boolean isPlayerStaringIntoEyes(EntityPlayer player){
		ItemStack is  = player.inventory.armorInventory[3];

		if (is != null && is.getItem() == Item.getItemFromBlock(Blocks.pumpkin))return false;
		else{
			Vec3 playerLook = player.getLook(1F).normalize();
			Vec3 eyeVecDiff = Vec3.createVectorHelper(posX-player.posX,boundingBox.minY+(height/2F)-(player.posY+player.getEyeHeight()),posZ-player.posZ);
			double eyeVecLen = eyeVecDiff.lengthVector();
			return playerLook.dotProduct(eyeVecDiff.normalize()) > 1D-0.025D/eyeVecLen && player.canEntityBeSeen(this);
		}
	}
	
	// OVERRIDDEN METHODS
	
	@Override
	protected String getLivingSound(){
		return isScreaming() ? "mob.endermen.scream" : "mob.endermen.idle";
	}

	@Override
	protected String getHurtSound(){
		return "mob.endermen.hit";
	}

	@Override
	protected String getDeathSound(){
		return "mob.endermen.death";
	}

	@Override
	protected Item getDropItem(){
		return Items.ender_pearl;
	}

	@Override
	protected void dropFewItems(boolean recentlyHit, int looting){
		Item item = getDropItem();
		
		if (item != null){
			for(int a = 0, total = rand.nextInt(2+looting); a < total; a++)dropItem(item,1);
		}
	}
	
	// NO DESPAWN ON PEACEFUL, MOSTLY COPIED FROM onUpdate WITH UNIMPORTANT BITS REMOVED
	
	@Override
	public void onUpdate(){
		if (ForgeHooks.onLivingUpdate(this))return;
		onEntityUpdate();
		
		if (!worldObj.isRemote){
			if (ticksExisted%20 == 0)func_110142_aN().func_94549_h();
		}

		onLivingUpdate();
		
		double xDiff = posX-prevPosX, zDiff = posZ-prevPosZ;
		float distanceMoved = (float)(xDiff*xDiff+zDiff*zDiff);
		float yawOffset = renderYawOffset;
		float f2 = 0F, f3 = 0F;
		field_70768_au = field_110154_aX;

		if (distanceMoved > 0.0025F){
			f3 = 1F;
			f2 = (float)Math.sqrt(distanceMoved)*3F;
			yawOffset = (float)Math.atan2(zDiff,xDiff)*180F/(float)Math.PI-90F;
		}
		
		if (swingProgress > 0F)yawOffset = rotationYaw;
		if (!onGround)f3 = 0F;

		field_110154_aX += (f3-field_110154_aX)*0.3F;
		worldObj.theProfiler.startSection("headTurn");
		f2 = func_110146_f(yawOffset,f2);
		worldObj.theProfiler.endSection();
		worldObj.theProfiler.startSection("rangeChecks");

		while(rotationYaw-prevRotationYaw < -180F)prevRotationYaw -= 360F;
		while(rotationYaw-prevRotationYaw >= 180F)prevRotationYaw += 360F;
		while(renderYawOffset-prevRenderYawOffset < -180F)prevRenderYawOffset -= 360F;
		while(renderYawOffset-prevRenderYawOffset >= 180F)prevRenderYawOffset += 360F;
		while(rotationPitch-prevRotationPitch < -180F)prevRotationPitch -= 360F;
		while(rotationPitch-prevRotationPitch >= 180F)prevRotationPitch += 360F;
		while(rotationYawHead-prevRotationYawHead < -180F)prevRotationYawHead -= 360F;
		while(rotationYawHead-prevRotationYawHead >= 180F)prevRotationYawHead += 360F;

		worldObj.theProfiler.endSection();
		field_70764_aw += f2;
		
		if (!worldObj.isRemote)updateLeashedState();
	}
}