package su.rumishistem.fediverse_gyotaku.Module;

import static su.rumishistem.fediverse_gyotaku.Main.ConfigData;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import su.rumishistem.rumi_java_lib.FETCH;
import su.rumishistem.rumi_java_lib.FETCH_RESULT;

public class SessionLogin {
	private boolean LOGIN = false;
	private String SESSION = null;
	private JsonNode ACCOUNT_DATA = null;

	public SessionLogin(String SESSION) {
		try {
			FETCH AJAX = new FETCH("http://" + ConfigData.get("RSV").getData("ACCOUNT_SERVER").asString() + "/Session?ID=" + SESSION + "&SERVICE=ILANES");
			FETCH_RESULT AJAX_RESULT = AJAX.GET();
			if (AJAX_RESULT.getStatusCode() == 200) {
				JsonNode RESULT = new ObjectMapper().readTree(AJAX_RESULT.getString());
				if (RESULT.get("STATUS").asBoolean()) {
					this.LOGIN = true;
					this.SESSION = SESSION;
					this.ACCOUNT_DATA = RESULT.get("ACCOUNT_DATA");
				}
			}
		} catch (Exception EX) {
			EX.printStackTrace();
		}
	}

	public boolean Status() {
		return LOGIN;
	}

	public JsonNode GetAccountData() {
		return ACCOUNT_DATA;
	}
}
