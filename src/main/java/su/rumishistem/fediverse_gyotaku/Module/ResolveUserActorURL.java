package su.rumishistem.fediverse_gyotaku.Module;

import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import su.rumishistem.fediverse_gyotaku.Main;
import su.rumishistem.rumi_java_lib.FETCH;
import su.rumishistem.rumi_java_lib.FETCH_RESULT;

public class ResolveUserActorURL {
	private String host;
	private String actor_url;

	public ResolveUserActorURL(String input) {
		try {
			if (input.matches(".*@.+@.+") || input.matches(".+@.+")) {
				//メアドみたいなノリで書かれてたらActorのURLを撮ってくる
				Matcher mtc = Pattern.compile("^@?([^@]+)@([^@]+)$").matcher(input);
				if (mtc.find()) {
					String user = mtc.group(1);
					host = mtc.group(2);
					actor_url = getWebFinger(host, user);
				}
			} else {
				//URLだったらホスト名を持ってくる
				URL url = new URL(input);
				host = url.getHost();
				actor_url = input;
			}
		} catch (Exception EX) {
			EX.printStackTrace();
		}
	}

	public boolean get_status() {
		if (host != null && actor_url != null) {
			return true;
		} else {
			return false;
		}
	}

	public String get_host() {
		return host;
	}

	public String get_actor_url() {
		return actor_url;
	}

	private static String getWebFinger(String Host, String UserID) throws Exception {
		FETCH ajax = new FETCH("https://" + Host + "/.well-known/webfinger?resource=acct:" + UserID);
		ajax.setFollowRedirect(false);
		ajax.SetHEADER("Accept", "application/jrd+json");

		FETCH_RESULT result = ajax.GET();
		if (result.getStatusCode() == 200) {
			JsonNode body = new ObjectMapper().readTree(result.getString());

			for (int I = 0; I < body.get("links").size(); I++) {
				JsonNode row = body.get("links").get(I);
				if (row.get("type") != null && !row.get("type").isNull()) {
					if (row.get("type").asText().equals(Main.ActivityJsonMimetype)) {
						return row.get("href").asText();
					}
				}
			}

			return null;
		} else {
			return null;
		}
	}
}
