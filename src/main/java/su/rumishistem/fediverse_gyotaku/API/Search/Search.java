package su.rumishistem.fediverse_gyotaku.API.Search;

import java.util.LinkedHashMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import su.rumishistem.fediverse_gyotaku.Main;
import su.rumishistem.fediverse_gyotaku.Module.GetRequestType;
import su.rumishistem.fediverse_gyotaku.Module.ResolveUserActorURL;
import su.rumishistem.rumi_java_lib.ArrayNode;
import su.rumishistem.rumi_java_lib.FETCH;
import su.rumishistem.rumi_java_lib.FETCH_RESULT;
import su.rumishistem.rumi_java_lib.SQL;
import su.rumishistem.rumi_java_lib.SmartHTTP.HTTP_REQUEST;
import su.rumishistem.rumi_java_lib.SmartHTTP.HTTP_RESULT;
import su.rumishistem.rumi_java_lib.SmartHTTP.Type.EndpointFunction;

public class Search implements EndpointFunction {
	@Override
	public HTTP_RESULT Run(HTTP_REQUEST r) throws Exception {
		if (r.GetEVENT().getURI_PARAM().get("REQUEST") == null) {
			return new HTTP_RESULT(400, "{\"STATUS\":false, \"ERR\":\"URI_PARAM_GA_TALINAI\"}".getBytes(), Main.JsonMimetype);
		}

		String request = r.GetEVENT().getURI_PARAM().get("REQUEST");
		GetRequestType.Type type = GetRequestType.get(request);
		if (type == GetRequestType.Type.None) return new HTTP_RESULT(400, "{\"STATUS\":false, \"ERR\":\"NTF\"}".getBytes(), Main.JsonMimetype);

		String contents_id = null;

		switch (type) {
			case User: {
				ResolveUserActorURL resolve = new ResolveUserActorURL(request);
				if (resolve.get_status()) {
					FETCH ajax = new FETCH(resolve.get_actor_url());
					ajax.SetHEADER("Accept", Main.ActivityJsonMimetype);
					FETCH_RESULT result = ajax.GET();
					if (result.getStatusCode() == 200) {
						JsonNode body = new ObjectMapper().readTree(result.getString());

						ArrayNode sql_result = SQL.RUN("""
							SELECT
								U.*
							FROM
								`FG_USER` AS U
							WHERE
								U.UID = ?;
						""", new Object[] {
							body.get("id").asText()
						});

						if (sql_result.length() == 1) {
							contents_id = sql_result.get(0).getData("ID").asString();
						}
					}
				}
				break;
			}
		}

		LinkedHashMap<String, Object> return_body = new LinkedHashMap<String, Object>();
		return_body.put("STATUS", true);
		return_body.put("TYPE", type.name().toUpperCase());
		return_body.put("ID", contents_id);
		return new HTTP_RESULT(200, new ObjectMapper().writeValueAsString(return_body).getBytes(), Main.JsonMimetype);
	}
}
