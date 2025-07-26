package su.rumishistem.fediverse_gyotaku;

import static su.rumishistem.rumi_java_lib.LOG_PRINT.Main.LOG;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.function.Function;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import su.rumishistem.fediverse_gyotaku.Module.GetRequestType;
import su.rumishistem.fediverse_gyotaku.Module.ResolveUserActorURL;
import su.rumishistem.rumi_java_lib.ArrayNode;
import su.rumishistem.rumi_java_lib.CONFIG;
import su.rumishistem.rumi_java_lib.FETCH;
import su.rumishistem.rumi_java_lib.FETCH_RESULT;
import su.rumishistem.rumi_java_lib.SQL;
import su.rumishistem.rumi_java_lib.LOG_PRINT.LOG_TYPE;
import su.rumishistem.rumi_java_lib.RESOURCE.RESOURCE_MANAGER;
import su.rumishistem.rumi_java_lib.SmartHTTP.ERRORCODE;
import su.rumishistem.rumi_java_lib.SmartHTTP.HTTP_REQUEST;
import su.rumishistem.rumi_java_lib.SmartHTTP.HTTP_RESULT;
import su.rumishistem.rumi_java_lib.SmartHTTP.SmartHTTP;
import su.rumishistem.rumi_java_lib.SmartHTTP.Type.EndpointFunction;
import su.rumishistem.rumi_java_lib.SmartHTTP.Type.ErrorEndpointFunction;
import su.rumishistem.rumi_java_lib.SmartHTTP.Type.EndpointEntrie.Method;

public class Main {
	public static final String ActivityJsonMimetype = "application/activity+json";
	public static final String HTMLMimetype = "text/html; charset=UTF-8";
	public static final String JsonMimetype = "application/json; charset=UTF-8";

	public static ArrayNode ConfigData = null;

	public static void main(String[] args) throws Exception {
		LOG(LOG_TYPE.PROCESS, "Loading Config.ini");

		//設定ファイルを読み込む
		if (new File("Config.ini").exists()) {
			ConfigData = new CONFIG().DATA;
			LOG(LOG_TYPE.PROCESS_END_OK, "");
		} else {
			LOG(LOG_TYPE.PROCESS_END_FAILED, "");
			LOG(LOG_TYPE.FAILED, "ERR! Config.ini ga NAI!!!!!!!!!!!!!!");
			System.exit(1);
		}

		//SQL
		SQL.CONNECT(
			ConfigData.get("SQL").getData("IP").asString(),
			ConfigData.get("SQL").getData("PORT").asString(),
			ConfigData.get("SQL").getData("DB").asString(),
			ConfigData.get("SQL").getData("USER").asString(),
			ConfigData.get("SQL").getData("PASS").asString()
		);

		SmartHTTP SH = new SmartHTTP(ConfigData.get("HTTP").getData("PORT").asInt());

		//API
		SH.SetRoute("/api/Search", Method.GET, new EndpointFunction() {
			@Override
			public HTTP_RESULT Run(HTTP_REQUEST r) throws Exception {
				if (r.GetEVENT().getURI_PARAM().get("REQUEST") == null) {
					return new HTTP_RESULT(400, "{\"STATUS\":false, \"ERR\":\"URI_PARAM_GA_TALINAI\"}".getBytes(), JsonMimetype);
				}

				String request = r.GetEVENT().getURI_PARAM().get("REQUEST");
				GetRequestType.Type type = GetRequestType.get(request);
				if (type == GetRequestType.Type.None) return new HTTP_RESULT(400, "{\"STATUS\":false, \"ERR\":\"NTF\"}".getBytes(), JsonMimetype);

				String contents_id = null;

				switch (type) {
					case User: {
						ResolveUserActorURL resolve = new ResolveUserActorURL(request);
						if (resolve.get_status()) {
							FETCH ajax = new FETCH(resolve.get_actor_url());
							ajax.SetHEADER("Accept", ActivityJsonMimetype);
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
				return new HTTP_RESULT(200, new ObjectMapper().writeValueAsString(return_body).getBytes(), JsonMimetype);
			}
		});

		//エラー類
		SH.SetRoute("/api/*", Method.ALL, new EndpointFunction() {
			@Override
			public HTTP_RESULT Run(HTTP_REQUEST r) throws Exception {
				return new HTTP_RESULT(404, "{\"STATUS\": false, \"ERR\": \"EP_NOTFOUND\"}".getBytes(), JsonMimetype);
			}
		});

		SH.SetError("/api", ERRORCODE.INTERNAL_SERVER_ERROR, new ErrorEndpointFunction() {
			
			@Override
			public HTTP_RESULT Run(HTTP_REQUEST r, Exception ex) throws Exception {
				ex.printStackTrace();

				try {
					LinkedHashMap<String, Object> RETURN = new LinkedHashMap<String, Object>();
					RETURN.put("STATUS", false);
					RETURN.put("ERR", "SYSTEM_ERR");
					RETURN.put("EX", r.GetParam("EX"));
					return new HTTP_RESULT(500, new ObjectMapper().writeValueAsString(RETURN).getBytes(), JsonMimetype);
				} catch (Exception EX) {
					EX.printStackTrace();
					return null;
				}
			}
		});

		//WebUI
		SH.SetResourceDir("/webui/STYLE/", "/HTML/STYLE/", Main.class);
		SH.SetResourceDir("/webui/SCRIPT/", "/HTML/SCRIPT/", Main.class);
		SH.SetRoute("/webui/", Method.GET, new EndpointFunction() {
			@Override
			public HTTP_RESULT Run(HTTP_REQUEST r) throws Exception {
				return new HTTP_RESULT(200, new RESOURCE_MANAGER(Main.class).getResourceData("/HTML/index.html"), HTMLMimetype);
			}
		});;
		SH.SetRoute("/webui/user", Method.GET, new EndpointFunction() {
			@Override
			public HTTP_RESULT Run(HTTP_REQUEST r) throws Exception {
				return new HTTP_RESULT(200, new RESOURCE_MANAGER(Main.class).getResourceData("/HTML/user.html"), HTMLMimetype);
			}
		});;

		SH.Start();
	}
}
