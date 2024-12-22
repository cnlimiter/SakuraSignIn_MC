package xin.vanilla.mc.event;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.gui.DisplayEffectsScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;
import xin.vanilla.mc.SakuraSignIn;
import xin.vanilla.mc.config.ClientConfig;
import xin.vanilla.mc.rewards.RewardManager;
import xin.vanilla.mc.screen.RewardOptionScreen;
import xin.vanilla.mc.screen.SignInScreen;
import xin.vanilla.mc.screen.component.InventoryButton;
import xin.vanilla.mc.screen.coordinate.TextureCoordinate;
import xin.vanilla.mc.util.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

import static xin.vanilla.mc.SakuraSignIn.PNG_CHUNK_NAME;
import static xin.vanilla.mc.util.I18nUtils.getI18nKey;

/**
 * 客户端事件处理器
 */
@Mod.EventBusSubscriber(modid = SakuraSignIn.MODID, value = Dist.CLIENT)
public class ClientEventHandler {
    private static final Logger LOGGER = LogManager.getLogger();

    private static final String CATEGORIES = "key.sakura_sign_in.categories";

    // 定义按键绑定
    public static KeyBinding SIGN_IN_SCREEN_KEY = new KeyBinding("key.sakura_sign_in.sign_in",
            GLFW.GLFW_KEY_H, CATEGORIES);
    public static KeyBinding REWARD_OPTION_SCREEN_KEY = new KeyBinding("key.sakura_sign_in.reward_option",
            GLFW.GLFW_KEY_O, CATEGORIES);

    /**
     * 注册键绑定
     */
    public static void registerKeyBindings() {
        ClientRegistry.registerKeyBinding(SIGN_IN_SCREEN_KEY);
        ClientRegistry.registerKeyBinding(REWARD_OPTION_SCREEN_KEY);
    }

    /**
     * 创建配置文件目录
     */
    public static void createConfigPath() {
        File themesPath = new File(FMLPaths.CONFIGDIR.get().resolve(SakuraSignIn.MODID).toFile(), "themes");
        if (!themesPath.exists()) {
            themesPath.mkdirs();
        }
    }

    /**
     * 加载主题纹理
     */
    public static void loadThemeTexture() {
        try {
            SakuraSignIn.setThemeTexture(TextureUtils.loadCustomTexture(ClientConfig.THEME.get()));
            SakuraSignIn.setSpecialVersionTheme(Boolean.TRUE.equals(ClientConfig.SPECIAL_THEME.get()));
            InputStream inputStream = Minecraft.getInstance().getResourceManager().getResource(SakuraSignIn.getThemeTexture()).getInputStream();
            SakuraSignIn.setThemeTextureCoordinate(PNGUtils.readLastPrivateChunk(inputStream, PNG_CHUNK_NAME));
        } catch (IOException | ClassNotFoundException ignored) {
        }
        if (SakuraSignIn.getThemeTexture() == null) {
            // 使用默认配置
            SakuraSignIn.setThemeTextureCoordinate(TextureCoordinate.getDefault());
        }
        // 设置内置主题特殊图标UV的偏移量
        if (SakuraSignIn.isSpecialVersionTheme() && SakuraSignIn.getThemeTextureCoordinate().isSpecial()) {
            SakuraSignIn.getThemeTextureCoordinate().getNotSignedInUV().setX(320);
            SakuraSignIn.getThemeTextureCoordinate().getSignedInUV().setX(320);
        } else {
            SakuraSignIn.getThemeTextureCoordinate().getNotSignedInUV().setX(0);
            SakuraSignIn.getThemeTextureCoordinate().getSignedInUV().setX(0);
        }
    }

    /**
     * 在客户端Tick事件触发时执行
     *
     * @param event 客户端Tick事件
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        // 检测并消费点击事件
        if (SIGN_IN_SCREEN_KEY.consumeClick()) {
            // 打开签到界面
            ClientEventHandler.openSignInScreen(null);
        } else if (REWARD_OPTION_SCREEN_KEY.consumeClick()) {
            // 打开奖励配置界面
            Minecraft.getInstance().setScreen(new RewardOptionScreen());
        }
    }

    @SubscribeEvent
    public static void onRenderScreen(GuiScreenEvent.InitGuiEvent event) {
        if (event.getGui() instanceof DisplayEffectsScreen) {
            if (SakuraSignIn.getThemeTexture() == null) ClientEventHandler.loadThemeTexture();
            // 创建按钮并添加到界面
            String[] signInCoordinate = ClientConfig.INVENTORY_SIGN_IN_BUTTON_COORDINATE.get().split(",");
            String[] rewardOptionCoordinate = ClientConfig.INVENTORY_REWARD_OPTION_BUTTON_COORDINATE.get().split(",");
            double signInX_ = signInCoordinate.length == 2 ? StringUtils.toFloat(signInCoordinate[0]) : 0;
            double signInY_ = signInCoordinate.length == 2 ? StringUtils.toFloat(signInCoordinate[1]) : 0;
            double rewardOptionX_ = rewardOptionCoordinate.length == 2 ? StringUtils.toFloat(rewardOptionCoordinate[0]) : 0;
            double rewardOptionY_ = rewardOptionCoordinate.length == 2 ? StringUtils.toFloat(rewardOptionCoordinate[1]) : 0;

            double signInX = signInX_;
            double signInY = signInY_;
            double rewardOptionX = rewardOptionX_;
            double rewardOptionY = rewardOptionY_;

            // 如果坐标为0则设置默认坐标
            if (signInX == 0) signInX = 2;
            if (signInY == 0) signInY = 2;
            if (rewardOptionX == 0) rewardOptionX = 20;
            if (rewardOptionY == 0) rewardOptionY = 2;

            // 如果坐标发生变化则保存到配置文件
            if (signInX_ != signInX || signInY_ != signInY) {
                ClientConfig.INVENTORY_SIGN_IN_BUTTON_COORDINATE.set(String.format("%.6f,%.6f", signInX, signInY));
            }
            if (rewardOptionX_ != rewardOptionX || rewardOptionY_ != rewardOptionY) {
                ClientConfig.INVENTORY_REWARD_OPTION_BUTTON_COORDINATE.set(String.format("%.6f,%.6f", rewardOptionX, rewardOptionY));
            }

            // 如果坐标为百分比则转换为像素坐标
            if (signInX > 0 && signInX < 1) signInX *= event.getGui().width;
            if (signInY > 0 && signInY < 1) signInY *= event.getGui().height;
            if (rewardOptionX > 0 && rewardOptionX < 1) rewardOptionX *= event.getGui().width;
            if (rewardOptionY > 0 && rewardOptionY < 1) rewardOptionY *= event.getGui().height;

            InventoryButton signInButton = new InventoryButton((int) signInX, (int) signInY,
                    AbstractGuiUtils.ITEM_ICON_SIZE,
                    AbstractGuiUtils.ITEM_ICON_SIZE,
                    I18nUtils.get("key.sakura_sign_in.sign_in"))
                    .setUV(SakuraSignIn.getThemeTextureCoordinate().getSignInBtnUV(), SakuraSignIn.getThemeTextureCoordinate().getTotalWidth(), SakuraSignIn.getThemeTextureCoordinate().getTotalHeight())
                    .setOnClick((button) -> ClientEventHandler.openSignInScreen(event.getGui()))
                    .setOnDragEnd((coordinate) -> ClientConfig.INVENTORY_SIGN_IN_BUTTON_COORDINATE.set(String.format("%.6f,%.6f", coordinate.getX(), coordinate.getY())));
            InventoryButton rewardOptionButton = new InventoryButton((int) rewardOptionX, (int) rewardOptionY,
                    AbstractGuiUtils.ITEM_ICON_SIZE,
                    AbstractGuiUtils.ITEM_ICON_SIZE,
                    I18nUtils.get("key.sakura_sign_in.reward_option"))
                    .setUV(SakuraSignIn.getThemeTextureCoordinate().getRewardOptionBtnUV(), SakuraSignIn.getThemeTextureCoordinate().getTotalWidth(), SakuraSignIn.getThemeTextureCoordinate().getTotalHeight())
                    .setOnClick((button) -> Minecraft.getInstance().setScreen(new RewardOptionScreen().setPreviousScreen(event.getGui())))
                    .setOnDragEnd((coordinate) -> ClientConfig.INVENTORY_REWARD_OPTION_BUTTON_COORDINATE.set(String.format("%.6f,%.6f", coordinate.getX(), coordinate.getY())));
            event.addWidget(signInButton);
            event.addWidget(rewardOptionButton);
        }
    }

    public static void openSignInScreen(Screen previousScreen) {
        if (SakuraSignIn.isEnabled()) {
            SakuraSignIn.setCalendarCurrentDate(RewardManager.getCompensateDate(new Date()));
            Minecraft.getInstance().setScreen(new SignInScreen().setPreviousScreen(previousScreen));
        } else {
            ClientPlayerEntity player = Minecraft.getInstance().player;
            if (player != null) {
                player.sendMessage(new TranslationTextComponent(getI18nKey("SakuraSignIn server is offline!")), player.getUUID());
            }
        }
    }
}
