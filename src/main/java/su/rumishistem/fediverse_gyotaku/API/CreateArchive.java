package su.rumishistem.fediverse_gyotaku.API;

import java.util.LinkedHashMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import su.rumishistem.fediverse_gyotaku.Archiver;
import su.rumishistem.fediverse_gyotaku.Main;
import su.rumishistem.fediverse_gyotaku.Type.ArchiveResult;
import su.rumishistem.rumi_java_lib.FETCH;
import su.rumishistem.rumi_java_lib.FETCH_RESULT;
import su.rumishistem.rumi_java_lib.SmartHTTP.HTTP_REQUEST;
import su.rumishistem.rumi_java_lib.SmartHTTP.HTTP_RESULT;
import su.rumishistem.rumi_java_lib.SmartHTTP.Type.EndpointFunction;

public class CreateArchive implements EndpointFunction{
	@Override
	public HTTP_RESULT Run(HTTP_REQUEST r) throws Exception {
		if (Login(r.GetEVENT().getHEADER_DATA().get("TOKEN")) == false) {
			return new HTTP_RESULT(401, "{\"STATUS\": false}".getBytes(), Main.JsonMimetype);
		}

		ArchiveResult result = Archiver.archive(r.GetEVENT().getURI_PARAM().get("REQUEST"));

		LinkedHashMap<String, Object> return_body = new LinkedHashMap<String, Object>();
		return_body.put("STATUS", true);
		return_body.put("TYPE", result.Type.name());
		return_body.put("ID", result.ID);
		return new HTTP_RESULT(200, new ObjectMapper().writeValueAsString(return_body).getBytes(), Main.JsonMimetype);
	}

	private boolean Login(String Session) {
		try {
			FETCH AJAX = new FETCH("http://" + Main.ConfigData.get("RSV").getData("ACCOUNT_SERVER").asString() + "/Session?ID=" + Session + "&SERVICE=FEDIVERSE_GYOTAKU");
			FETCH_RESULT AJAX_RESULT = AJAX.GET();
			if (AJAX_RESULT.getStatusCode() == 200) {
				JsonNode RESULT = new ObjectMapper().readTree(AJAX_RESULT.getString());
				if (RESULT.get("STATUS").asBoolean()) {
					return true;
				}
			}

			return false;
		} catch (Exception EX) {
			return false;
		}
	}
}
