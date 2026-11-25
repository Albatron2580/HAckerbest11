package com.whiteclient.browser;

import com.mojang.blaze3d.textures.GpuTextureView;
import com.whiteclient.WhiteClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;

public final class WebView {
	public static final String MCEF_ID = "mcef-modern";
	public static final String MCEF_PAGE = "https://modrinth.com/mod/mcef-modern";

	private static Object initialization;
	private static Throwable initError;

	private final Object browser;
	private Object cef;
	private int width = -1, height = -1;

	private WebView(Object browser) {
		this.browser = browser;
	}

	public static boolean installed() {
		try {
			return FabricLoader.getInstance().isModLoaded(MCEF_ID);
		} catch (Throwable t) {
			return false;
		}
	}

	private static Class<?> api() throws ClassNotFoundException {
		return Class.forName("net.dimaskama.mcef.api.MCEFApi");
	}

	public static void startEngine() {
		if (!installed() || initialization != null || initError != null) {
			return;
		}
		try {
			initialization = api().getMethod("initialize").invoke(null);
		} catch (Throwable t) {
			initError = t;
			WhiteClient.LOGGER.warn("[WHITECLIENT] browser engine failed to start", t);
		}
	}

	private static CompletableFuture<?> future() throws ReflectiveOperationException {
		Method m = initialization.getClass().getMethod("getFuture");
		m.setAccessible(true);
		return (CompletableFuture<?>) m.invoke(initialization);
	}

	public static boolean ready() {
		if (initialization == null || initError != null) {
			return false;
		}
		try {
			CompletableFuture<?> f = future();
			if (f.isCompletedExceptionally()) {
				try {
					f.join();
				} catch (Throwable t) {
					initError = t;
				}
				return false;
			}
			return f.isDone();
		} catch (Throwable t) {
			initError = t;
			return false;
		}
	}

	public static Throwable error() {
		return initError;
	}

	public static String progress() {
		if (initialization == null) {
			return "Starting";
		}
		try {
			Method gs = initialization.getClass().getMethod("getStage");
			gs.setAccessible(true);
			Method gp = initialization.getClass().getMethod("getPercentage");
			gp.setAccessible(true);
			String stage = String.valueOf(gs.invoke(initialization)).toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
			float pct = (Float) gp.invoke(initialization);
			stage = Character.toUpperCase(stage.charAt(0)) + stage.substring(1);
			return pct >= 0 ? stage + " " + Math.round(pct) + "%" : stage;
		} catch (Throwable t) {
			return "Starting";
		}
	}

	public static float percent() {
		try {
			Method gp = initialization.getClass().getMethod("getPercentage");
			gp.setAccessible(true);
			return (Float) gp.invoke(initialization);
		} catch (Throwable t) {
			return -1f;
		}
	}

	public static WebView create(String url) {
		try {
			Object apiInstance = future().join();
			Method create = api().getMethod("createBrowser", String.class, boolean.class);
			return new WebView(create.invoke(apiInstance, url, false));
		} catch (Throwable t) {
			initError = t;
			WhiteClient.LOGGER.warn("[WHITECLIENT] could not open a browser page", t);
			return null;
		}
	}

	private Object call(String name, Object... args) {
		try {
			for (Method m : Class.forName("net.dimaskama.mcef.api.MCEFBrowser").getMethods()) {
				if (m.getName().equals(name) && m.getParameterCount() == args.length) {
					return m.invoke(browser, args);
				}
			}
		} catch (Throwable t) {
			WhiteClient.LOGGER.debug("[WHITECLIENT] browser call {} failed", name, t);
		}
		return null;
	}

	private Object cefCall(String name, Object... args) {
		try {
			if (cef == null) {
				cef = call("getCefBrowser");
			}
			if (cef == null) {
				return null;
			}
			for (Method m : Class.forName("org.cef.browser.CefBrowser").getMethods()) {
				if (m.getName().equals(name) && m.getParameterCount() == args.length) {
					return m.invoke(cef, args);
				}
			}
		} catch (Throwable t) {
			WhiteClient.LOGGER.debug("[WHITECLIENT] browser call {} failed", name, t);
		}
		return null;
	}

	public void resize(int w, int h) {
		w = Math.max(16, w);
		h = Math.max(16, h);
		if (w != width || h != height) {
			width = w;
			height = h;
			call("resize", w, h);
		}
	}

	private double zoom = 1.0;
	private String zoomedFor = "";

	public void zoom(double z) {
		String u = url();
		if (u.isBlank()) {
			return;
		}
		String host = u.replaceFirst("(?i)^[a-z]+://", "");
		int i = host.indexOf('/');
		host = i > 0 ? host.substring(0, i) : host;
		if (Math.abs(z - zoom) > 1.0e-3 || !host.equals(zoomedFor)) {
			zoom = z;
			zoomedFor = host;
			cefCall("setZoomLevel", Math.log(z) / Math.log(1.2));
		}
	}

	public void focus(boolean f) {
		call("setFocus", f);
	}

	public GpuTextureView texture() {
		Object v = call("getTextureView");
		return v instanceof GpuTextureView view ? view : null;
	}

	private static MouseButtonInfo buttonOf(MouseButtonEvent click) {
		try {
			return (MouseButtonInfo) MouseButtonEvent.class.getRecordComponents()[2].getAccessor().invoke(click);
		} catch (Throwable t) {
			return new MouseButtonInfo(click.button(), 0);
		}
	}

	public void mouseDown(MouseButtonEvent click, double x, double y, boolean doubled) {
		call("onMouseClicked", new MouseButtonEvent(x, y, buttonOf(click)), doubled);
	}

	public void mouseUp(MouseButtonEvent click, double x, double y) {
		call("onMouseReleased", new MouseButtonEvent(x, y, buttonOf(click)));
	}

	public void mouseMove(double x, double y) {
		call("onMouseMoved", (int) x, (int) y);
	}

	public void scroll(double x, double y, double amount) {
		call("onMouseScrolled", (int) x, (int) y, amount);
	}

	public void keyDown(KeyEvent input) {
		call("onKeyPressed", input);
	}

	public void keyUp(KeyEvent input) {
		call("onKeyReleased", input);
	}

	public void type(CharacterEvent input) {
		call("onCharTyped", input);
	}

	public void load(String url) {
		cefCall("loadURL", url);
	}

	public void back() {
		cefCall("goBack");
	}

	public void forward() {
		cefCall("goForward");
	}

	public void reload() {
		cefCall("reload");
	}

	public String url() {
		Object u = cefCall("getURL");
		return u == null ? "" : u.toString();
	}

	public boolean loading() {
		return Boolean.TRUE.equals(cefCall("isLoading"));
	}

	public void close() {
		call("close");
	}
}
