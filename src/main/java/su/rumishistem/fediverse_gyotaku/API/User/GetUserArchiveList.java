package su.rumishistem.fediverse_gyotaku.API.User;

import java.sql.Timestamp;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

import su.rumishistem.fediverse_gyotaku.Main;
import su.rumishistem.rumi_java_lib.ArrayNode;
import su.rumishistem.rumi_java_lib.SQL;
import su.rumishistem.rumi_java_lib.SmartHTTP.HTTP_REQUEST;
import su.rumishistem.rumi_java_lib.SmartHTTP.HTTP_RESULT;
import su.rumishistem.rumi_java_lib.SmartHTTP.Type.EndpointFunction;

public class GetUserArchiveList implements EndpointFunction{
	@Override
	public HTTP_RESULT Run(HTTP_REQUEST r) throws Exception {
		if (r.GetEVENT().getURI_PARAM().get("ID") == null) {
			return new HTTP_RESULT(400, "{\"STATUS\":false, \"ERR\":\"URI_PARAM_GA_TALINAI\"}".getBytes(), Main.JsonMimetype);
		}

		ArrayNode sql_result = SQL.RUN("""
			SELECT
				P.*
			FROM
				`FG_USER_PROFILE` AS P
			WHERE
				P.USER = ?
			ORDER BY
				P.DATE DESC;
		""", new Object[] {
			r.GetEVENT().getURI_PARAM().get("ID")
		});

		List<Object> archive_list = new ArrayList<Object>();
		for (int I = 0; I < sql_result.length(); I++) {
			ArrayNode row = sql_result.get(I);
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
			archive_list.add(item);
		}

		LinkedHashMap<String, Object> return_body = new LinkedHashMap<String, Object>();
		return_body.put("STATUS", true);
		return_body.put("LIST", archive_list);
		return new HTTP_RESULT(200, new ObjectMapper().writeValueAsString(return_body).getBytes(), Main.JsonMimetype);
	}
}
