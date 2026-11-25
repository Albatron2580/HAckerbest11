package com.whiteclient;

import com.whiteclient.config.ConfigManager;
import com.whiteclient.gui.ClickGuiScreen;
import com.whiteclient.gui.MenuIntegration;
import com.whiteclient.module.Module;
import com.whiteclient.module.ModuleManager;
import com.whiteclient.modules.*;
import com.whiteclient.combat.CombatTracker;
import com.whiteclient.hud.HudLayout;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import com.whiteclient.music.MusicService;
import com.whiteclient.render.GlowBatch;
import com.whiteclient.render.Perf;
import com.whiteclient.render.WorldContext;
import com.mojang.blaze3d.vertex.PoseStack;
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
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

public final class WhiteClient implements ClientModInitializer {
	public static final String MOD_ID = "whiteclient";
	public static final String VERSION = "1.13.3";
	public static final Logger LOGGER = LoggerFactory.getLogger("WHITECLIENT");

	private static WhiteClient instance;

	private final ModuleManager modules = new ModuleManager();
	private MusicService music;
	private final GlowBatch glow = new GlowBatch();
	private final Set<Module> crashed = new HashSet<>();
	private ConfigManager config;
	private KeyMapping menuKey;
	private KeyMapping zoomKey;
	private KeyMapping carKey;
	private KeyMapping browserKey;
	public static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
	private boolean openMenuNextTick;
	private final long startNanos = System.nanoTime();
	private long lastFrameNanos = System.nanoTime();

	public static WhiteClient get() {
		return instance;
	}

	public MusicService music() {
		return music;
	}

	public ModuleManager modules() {
		return modules;
	}

	public ConfigManager config() {
		return config;
	}

	public float time() {
		return (System.nanoTime() - startNanos) / 1.0e9f;
	}

	@Override
	public void onInitializeClient() {
		com.whiteclient.gui.Shapes.prewarm();
		instance = this;

		modules.register(new Halo());
		modules.register(new Wings());
		modules.register(new Aura());
		modules.register(new BlockTrail());
		modules.register(new Pets());
		modules.register(new ChinaHat());
		modules.register(new HeadWear());
		modules.register(new Trail());
		modules.register(new JumpCircles());
		modules.register(new Capes());
		modules.register(new CarEmote());

		modules.register(new TargetESP());
		modules.register(new HitEffects());
		modules.register(new KillEffects());
		modules.register(new DamageNumbers());
		modules.register(new Hitmarker());
		modules.register(new GalaxySky());
		modules.register(new Snow());
		modules.register(new RainFX());
		modules.register(new Ambience());
		modules.register(new BlockOutline());
		modules.register(new Zoom());
		modules.register(new FpsBoost());
		modules.register(new TimeChanger());
		modules.register(new HandView());
		modules.register(new Spotify());
		modules.register(new Text3D());
		modules.register(new Watermark());
		modules.register(new InfoHUD());
		modules.register(new Keystrokes());
		modules.register(new PotionHUD());
		modules.register(new ArmorHUD());
		modules.register(new ComboCounter());
		modules.register(new ReachDisplay());
		modules.register(new CustomCrosshair());
		modules.register(new LowHealth());
		modules.register(new Notifications());
		modules.register(new Browser());
		modules.register(new Cinematic());
		modules.register(new com.whiteclient.modules.DiscordStatus());
		modules.register(new com.whiteclient.modules.AutoUpdate());
		modules.register(new MenuBackground());
		modules.register(new ModsButton());
		modules.register(new MenuButtons());
		modules.register(new MainMenu());
		modules.register(new InventoryTheme());

		modules.register(new Theme());
		modules.register(new Wardrobe());

		config = new ConfigManager(modules);
		config.load();
		if (config.loadedVersion() < 4) {
			for (com.whiteclient.module.Module mod : modules.all()) {
				if (!(mod instanceof Cinematic) && !(mod instanceof TimeChanger)) {
					mod.setEnabled(true);
				}
			}
			config.save();
		}
		if (config.loadedVersion() < 3) {
			MainMenu mm = modules.get(MainMenu.class);
			mm.layout.reset();
			mm.background.reset();
			config.save();
		}
		if (config.loadedVersion() < 2) {
			modules.get(MenuBackground.class).color.reset();
			modules.get(Halo.class).color.reset();
			config.save();
		}

		music = new MusicService(
				() -> {
					Spotify sp = modules.get(Spotify.class);
					Text3D t3 = modules.get(Text3D.class);
					return sp.isEnabled() || (t3.isEnabled() && t3.source.is("Spotify Lyrics"));
				},
				() -> modules.get(Spotify.class).noLogin.isOn());
		music.start();

		menuKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.whiteclient.menu",
				GLFW.GLFW_KEY_RIGHT_SHIFT,
				KEY_CATEGORY));
		zoomKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.whiteclient.zoom",
				GLFW.GLFW_KEY_C,
				KEY_CATEGORY));
		carKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.whiteclient.car",
				GLFW.GLFW_KEY_N,
				KEY_CATEGORY));
		browserKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.whiteclient.browser",
				GLFW.GLFW_KEY_B,
				KEY_CATEGORY));
		CombatTracker.init();
		HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, vanilla -> (ctx, tickCounter) -> {
			CustomCrosshair ch = modules.get(CustomCrosshair.class);
			if (ch != null && ch.isEnabled() && !crashed.contains(ch)) {
				try {
					ch.draw(ctx);
					return;
				} catch (Throwable t) {
					fail(ch, t);
				}
			}
			vanilla.render(ctx, tickCounter);
		});
		HudElementRegistry.replaceElement(VanillaHudElements.STATUS_EFFECTS, vanilla -> (ctx, tickCounter) -> {
			PotionHUD ph = modules.get(PotionHUD.class);
			if (ph != null && ph.hidesVanilla() && !crashed.contains(ph)) {
				return;
			}
			vanilla.render(ctx, tickCounter);
		});
		WorldRenderEvents.BEFORE_BLOCK_OUTLINE.register((context, outline) -> {
			BlockOutline bo = modules.get(BlockOutline.class);
			return bo == null || !bo.isEnabled() || crashed.contains(bo);
		});

		ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "hud"), this::renderHud);
		WorldRenderEvents.END_MAIN.register(this::renderWorld);
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> config.save());
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			com.whiteclient.modules.AutoUpdate au = modules.get(com.whiteclient.modules.AutoUpdate.class);
			if (au != null && au.isEnabled() && au.installAuto.isOn()) {
				com.whiteclient.update.AutoUpdater.onGameClosing();
			}
		});
		com.whiteclient.update.AutoUpdater.startup();
		MenuIntegration.register();
		registerCommands();

		LOGGER.info("[WHITECLIENT] {} loaded with {} modules. Press Right Shift to open the menu.", VERSION, modules.all().size());
	}

	public KeyMapping zoomKey() {
		return zoomKey;
	}

	private void onTick(Minecraft client) {
		try {
			CarEmote car = modules.get(CarEmote.class);
			while (carKey != null && carKey.consumeClick()) {
				if (car != null && car.isEnabled() && client.player != null && client.screen == null) {
					car.toggleCar();
				}
			}
		} catch (Throwable t) {
			LOGGER.debug("[WHITECLIENT] car key", t);
		}
		try {
			CombatTracker.tick(client);
		} catch (Throwable t) {
			LOGGER.debug("[WHITECLIENT] combat tick", t);
		}
		while (browserKey != null && browserKey.consumeClick()) {
			Browser br = modules.get(Browser.class);
			if (br != null && br.isEnabled() && client.screen == null) {
				br.open();
			}
		}
		while (menuKey.consumeClick()) {
			if (client.screen == null) {
				client.setScreen(new ClickGuiScreen(null));
			}
		}
		if (openMenuNextTick && client.screen == null) {
			openMenuNextTick = false;
			client.setScreen(new ClickGuiScreen(null));
		}
		if (client.level == null || client.player == null) {
			return;
		}
		for (Module module : modules.all()) {
			if (module.isEnabled() && !crashed.contains(module)) {
				long t0 = System.nanoTime();
				try {
					module.onTick(client);
					Perf.add(module.getName() + ":tick", System.nanoTime() - t0);
				} catch (Throwable t) {
					fail(module, t);
				}
			}
		}
	}

	private void renderHud(GuiGraphics context, DeltaTracker tickCounter) {
		Minecraft client = Minecraft.getInstance();
		if (client.options.hideGui) {
			return;
		}
		float tickDelta = tickCounter.getGameTimeDeltaPartialTick(false);
		HudLayout.begin(context.guiWidth(), context.guiHeight());
		for (Module module : modules.all()) {
			if (module.isEnabled() && !crashed.contains(module)) {
				long t0 = System.nanoTime();
				try {
					module.renderHud(context, tickDelta);
					Perf.add(module.getName() + ":hud", System.nanoTime() - t0);
				} catch (Throwable t) {
					fail(module, t);
				}
			}
		}
	}

	private void renderWorld(WorldRenderContext context) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null) {
			return;
		}
		long now = System.nanoTime();
		float frameDelta = Math.min(0.1f, Math.max(0f, (now - lastFrameNanos) / 1.0e9f));
		lastFrameNanos = now;

		MultiBufferSource consumers = context.consumers();
		if (consumers == null) {
			return;
		}
		Vec3 cam = context.worldState().cameraRenderState.pos;
		Quaternionf rotation = context.worldState().cameraRenderState.orientation;
		if (cam == null) {
			return;
		}
		PoseStack matrices = context.matrices() != null ? context.matrices() : new PoseStack();
		float tickDelta = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);

		WorldContext ctx = new WorldContext(client, matrices, consumers, cam,
				rotation != null ? rotation : new Quaternionf(), tickDelta, time(), frameDelta, glow);
		glow.clear();
		float camYaw = (float) Math.toRadians(client.gameRenderer.getMainCamera().yRot());
		float camPitch = (float) Math.toRadians(client.gameRenderer.getMainCamera().xRot());
		glow.setView((float) (-Math.sin(camYaw) * Math.cos(camPitch)), (float) -Math.sin(camPitch), (float) (Math.cos(camYaw) * Math.cos(camPitch)));
		matrices.pushPose();
		for (Module module : modules.all()) {
			if (module.isEnabled() && !crashed.contains(module)) {
				long t0 = System.nanoTime();
				try {
					module.renderWorld(ctx);
					Perf.add(module.getName() + ":world", System.nanoTime() - t0);
				} catch (Throwable t) {
					fail(module, t);
				}
			}
		}
		try {
			long d0 = System.nanoTime();
			if (consumers instanceof MultiBufferSource.BufferSource immediate) {
				immediate.endBatch();
			}
			long d1 = System.nanoTime();
			Perf.add("draw:solid+text", d1 - d0);
			glow.draw(consumers, matrices.last().pose());
			Perf.add("draw:glow", System.nanoTime() - d1);
			if (consumers instanceof MultiBufferSource.BufferSource immediate) {
				immediate.endBatch();
			}
		} catch (Throwable t) {
			LOGGER.error("[WHITECLIENT] world draw failed", t);
			glow.clear();
		}
		matrices.popPose();
	}

	private void fail(Module module, Throwable t) {
		crashed.add(module);
		LOGGER.error("[WHITECLIENT] module '{}' crashed and was paused for this session", module.getName(), t);
	}

	private void registerCommands() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
				ClientCommandManager.literal("whiteclient")
						.executes(ctx -> {
							openMenuNextTick = true;
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
											ctx.getSource().sendFeedback(Component.literal("§f[WHITECLIENT] §73D text set to: §f" + message));
											return 1;
										})))
						.then(ClientCommandManager.literal("spotify")
								.then(ClientCommandManager.literal("login")
										.executes(ctx -> {
											Spotify sp = modules.get(Spotify.class);
											sp.login(sp.clientId.get());
											return 1;
										})
										.then(ClientCommandManager.argument("clientId", StringArgumentType.word())
												.executes(ctx -> {
													modules.get(Spotify.class).login(StringArgumentType.getString(ctx, "clientId"));
													config.save();
													return 1;
												})))
								.then(ClientCommandManager.literal("logout")
										.executes(ctx -> {
											music.auth().logout();
											ctx.getSource().sendFeedback(Component.literal("\u00a7f[WHITECLIENT] \u00a77Logged out of Spotify."));
											return 1;
										}))
								.then(ClientCommandManager.literal("status")
										.executes(ctx -> {
											var np = music.current();
											ctx.getSource().sendFeedback(Component.literal("\u00a7f[WHITECLIENT] \u00a77" + music.status()
													+ (np == null ? "" : " \u00a7f" + np.artist() + " - " + np.title())));
											return 1;
										})))
						.then(ClientCommandManager.literal("reload")
								.executes(ctx -> {
									config.load();
									crashed.clear();
									ctx.getSource().sendFeedback(Component.literal("§f[WHITECLIENT] §7config reloaded"));
									return 1;
								}))));
	}
}
