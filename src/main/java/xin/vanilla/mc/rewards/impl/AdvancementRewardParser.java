package xin.vanilla.mc.rewards.impl;

import com.google.gson.JsonObject;
import lombok.NonNull;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import xin.vanilla.mc.SakuraSignIn;
import xin.vanilla.mc.enums.ERewardType;
import xin.vanilla.mc.network.AdvancementData;
import xin.vanilla.mc.rewards.RewardParser;
import xin.vanilla.mc.util.I18nUtils;

public class AdvancementRewardParser implements RewardParser<ResourceLocation> {

    @Override
    public @NonNull ResourceLocation deserialize(JsonObject json) {
        String advancementId;
        try {
            advancementId = json.get("advancement").getAsString();
        } catch (Exception e) {
            LOGGER.error("Failed to parse advancement reward", e);
            advancementId = SakuraSignIn.MODID + ":unknownAdvancement";
        }
        return new ResourceLocation(advancementId);
    }

    @Override
    public JsonObject serialize(ResourceLocation reward) {
        JsonObject json = new JsonObject();
        json.addProperty("advancement", reward.toString());
        return json;
    }

    @Override
    public String getDisplayName(JsonObject json) {
        ResourceLocation deserialize = deserialize(json);
        return String.format("%s: %s", I18nUtils.get(String.format("reward.sakura_sign_in.reward_type_%s", ERewardType.ADVANCEMENT.getCode()))
                , SakuraSignIn.getAdvancementData().stream()
                        .filter(data -> data.getId().equals(deserialize))
                        .findFirst().orElse(new AdvancementData(deserialize, "", "", new ItemStack(Items.AIR)))
                        .getTitle());
    }

    public static String getId(AdvancementData advancementData) {
        return getId(advancementData.getId());
    }

    public static String getId(Advancement advancement) {
        return getId(advancement.getId());
    }

    public static String getId(ResourceLocation resourceLocation) {
        return resourceLocation.toString();
    }

    public static AdvancementData getAdvancementData(ResourceLocation resourceLocation) {
        return getAdvancementData(resourceLocation.toString());
    }

    public static AdvancementData getAdvancementData(String id) {
        return SakuraSignIn.getAdvancementData().stream()
                .filter(data -> data.getId().toString().equalsIgnoreCase(id))
                .findFirst().orElse(new AdvancementData(new ResourceLocation(id), "", "", new ItemStack(Items.AIR)));
    }

    public static String getDisplayName(AdvancementData advancementData) {
        return advancementData.getTitle();
    }

    public static String getDisplayName(Advancement advancement) {
        String result = "";
        DisplayInfo display = advancement.getDisplay();
        if (display != null)
            result = display.getTitle().getString();
        return result;
    }

    public static String getDescription(AdvancementData advancementData) {
        return advancementData.getDescription();
    }

    public static String getDescription(Advancement advancement) {
        String result = "";
        DisplayInfo display = advancement.getDisplay();
        if (display != null)
            result = advancement.getDisplay().getDescription().getString();
        return result;
    }
}
