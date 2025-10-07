package su.rumishistem.fediverse_gyotaku;

import java.io.IOException;
import java.sql.SQLException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import su.rumishistem.fediverse_gyotaku.Module.GetInstanceID;
import su.rumishistem.fediverse_gyotaku.Type.ArchiveType;
import su.rumishistem.rumi_java_lib.ArrayNode;
import su.rumishistem.rumi_java_lib.FETCH;
import su.rumishistem.rumi_java_lib.FETCH_RESULT;
import su.rumishistem.rumi_java_lib.HASH;
import su.rumishistem.rumi_java_lib.HASH.HASH_TYPE;
import su.rumishistem.rumi_java_lib.SQL;
import su.rumishistem.rumi_java_lib.SnowFlake;

public class Archiver {
	private static final String json_mime = "application/json";
	
	public String archive(String input, ArchiveType type, String user_id) throws IOException, SQLException {
		switch (type) {
			case Instance:
				return archive_instance_and_get_id(input, user_id);

			default:
				throw new RuntimeException("あ？");
		}
	}

	/**
	 * インスタンスのメタデータをアーカイブし、アーカイブIDを返します
	 * @param host インスタンスのホスト
	 * @param user_id アーカイブしたユーザーのID
	 * @return アーカイブID
	 * @throws IOException Fetchエラー
	 * @throws SQLException SQLエラー
	 */
	private String archive_instance_and_get_id(String host, String user_id) throws IOException, SQLException {
		String url = get_node_info_url(host);
		FETCH ajax = new FETCH(url);
		ajax.SetHEADER("Accept", json_mime);

		//取得
		FETCH_RESULT result = ajax.GET();
		if (result.getStatusCode() != 200) throw new RuntimeException("[nodeinfo]の応答が200ではなかった");

		//解析
		String raw_body = result.getString();
		JsonNode body = new ObjectMapper().readTree(raw_body);
		String hash = HASH.Gen(HASH_TYPE.MD5, raw_body.getBytes());
		String instance_id = GetInstanceID.from_host(host);

		if (instance_id == null) {
			instance_id = String.valueOf(SnowFlake.GEN());
			SQL.UP_RUN("""
				INSERT
					INTO `FG_INSTANCE`
						(`ID`, `HOST`, `REGIST_DATE`, `UPDATE_DATE`)
				VALUES
					(?, ?, NOW(), NOW());
				""", new Object[] {
				instance_id, host
			});
		}

		//SQLを見て、既に同じ内容のが有ればそのIDを返す
		ArrayNode sql = SQL.RUN("SELECT `ID` FROM `FG_INSTANCE_DATA` WHERE `INSTANCE` = ? AND `HASH` = ?;", new Object[] {instance_id, hash});
		if (sql.length() == 1) return sql.get(0).getData("ID").asString();

		//インサート
		String id = String.valueOf(SnowFlake.GEN());
		SQL.UP_RUN("""
			INSERT
				INTO `FG_INSTANCE_DATA`
					(`ID`, `INSTANCE`, `USER`, `REGIST_DATE`, `HASH`, `DUMP`, `INSTANCE_NAME`, `INSTANCE_DESCRIPTION`, `SOFTWARE_NAME`, `SOFTWARE_VERSION`)
			VALUES
				(?, ?, ?, NOW(), ?, ?, ?, ?, ?, ?);
			""", new Object[] {
			id, instance_id, user_id, hash, raw_body,
			body.get("metadata").get("nodeName").asText(),
			body.get("metadata").get("nodeDescription").asText(),
			body.get("software").get("name").asText(),
			body.get("software").get("version").asText(),
		});
		return id;
	}

	/**
	 * well-known/nodeinfoから、NodeInfoのURLを取ってくる。
	 * @param host ホスト
	 * @return URL
	 * @throws IOException Fetchエラー
	 */
	private String get_node_info_url(String host) throws IOException {
		FETCH ajax = new FETCH("https://"+host+"/.well-known/nodeinfo");
		ajax.SetHEADER("Accept", json_mime);

		FETCH_RESULT result = ajax.GET();
		if (result.getStatusCode() != 200) throw new RuntimeException("[/.well-known/nodeinfo]の応答が200ではなかった");

		JsonNode body = new ObjectMapper().readTree(result.getString());
		if (body.size() == 0) throw new RuntimeException("[/.well-known/nodeinfo]にnodeinfoのURLはなかった。");

		return body.get("links").get(0).get("href").asText();
	}
}
