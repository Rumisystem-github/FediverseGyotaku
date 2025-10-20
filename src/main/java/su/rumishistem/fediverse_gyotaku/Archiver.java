package su.rumishistem.fediverse_gyotaku;

import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import su.rumishistem.fediverse_gyotaku.Module.GetInstanceID;
import su.rumishistem.fediverse_gyotaku.Module.GetNoteID;
import su.rumishistem.fediverse_gyotaku.Module.GetUserID;
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
	private static final String activity_json_mime = "application/activity+json";
	
	public String archive(String input, ArchiveType type, String user_id) throws IOException, SQLException {
		switch (type) {
			case Instance:
				return archive_instance_and_get_id(input, user_id);

			case User:
				String name = input.split("@")[0];
				String host = input.split("@")[1];
				String actor_url = get_user_actor_url(name, host);
				return archive_user_and_get_id(actor_url, host, user_id);

			case Post:
				return archive_post_and_get_id(input, user_id);

			default:
				throw new RuntimeException("あ？");
		}
	}

	private String archive_post_and_get_id(String url, String archiver_user_id) throws IOException, SQLException{
		String host = new URL(url).getHost();
		FETCH ajax = new FETCH(url);
		ajax.SetHEADER("Accept", activity_json_mime);

		FETCH_RESULT result = ajax.GET();
		if (result.getStatusCode() != 200) throw new RuntimeException("200を返さなかった");

		String raw_body = result.getString();
		JsonNode body = new ObjectMapper().readTree(raw_body);
		String hash = HASH.Gen(HASH_TYPE.MD5, raw_body.getBytes());
		String instance_archive_id = archive_instance_and_get_id(host, archiver_user_id);
		String instance_id = GetInstanceID.from_host(host);
		String user_id = null;
		String user_archive_id = archive_user_and_get_id(body.get("attributedTo").asText(), host, archiver_user_id);
		String post_id = GetNoteID.from_remote_id(body.get("id").asText(), instance_id);
		String reply_archive_id = null;
		String quote_archive_id = null;

		//リプライ
		if (body.get("inReplyTo") != null && !body.get("inReplyTo").isNull()) {
			reply_archive_id = archive_post_and_get_id(body.get("inReplyTo").asText(), archiver_user_id);
		}
		//引用
		if (body.get("quoteUrl") != null && !body.get("quoteUrl").isNull()) {
			quote_archive_id = archive_post_and_get_id(body.get("quoteUrl").asText(), archiver_user_id);
		}

		//SQLからユーザーのIDを探すよ
		ArrayNode user_sql = SQL.RUN("SELECT U.ID FROM `FG_USER_DATA` AS D JOIN `FG_USER` AS U ON U.ID = D.USER;", new Object[] {});
		if (user_sql.length() == 0) throw new RuntimeException("SQLエラー");
		user_id = user_sql.get(0).getData("ID").asString();

		//POSTID
		if (post_id == null) {
			post_id = String.valueOf(SnowFlake.GEN());
			SQL.UP_RUN("""
				INSERT
					INTO `FG_POST` (`ID`, `INSTANCE`, `USER`, `REMOTE_ID`, `REGIST_DATE`, `UPDATE_DATE`)
				VALUES
					(?, ?, ?, ?, NOW(), NOW())
			""", new Object[] {
				post_id, instance_id, user_id, body.get("id").asText()
			});
		}

		String id = String.valueOf(SnowFlake.GEN());
		SQL.UP_RUN("""
			INSERT
				INTO `FG_POST_DATA` (`ID`, `ARCHIVE_USER`, `INSTANCE`, `INSTANCE_DATA`, `USER`, `USER_DATA`, `POST`, `REGIST_DATE`, `HASH`, `DUMP`, `CONTENT`, `REPLY`, `QUOTE`)
			VALUES
				(?, ?, ?, ?, ?, ?, ?, NOW(), ?, ?, ?, ?, ?)
		""", new Object[] {
			id, archiver_user_id,
			instance_id, instance_archive_id,
			user_id, user_archive_id,
			post_id,
			hash, raw_body,
			body.get("content").asText(),
			reply_archive_id, quote_archive_id
		});

		//ファイル
		for (int i = 0; i < body.get("attachment").size(); i++) {
			JsonNode f = body.get("attachment").get(i);
			SQL.UP_RUN("""
				INSERT
					INTO `FG_POST_FILE` (`ID`, `POST_DATA`, `INDEX`, `TYPE`, `MIMETYPE`, `NAME`, `NSFW`, `ORIGINAL_URL`, `ARCHIVE_URL`)
				VALUES
					(?, ?, ?, ?, ?, ?, ?, ?, ?)
			""", new Object[] {
				String.valueOf(SnowFlake.GEN()), id, (i + 1),
				f.get("type").asText(), f.get("mediaType").asText(), f.get("name").asText(), false,
				f.get("url").asText(), f.get("url").asText()
			});
		}

		return id;
	}

	/**
	 * ユーザーのプロフをアーカイブし、アーカイブIDを返します。
	 * @param actor_url ActorUrl
	 * @param host ホスト
	 * @param user_id アーカイブを実行したユーザーのID
	 * @return アーカイブID
	 * @throws IOException Fetcヘラー
	 * @throws SQLException SQLエラー
	 */
	private String archive_user_and_get_id(String actor_url, String host, String user_id) throws IOException, SQLException {
		FETCH ajax = new FETCH(actor_url);
		ajax.SetHEADER("Accept", activity_json_mime);

		FETCH_RESULT result = ajax.GET();
		if (result.getStatusCode() != 200) throw new RuntimeException("ActorURLが200を返さなかった");

		String raw_body = result.getString();
		JsonNode body = new ObjectMapper().readTree(raw_body);
		String hash = HASH.Gen(HASH_TYPE.MD5, raw_body.getBytes());
		String instance_id = GetInstanceID.from_host(host);
		String instance_archive_id = archive_instance_and_get_id(host, user_id);
		String archive_user_id = GetUserID.from_user_name(body.get("preferredUsername").asText(), host);

		if (archive_user_id == null) {
			archive_user_id = String.valueOf(SnowFlake.GEN());
			SQL.UP_RUN("""
				INSERT
					INTO `FG_USER`
						(`ID`, `INSTANCE`, `NAME`, `REMOTE_ID`, `REGIST_DATE`, `UPDATE_DATE`)
				VALUES
					(?, ?, ?, ?, NOW(), NOW());
				""", new Object[] {
				archive_user_id, instance_id, body.get("preferredUsername").asText(), body.get("id").asText()
			});
		}

		//SQLを見て、既に同じ内容のが有ればそのIDを返す
		ArrayNode sql = SQL.RUN("SELECT `ID` FROM `FG_USER_DATA` WHERE `USER` = ? AND `HASH` = ?;", new Object[] {archive_user_id, hash});
		if (sql.length() == 1) return sql.get(0).getData("ID").asString();

		String icon_url = "";
		String header_url = "";

		if (!body.get("icon").isNull()) {
			icon_url = body.get("icon").get("url").asText();
		}
		if (!body.get("image").isNull()) {
			icon_url = body.get("image").get("url").asText();
		}

		//インサート
		String id = String.valueOf(SnowFlake.GEN());
		SQL.UP_RUN("""
			INSERT
				INTO `FG_USER_DATA`
					(`ID`, `ARCHIVE_USER`, `USER`, `INSTANCE`, `INSTANCE_DATA`, `REGIST_DATE`, `HASH`, `DUMP`, `NAME`, `DESCRIPTION`, `PUBLIC_KEY`, `ICON_ORIGINAL_URL`, `ICON_ARCHIVE_URL`, `HEADER_ORIGINAL_URL`, `HEADER_ARCHIVE_URL`)
			VALUES
				(?, ?, ?, ?, ?, NOW(), ?, ?, ?, ?, ?, ?, ?, ?, ?);
			""", new Object[] {
			id, user_id, archive_user_id, instance_id, instance_archive_id, hash, raw_body,
			body.get("name").asText(),
			body.get("summary").asText(),
			body.get("publicKey").get("publicKeyPem").asText(),
			icon_url, icon_url, header_url, header_url
		});
		return id;
	}

	/**
	 * ユーザー名とホスト名からActorURLを取ってくる
	 * @param name ユーザー名
	 * @param host ホスト名
	 * @return ActorURL
	 * @throws IOException Fetchエラー
	 */
	private String get_user_actor_url(String name, String host) throws IOException {
		FETCH ajax = new FETCH("https://"+host+"/.well-known/webfinger?resource=acct:"+name);
		ajax.setFollowRedirect(false);
		ajax.SetHEADER("Accept", "application/jrd+json");

		//WebFingerからURLを取ってくる
		FETCH_RESULT result = ajax.GET();
		if (result.getStatusCode() != 200) throw new RuntimeException("webfingerが200を返さなかった");
		JsonNode body = new ObjectMapper().readTree(result.getString());
		for (int I = 0; I < body.get("links").size(); I++) {
			JsonNode row = body.get("links").get(I);
			if (row.get("type") != null && !row.get("type").isNull()) {
				if (row.get("type").asText().equals(activity_json_mime)) {
					return row.get("href").asText();
				}
			}
		}

		throw new RuntimeException("webfingerにURLがありません。");
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
					(`ID`, `INSTANCE`, `ARCHIVE_USER`, `REGIST_DATE`, `HASH`, `DUMP`, `INSTANCE_NAME`, `INSTANCE_DESCRIPTION`, `SOFTWARE_NAME`, `SOFTWARE_VERSION`)
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
