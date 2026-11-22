package com.whiteclient.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ModuleManager {
	private final List<Module> modules = new ArrayList<>();

	public <M extends Module> M register(M module) {
		modules.add(module);
		return module;
	}

	public List<Module> all() {
		return Collections.unmodifiableList(modules);
	}

	public List<Module> byCategory(Category category) {
		List<Module> list = new ArrayList<>();
		for (Module m : modules) {
			if (m.getCategory() == category) {
				list.add(m);
			}
		}
		return list;
	}

	@SuppressWarnings("unchecked")
	public <M extends Module> M get(Class<M> type) {
		for (Module m : modules) {
			if (type.isInstance(m)) {
				return (M) m;
			}
		}
		return null;
	}
}
