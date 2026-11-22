package com.whiteclient;

import com.whiteclient.config.ConfigManager;
import com.whiteclient.gui.ClickGuiScreen;
import com.whiteclient.gui.MenuIntegration;
import com.whiteclient.module.Module;
import com.whiteclient.module.ModuleManager;
import com.whiteclient.modules.Aura;
import com.whiteclient.modules.BlockTrail;
import com.whiteclient.modules.GalaxySky;
import com.whiteclient.modules.Halo;
import com.whiteclient.modules.MenuBackground;
import com.whiteclient.modules.ModsButton;
import com.whiteclient.modules.Snow;
import com.whiteclient.modules.Text3D;
import com.whiteclient.modules.Watermark;
import com.whiteclient.modules.Wings;
import com.whiteclient.render.GlowBatch;
import com.whiteclient.render.WorldContext;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

public final class WhiteClient implements ClientModInitializer {
	public static final String MOD_ID = "whiteclient";
	public static final String VERSION = "1.1.0";
	public static final Logger LOGGER = LoggerFactory.getLogger("WHITECLIENT");

	private static WhiteClient instance;

	private final ModuleManager modules = new ModuleManager();
	private final GlowBatch glow = new GlowBatch();
	private final Set<Module> crashed = new HashSet<>();
	private ConfigManager config;
	private KeyBinding menuKey;
	private boolean openMenuNextTick;
	private final long startNanos = System.nanoTime();
	private long lastFrameNanos = System.nanoTime();

	public static WhiteClient get() {
		return instance;
	}

	public ModuleManager modules() {
		return modules;
	}

	public ConfigManager config() {
		return config;
	}

	/** Smooth animation clock in seconds. */
	public float time() {
		return (System.nanoTime() - startNanos) / 1.0e9f;
	}

	@Override
	public void onInitializeClient() {
		instance = this;

		// Visuals
		modules.register(new Halo());
		modules.register(new Wings());
		modules.register(new Aura());
		modules.register(new BlockTrail());
		// World
		modules.register(new GalaxySky());
		modules.register(new Snow());
		modules.register(new Text3D());
		// HUD
		modules.register(new Watermark());
		// Menu
		modules.register(new MenuBackground());
		modules.register(new ModsButton());

		config = new ConfigManager(modules);
		config.load();

		menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.whiteclient.menu",
				GLFW.GLFW_KEY_RIGHT_SHIFT,
				KeyBinding.Category.create(Identifier.of(MOD_ID, "main"))));

		ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
		HudElementRegistry.addLast(Identifier.of(MOD_ID, "hud"), this::renderHud);
		WorldRenderEvents.END_MAIN.register(this::renderWorld);
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> config.save());
		MenuIntegration.register();
		registerCommands();

		LOGGER.info("[WHITECLIENT] {} loaded with {} modules. Press Right Shift to open the menu.", VERSION, modules.all().size());
	}

	private void onTick(MinecraftClient client) {
		while (menuKey.wasPressed()) {
			if (client.currentScreen == null) {
				client.setScreen(new ClickGuiScreen(null));
			}
		}
		if (openMenuNextTick && client.currentScreen == null) {
			openMenuNextTick = false;
			client.setScreen(new ClickGuiScreen(null));
		}
		if (client.world == null || client.player == null) {
			return;
		}
		for (Module module : modules.all()) {
			if (module.isEnabled() && !crashed.contains(module)) {
				try {
					module.onTick(client);
				} catch (Throwable t) {
					fail(module, t);
				}
			}
		}
	}

	private void renderHud(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.options.hudHidden) {
			return;
		}
		float tickDelta = tickCounter.getTickProgress(false);
		for (Module module : modules.all()) {
			if (module.isEnabled() && !crashed.contains(module)) {
				try {
					module.renderHud(context, tickDelta);
				} catch (Throwable t) {
					fail(module, t);
				}
			}
		}
	}

	private void renderWorld(WorldRenderContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null || client.player == null) {
			return;
		}
		long now = System.nanoTime();
		float frameDelta = Math.min(0.1f, Math.max(0f, (now - lastFrameNanos) / 1.0e9f));
		lastFrameNanos = now;

		VertexConsumerProvider consumers = context.consumers();
		if (consumers == null) {
			return;
		}
		Vec3d cam = context.worldState().cameraRenderState.pos;
		Quaternionf rotation = context.worldState().cameraRenderState.orientation;
		if (cam == null) {
			return;
		}
		MatrixStack matrices = context.matrices() != null ? context.matrices() : new MatrixStack();
		float tickDelta = client.getRenderTickCounter().getTickProgress(true);

		WorldContext ctx = new WorldContext(client, matrices, consumers, cam,
				rotation != null ? rotation : new Quaternionf(), tickDelta, time(), frameDelta, glow);
		glow.clear();
		float camYaw = (float) Math.toRadians(client.gameRenderer.getCamera().getYaw());
		float camPitch = (float) Math.toRadians(client.gameRenderer.getCamera().getPitch());
		glow.setView((float) (-Math.sin(camYaw) * Math.cos(camPitch)), (float) -Math.sin(camPitch), (float) (Math.cos(camYaw) * Math.cos(camPitch)));
		matrices.push();
		for (Module module : modules.all()) {
			if (module.isEnabled() && !crashed.contains(module)) {
				try {
					module.renderWorld(ctx);
				} catch (Throwable t) {
					fail(module, t);
				}
			}
		}
		try {
			// 1) text & solid stuff, 2) additive glow on top (sorted back to front)
			if (consumers instanceof VertexConsumerProvider.Immediate immediate) {
				immediate.draw();
			}
			glow.draw(consumers, matrices.peek().getPositionMatrix());
			if (consumers instanceof VertexConsumerProvider.Immediate immediate) {
				immediate.draw();
			}
		} catch (Throwable t) {
			LOGGER.error("[WHITECLIENT] world draw failed", t);
			glow.clear();
		}
		matrices.pop();
	}

	private void fail(Module module, Throwable t) {
		crashed.add(module);
		LOGGER.error("[WHITECLIENT] module '{}' crashed and was paused for this session", module.getName(), t);
	}

	private void registerCommands() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
				ClientCommandManager.literal("whiteclient")
						.executes(ctx -> {
							openMenuNextTick = true; // chat closes first, open on the next tick
							return 1;
						})
						.then(ClientCommandManager.literal("text")
								.then(ClientCommandManager.argument("message", StringArgumentType.greedyString())
										.executes(ctx -> {
											String message = StringArgumentType.getString(ctx, "message");
											Text3D text3D = modules.get(Text3D.class);
											text3D.setMessage(message);
											text3D.setEnabled(true);
											config.save();
											ctx.getSource().sendFeedback(Text.literal("§f[WHITECLIENT] §73D text set to: §f" + message));
											return 1;
										})))
						.then(ClientCommandManager.literal("reload")
								.executes(ctx -> {
									config.load();
									crashed.clear();
									ctx.getSource().sendFeedback(Text.literal("§f[WHITECLIENT] §7config reloaded"));
									return 1;
								}))));
	}
}
