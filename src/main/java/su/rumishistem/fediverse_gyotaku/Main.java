package su.rumishistem.fediverse_gyotaku;

import static su.rumishistem.rumi_java_lib.LOG_PRINT.Main.LOG;

import java.io.File;

import su.rumishistem.fediverse_gyotaku.API.Route;
import su.rumishistem.rumi_java_lib.ArrayNode;
import su.rumishistem.rumi_java_lib.CONFIG;
import su.rumishistem.rumi_java_lib.SQL;
import su.rumishistem.rumi_java_lib.LOG_PRINT.LOG_TYPE;
import su.rumishistem.rumi_java_lib.RESOURCE.RESOURCE_MANAGER;
import su.rumishistem.rumi_java_lib.SmartHTTP.HTTP_REQUEST;
import su.rumishistem.rumi_java_lib.SmartHTTP.HTTP_RESULT;
import su.rumishistem.rumi_java_lib.SmartHTTP.SmartHTTP;
import su.rumishistem.rumi_java_lib.SmartHTTP.Type.EndpointFunction;
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
		Route.init(SH);

		//WebUI
		SH.SetResourceDir("/webui/STYLE/", "/HTML/STYLE/", Main.class);
		SH.SetResourceDir("/webui/SCRIPT/", "/HTML/SCRIPT/", Main.class);
		SH.SetRoute("/webui/", Method.GET, new EndpointFunction() {
			@Override
			public HTTP_RESULT Run(HTTP_REQUEST r) throws Exception {
				return new HTTP_RESULT(200, new RESOURCE_MANAGER(Main.class).getResourceData("/HTML/index.html"), HTMLMimetype);
			}
		});
		SH.SetRoute("/webui/archive", Method.GET, new EndpointFunction() {
			@Override
			public HTTP_RESULT Run(HTTP_REQUEST r) throws Exception {
				return new HTTP_RESULT(200, new RESOURCE_MANAGER(Main.class).getResourceData("/HTML/archive.html"), HTMLMimetype);
			}
		});
		SH.SetRoute("/webui/user", Method.GET, new EndpointFunction() {
			@Override
			public HTTP_RESULT Run(HTTP_REQUEST r) throws Exception {
				return new HTTP_RESULT(200, new RESOURCE_MANAGER(Main.class).getResourceData("/HTML/user.html"), HTMLMimetype);
			}
		});
		SH.SetRoute("/webui/user_archive", Method.GET, new EndpointFunction() {
			@Override
			public HTTP_RESULT Run(HTTP_REQUEST r) throws Exception {
				return new HTTP_RESULT(200, new RESOURCE_MANAGER(Main.class).getResourceData("/HTML/user_archive.html"), HTMLMimetype);
			}
		});
		SH.SetRoute("/webui/instance_archive", Method.GET, new EndpointFunction() {
			@Override
			public HTTP_RESULT Run(HTTP_REQUEST r) throws Exception {
				return new HTTP_RESULT(200, new RESOURCE_MANAGER(Main.class).getResourceData("/HTML/instance_archive.html"), HTMLMimetype);
			}
		});

		SH.Start();
	}
}
