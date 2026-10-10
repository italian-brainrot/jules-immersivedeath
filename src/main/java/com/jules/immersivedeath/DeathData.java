package com.jules.immersivedeath;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.common.util.INBTSerializable;

public class DeathData implements INBTSerializable<CompoundTag> {
    private long lastDeathTime = -10000L;
    private double lostMaxHp = 0.0;
    private boolean pendingPenalty = false;
    private ListTag inventorySnapshot = new ListTag();
    private CompoundTag curiosSnapshot = new CompoundTag();

    private int nauseaTicks = 0;
    private int blindnessTicks = 0;
    private int weaknessTicks = 0;

    public DeathData() {
    }

    public long getLastDeathTime() {
        return lastDeathTime;
    }

    public void setLastDeathTime(long lastDeathTime) {
        this.lastDeathTime = lastDeathTime;
    }

    public double getLostMaxHp() {
        return lostMaxHp;
    }

    public void setLostMaxHp(double lostMaxHp) {
        this.lostMaxHp = lostMaxHp;
    }

    public boolean isPendingPenalty() {
        return pendingPenalty;
    }

    public void setPendingPenalty(boolean pendingPenalty) {
        this.pendingPenalty = pendingPenalty;
    }

    public ListTag getInventorySnapshot() {
        return inventorySnapshot;
    }

    public void setInventorySnapshot(ListTag inventorySnapshot) {
        this.inventorySnapshot = inventorySnapshot != null ? inventorySnapshot : new ListTag();
    }

    public CompoundTag getCuriosSnapshot() {
        return curiosSnapshot;
    }

    public void setCuriosSnapshot(CompoundTag curiosSnapshot) {
        this.curiosSnapshot = curiosSnapshot != null ? curiosSnapshot.copy() : new CompoundTag();
    }

    public int getNauseaTicks() {
        return nauseaTicks;
    }

    public void setNauseaTicks(int nauseaTicks) {
        this.nauseaTicks = Math.max(0, nauseaTicks);
    }

    public int getBlindnessTicks() {
        return blindnessTicks;
    }

    public void setBlindnessTicks(int blindnessTicks) {
        this.blindnessTicks = Math.max(0, blindnessTicks);
    }

    public int getWeaknessTicks() {
        return weaknessTicks;
    }

    public void setWeaknessTicks(int weaknessTicks) {
        this.weaknessTicks = Math.max(0, weaknessTicks);
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("lastDeathTime", lastDeathTime);
        tag.putDouble("lostMaxHp", lostMaxHp);
        tag.putBoolean("pendingPenalty", pendingPenalty);
        tag.put("inventorySnapshot", inventorySnapshot.copy());
        tag.put("curiosSnapshot", curiosSnapshot.copy());
        tag.putInt("nauseaTicks", nauseaTicks);
        tag.putInt("blindnessTicks", blindnessTicks);
        tag.putInt("weaknessTicks", weaknessTicks);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        if (tag.contains("lastDeathTime", Tag.TAG_ANY_NUMERIC)) {
            this.lastDeathTime = tag.getLong("lastDeathTime");
        }
        if (tag.contains("lostMaxHp", Tag.TAG_ANY_NUMERIC)) {
            this.lostMaxHp = tag.getDouble("lostMaxHp");
        }
        if (tag.contains("pendingPenalty", Tag.TAG_BYTE)) {
            this.pendingPenalty = tag.getBoolean("pendingPenalty");
        }
        if (tag.contains("inventorySnapshot", Tag.TAG_LIST)) {
            this.inventorySnapshot = tag.getList("inventorySnapshot", Tag.TAG_COMPOUND).copy();
        } else {
            this.inventorySnapshot = new ListTag();
        }
        if (tag.contains("curiosSnapshot", Tag.TAG_COMPOUND)) {
            this.curiosSnapshot = tag.getCompound("curiosSnapshot").copy();
        } else {
            this.curiosSnapshot = new CompoundTag();
        }
        if (tag.contains("nauseaTicks", Tag.TAG_INT)) {
            this.nauseaTicks = tag.getInt("nauseaTicks");
        }
        if (tag.contains("blindnessTicks", Tag.TAG_INT)) {
            this.blindnessTicks = tag.getInt("blindnessTicks");
        }
        if (tag.contains("weaknessTicks", Tag.TAG_INT)) {
            this.weaknessTicks = tag.getInt("weaknessTicks");
        }
    }
}
