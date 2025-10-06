package su.rumishistem.fediverse_gyotaku;

import static su.rumishistem.rumi_java_lib.LOG_PRINT.Main.LOG;

import java.io.File;

import su.rumishistem.rumi_java_lib.ArrayNode;
import su.rumishistem.rumi_java_lib.CONFIG;
import su.rumishistem.rumi_java_lib.SQL;
import su.rumishistem.rumi_java_lib.LOG_PRINT.LOG_TYPE;

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
	}
}
