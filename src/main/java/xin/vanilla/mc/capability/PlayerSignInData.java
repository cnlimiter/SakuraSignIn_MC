package xin.vanilla.mc.capability;

import lombok.NonNull;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import xin.vanilla.mc.util.CollectionUtils;
import xin.vanilla.mc.util.DateUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 玩家签到数据
 */
public class PlayerSignInData implements IPlayerSignInData {
    private final AtomicInteger totalSignInDays = new AtomicInteger();
    private final AtomicInteger continuousSignInDays = new AtomicInteger();
    private Date lastSignInTime;
    private final AtomicInteger signInCard = new AtomicInteger();
    private boolean autoRewarded;
    private List<SignInRecord> signInRecords;

    @Override
    public int getTotalSignInDays() {
        return this.totalSignInDays.get();
    }

    @Override
    public void setTotalSignInDays(int days) {
        this.totalSignInDays.set(days);
    }

    @Override
    public int plusTotalSignInDays() {
        return this.totalSignInDays.incrementAndGet();
    }

    @Override
    public int getContinuousSignInDays() {
        return this.continuousSignInDays.get();
    }

    @Override
    public void setContinuousSignInDays(int days) {
        this.continuousSignInDays.set(days);
    }

    @Override
    public int plusContinuousSignInDays() {
        return this.continuousSignInDays.incrementAndGet();
    }

    @Override
    public void resetContinuousSignInDays() {
        this.continuousSignInDays.set(1);
    }

    @Override
    public @NonNull Date getLastSignInTime() {
        return this.lastSignInTime = this.lastSignInTime == null ? DateUtils.getDate(0, 1, 1) : this.lastSignInTime;
    }

    @Override
    public void setLastSignInTime(Date time) {
        this.lastSignInTime = time;
    }

    @Override
    public int getSignInCard() {
        return this.signInCard.get();
    }

    @Override
    public int plusSignInCard() {
        return this.signInCard.incrementAndGet();
    }


    @Override
    public int plusSignInCard(int num) {
        return this.signInCard.addAndGet(num);
    }

    @Override
    public int subSignInCard() {
        return this.signInCard.decrementAndGet();
    }

    @Override
    public int subSignInCard(int num) {
        return this.signInCard.addAndGet(-num);
    }

    @Override
    public void setSignInCard(int num) {
        this.signInCard.set(num);
    }

    @Override
    public boolean isAutoRewarded() {
        return this.autoRewarded;
    }

    @Override
    public void setAutoRewarded(boolean autoRewarded) {
        this.autoRewarded = autoRewarded;
    }

    @Override
    public @NonNull List<SignInRecord> getSignInRecords() {
        return signInRecords = CollectionUtils.isNullOrEmpty(signInRecords) ? new ArrayList<>() : signInRecords;
    }

    @Override
    public void setSignInRecords(List<SignInRecord> records) {
        this.signInRecords = records;
    }

    public void writeToBuffer(FriendlyByteBuf buffer) {
        buffer.writeInt(this.getTotalSignInDays());
        buffer.writeInt(this.getContinuousSignInDays());
        buffer.writeDate(this.getLastSignInTime());
        buffer.writeInt(this.getSignInCard());
        buffer.writeBoolean(this.isAutoRewarded());
        buffer.writeInt(this.getSignInRecords().size());
        for (SignInRecord record : this.getSignInRecords()) {
            buffer.writeNbt(record.writeToNBT());
        }
    }

    public void readFromBuffer(FriendlyByteBuf buffer) {
        this.totalSignInDays.set(buffer.readInt());
        this.continuousSignInDays.set(buffer.readInt());
        this.lastSignInTime = buffer.readDate();
        this.signInCard.set(buffer.readInt());
        this.autoRewarded = buffer.readBoolean();
        int size = buffer.readInt();
        this.signInRecords = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            this.signInRecords.add(SignInRecord.readFromNBT(Objects.requireNonNull(buffer.readNbt())));
        }
    }

    public void copyFrom(IPlayerSignInData capability) {
        this.totalSignInDays.set(capability.getTotalSignInDays());
        this.continuousSignInDays.set(capability.getContinuousSignInDays());
        this.lastSignInTime = capability.getLastSignInTime();
        this.signInCard.set(capability.getSignInCard());
        this.autoRewarded = capability.isAutoRewarded();
        this.signInRecords = capability.getSignInRecords();
    }

    @Override
    public CompoundTag serializeNBT() {
        // 创建一个CompoundNBT对象，并将玩家的分数和活跃状态写入其中
        CompoundTag tag = new CompoundTag();
        tag.putInt("totalSignInDays", this.getTotalSignInDays());
        tag.putInt("continuousSignInDays", this.getContinuousSignInDays());
        tag.putString("lastSignInTime", DateUtils.toDateTimeString(this.getLastSignInTime()));
        tag.putInt("signInCard", this.getSignInCard());
        tag.putBoolean("autoRewarded", this.isAutoRewarded());
        // 序列化签到记录
        ListTag recordsNBT = new ListTag();
        for (SignInRecord record : this.getSignInRecords()) {
            recordsNBT.add(record.writeToNBT());
        }
        tag.put("signInRecords", recordsNBT);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        // 从NBT标签中读取玩家的分数和活跃状态，并更新到实例中
        this.setTotalSignInDays(nbt.getInt("totalSignInDays"));
        this.setContinuousSignInDays(nbt.getInt("continuousSignInDays"));
        this.setLastSignInTime(DateUtils.format(nbt.getString("lastSignInTime")));
        this.setSignInCard(nbt.getInt("signInCard"));
        this.setAutoRewarded(nbt.getBoolean("autoRewarded"));
        // 反序列化签到记录
        ListTag recordsNBT = nbt.getList("signInRecords", 10); // 10 是 CompoundNBT 的类型ID
        List<SignInRecord> records = new ArrayList<>();
        for (int i = 0; i < recordsNBT.size(); i++) {
            records.add(SignInRecord.readFromNBT(recordsNBT.getCompound(i)));
        }
        this.setSignInRecords(records);
    }

    @Override
    public void save(ServerPlayer player) {
        player.getCapability(PlayerSignInDataCapability.PLAYER_DATA).ifPresent(this::copyFrom);
    }
}
