package me.zeroeightsix.kami.module.modules.dl;

import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;
import me.zeroeightsix.kami.util.LagCompensator;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.CPacketPlayerDigging;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

@Module.Info(name="DLSpiral", category=Module.Category.DL, description="a")
public class DLSpiral
extends Module {
    public Setting<Modes> mode = this.register(Settings.e("Mode", Modes.ZERO_ZERO));
    public Setting<Integer> centerX = this.register(Settings.integerBuilder("centerX").withValue(0).withVisibility(v -> this.mode.getValue().equals((Object)Modes.CUSTOM)).build());
    public Setting<Integer> centerZ = this.register(Settings.integerBuilder("centerZ").withValue(0).withVisibility(v -> this.mode.getValue().equals((Object)Modes.CUSTOM)).build());
    private Setting<Integer> max = this.register(Settings.integerBuilder("Max").withMinimum(0).withValue(0).build());
    private Setting<Integer> skip = this.register(Settings.integerBuilder("Skip").withMinimum(0).withValue(0).build());
    private Setting<Integer> step = this.register(Settings.integerBuilder("Step").withMinimum(1).withValue(144).withVisibility(v -> true).build());
    private Setting<Integer> amountPerTick = this.register(Settings.integerBuilder("PPT").withMinimum(1).withValue(4).withMaximum(15).build());
    private Setting<Boolean> debug = this.register(Settings.b("debug", false));
    int spiralX;
    int spiralZ;
    int sx;
    int sz;
    int center_x;
    int center_z;
    int steps;
    boolean isSkipping = false;
    public static DLSpiral INSTANCE;

    public DLSpiral() {
        INSTANCE = this;
    }

    @Override
    protected void onEnable() {
        if (DLSpiral.mc.player == null) {
            return;
        }
        this.spiralX = this.skip.getValue();
        this.spiralZ = this.skip.getValue();
        if (this.mode.getValue().equals((Object)Modes.ZERO_ZERO)) {
            this.center_x = 0;
            this.center_z = 0;
        } else if (this.mode.getValue().equals((Object)Modes.YOU)) {
            this.center_x = DLSpiral.mc.player.getPosition().getX();
            this.center_z = DLSpiral.mc.player.getPosition().getZ();
        } else {
            this.center_x = this.centerX.getValue();
            this.center_z = this.centerZ.getValue();
        }
        this.steps = this.step.getValue();
        this.isSkipping = this.skip.getValue() != 0;
    }

    @Override
    public void onUpdate() {
        if (DLSpiral.mc.player.connection == null) {
            return;
        }
        if ((double)LagCompensator.INSTANCE.getTimeLastResponse() > 0.8) {
            return;
        }
        for (int i = 0; i < this.amountPerTick.getValue(); ++i) {
            this.sx = this.spiralX + this.center_x;
            this.sz = this.spiralZ + this.center_z;
            if (this.debug.getValue().booleanValue()) {
                this.SendMessage(this.sx + ", " + this.sz);
            }
            DLSpiral.mc.player.connection.sendPacket((Packet)new CPacketPlayerDigging(CPacketPlayerDigging.Action.ABORT_DESTROY_BLOCK, new BlockPos(this.sx, 0, this.sz), EnumFacing.UP));
            if (this.isSkipping) {
                if (Math.abs(this.spiralX) < Math.abs(this.spiralZ) && (this.spiralX != this.spiralZ || this.spiralX >= 0)) {
                    this.spiralX += this.spiralZ >= 0 ? this.steps : -this.steps;
                } else {
                    this.spiralZ += this.spiralX >= 0 ? -this.steps : this.steps;
                }
            } else if (Math.abs(this.spiralX) <= Math.abs(this.spiralZ) && (this.spiralX != this.spiralZ || this.spiralX >= 0)) {
                this.spiralX += this.spiralZ >= 0 ? this.steps : -this.steps;
            } else {
                this.spiralZ += this.spiralX >= 0 ? -this.steps : this.steps;
            }
            if (this.max.getValue() < this.steps || this.spiralX <= this.max.getValue()) continue;
            this.spiralX = 0;
            this.spiralZ = 0;
            this.SendMessage("maximum reached, Reseting spiral relative coords and scanning again.");
        }
    }

    @Override
    public String getHudInfo() {
        return this.sx + ", " + this.sz;
    }

    @Override
    protected void onDisable() {
        this.SendMessage("Stopped at " + this.sx + ", " + this.sz);
    }

    static enum Modes {
        ZERO_ZERO,
        YOU,
        CUSTOM;

    }
}

