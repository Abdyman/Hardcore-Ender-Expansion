package chylex.hee.mechanics.charms;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.item.ItemStack;
import org.apache.commons.lang3.tuple.Pair;
import chylex.hee.item.ItemCharmPouch;

public final class CharmPouchInfo{
	private static final long maxIdleTime = 1000000000;
	
	public final long pouchID;
	public final List<Pair<CharmType,CharmRecipe>> charms = new ArrayList<Pair<CharmType,CharmRecipe>>(5);
	private long lastUpdateTime;
	
	public CharmPouchInfo(ItemStack is){
		pouchID = ItemCharmPouch.getPouchID(is);
		for(ItemStack charmStack:ItemCharmPouch.getPouchCharms(is))charms.add(CharmType.getFromDamage(charmStack.getItemDamage()));
		lastUpdateTime = System.nanoTime();
	}
	
	public void update(){
		lastUpdateTime = System.nanoTime();
	}
	
	public boolean isIdle(){
		return System.nanoTime()-lastUpdateTime > maxIdleTime; 
	}
	
	@Override
	public boolean equals(Object o){
		if (o != null && o.getClass() == CharmPouchInfo.class)return ((CharmPouchInfo)o).pouchID == pouchID;
		else return false;
	}
	
	@Override
	public int hashCode(){
		return (int)(pouchID^(pouchID>>>32));
	}
}
