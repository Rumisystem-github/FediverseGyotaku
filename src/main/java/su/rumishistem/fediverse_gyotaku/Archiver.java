package su.rumishistem.fediverse_gyotaku;

import java.net.URL;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import su.rumishistem.fediverse_gyotaku.Exception.CancelException;
import su.rumishistem.fediverse_gyotaku.Module.GetRequestType;
import su.rumishistem.fediverse_gyotaku.Module.GetRequestType.Type;
import su.rumishistem.fediverse_gyotaku.Module.ResolveUserActorURL;
import su.rumishistem.fediverse_gyotaku.Type.ArchiveResult;
import su.rumishistem.rumi_java_lib.ArrayNode;
import su.rumishistem.rumi_java_lib.FETCH;
import su.rumishistem.rumi_java_lib.FETCH_RESULT;
import su.rumishistem.rumi_java_lib.SQL;
import su.rumishistem.rumi_java_lib.SnowFlake;

public class Archiver {
	public static ArchiveResult archive(String request) throws CancelException {
		switch (GetRequestType.get(request)) {
			case Post: {
				try {
					String host = new URL(request).getHost();
					String[] instance_id = ArchiveInstanceAndGetID(host);
					String post_id = ArchivePostAndGetID(request, instance_id[0], instance_id[1]);
					return new ArchiveResult(Type.Post, post_id);
				} catch (Exception EX) {
					EX.printStackTrace();
					throw new CancelException();
				}
			}

			case User: {
				try {
					ResolveUserActorURL resolve = new ResolveUserActorURL(request);

					if (resolve.get_status()) {
						String[] instance_id = ArchiveInstanceAndGetID(resolve.get_host());
						String[] user_id = ArchiveUserAndGetID(resolve.get_actor_url(), resolve.get_host(), instance_id[0], instance_id[1]);
						return new ArchiveResult(Type.User, user_id[1]);
					} else {
						throw new CancelException();
					}
				} catch (Exception EX) {
					EX.printStackTrace();
					throw new CancelException();
				}
			}

			case Instance: {
				try {
					URL url = new URL(request);
					String host = url.getHost();
					String[] instance_id = ArchiveInstanceAndGetID(host);
					return new ArchiveResult(Type.Instance, instance_id[1]);
				} catch (Exception EX) {
					EX.printStackTrace();
					throw new CancelException();
				}
			}

			default: {
				throw new CancelException();
			}
		}
	}

	private static String ArchivePostAndGetID(String PostURL, String InstanceID, String InstanceDataID) throws Exception {
		String Host = new URL(PostURL).getHost();

		FETCH ajax = new FETCH(PostURL);
		ajax.SetHEADER("Accept", Main.ActivityJsonMimetype);
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

	private static String[] ArchiveUserAndGetID(String ActorURL, String Host, String InstanceID, String InstanceDataID) throws Exception {
		FETCH ajax = new FETCH(ActorURL);
		ajax.SetHEADER("Accept", Main.ActivityJsonMimetype);

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

	private static String[] ArchiveInstanceAndGetID(String Host) throws Exception {
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

	private static String getNodeinfoURL(String Host) throws Exception {
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
}
