package dev.enjarai.trickster.cca;

import com.mojang.serialization.DataResult;
import dev.enjarai.trickster.spell.Fragment;
import dev.enjarai.trickster.spell.SpellPart;
import dev.enjarai.trickster.spell.execution.source.PlayerSpellSource;
import dev.enjarai.trickster.spell.execution.SpellExecutionManager;
import dev.enjarai.trickster.spell.mana.ManaPool;
import io.wispforest.endec.Endec;
import io.wispforest.endec.impl.ReflectiveEndecBuilder;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CasterComponent implements ServerTickingComponent, AutoSyncedComponent {
    private final PlayerEntity player;
    private SpellExecutionManager executionManager;

    // Please ignore the terrible syncing im doing here, i promise ill fix it later.
    // - Rai
    private List<RunningSpellData> runningSpellData = new ArrayList<>();
    private HashMap<Integer, RunningSpellData> clientRunningSpellData = new HashMap<>();

    public static final Endec<List<RunningSpellData>> SPELL_DATA_ENDEC = ReflectiveEndecBuilder.SHARED_INSTANCE
            .get(RunningSpellData.class).listOf();

    public CasterComponent(PlayerEntity player) {
        this.player = player;
        this.executionManager = new SpellExecutionManager();

        if (!player.getWorld().isClient()) {
            this.executionManager.setSource(new PlayerSpellSource((ServerPlayerEntity) player));
        }
    }

    @Override
    public void serverTick() {
        runningSpellData.clear();
        executionManager.tick(executor -> runningSpellData.add(new RunningSpellData(executor.hashCode(), executor.getLastRunExecutions())));
        ModEntityCumponents.CASTER.sync(player);
    }

    @Override
    public void readFromNbt(NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        DataResult<SpellExecutionManager> result = SpellExecutionManager.CODEC.parse(NbtOps.INSTANCE, tag.get("manager"));

        if (result.hasResultOrPartial())
            executionManager = result.resultOrPartial().orElseThrow();

        executionManager.setSource(new PlayerSpellSource((ServerPlayerEntity) player));
    }

    @Override
    public void writeToNbt(NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        DataResult<NbtElement> result = SpellExecutionManager.CODEC.encodeStart(NbtOps.INSTANCE, executionManager);
        tag.put("manager", result.result().orElseThrow());
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity player) {
        return player == this.player;
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        var newSpellData = new ArrayList<>(buf.read(SPELL_DATA_ENDEC));
        for (var entry : clientRunningSpellData.entrySet()) {
//            if (newSpellData.stream().filter(data -> data.id == entry.))
        }
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        buf.write(SPELL_DATA_ENDEC, runningSpellData);
    }

    public void queue(SpellPart spell, List<Fragment> arguments) {
        executionManager.queue(spell, arguments);
    }

    public void queue(SpellPart spell, List<Fragment> arguments, ManaPool poolOverride) {
        executionManager.queue(spell, arguments, poolOverride);
    }

    public void killAll() {
        executionManager.killAll();
    }

    public void kill(int index) {
        executionManager.kill(index);
    }

    public record RunningSpellData(int id, int executionsLastTick) {
    }
}
