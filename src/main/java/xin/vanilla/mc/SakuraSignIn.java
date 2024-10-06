package xin.vanilla.mc;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.mc.command.CheckInCommand;
import xin.vanilla.mc.event.ClientEventHandler;
import xin.vanilla.mc.network.ModNetworkHandler;

@Mod(SakuraSignIn.MODID)
public class SakuraSignIn {

    public static final String MODID = "sakura_sign_in";

    // 直接引用 log4j 记录器。
    private static final Logger LOGGER = LogManager.getLogger();

    public SakuraSignIn() {

        // 注册网络通道
        ModNetworkHandler.registerPackets();

        // 注册当前实例到MinecraftForge的事件总线，以便监听和处理游戏内的各种事件
        MinecraftForge.EVENT_BUS.register(this);
    }

    /**
     * 在客户端设置阶段触发的事件处理方法
     * 此方法主要用于接收 FML 客户端设置事件，并执行相应的初始化操作
     *
     * @param event FMLClientSetupEvent 类型的事件参数，包含 Minecraft 的供应商对象
     */
    @SubscribeEvent
    public static void onClientSetup(final FMLClientSetupEvent event) {
        LOGGER.info("Got game settings {}", event.getMinecraftSupplier().get().options);

        // 注册键绑定
        ClientEventHandler.registerKeyBindings();
    }

    /**
     * 注册命令事件的处理方法
     * 当注册命令事件被触发时，此方法将被调用
     * 该方法主要用于注册签到命令到事件调度器
     *
     * @param event 注册命令事件对象，通过该对象可以获取到事件调度器
     */
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        // 注册签到命令到事件调度器
        CheckInCommand.register(event.getDispatcher());
    }
}
