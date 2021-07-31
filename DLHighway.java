package me.zeroeightsix.kami.module.modules.dl;

import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;
import me.zeroeightsix.kami.util.LagCompensator;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.CPacketPlayerDigging;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

@Module.Info(name="DLHighway", category=Module.Category.DL, description="a")
public class DLHighway
extends Module {
    public Setting<modes> Mode = this.register(Settings.e("Mode", modes.AXIS));
    public Setting<highways> axis = this.register(Settings.enumBuilder(highways.class).withValue(highways.PX).withName("Axis").withVisibility(o -> this.Mode.getValue().equals((Object)modes.AXIS)).build());
    public Setting<diagonal_highways> diagonal = this.register(Settings.enumBuilder(diagonal_highways.class).withValue(diagonal_highways.pXpZ).withName("Diagonal").withVisibility(o -> this.Mode.getValue().equals((Object)modes.DIAGONAL)).build());
    public Setting<Integer> amountPerTick = this.register(Settings.integerBuilder("PPT").withMinimum(1).withValue(4).withMaximum(15).build());
    public Setting<Integer> step = this.register(Settings.integerBuilder("Step").withMinimum(1).withValue(144).build());
    public Setting<Integer> startFrom = this.register(Settings.integerBuilder("startFrom").withMinimum(0).withValue(0).build());
    private BlockPos pos = new BlockPos(0, 0, 0);
    private int coord;

    @Override
    protected void onEnable() {
        this.coord = this.startFrom.getValue();
        this.pos = new BlockPos(0, 0, 0);
    }

    @Override
    public void onUpdate() {
        if (DLHighway.mc.player.connection == null || DLHighway.mc.world == null) {
            return;
        }
        if ((double)LagCompensator.INSTANCE.getTimeLastResponse() > 0.8) {
            return;
        }
        for (int i = 0; i < this.amountPerTick.getValue(); ++i) {
            if (this.coord > 30000000) {
                this.SendMessage("Finished at 30 million. stopping.");
                this.toggle();
                return;
            }
            if (this.Mode.getValue().equals((Object)modes.AXIS)) {
                switch (this.axis.getValue()) {
                    case NX: {
                        this.pos = new BlockPos(-this.coord, 0, 0);
                        break;
                    }
                    case NZ: {
                        this.pos = new BlockPos(0, 0, -this.coord);
                        break;
                    }
                    case PX: {
                        this.pos = new BlockPos(this.coord, 0, 0);
                        break;
                    }
                    case PZ: {
                        this.pos = new BlockPos(0, 0, this.coord);
                    }
                }
            } else {
                switch (this.diagonal.getValue()) {
                    case pXpZ: {
                        this.pos = new BlockPos(this.coord, 0, this.coord);
                        break;
                    }
                    case nXnZ: {
                        this.pos = new BlockPos(-this.coord, 0, -this.coord);
                        break;
                    }
                    case pXnZ: {
                        this.pos = new BlockPos(this.coord, 0, -this.coord);
                        break;
                    }
                    case nXpZ: {
                        this.pos = new BlockPos(-this.coord, 0, this.coord);
                    }
                }
            }
            DLHighway.mc.player.connection.sendPacket((Packet)new CPacketPlayerDigging(CPacketPlayerDigging.Action.ABORT_DESTROY_BLOCK, this.pos, EnumFacing.UP));
            this.coord += this.step.getValue().intValue();
        }
    }

    @Override
    public String getHudInfo() {
        return this.pos.getX() + ", " + this.pos.getZ();
    }

    static enum diagonal_highways {
        pXpZ,
        nXnZ,
        pXnZ,
        nXpZ;

    }

    static enum highways {
        PX,
        PZ,
        NX,
        NZ;

    }

    static enum modes {
        AXIS,
        DIAGONAL;

    }
}

