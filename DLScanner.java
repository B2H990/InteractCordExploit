package me.zeroeightsix.kami.module.modules.dl;

import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;
import me.zeroeightsix.kami.util.LagCompensator;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.CPacketPlayerDigging;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

@Module.Info(name="DLScanner", category=Module.Category.DL, description="a")
public class DLScanner
extends Module {
    public Setting<Integer> x1 = this.register(Settings.i("X1", 0));
    public Setting<Integer> z1 = this.register(Settings.i("Z1", 0));
    public Setting<Integer> x2 = this.register(Settings.i("X2", 0));
    public Setting<Integer> z2 = this.register(Settings.i("Z2", 0));
    private Setting<Integer> step = this.register(Settings.integerBuilder("Step").withMinimum(1).withValue(144).withVisibility(v -> true).build());
    private Setting<Integer> amountPerTick = this.register(Settings.integerBuilder("PPT").withMinimum(1).withValue(4).withMaximum(15).build());
    private Setting<Boolean> debug = this.register(Settings.b("debug", false));
    private BlockPos pos1 = new BlockPos(0, 0, 0);
    private BlockPos pos2 = new BlockPos(0, 0, 0);
    int x;
    int z;
    public static DLScanner INSTANCE;

    public DLScanner() {
        INSTANCE = this;
    }

    @Override
    protected void onEnable() {
        this.pos1 = new BlockPos(this.x1.getValue().intValue(), 0, this.z1.getValue().intValue());
        this.pos2 = new BlockPos(this.x2.getValue().intValue(), 0, this.z2.getValue().intValue());
        this.x = this.pos1.x;
        this.z = this.pos1.z;
    }

    @Override
    public void onUpdate() {
        if (DLScanner.mc.player.connection == null || DLScanner.mc.world == null) {
            return;
        }
        if ((double)LagCompensator.INSTANCE.getTimeLastResponse() > 0.8) {
            return;
        }
        for (int i = 0; i < this.amountPerTick.getValue(); ++i) {
            if (this.debug.getValue().booleanValue()) {
                this.SendMessage(this.x + ", " + this.z);
            }
            DLScanner.mc.player.connection.sendPacket((Packet)new CPacketPlayerDigging(CPacketPlayerDigging.Action.ABORT_DESTROY_BLOCK, new BlockPos(this.x, 0, this.z), EnumFacing.UP));
            if (this.x < this.pos2.x) {
                this.x += this.step.getValue().intValue();
            }
            if (this.x > this.pos2.x) {
                this.x = this.pos1.x;
                this.z += this.step.getValue().intValue();
            }
            if (this.z <= this.pos2.z) continue;
            this.SendMessage("Finished.");
            this.toggle();
            return;
        }
    }

    @Override
    protected void onDisable() {
        this.SendMessage("Stopped at " + this.x + ", " + this.z);
    }

    @Override
    public String getHudInfo() {
        return this.x + ", " + this.z;
    }
}

