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
import com.whiteclient.modules.BlockTrail;
import com.whiteclient.modules.GalaxySky;
import com.whiteclient.modules.Halo;
import com.whiteclient.modules.Wardrobe;
import com.whiteclient.modules.Theme;
import com.whiteclient.modules.RainFX;
import com.whiteclient.modules.TargetESP;
import com.whiteclient.modules.Pets;
import com.whiteclient.modules.MenuBackground;
import com.whiteclient.modules.ModsButton;
import com.whiteclient.modules.Snow;
import com.whiteclient.modules.Text3D;
import com.whiteclient.modules.Watermark;
import com.whiteclient.modules.Wings;
import com.whiteclient.modules.MenuButtons;
import com.whiteclient.modules.MainMenu;
import com.whiteclient.modules.Spotify;
import com.whiteclient.music.MusicService;
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
	public static final String VERSION = "1.7.0";
	public static final Logger LOGGER = LoggerFactory.getLogger("WHITECLIENT");

	private static WhiteClient instance;

	private final ModuleManager modules = new ModuleManager();
	private MusicService music;
	private final GlowBatch glow = new GlowBatch();
	private final Set<Module> crashed = new HashSet<>();
	private ConfigManager config;
	private KeyBinding menuKey;
	private KeyBinding zoomKey;
	public static final KeyBinding.Category KEY_CATEGORY = KeyBinding.Category.create(Identifier.of(MOD_ID, "main"));
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
		modules.register(new Pets());
		modules.register(new ChinaHat());
		modules.register(new Trail());
		modules.register(new JumpCircles());
		modules.register(new Capes());

		modules.register(new TargetESP());
		modules.register(new HitEffects());
		modules.register(new KillEffects());
		modules.register(new DamageNumbers());
		modules.register(new Hitmarker());
		modules.register(new TargetHUD());
		modules.register(new ComboCounter());
		modules.register(new ReachDisplay());
		// World
		modules.register(new GalaxySky());
		modules.register(new Snow());
		modules.register(new RainFX());
		modules.register(new Ambience());
		modules.register(new BlockOutline());
		modules.register(new Zoom());
		modules.register(new HandView());
		// Music
		modules.register(new Spotify());
		modules.register(new Text3D());
		// HUD
		modules.register(new Watermark());
		modules.register(new InfoHUD());
		modules.register(new Keystrokes());
		modules.register(new PotionHUD());
		modules.register(new ArmorHUD());
		modules.register(new CustomCrosshair());
		modules.register(new LowHealth());
		modules.register(new Notifications());
		// Menu
		modules.register(new MenuBackground());
		modules.register(new ModsButton());
		modules.register(new MenuButtons());
		modules.register(new MainMenu());

		modules.register(new Theme());
		modules.register(new Wardrobe());

		config = new ConfigManager(modules);
		config.load();
		if (config.loadedVersion() < 3) {
			// 1.5: new showcase main menu with silk background
			MainMenu mm = modules.get(MainMenu.class);
			mm.layout.reset();
			mm.background.reset();
			config.save();
		}
		if (config.loadedVersion() < 2) {
			// 1.2: everything switched to the white theme
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

		menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.whiteclient.menu",
				GLFW.GLFW_KEY_RIGHT_SHIFT,
				KEY_CATEGORY));
		zoomKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.whiteclient.zoom",
				GLFW.GLFW_KEY_C,
				KEY_CATEGORY));
		CombatTracker.init();
		// custom crosshair / potion HUD replace the vanilla ones only while they are on
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
		HudElementRegistry.addLast(Identifier.of(MOD_ID, "hud"), this::renderHud);
		WorldRenderEvents.END_MAIN.register(this::renderWorld);
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> config.save());
		MenuIntegration.register();
		registerCommands();

		LOGGER.info("[WHITECLIENT] {} loaded with {} modules. Press Right Shift to open the menu.", VERSION, modules.all().size());
	}

	public KeyBinding zoomKey() {
		return zoomKey;
	}

	private void onTick(MinecraftClient client) {
		try {
			CombatTracker.tick(client);
		} catch (Throwable t) {
			LOGGER.debug("[WHITECLIENT] combat tick", t);
		}
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
		HudLayout.begin(context.getScaledWindowWidth(), context.getScaledWindowHeight());
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
											ctx.getSource().sendFeedback(Text.literal("\u00a7f[WHITECLIENT] \u00a77Logged out of Spotify."));
											return 1;
										}))
								.then(ClientCommandManager.literal("status")
										.executes(ctx -> {
											var np = music.current();
											ctx.getSource().sendFeedback(Text.literal("\u00a7f[WHITECLIENT] \u00a77" + music.status()
													+ (np == null ? "" : " \u00a7f" + np.artist() + " - " + np.title())));
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
