package com.worldstudio.edit;

/** Every editing operation the panel and the command expose. */
public enum EditOp {
	FILL("worldstudio.tool.fill"),
	REPLACE("worldstudio.tool.replace"),
	WALLS("worldstudio.tool.walls"),
	OUTLINE("worldstudio.tool.outline"),
	HOLLOW("worldstudio.tool.hollow"),
	CLEAR("worldstudio.tool.clear"),
	COPY("worldstudio.tool.copy"),
	PASTE("worldstudio.tool.paste");

	private final String translationKey;

	EditOp(String translationKey) {
		this.translationKey = translationKey;
	}

	public String translationKey() {
		return this.translationKey;
	}

	public static EditOp byId(int id) {
		EditOp[] values = values();

		if (id < 0 || id >= values.length) {
			return null;
		}

		return values[id];
	}
}
