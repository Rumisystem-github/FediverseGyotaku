package su.rumishistem.fediverse_gyotaku.Module;

import java.net.URL;

import com.fasterxml.jackson.databind.ObjectMapper;

import su.rumishistem.fediverse_gyotaku.Main;
import su.rumishistem.rumi_java_lib.FETCH;
import su.rumishistem.rumi_java_lib.FETCH_RESULT;

public class GetRequestType {
	public enum Type {
		None,
		Instance,
		User,
		Post
	}

	public static Type get(String Request) {
		try {
			if (Request.matches(".*@.+@.+") || Request.matches(".+@.+")) {
				return Type.User;
			} else if (new URL(Request).getPath() == null || new URL(Request).getPath().isEmpty() || new URL(Request).getPath().equals("/")) {
				return Type.Instance;
			} else {
				FETCH ajax = new FETCH(Request);
				ajax.SetHEADER("Accept", Main.ActivityJsonMimetype);
				FETCH_RESULT result = ajax.GET();
				switch (new ObjectMapper().readTree(result.getString()).get("type").asText().toUpperCase()) {
					case "NOTE":
						return Type.Post;
					case "PERSON":
						return Type.User;
					default:
						return Type.None;
				}
			}
		} catch (Exception EX) {
			EX.printStackTrace();
			return Type.None;
		}
	}
}
