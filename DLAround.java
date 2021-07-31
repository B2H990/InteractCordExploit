package me.zeroeightsix.kami.module.modules.dl;

import java.text.DecimalFormat;
import java.util.ArrayList;
import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.module.ModuleManager;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;
import me.zeroeightsix.kami.util.LagCompensator;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.CPacketPlayerDigging;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

@Module.Info(name="DLAround", category=Module.Category.DL, description="kek")
public class DLAround
extends Module {
    private Setting<Integer> radius = this.register(Settings.integerBuilder("Radius").withMinimum(1).withValue(1).build());
    public Setting<Integer> delay = this.register(Settings.integerBuilder("Delay").withMinimum(0).withValue(5).build());
    private Setting<Integer> amountPerCycle = this.register(Settings.integerBuilder("AmountPerCycle").withMinimum(1).withValue(2).withMaximum(15).build());
    private Setting<Boolean> debug = this.register(Settings.b("debug", false));
    ArrayList<BlockPos> blocksToDL = new ArrayList();
    int delayedTicks = 0;
    int initialSize = 0;
    float percentDone = 0.0f;

    @Override
    protected void onEnable() {
        this.blocksToDL.clear();
        this.delayedTicks = 0;
        this.initialSize = 0;
        this.percentDone = 0.0f;
    }

    @Override
    public void onUpdate() {
        if (ModuleManager.getModuleByName("Freecam").isDisabled()) {
            return;
        }
        if ((double)LagCompensator.INSTANCE.getTimeLastResponse() > 0.8) {
            return;
        }
        if (this.blocksToDL.isEmpty()) {
            BlockPos a = mc.getRenderViewEntity().getPosition();
            int r = this.radius.getValue();
            BlockPos.getAllInBox((int)(a.getX() - r), (int)(a.getY() - r), (int)(a.getZ() - r), (int)(a.getX() + r), (int)(a.getY() + r), (int)(a.getZ() + r)).forEach(b -> this.blocksToDL.add((BlockPos)b));
            this.initialSize = this.blocksToDL.size();
            this.percentDone = 0.0f;
        }
        if (this.delayedTicks < this.delay.getValue()) {
            ++this.delayedTicks;
            return;
        }
        for (int i = 0; i < this.amountPerCycle.getValue() && !this.blocksToDL.isEmpty(); ++i) {
            BlockPos pos = this.blocksToDL.get(0);
            this.blocksToDL.remove(0);
            if (this.debug.getValue().booleanValue()) {
                this.SendMessage(pos.x + ", " + pos.z);
            }
            DLAround.mc.player.connection.sendPacket((Packet)new CPacketPlayerDigging(CPacketPlayerDigging.Action.ABORT_DESTROY_BLOCK, pos, EnumFacing.UP));
        }
        if (this.initialSize != 0) {
            this.percentDone = 100.0f - (float)(this.blocksToDL.size() / this.initialSize) * 100.0f;
        }
    }

    @Override
    public String getHudInfo() {
        DecimalFormat df = new DecimalFormat("#.##");
        return df.format(this.percentDone);
    }
}

