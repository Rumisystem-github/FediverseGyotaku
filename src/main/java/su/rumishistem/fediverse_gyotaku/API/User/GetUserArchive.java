package su.rumishistem.fediverse_gyotaku.API.User;

import java.sql.Timestamp;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;

import com.fasterxml.jackson.databind.ObjectMapper;

import su.rumishistem.fediverse_gyotaku.Main;
import su.rumishistem.rumi_java_lib.ArrayNode;
import su.rumishistem.rumi_java_lib.SQL;
import su.rumishistem.rumi_java_lib.SmartHTTP.HTTP_REQUEST;
import su.rumishistem.rumi_java_lib.SmartHTTP.HTTP_RESULT;
import su.rumishistem.rumi_java_lib.SmartHTTP.Type.EndpointFunction;

public class GetUserArchive implements EndpointFunction{
	@Override
	public HTTP_RESULT Run(HTTP_REQUEST r) throws Exception {
		if (r.GetEVENT().getURI_PARAM().get("ID") == null) {
			return new HTTP_RESULT(400, "{\"STATUS\":false, \"ERR\":\"URI_PARAM_GA_TALINAI\"}".getBytes(), Main.JsonMimetype);
		}

		ArrayNode sql_result = SQL.RUN("""
			SELECT
				P.*,
				(SELECT `ID` FROM `FG_USER_PROFILE` WHERE `DATE` < P.DATE AND `USER` = P.USER ORDER BY `DATE` DESC LIMIT 1) AS BEFORE_ID,
				(SELECT `ID` FROM `FG_USER_PROFILE` WHERE `DATE` > P.DATE AND `USER` = P.USER ORDER BY `DATE` ASC LIMIT 1) AS AFTER_ID,
				I.HOST,
				I_D.ID AS INSTANCE_ARCHIVE
			FROM
				`FG_USER_PROFILE` AS P
			LEFT JOIN
				`FG_INSTANCE_DATA` AS I_D
				ON I_D.ID = P.INSTANCE_DATA
			LEFT JOIN
				`FG_INSTANCE` AS I
				ON I.ID = I_D.INSTANCE
			WHERE
				P.ID = ?
			ORDER BY
				P.DATE DESC;
		""", new Object[] {
			r.GetEVENT().getURI_PARAM().get("ID")
		});

		ArrayNode row = sql_result.get(0);
		if (row == null) return new HTTP_RESULT(404, "{\"STATUS\": false, \"ERR\": \"NTF\"}".getBytes(), Main.JsonMimetype);

		LinkedHashMap<String, Object> item = new LinkedHashMap<String, Object>();
		item.put("ID", row.getData("ID").asString());
		item.put("DATE", ((Timestamp)row.getData("DATE").asObject()).toInstant().atOffset(ZoneOffset.ofHours(9)).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
		item.put("PREFERRED_UID", row.getData("PREFERRED_UID").asString());
		item.put("NAME", row.getData("NAME").asString());
		item.put("DESCRIPTION", row.getData("DESCRIPTION").asString());
		item.put("ICON_ORIGINAL", row.getData("ICON_ORIGINAL").asString());
		item.put("ICON_ARCHIVE", row.getData("ICON_ARCHIVE").asString());
		item.put("HEADER_ORIGINAL", row.getData("HEADER_ORIGINAL").asString());
		item.put("HEADER_ARCHIVE", row.getData("HEADER_ARCHIVE").asString());

		LinkedHashMap<String, Object> return_body = new LinkedHashMap<String, Object>();
		return_body.put("STATUS", true);
		if (!row.getData("BEFORE_ID").isNull()) {
			return_body.put("BEFORE", row.getData("BEFORE_ID").asString());
		} else {
			return_body.put("BEFORE", null);
		}
		if (!row.getData("AFTER_ID").isNull()) {
			return_body.put("AFTER", row.getData("AFTER_ID").asString());
		} else {
			return_body.put("AFTER", null);
		}

		return_body.put("USER", item);
		return_body.put("HOST", row.getData("HOST").asString());
		return_body.put("INSTANCE", row.getData("INSTANCE_ARCHIVE").asString());
		return new HTTP_RESULT(200, new ObjectMapper().writeValueAsString(return_body).getBytes(), Main.JsonMimetype);
	}
}
