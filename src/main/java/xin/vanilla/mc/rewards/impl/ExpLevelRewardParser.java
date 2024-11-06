package xin.vanilla.mc.rewards.impl;

import com.google.gson.JsonObject;
import lombok.NonNull;
import xin.vanilla.mc.rewards.RewardParser;

public class ExpLevelRewardParser implements RewardParser<Integer> {

    @Override
    public @NonNull Integer deserialize(JsonObject json) {
        try {
            return json.get("expLevel").getAsInt();
        } catch (Exception e) {
            LOGGER.error("Failed to parse exp level reward", e);
            return 0;
        }
    }

    @Override
    public JsonObject serialize(Integer reward) {
        JsonObject json = new JsonObject();
        json.addProperty("expLevel", reward);
        return json;
    }
}
