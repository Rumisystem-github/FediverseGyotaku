package su.rumishistem.fediverse_gyotaku;

import static su.rumishistem.rumi_java_lib.LOG_PRINT.Main.LOG;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import su.rumishistem.fediverse_gyotaku.Module.GetRequestType;
import su.rumishistem.rumi_java_lib.ArrayNode;
import su.rumishistem.rumi_java_lib.CONFIG;
import su.rumishistem.rumi_java_lib.FETCH;
import su.rumishistem.rumi_java_lib.FETCH_RESULT;
import su.rumishistem.rumi_java_lib.SQL;
import su.rumishistem.rumi_java_lib.SnowFlake;
import su.rumishistem.rumi_java_lib.LOG_PRINT.LOG_TYPE;

public class Main {
	public static final String ActivityJsonMimetype = "application/activity+json";

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

		String request = "";

		switch (GetRequestType.get(request)) {
			case Post: {
				String host = new URL(request).getHost();
				String[] instance_id = ArchiveInstanceAndGetID(host);
				String post_id = ArchivePostAndGetID(request, instance_id[0], instance_id[1]);
				System.out.println(post_id);
				return;
			}

			case User: {
				String host = null;
				String actor_url = null;

				if (request.matches(".*@.+@.+") || request.matches(".+@.+")) {
					//メアドみたいなノリで書かれてたらActorのURLを撮ってくる
					Matcher mtc = Pattern.compile("^@?([^@]+)@([^@]+)$").matcher(request);
					if (mtc.find()) {
						String user = mtc.group(1);
						host = mtc.group(2);
						actor_url = getWebFinger(host, user);
					}
				} else {
					//URLだったらホスト名を持ってくる
					URL url = new URL(request);
					host = url.getHost();
					actor_url = request;
				}

				if (host != null && actor_url != null) {
					String[] instance_id = ArchiveInstanceAndGetID(host);
					String[] user_id = ArchiveUserAndGetID(actor_url, host, instance_id[0], instance_id[1]);
				}
				return;
			}

			case Instance: {
				URL url = new URL(request);
				String host = url.getHost();
				String[] instance_id = ArchiveInstanceAndGetID(host);
				return;
			}

			default: {
				System.out.println("キャンセル");
				return;
			}
		}
	}

	public static String ArchivePostAndGetID(String PostURL, String InstanceID, String InstanceDataID) throws Exception {
		String Host = new URL(PostURL).getHost();

		FETCH ajax = new FETCH(PostURL);
		ajax.SetHEADER("Accept", ActivityJsonMimetype);
		FETCH_RESULT result = ajax.GET();
		if (result.getStatusCode() == 200) {
			String raw_body = result.getString();
			JsonNode body = new ObjectMapper().readTree(raw_body);

			ArrayNode check = SQL.RUN("SELECT `ID` FROM `FG_POST` WHERE `DUMP` = ?;", new Object[] {raw_body});
			if (check.length() != 0) {
				return check.get(0).getData("ID").asString();
			}

			String ActorURL = body.get("attributedTo").asText();
			String UserID[] = ArchiveUserAndGetID(ActorURL, Host, InstanceID, InstanceDataID);
			if (UserID == null) return null;

			String ID = String.valueOf(SnowFlake.GEN());
			SQL.UP_RUN("""
				INSERT
					INTO `FG_POST` (`ID`, `USER_PROFILE`, `INSTANCE_DATA`, `POST_ID`, `CONTENTS`, `PUBLISHED`, `REPLY`, `DATE`, `DUMP`)
				VALUES
					(?, ?, ?, ?, ?, ?, ?, NOW(), ?)
			""", new Object[] {
				ID,
				UserID[1],
				InstanceDataID,
				body.get("id").asText(),
				body.get("content").asText(),
				ZonedDateTime.parse(body.get("published").asText()).withZoneSameInstant(ZoneId.of("Asia/Tokyo")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
				null,
				raw_body
			});

			for (int I = 0; I < body.get("attachment").size(); I++) {
				JsonNode attachment = body.get("attachment").get(I);
				SQL.UP_RUN("""
					INSERT
						INTO `FG_POST_ATTACHMENT` (`ID`, `POST`, `INDEX`, `TYPE`, `MEDIATYPE`, `URL`, `ARCHIVE_URL`, `NAME`, `NSFW`)
					VALUES
						(?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", new Object[] {
					String.valueOf(SnowFlake.GEN()),
					ID,
					I,
					attachment.get("type").asText().toUpperCase(),
					attachment.get("mediaType").asText(),
					attachment.get("url").asText(),
					attachment.get("url").asText(),
					attachment.get("name").asText(),
					attachment.get("sensitive").asBoolean()
				});
			}

			return ID;
		} else {
			return null;
		}
	}

	public static String[] ArchiveUserAndGetID(String ActorURL, String Host, String InstanceID, String InstanceDataID) throws Exception {
		FETCH ajax = new FETCH(ActorURL);
		ajax.SetHEADER("Accept", ActivityJsonMimetype);

		FETCH_RESULT result = ajax.GET();
		if (result.getStatusCode() == 200) {
			String raw_body = result.getString();
			JsonNode body = new ObjectMapper().readTree(raw_body);

			String ID;

			ArrayNode CheckUserExits = SQL.RUN("SELECT `ID` FROM `FG_USER` WHERE `UID` = ?;", new Object[] {body.get("id").asText()});
			if (CheckUserExits.length() == 0) {
				ID = String.valueOf(SnowFlake.GEN());

				SQL.UP_RUN("""
					INSERT
						INTO `FG_USER` (`ID`, `INSTANCE`, `UID`, `PUBLICKEY`, `UPDATE`)
					VALUES
						(?, ?, ?, ?, NOW())
				""", new Object[] {
					ID, InstanceID, body.get("id").asText(), Host, body.get("publicKey").get("publicKeyPem").asText()
				});
			} else {
				ID = CheckUserExits.get(0).getData("ID").asString();
			}

			ArrayNode check = SQL.RUN("SELECT `ID` FROM `FG_USER_PROFILE` WHERE `DUMP` = ?;", new Object[] {raw_body});
			if (check.length() != 0) {
				return new String[] {ID, check.get(0).getData("ID").asString()};
			}

			String ProfileID = String.valueOf(SnowFlake.GEN());
			SQL.UP_RUN("""
				INSERT
					INTO `FG_USER_PROFILE` (`ID`, `USER`, `INSTANCE_DATA`, `PREFERRED_UID`, `NAME`, `DESCRIPTION`, `DATE`, `DUMP`)
				VALUES
					(?, ?, ?, ?, ?, ?, NOW(), ?)
			""", new Object[] {
				ProfileID,
				ID,
				InstanceDataID,
				body.get("preferredUsername").asText(),
				body.get("name").asText(),
				body.get("summary").asText(),
				raw_body
			});

			return new String[] {ID, ProfileID};
		} else {
			return null;
		}
	}

	public static String[] ArchiveInstanceAndGetID(String Host) throws Exception {
		String NodeinfoURL = getNodeinfoURL(Host);

		FETCH ajax = new FETCH(NodeinfoURL);
		FETCH_RESULT result = ajax.GET();
		if (result.getStatusCode() == 200) {
			String raw_body = result.getString();
			JsonNode body = new ObjectMapper().readTree(raw_body);

			String ID;

			ArrayNode CheckUserExits = SQL.RUN("SELECT `ID` FROM `FG_INSTANCE` WHERE `HOST` = ?;", new Object[] {Host});
			if (CheckUserExits.length() == 0) {
				ID = String.valueOf(SnowFlake.GEN());

				SQL.UP_RUN("""
					INSERT
						INTO `FG_INSTANCE` (`ID`, `HOST`, `UPDATE`)
					VALUES
						(?, ?, NOW())
				""", new Object[] {
					ID, Host
				});
			} else {
				ID = CheckUserExits.get(0).getData("ID").asString();
			}

			ArrayNode check = SQL.RUN("SELECT `ID` FROM `FG_INSTANCE_DATA` WHERE `DUMP` = ?;", new Object[] {raw_body});
			if (check.length() != 0) {
				return new String[] {ID, check.get(0).getData("ID").asString()};
			}

			String DataID = String.valueOf(SnowFlake.GEN());
			SQL.UP_RUN("""
				INSERT
					INTO `FG_INSTANCE_DATA` (`ID`, `INSTANCE`, `DATE`, `SOFTWARE_NAME`, `SOFTWARE_VERSION`, `NAME`, `DESCRIPTION`, `ADMIN_NAME`, `ADMIN_ADDRESS`, `DUMP`)
				VALUES
					(?, ?, NOW(), ?, ?, ?, ?, ?, ?, ?)
			""", new Object[] {
				DataID,
				ID,
				body.get("software").get("name").asText(),
				body.get("software").get("version").asText(),
				body.get("metadata").get("nodeName").asText(),
				body.get("metadata").get("nodeDescription").asText(),
				body.get("metadata").get("nodeAdmins").get(0).get("name").asText(),
				body.get("metadata").get("nodeAdmins").get(0).get("email").asText(),
				raw_body
			});

			return new String[] {ID, DataID};
		} else {
			return null;
		}
	}

	public static String getNodeinfoURL(String Host) throws Exception {
		FETCH ajax = new FETCH("https://" + Host + "/.well-known/nodeinfo");
		ajax.SetHEADER("Accept", "application/json");

		FETCH_RESULT result = ajax.GET();
		if (result.getStatusCode() == 200) {
			JsonNode body = new ObjectMapper().readTree(result.getString());
			for (int I = 0; I < body.get("links").size(); I++) {
				JsonNode row = body.get("links").get(I);
				return row.get("href").asText();
			}

			return null;
		} else {
			return null;
		}
	}

	public static String getWebFinger(String Host, String UserID) throws Exception {
		FETCH ajax = new FETCH("https://" + Host + "/.well-known/webfinger?resource=acct:" + UserID);
		ajax.setFollowRedirect(false);
		ajax.SetHEADER("Accept", "application/jrd+json");

		FETCH_RESULT result = ajax.GET();
		if (result.getStatusCode() == 200) {
			JsonNode body = new ObjectMapper().readTree(result.getString());

			for (int I = 0; I < body.get("links").size(); I++) {
				JsonNode row = body.get("links").get(I);
				if (row.get("type") != null && !row.get("type").isNull()) {
					if (row.get("type").asText().equals(ActivityJsonMimetype)) {
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
