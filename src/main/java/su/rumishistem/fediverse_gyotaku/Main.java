package su.rumishistem.fediverse_gyotaku;

import static su.rumishistem.rumi_java_lib.LOG_PRINT.Main.LOG;

import java.io.File;
import java.net.URL;
import java.sql.Timestamp;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Function;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import su.rumishistem.fediverse_gyotaku.Module.SessionLogin;
import su.rumishistem.fediverse_gyotaku.Type.ArchiveType;
import su.rumishistem.rumi_java_lib.ArrayNode;
import su.rumishistem.rumi_java_lib.CONFIG;
import su.rumishistem.rumi_java_lib.EXCEPTION_READER;
import su.rumishistem.rumi_java_lib.SQL;
import su.rumishistem.rumi_java_lib.Ajax.Ajax;
import su.rumishistem.rumi_java_lib.Ajax.AjaxResult;
import su.rumishistem.rumi_java_lib.LOG_PRINT.LOG_TYPE;
import su.rumishistem.rumi_java_lib.SmartHTTP.ERRORCODE;
import su.rumishistem.rumi_java_lib.SmartHTTP.HTTP_REQUEST;
import su.rumishistem.rumi_java_lib.SmartHTTP.HTTP_RESULT;
import su.rumishistem.rumi_java_lib.SmartHTTP.SmartHTTP;
import su.rumishistem.rumi_java_lib.SmartHTTP.Type.EndpointFunction;
import su.rumishistem.rumi_java_lib.SmartHTTP.Type.ErrorEndpointFunction;
import su.rumishistem.rumi_java_lib.SmartHTTP.Type.EndpointEntrie.Method;

public class Main {
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

		SmartHTTP sh = new SmartHTTP(ConfigData.get("HTTP").getData("PORT").asInt());

		sh.SetRoute("/api/Archive", Method.POST, new EndpointFunction() {
			@Override
			public HTTP_RESULT Run(HTTP_REQUEST r) throws Exception {
				SessionLogin sl = new SessionLogin(r.GetEVENT().getHEADER_DATA().get("TOKEN"));
				if (!sl.Status()) {
					return new HTTP_RESULT(402, "{\"STATUS\": false}".getBytes(), "application/json; charset=UTF-8");
				}

				if (!sl.GetAccountData().get("ID").asText().equals("1")) {
					return new HTTP_RESULT(402, "{\"STATUS\": false}".getBytes(), "application/json; charset=UTF-8");
				}

				JsonNode body = new ObjectMapper().readTree(r.GetEVENT().getPOST_DATA());
				if (body.get("url") == null && body.get("url").isNull()) {
					return new HTTP_RESULT(400, "{\"STATUS\": false}".getBytes(), "application/json; charset=UTF-8");
				}

				String url = body.get("url").asText();
				ArchiveType type = ArchiveType.Instance;

				Ajax ajax = new Ajax(url);
				ajax.set_header("Accept", "application/activity+json");
				ajax.set_follow_redirect(false);
				AjaxResult result = ajax.GET();

				//リダイレクト？
				if (result.get_header("Location") != null) {
					return new HTTP_RESULT(405, "{\"STATUS\": false}".getBytes(), "application/json; charset=UTF-8");
				}

				if (new URL(url).getPath().toString().equals("/")) {
					//インスタンス
					type = ArchiveType.Instance;
				} else {
					if (result.get_code() != 200) {
						return new HTTP_RESULT(405, "{\"STATUS\": false}".getBytes(), "application/json; charset=UTF-8");
					}

					JsonNode result_body = new ObjectMapper().readTree(result.get_body_as_byte());
					switch (result_body.get("type").asText().toUpperCase()) {
						case "NOTE":
							type = ArchiveType.Post;
							break;
						case "PERSON":
							type = ArchiveType.User;
							break;

						default:
							return new HTTP_RESULT(405, "{\"STATUS\": false}".getBytes(), "application/json; charset=UTF-8");
					}
				}

				Archiver archiver = new Archiver();
				String id = archiver.archive(url, type, sl.GetAccountData().get("ID").asText());
				return new HTTP_RESULT(200, ("{\"STATUS\": true, \"ID\": \""+id+"\", \"TYPE\": \""+type.name().toUpperCase()+"\"}").getBytes(), "application/json; charset=UTF-8");
			}
		});

		sh.SetRoute("/api/Instance", Method.GET, new EndpointFunction() {
			@Override
			public HTTP_RESULT Run(HTTP_REQUEST r) throws Exception {
				if (r.GetEVENT().getURI_PARAM().get("ID") == null && r.GetEVENT().getURI_PARAM().get("HOST") == null) {
					return new HTTP_RESULT(400, "{\"STATUS\": false}".getBytes(), "application/json; charset=UTF-8");
				}

				ArrayNode sql = null;
				if (r.GetEVENT().getURI_PARAM().get("ID") != null) {
					sql = SQL.RUN("SELECT * FROM `FG_INSTANCE` WHERE `ID` = ?;", new Object[] {r.GetEVENT().getURI_PARAM().get("ID")});
				} else {
					sql = SQL.RUN("SELECT * FROM `FG_INSTANCE` WHERE `HOST` = ?;", new Object[] {r.GetEVENT().getURI_PARAM().get("HOST")});
				}

				//ある？
				if (sql.length() == 0) {
					return new HTTP_RESULT(404, "{\"STATUS\": false}".getBytes(), "application/json; charset=UTF-8");
				}

				LinkedHashMap<String, Object> instance = new LinkedHashMap<String, Object>();
				instance.put("ID", sql.get(0).getData("ID").asString());
				instance.put("HOST", sql.get(0).getData("HOST").asString());
				instance.put("REGIST_DATE", ((Timestamp)sql.get(0).getData("REGIST_DATE").asObject()).toInstant().atOffset(ZoneOffset.ofHours(9)).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
				instance.put("UPDATE_DATE", ((Timestamp)sql.get(0).getData("UPDATE_DATE").asObject()).toInstant().atOffset(ZoneOffset.ofHours(9)).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));

				LinkedHashMap<String, Object> return_body = new LinkedHashMap<String, Object>();
				return_body.put("STATUS", true);
				return_body.put("INSTANCE", instance);
				return new HTTP_RESULT(200, new ObjectMapper().writeValueAsString(return_body).getBytes(), "application/json; charset=UTF-8");
			}
		});

		sh.SetRoute("/api/Instance/Archive", Method.GET, new EndpointFunction() {
			@Override
			public HTTP_RESULT Run(HTTP_REQUEST r) throws Exception {
				if (r.GetEVENT().getURI_PARAM().get("ARCHIVE") == null && r.GetEVENT().getURI_PARAM().get("ID") != null) {
					//アーカイブ一覧
					ArrayNode sql = SQL.RUN("SELECT `ID`, `ARCHIVE_USER`, `REGIST_DATE` FROM `FG_INSTANCE_DATA` WHERE `INSTANCE` = ? ORDER BY `REGIST_DATE` DESC;", new Object[] {r.GetEVENT().getURI_PARAM().get("ID")});

					List<Object> archive_list = new ArrayList<Object>();
					for (int i = 0; i < sql.length(); i++) {
						ArrayNode row = sql.get(i);
						LinkedHashMap<String, Object> archive = new LinkedHashMap<String, Object>();
						archive.put("ID", row.getData("ID").asString());
						archive.put("USER", row.getData("ARCHIVE_USER").asString());
						archive.put("REGIST_DATE", ((Timestamp)row.getData("REGIST_DATE").asObject()).toInstant().atOffset(ZoneOffset.ofHours(9)).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
						archive_list.add(archive);
					}

					LinkedHashMap<String, Object> return_body = new LinkedHashMap<String, Object>();
					return_body.put("STATUS", true);
					return_body.put("LIST", archive_list);
					return new HTTP_RESULT(200, new ObjectMapper().writeValueAsString(return_body).getBytes(), "application/json; charset=UTF-8");
				} else if (r.GetEVENT().getURI_PARAM().get("ARCHIVE") != null) {
					//アーカイブ取得
					ArrayNode sql = SQL.RUN("SELECT D.ID, D.ARCHIVE_USER, D.REGIST_DATE, D.DUMP, D.INSTANCE_NAME, D.INSTANCE_DESCRIPTION, D.SOFTWARE_NAME, D.SOFTWARE_VERSION, I.HOST FROM `FG_INSTANCE_DATA` AS D JOIN `FG_INSTANCE` AS I ON I.ID = D.INSTANCE WHERE D.ID = ?;", new Object[] {r.GetEVENT().getURI_PARAM().get("ARCHIVE")});

					//ある？
					if (sql.length() == 0) {
						return new HTTP_RESULT(404, "{\"STATUS\": false}".getBytes(), "application/json; charset=UTF-8");
					}

					LinkedHashMap<String, Object> archive = new LinkedHashMap<String, Object>();
					archive.put("ID", sql.get(0).getData("ID").asString());
					archive.put("USER", sql.get(0).getData("ARCHIVE_USER").asString());
					archive.put("REGIST_DATE", ((Timestamp)sql.get(0).getData("REGIST_DATE").asObject()).toInstant().atOffset(ZoneOffset.ofHours(9)).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
					archive.put("DUMP", sql.get(0).getData("DUMP").asString());
					archive.put("HOST", sql.get(0).getData("HOST").asString());
					archive.put("INSTANCE_NAME", sql.get(0).getData("INSTANCE_NAME").asString());
					archive.put("INSTANCE_DESCRIPTION", sql.get(0).getData("INSTANCE_DESCRIPTION").asString());
					archive.put("SOFTWARE_NAME", sql.get(0).getData("SOFTWARE_NAME").asString());
					archive.put("SOFTWARE_VERSION", sql.get(0).getData("SOFTWARE_VERSION").asString());

					LinkedHashMap<String, Object> return_body = new LinkedHashMap<String, Object>();
					return_body.put("STATUS", true);
					return_body.put("ARCHIVE", archive);
					return new HTTP_RESULT(200, new ObjectMapper().writeValueAsString(return_body).getBytes(), "application/json; charset=UTF-8");
				} else {
					return new HTTP_RESULT(400, "{\"STATUS\": false}".getBytes(), "application/json; charset=UTF-8");
				}
			}
		});

		sh.SetRoute("/api/User", Method.GET, new EndpointFunction() {
			@Override
			public HTTP_RESULT Run(HTTP_REQUEST r) throws Exception {
				if (r.GetEVENT().getURI_PARAM().get("NAME") == null || r.GetEVENT().getURI_PARAM().get("HOST") == null) {
					return new HTTP_RESULT(400, "{\"STATUS\": false}".getBytes(), "application/json; charset=UTF-8");
				}

				ArrayNode sql = SQL.RUN("SELECT U.ID, U.REGIST_DATE, U.UPDATE_DATE FROM `FG_USER` AS U JOIN `FG_INSTANCE` AS I ON I.ID = U.INSTANCE WHERE U.NAME = ? AND I.HOST = ?;", new Object[] {r.GetEVENT().getURI_PARAM().get("NAME"), r.GetEVENT().getURI_PARAM().get("HOST")});

				//ある？
				if (sql.length() == 0) {
					return new HTTP_RESULT(404, "{\"STATUS\": false}".getBytes(), "application/json; charset=UTF-8");
				}

				LinkedHashMap<String, Object> user = new LinkedHashMap<String, Object>();
				user.put("ID", sql.get(0).getData("ID").asString());
				user.put("REGIST_DATE", ((Timestamp)sql.get(0).getData("REGIST_DATE").asObject()).toInstant().atOffset(ZoneOffset.ofHours(9)).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
				user.put("UPDATE_DATE", ((Timestamp)sql.get(0).getData("UPDATE_DATE").asObject()).toInstant().atOffset(ZoneOffset.ofHours(9)).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));

				//投稿
				ArrayNode post_sql = SQL.RUN("SELECT P.ID, P.REGIST_DATE, (SELECT `CONTENT` FROM `FG_POST_DATA` WHERE `POST` = P.ID LIMIT 1) AS CONTENT FROM `FG_POST` AS P ORDER BY P.REGIST_DATE DESC;", new Object[] {sql.get(0).getData("ID").asString()});
				List<Object> post_list = new ArrayList<Object>();
				for (int i = 0; i < post_sql.length(); i++) {
					LinkedHashMap<String, Object> post = new LinkedHashMap<String, Object>();
					post.put("ID", post_sql.get(i).getData("ID").asString());
					post.put("CONTENT", post_sql.get(i).getData("CONTENT").asString());
					post.put("REGIST_DATE", ((Timestamp)post_sql.get(i).getData("REGIST_DATE").asObject()).toInstant().atOffset(ZoneOffset.ofHours(9)).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
					post_list.add(post);
				}

				LinkedHashMap<String, Object> return_body = new LinkedHashMap<String, Object>();
				return_body.put("STATUS", true);
				return_body.put("USER", user);
				return_body.put("POST", post_list);
				return new HTTP_RESULT(200, new ObjectMapper().writeValueAsString(return_body).getBytes(), "application/json; charset=UTF-8");
			}
		});

		sh.SetRoute("/api/User/Archive", Method.GET, new EndpointFunction() {
			@Override
			public HTTP_RESULT Run(HTTP_REQUEST r) throws Exception {
				if (r.GetEVENT().getURI_PARAM().get("ARCHIVE") == null && r.GetEVENT().getURI_PARAM().get("ID") != null) {
					//アーカイブ一覧
					ArrayNode sql = SQL.RUN("SELECT `ID`, `ARCHIVE_USER`, `REGIST_DATE` FROM `FG_USER_DATA` WHERE `USER` = ? ORDER BY `REGIST_DATE` DESC;", new Object[] {r.GetEVENT().getURI_PARAM().get("ID")});

					//アーカイブ一覧
					List<Object> archive_list = new ArrayList<Object>();
					for (int i = 0; i < sql.length(); i++) {
						ArrayNode row = sql.get(i);
						LinkedHashMap<String, Object> archive = new LinkedHashMap<String, Object>();
						archive.put("ID", row.getData("ID").asString());
						archive.put("USER", row.getData("ARCHIVE_USER").asString());
						archive.put("REGIST_DATE", ((Timestamp)row.getData("REGIST_DATE").asObject()).toInstant().atOffset(ZoneOffset.ofHours(9)).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
						archive_list.add(archive);
					}

					LinkedHashMap<String, Object> return_body = new LinkedHashMap<String, Object>();
					return_body.put("STATUS", true);
					return_body.put("LIST", archive_list);
					return new HTTP_RESULT(200, new ObjectMapper().writeValueAsString(return_body).getBytes(), "application/json; charset=UTF-8");
				} else if (r.GetEVENT().getURI_PARAM().get("ARCHIVE") != null) {
					//アーカイブ取得
					ArrayNode sql = SQL.RUN("SELECT `ID`, `ARCHIVE_USER`, `REGIST_DATE`, `DUMP`, `NAME`, `DESCRIPTION`, `PUBLIC_KEY`, `ICON_ORIGINAL_URL`, `HEADER_ORIGINAL_URL` FROM `FG_USER_DATA` WHERE `ID` = ?;", new Object[] {r.GetEVENT().getURI_PARAM().get("ARCHIVE")});

					//ある？
					if (sql.length() == 0) {
						return new HTTP_RESULT(404, "{\"STATUS\": false}".getBytes(), "application/json; charset=UTF-8");
					}

					LinkedHashMap<String, Object> archive = new LinkedHashMap<String, Object>();
					archive.put("ID", sql.get(0).getData("ID").asString());
					archive.put("USER", sql.get(0).getData("ARCHIVE_USER").asString());
					archive.put("REGIST_DATE", ((Timestamp)sql.get(0).getData("REGIST_DATE").asObject()).toInstant().atOffset(ZoneOffset.ofHours(9)).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
					archive.put("DUMP", sql.get(0).getData("DUMP").asString());
					archive.put("NAME", sql.get(0).getData("NAME").asString());
					archive.put("DESCRIPTION", sql.get(0).getData("DESCRIPTION").asString());
					archive.put("ICON_ORIGINAL_URL", sql.get(0).getData("ICON_ORIGINAL_URL").asString());
					archive.put("HEADER_ORIGINAL_URL", sql.get(0).getData("HEADER_ORIGINAL_URL").asString());
					archive.put("PUBLIC_KEY", sql.get(0).getData("PUBLIC_KEY").asString());

					LinkedHashMap<String, Object> return_body = new LinkedHashMap<String, Object>();
					return_body.put("STATUS", true);
					return_body.put("ARCHIVE", archive);
					return new HTTP_RESULT(200, new ObjectMapper().writeValueAsString(return_body).getBytes(), "application/json; charset=UTF-8");
				} else {
					return new HTTP_RESULT(400, "{\"STATUS\": false}".getBytes(), "application/json; charset=UTF-8");
				}
			}
		});

		sh.SetRoute("/api/Post/Archive", Method.GET, new EndpointFunction() {
			@Override
			public HTTP_RESULT Run(HTTP_REQUEST r) throws Exception {
				if (r.GetEVENT().getURI_PARAM().get("ARCHIVE") == null && r.GetEVENT().getURI_PARAM().get("ID") != null) {
					//アーカイブ一覧
					ArrayNode sql = SQL.RUN("SELECT `ID`, `ARCHIVE_USER`, `REGIST_DATE` FROM `FG_POST_DATA` WHERE `POST` = ? ORDER BY `REGIST_DATE` DESC;", new Object[] {r.GetEVENT().getURI_PARAM().get("ID")});

					//アーカイブ一覧
					List<Object> archive_list = new ArrayList<Object>();
					for (int i = 0; i < sql.length(); i++) {
						ArrayNode row = sql.get(i);
						LinkedHashMap<String, Object> archive = new LinkedHashMap<String, Object>();
						archive.put("ID", row.getData("ID").asString());
						archive.put("USER", row.getData("ARCHIVE_USER").asString());
						archive.put("REGIST_DATE", ((Timestamp)row.getData("REGIST_DATE").asObject()).toInstant().atOffset(ZoneOffset.ofHours(9)).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
						archive_list.add(archive);
					}

					LinkedHashMap<String, Object> return_body = new LinkedHashMap<String, Object>();
					return_body.put("STATUS", true);
					return_body.put("LIST", archive_list);
					return new HTTP_RESULT(200, new ObjectMapper().writeValueAsString(return_body).getBytes(), "application/json; charset=UTF-8");
				} else if (r.GetEVENT().getURI_PARAM().get("ARCHIVE") != null) {
					//アーカイブ取得
					ArrayNode sql = SQL.RUN("""
						SELECT
							P.ID AS POST_ID,
							P.ARCHIVE_USER AS POST_ARCHIVE_USER,
							P.REGIST_DATE AS POST_REGIST_DATE,
							P.DUMP AS POST_DUMP,
							P.CONTENT AS POST_CONTENT,
							R.ID AS REPLY_ID,
							R.ARCHIVE_USER AS REPLY_ARCHIVE_USER,
							R.REGIST_DATE AS REPLY_REGIST_DATE,
							R.DUMP AS REPLY_DUMP,
							Q.ID AS QUOTE_ID,
							Q.ARCHIVE_USER AS QUOTE_ARCHIVE_USER,
							Q.REGIST_DATE AS QUOTE_REGIST_DATE,
							Q.DUMP AS QUOTE_DUMP
						FROM
							`FG_POST_DATA` AS P
						LEFT JOIN
							`FG_POST_DATA` AS R
								ON R.ID = P.REPLY
						LEFT JOIN
							`FG_POST_DATA` AS Q
								ON Q.ID = P.QUOTE
						WHERE
							P.ID = ?;
					""", new Object[] {
						r.GetEVENT().getURI_PARAM().get("ARCHIVE")
					});

					//ある？
					if (sql.length() == 0) {
						return new HTTP_RESULT(404, "{\"STATUS\": false}".getBytes(), "application/json; charset=UTF-8");
					}

					LinkedHashMap<String, Object> return_body = new LinkedHashMap<String, Object>();
					return_body.put("STATUS", true);
					return_body.put("POST", new LinkedHashMap<String, Object>(){
						{
							put("ID", sql.get(0).getData("POST_ID").asString());
							put("USER", sql.get(0).getData("POST_ARCHIVE_USER").asString());
							put("REGIST_DATE", ((Timestamp)sql.get(0).getData("POST_REGIST_DATE").asObject()).toInstant().atOffset(ZoneOffset.ofHours(9)).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
							put("DUMP", sql.get(0).getData("POST_DUMP").asString());
							put("CONTENT", sql.get(0).getData("POST_CONTENT").asString());
						}
					});

					if (sql.get(0).getData("REPLY_ID").isNull()) {
						return_body.put("REPLY", null);
					} else {
						return_body.put("REPLY", new LinkedHashMap<String, Object>(){
							{
								put("ID", sql.get(0).getData("REPLY_ID").asString());
								put("USER", sql.get(0).getData("REPLY_ARCHIVE_USER").asString());
								put("REGIST_DATE", ((Timestamp)sql.get(0).getData("REPLY_REGIST_DATE").asObject()).toInstant().atOffset(ZoneOffset.ofHours(9)).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
								put("DUMP", sql.get(0).getData("REPLY_DUMP").asString());
								put("CONTENT", sql.get(0).getData("REPLY_CONTENT").asString());
							}
						});
					}

					if (sql.get(0).getData("QUOTE_ID").isNull()) {
						return_body.put("QUOTE", null);
					} else {
						return_body.put("QUOTE", new LinkedHashMap<String, Object>(){
							{
								put("ID", sql.get(0).getData("QUOTE_ID").asString());
								put("USER", sql.get(0).getData("QUOTE_ARCHIVE_USER").asString());
								put("REGIST_DATE", ((Timestamp)sql.get(0).getData("QUOTE_REGIST_DATE").asObject()).toInstant().atOffset(ZoneOffset.ofHours(9)).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
								put("DUMP", sql.get(0).getData("QUOTE_DUMP").asString());
								put("CONTENT", sql.get(0).getData("QUOTE_CONTENT").asString());
							}
						});
					}
					return new HTTP_RESULT(200, new ObjectMapper().writeValueAsString(return_body).getBytes(), "application/json; charset=UTF-8");
				} else {
					return new HTTP_RESULT(400, "{\"STATUS\": false}".getBytes(), "application/json; charset=UTF-8");
				}
			}
		});

		//エラー類
		sh.SetRoute("/api/*", Method.ALL, new EndpointFunction() {
			@Override
			public HTTP_RESULT Run(HTTP_REQUEST r) throws Exception {
				return new HTTP_RESULT(404, "{\"STATUS\": false, \"ERR\": \"EP_NOTFOUND\"}".getBytes(), "application/json; charset=UTF-8");
			}
		});

		sh.SetError("/api", ERRORCODE.INTERNAL_SERVER_ERROR, new ErrorEndpointFunction() {
			
			@Override
			public HTTP_RESULT Run(HTTP_REQUEST r, Exception ex) throws Exception {
				LinkedHashMap<String, Object> RETURN = new LinkedHashMap<String, Object>();
				RETURN.put("STATUS", false);
				RETURN.put("ERR", "SYSTEM_ERR");
				RETURN.put("EX", EXCEPTION_READER.READ(ex));
				return new HTTP_RESULT(500, new ObjectMapper().writeValueAsString(RETURN).getBytes(), "application/json; charset=UTF-8");
			}
		});

		sh.Start();
	}
}
