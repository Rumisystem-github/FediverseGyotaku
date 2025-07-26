package su.rumishistem.fediverse_gyotaku.Type;

import su.rumishistem.fediverse_gyotaku.Module.GetRequestType;

public class ArchiveResult {
	public GetRequestType.Type Type;
	public String ID;

	public ArchiveResult(GetRequestType.Type Type, String ID) {
		this.Type = Type;
		this.ID = ID;
	}
}
