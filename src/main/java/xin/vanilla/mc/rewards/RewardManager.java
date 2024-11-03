package xin.vanilla.mc.rewards;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import lombok.NonNull;
import net.minecraft.advancements.Advancement;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.EffectInstance;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.StringTextComponent;
import xin.vanilla.mc.capability.IPlayerSignInData;
import xin.vanilla.mc.capability.PlayerSignInDataCapability;
import xin.vanilla.mc.capability.SignInRecord;
import xin.vanilla.mc.config.ServerConfig;
import xin.vanilla.mc.config.SignInData;
import xin.vanilla.mc.config.SignInDataManager;
import xin.vanilla.mc.enums.ERewardType;
import xin.vanilla.mc.enums.ESignInType;
import xin.vanilla.mc.enums.ETimeCoolingMethod;
import xin.vanilla.mc.network.SignInPacket;
import xin.vanilla.mc.rewards.impl.*;
import xin.vanilla.mc.util.CollectionUtils;
import xin.vanilla.mc.util.DateUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 奖励管理器
 */
public class RewardManager {
    private static final Map<ERewardType, RewardParser<?>> rewardParsers = new HashMap<>();

    // 注册不同类型的奖励解析器
    static {
        rewardParsers.put(ERewardType.ITEM, new ItemRewardParser());
        rewardParsers.put(ERewardType.EFFECT, new EffectRewardParser());
        rewardParsers.put(ERewardType.EXP_POINT, new ExpPointRewardParser());
        rewardParsers.put(ERewardType.EXP_LEVEL, new ExpLevelRewardParser());
        rewardParsers.put(ERewardType.SIGN_IN_CARD, new SignInCardRewardParser());
        rewardParsers.put(ERewardType.ADVANCEMENT, new AdvancementRewardParser());
        rewardParsers.put(ERewardType.MESSAGE, new MessageRewardParser());
        // MORE ...
    }

    /**
     * 反序列化奖励
     */
    @SuppressWarnings("unchecked")
    public static <T> T deserializeReward(Reward reward) {
        RewardParser<T> parser = (RewardParser<T>) rewardParsers.get(reward.getType());
        if (parser == null) {
            throw new JsonParseException("Unknown reward type: " + reward.getType());
        }
        return parser.deserialize(reward.getContent());
    }

    /**
     * 序列化奖励
     */
    @SuppressWarnings("unchecked")
    public static <T> JsonObject serializeReward(T reward, ERewardType type) {
        RewardParser<T> parser = (RewardParser<T>) rewardParsers.get(type);
        if (parser == null) {
            throw new JsonParseException("Unknown reward type: " + type);
        }
        return parser.serialize(reward);
    }

    /**
     * 判断玩家是否签到
     *
     * @param signInData 玩家签到数据
     * @param date       日期
     * @param compensate 是否校准date
     */
    public static boolean isSignedIn(IPlayerSignInData signInData, Date date, boolean compensate) {
        int dateInt = compensate ? DateUtils.toDateInt(getCompensateDate(date)) : DateUtils.toDateInt(date);
        return signInData.getSignInRecords().stream().anyMatch(record -> DateUtils.toDateInt(record.getCompensateTime()) == dateInt);
    }

    /**
     * 判断玩家是否领取奖励
     *
     * @param signInData 玩家签到数据
     * @param date       日期
     * @param compensate 是否校准date
     */
    public static boolean isRewarded(IPlayerSignInData signInData, Date date, boolean compensate) {
        int dateInt = compensate ? DateUtils.toDateInt(getCompensateDate(date)) : DateUtils.toDateInt(date);
        return signInData.getSignInRecords().stream().anyMatch(record ->
                DateUtils.toDateInt(record.getCompensateTime()) == dateInt && record.isRewarded()
        );
    }

    /**
     * 获取玩家签到总天数
     *
     * @param signInData 玩家签到数据
     */
    public static int getTotalSignInDays(IPlayerSignInData signInData) {
        return (int) signInData.getSignInRecords().stream().map(SignInRecord::getCompensateTime).map(DateUtils::toDateInt).distinct().count();
    }

    /**
     * 获取服务器校准时间的签到时间
     * <p>
     * 服务器校准时间减去 签到冷却刷新时间
     */
    public static int getCompensateDateInt() {
        return DateUtils.toDateInt(getCompensateDate(DateUtils.getServerDate()));
    }

    /**
     * 获取签到时间的校准时间
     * <p>
     * 当前时间减去 签到冷却刷新时间
     *
     * @param date 若date为null, 则使用服务器当前时间
     */
    public static Date getCompensateDate(Date date) {
        if (date == null) {
            date = DateUtils.getServerDate();
        }
        // 签到冷却刷新时间, 固定间隔不需要校准时间
        double cooling;
        switch (ServerConfig.TIME_COOLING_METHOD.get()) {
            case MIXED:
            case FIXED_TIME:
                cooling = ServerConfig.TIME_COOLING_TIME.get();
                break;
            default:
                cooling = 0;
                break;
        }
        // 校准后当前时间
        return DateUtils.addDate(date, -cooling);
    }

    /**
     * 获取指定月份的奖励列表
     *
     * @param currentMonth 当前月份
     * @param playerData   玩家签到数据
     * @param lastOffset   上月最后offset天
     * @param nextOffset   下月开始offset天
     */
    public static Map<Integer, RewardList> getMonthRewardList(Date currentMonth, IPlayerSignInData playerData, int lastOffset, int nextOffset) {
        Map<Integer, RewardList> result = new LinkedHashMap<>();
        // 选中月份的上一个月
        Date lastMonth = DateUtils.addMonth(currentMonth, -1);
        // 选中月份的下一个月
        Date nextMonth = DateUtils.addMonth(currentMonth, 1);
        // 上月的总天数
        int daysOfLastMonth = DateUtils.getDaysOfMonth(lastMonth);
        // 本月总天数
        int daysOfCurrentMonth = DateUtils.getDaysOfMonth(currentMonth);

        // 计算本月+上月最后offset天+下月开始offset的奖励
        for (int i = 1; i <= daysOfCurrentMonth + lastOffset + nextOffset; i++) {
            int month, day, year;
            if (i <= lastOffset) {
                // 属于上月的日期
                year = DateUtils.getYearPart(lastMonth);
                month = DateUtils.getMonthOfDate(lastMonth);
                day = daysOfLastMonth - (lastOffset - i);
            } else if (i <= lastOffset + daysOfCurrentMonth) {
                // 属于当前月的日期
                year = DateUtils.getYearPart(currentMonth);
                month = DateUtils.getMonthOfDate(currentMonth);
                day = i - lastOffset;
            } else {
                // 属于下月的日期
                year = DateUtils.getYearPart(nextMonth);
                month = DateUtils.getMonthOfDate(nextMonth);
                day = i - daysOfCurrentMonth - nextOffset;
            }
            int key = year * 10000 + month * 100 + day;
            RewardList rewardList = getRewardListByDate(DateUtils.getDate(year, month, day, DateUtils.getHourOfDay(currentMonth), DateUtils.getMinuteOfHour(currentMonth), DateUtils.getSecondOfMinute(currentMonth)), playerData, false);
            result.put(key, rewardList);
        }
        return result;
    }

    /**
     * 获取指定日期的奖励列表
     *
     * @param currentDay  日期
     * @param playerData  玩家签到数据
     * @param onlyHistory 是否仅获取玩家签到记录中的奖励
     */
    @NonNull
    public static RewardList getRewardListByDate(Date currentDay, IPlayerSignInData playerData, boolean onlyHistory) {
        RewardList result = new RewardList();
        SignInData serverData = SignInDataManager.getSignInData();
        int nowCompensate8 = getCompensateDateInt();
        // long nowCompensate14 = DateUtils.toDateTimeInt(nowCompensate);
        // 本月总天数
        int daysOfCurrentMonth = DateUtils.getDaysOfMonth(currentDay);
        // 本年总天数
        int daysOfCurrentYear = DateUtils.getDaysOfYear(currentDay);

        // 计算本月+上月最后offset天+下月开始offset的奖励
        int month, day, year;
        // 属于当前月的日期
        year = DateUtils.getYearPart(currentDay);
        month = DateUtils.getMonthOfDate(currentDay);
        day = DateUtils.getDayOfMonth(currentDay);
        int key = year * 10000 + month * 100 + day;
        Date date = DateUtils.getDate(year, month, day);
        int curDayOfYear = DateUtils.getDayOfYear(date);
        int curDayOfMonth = DateUtils.getDayOfMonth(date);
        int curDayOfWeek = DateUtils.getDayOfWeek(date);

        // 已签到的奖励记录
        List<Reward> rewardRecords = null;
        // 如果日历日期小于当前日期, 则从签到记录中获取已签到的奖励记录
        if (key <= nowCompensate8) {
            rewardRecords = playerData.getSignInRecords().stream()
                    .map(SignInRecord::clone)
                    // 若签到日期等于当前日期
                    .filter(record -> DateUtils.toDateInt(record.getCompensateTime()) == key)
                    .flatMap(record -> record.getRewardList().stream())
                    // .peek(reward -> {
                    //     reward.setRewarded(true);
                    //     reward.setDisabled(true);
                    // })
                    .collect(Collectors.toList());
        }

        // 若签到记录存在，则添加签到奖励记录
        if (!CollectionUtils.isNullOrEmpty(rewardRecords)) {
            result.addAll(rewardRecords);
        }
        // 若日期小于当前日期 且 补签仅计算基础奖励
        else if (!onlyHistory && key < nowCompensate8 && ServerConfig.SIGN_IN_CARD_ONLY_BASE_REWARD.get()) {
            // 基础奖励
            result.addAll(serverData.getBaseRewards());
        } else if (!onlyHistory) {
            // 基础奖励
            result.addAll(serverData.getBaseRewards());
            // 年度签到奖励(正数第几天)
            result.addAll(serverData.getYearRewards().getOrDefault(String.valueOf(curDayOfYear), new RewardList()));
            // 年度签到奖励(倒数第几天)
            result.addAll(serverData.getYearRewards().getOrDefault(String.valueOf(curDayOfYear - 1 - daysOfCurrentYear), new RewardList()));
            // 月度签到奖励(正数第几天)
            result.addAll(serverData.getMonthRewards().getOrDefault(String.valueOf(curDayOfMonth), new RewardList()));
            // 月度签到奖励(倒数第几天)
            result.addAll(serverData.getMonthRewards().getOrDefault(String.valueOf(curDayOfMonth - 1 - daysOfCurrentMonth), new RewardList()));
            // 周度签到奖励(每周固定7天, 没有倒数的说法)
            result.addAll(serverData.getWeekRewards().getOrDefault(String.valueOf(curDayOfWeek), new RewardList()));
            // 自定义日期奖励
            result.addAll(
                    serverData.getDateTimeRewardsRelation().keySet().stream()
                            .filter(getDateStringList(currentDay)::contains)
                            .map(serverData.getDateTimeRewardsRelation()::get)
                            .distinct()
                            .map(serverData.getDateTimeRewards()::get)
                            .flatMap(Collection::stream)
                            .collect(Collectors.toList())
            );

            //  若日历日期>=当前日期，则添加连续签到奖励(不同玩家不一样)
            if (key >= nowCompensate8) {
                // 连续签到天数
                int continuousSignInDays = playerData.getContinuousSignInDays();
                if (DateUtils.toDateInt(playerData.getLastSignInTime()) < nowCompensate8) {
                    continuousSignInDays++;
                }
                continuousSignInDays += key - nowCompensate8;
                // 连续签到奖励
                int continuousMax = serverData.getContinuousRewardsRelation().keySet().stream().map(Integer::parseInt).max(Comparator.naturalOrder()).orElse(0);
                result.addAll(
                        serverData.getContinuousRewards().get(
                                serverData.getContinuousRewardsRelation().get(
                                        String.valueOf(Math.min(continuousMax, continuousSignInDays))
                                )
                        )
                );
                // 连续签到周期奖励
                int cycleMax = serverData.getCycleRewardsRelation().keySet().stream().map(Integer::parseInt).max(Comparator.naturalOrder()).orElse(0);
                result.addAll(
                        serverData.getCycleRewards().get(
                                serverData.getCycleRewardsRelation().get(
                                        String.valueOf(continuousSignInDays % cycleMax == 0 ? cycleMax : continuousSignInDays % cycleMax)
                                )
                        )
                );
            }
        }
        return RewardManager.mergeRewards(result);
    }

    /**
     * 合并重复类型的奖励
     */
    public static RewardList mergeRewards(RewardList rewardList) {
        // return rewardList;
        List<Reward> rewards = rewardList.stream()
                .collect(Collectors.groupingBy(reward -> {
                            ERewardType type = reward.getType();
                            String key = type.name();
                            // 分组时基于type和内容字段进行分组键
                            switch (type) {
                                case ITEM:
                                    ItemStack itemStack = RewardManager.deserializeReward(reward);
                                    key = itemStack.getItem().getRegistryName().toString();
                                    if (itemStack.hasTag()) {
                                        key += itemStack.getTag().toString();
                                    }
                                    break;
                                case EFFECT:
                                    EffectInstance effectInstance = RewardManager.deserializeReward(reward);
                                    key = effectInstance.getEffect().getRegistryName().toString() + " " + effectInstance.getAmplifier();
                                    break;
                                case EXP_POINT:
                                    break;
                                case EXP_LEVEL:
                                    break;
                                case SIGN_IN_CARD:
                                    break;
                                case ADVANCEMENT:
                                case MESSAGE:
                                default:
                                    key = reward.getContent().toString();
                                    break;
                            }
                            return key;
                        },
                        Collectors.reducing(null, (reward1, reward2) -> {
                            if (reward1 == null) return reward2;
                            if (reward2 == null) return reward1;

                            ERewardType type = reward1.getType();
                            Object content1 = RewardManager.deserializeReward(reward1);
                            Object content2 = RewardManager.deserializeReward(reward2);
                            switch (type) {
                                case ITEM:
                                    content1 = new ItemStack(((ItemStack) content1).getItem(), ((ItemStack) content1).getCount() + ((ItemStack) content2).getCount());
                                    ((ItemStack) content1).setTag(((ItemStack) content2).getTag());
                                    break;
                                case EFFECT:
                                    content1 = new EffectInstance(((EffectInstance) content1).getEffect(), ((EffectInstance) content1).getDuration() + ((EffectInstance) content2).getDuration(), ((EffectInstance) content1).getAmplifier());
                                    break;
                                case EXP_POINT:
                                case SIGN_IN_CARD:
                                case EXP_LEVEL:
                                    content1 = ((Integer) content1) + ((Integer) content2);
                                    break;
                                case ADVANCEMENT:
                                case MESSAGE:
                                default:
                                    break;
                            }
                            return new Reward().setRewarded(reward1.isRewarded()).setType(type).setDisabled(reward1.isDisabled()).setContent(RewardManager.serializeReward(content1, type));
                        })))
                .values().stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        return new RewardList(rewards);
    }

    public static List<String> getDateStringList(Date date) {
        List<String> result = new ArrayList<>();
        result.add(DateUtils.toDateTimeString(date));
        result.add(DateUtils.toString(date));
        result.add(DateUtils.toString(date, "'0000'-MM-dd"));
        result.add(DateUtils.toString(date, "yyyy-'00'-dd"));
        result.add(DateUtils.toString(date, "yyyy-MM-'00'"));
        result.add(DateUtils.toString(date, "'0000'-'00'-dd"));
        result.add(DateUtils.toString(date, "'0000'-MM-'00'"));
        result.add(DateUtils.toString(date, "yyyy-'00'-'00'"));
        result.add(DateUtils.toString(date, "'0000'-MM-dd HH:mm:ss"));
        result.add(DateUtils.toString(date, "yyyy-'00'-dd HH:mm:ss"));
        result.add(DateUtils.toString(date, "yyyy-MM-'00' HH:mm:ss"));
        result.add(DateUtils.toString(date, "'0000'-'00'-dd HH:mm:ss"));
        result.add(DateUtils.toString(date, "'0000'-MM-'00' HH:mm:ss"));
        result.add(DateUtils.toString(date, "yyyy-'00'-'00' HH:mm:ss"));
        return result;
    }

    /**
     * 签到or补签
     */
    public static void signIn(ServerPlayerEntity player, SignInPacket packet) {
        IPlayerSignInData signInData = PlayerSignInDataCapability.getData(player);
        ETimeCoolingMethod coolingMethod = ServerConfig.TIME_COOLING_METHOD.get();
        int serverDateInt = getCompensateDateInt();
        int signInDateInt = DateUtils.toDateInt(packet.getSignInTime());
        // 判断签到/补签时间合法性
        if (serverDateInt < signInDateInt) {
            player.sendMessage(new StringTextComponent("签到日期晚于服务器当前日期，签到失败"), player.getUUID());
            return;
        } else if (ESignInType.SIGN_IN.equals(packet.getSignInType()) && serverDateInt > signInDateInt) {
            player.sendMessage(new StringTextComponent("签到日期早于服务器当前日期，签到失败"), player.getUUID());
            return;
        } else if (ESignInType.RE_SIGN_IN.equals(packet.getSignInType()) && serverDateInt <= signInDateInt) {
            player.sendMessage(new StringTextComponent("补签日期不早于服务器当前日期，补签失败"), player.getUUID());
            return;
        } else if (ESignInType.SIGN_IN.equals(packet.getSignInType()) && serverDateInt == DateUtils.toDateInt(signInData.getLastSignInTime())) {
            player.sendMessage(new StringTextComponent("今天已经签过到啦"), player.getUUID());
            return;
        }
        // 判断签到CD
        if (ESignInType.SIGN_IN.equals(packet.getSignInType()) && coolingMethod.getCode() >= ETimeCoolingMethod.FIXED_INTERVAL.getCode()) {
            Date lastSignInTime = DateUtils.addDate(signInData.getLastSignInTime(), ServerConfig.TIME_COOLING_INTERVAL.get());
            if (packet.getSignInTime().before(lastSignInTime)) {
                player.sendMessage(new StringTextComponent("签到冷却中，签到失败，请稍后再试"), player.getUUID());
                return;
            }
        }
        // 判断补签
        if (ESignInType.RE_SIGN_IN.equals(packet.getSignInType()) && !ServerConfig.SIGN_IN_CARD.get()) {
            player.sendMessage(new StringTextComponent("服务器未开启补签功能，补签失败"), player.getUUID());
            return;
        } else if (ESignInType.RE_SIGN_IN.equals(packet.getSignInType()) && signInData.getSignInCard() <= 0) {
            player.sendMessage(new StringTextComponent("补签卡不足，补签失败"), player.getUUID());
            return;
        } else if (ESignInType.RE_SIGN_IN.equals(packet.getSignInType()) && isSignedIn(signInData, packet.getSignInTime(), false)) {
            player.sendMessage(new StringTextComponent("已经签过到了哦"), player.getUUID());
            return;
        }
        // 判断领取奖励
        if (ESignInType.REWARD.equals(packet.getSignInType())) {
            if (isRewarded(signInData, packet.getSignInTime(), false)) {
                player.sendMessage(new StringTextComponent(DateUtils.toString(packet.getSignInTime()) + "的奖励已经领取过啦"), player.getUUID());
                return;
            } else if (!isSignedIn(signInData, packet.getSignInTime(), false)) {
                player.sendMessage(new StringTextComponent(String.format("没有查询到[%s]的签到记录哦，鉴定为阁下没有签到！", DateUtils.toString(packet.getSignInTime()))), player.getUUID());
                return;
            } else {
                signInData.getSignInRecords().stream()
                        // 若签到日期等于当前日期
                        .filter(record -> DateUtils.toDateInt(record.getCompensateTime()) == DateUtils.toDateInt(packet.getSignInTime()))
                        // 若奖励未领取
                        .filter(record -> !record.isRewarded())
                        .forEach(record -> {
                            // 设置奖励为已领取
                            record.setRewarded(true);
                            record.getRewardList().stream()
                                    .filter(reward -> !reward.isDisabled())
                                    .filter(reward -> !reward.isRewarded())
                                    .forEach(reward -> {
                                        reward.setDisabled(true);
                                        reward.setRewarded(true);
                                        giveRewardToPlayer(player, signInData, reward);
                                    });
                        });
                player.sendMessage(new StringTextComponent("奖励领取成功"), player.getUUID());
            }
        }
        // 签到/补签
        else {
            RewardList rewardList = RewardManager.getRewardListByDate(packet.getSignInTime(), signInData, false);
            if (ESignInType.RE_SIGN_IN.equals(packet.getSignInType())) signInData.subSignInCard();
            SignInRecord signInRecord = new SignInRecord();
            signInRecord.setRewarded(packet.isAutoRewarded());
            signInRecord.setRewardList(new RewardList());
            signInRecord.setSignInTime(packet.getSignInTime());
            if (packet.getSignInType().equals(ESignInType.RE_SIGN_IN)) {
                signInRecord.setCompensateTime(packet.getSignInTime());
            } else {
                signInRecord.setCompensateTime(getCompensateDate(null));
            }
            signInRecord.setSignInUUID(player.getUUID().toString());
            // 是否自动领取
            if (packet.isAutoRewarded()) {
                rewardList.forEach(reward -> {
                    giveRewardToPlayer(player, signInData, reward);
                    signInRecord.getRewardList().add(reward);
                });
            } else {
                signInRecord.getRewardList().addAll(rewardList);
            }
            signInData.setLastSignInTime(packet.getSignInTime());
            signInData.getSignInRecords().add(signInRecord);
            // 若为签到
            if (packet.getSignInType().equals(ESignInType.SIGN_IN)) {
                // 若签到时间大于最后签到时间一天, 则连续签到天数+1
                if (serverDateInt == DateUtils.toDateInt(DateUtils.addDay(signInData.getLastSignInTime(), 1))) {
                    signInData.plusContinuousSignInDays();
                }
                // 重置连续签到天数
                else {
                    signInData.resetContinuousSignInDays();
                }
            }
            // 若为补签, 重新计算连续签到天数
            else if (packet.getSignInType().equals(ESignInType.RE_SIGN_IN)) {
                signInData.setContinuousSignInDays(DateUtils.calculateContinuousDays(signInData.getSignInRecords().stream().map(SignInRecord::getCompensateTime).collect(Collectors.toList()), getCompensateDate(null)));
            }
            player.sendMessage(new StringTextComponent(String.format("签到成功, %s/%s", signInData.getContinuousSignInDays(), getTotalSignInDays(signInData))), player.getUUID());
        }
        // PlayerSignInDataCapability.setData(player, signInData);
        signInData.save(player);
        // 同步数据只客户端
        PlayerSignInDataCapability.syncPlayerData(player);
    }

    private static void giveRewardToPlayer(ServerPlayerEntity player, IPlayerSignInData signInData, Reward reward) {
        reward.setRewarded(true);
        Object object = RewardManager.deserializeReward(reward);
        switch (reward.getType()) {
            case ITEM:
                RewardManager.giveItemStack(player, (ItemStack) object, true);
                break;
            case SIGN_IN_CARD:
                signInData.plusSignInCard((Integer) object);
                break;
            case EFFECT:
                player.addEffect((EffectInstance) object);
                break;
            case EXP_LEVEL:
                player.giveExperienceLevels((Integer) object);
                break;
            case EXP_POINT:
                player.giveExperiencePoints((Integer) object);
                break;
            case ADVANCEMENT:
                // TODO 待研究成就解锁
                Advancement advancement = player.server.getAdvancements().getAdvancement((ResourceLocation) object);
                if (advancement != null) {
                    player.getAdvancements().award(advancement, "impossible");
                }
                break;
            case MESSAGE:
                player.sendMessage((StringTextComponent) object, player.getUUID());
                break;
            default:
        }
    }

    /**
     * 给予玩家物品
     *
     * @param player    目标玩家
     * @param itemStack 物品堆
     * @param drop      若玩家背包空间不足, 是否以物品实体的形式生成在世界上
     * @return 是否添加成功
     */
    public static boolean giveItemStack(ServerPlayerEntity player, ItemStack itemStack, boolean drop) {
        // 尝试将物品堆添加到玩家的库存中
        boolean added = player.inventory.add(itemStack);
        // 如果物品堆无法添加到库存，则以物品实体的形式生成在世界上
        if (!added && drop) {
            ItemEntity itemEntity = new ItemEntity(player.level, player.getX(), player.getY(), player.getZ(), itemStack);
            added = player.level.addFreshEntity(itemEntity);
        }
        return added;
    }

}
