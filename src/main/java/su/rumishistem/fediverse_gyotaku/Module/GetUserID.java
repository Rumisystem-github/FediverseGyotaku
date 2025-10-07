package su.rumishistem.fediverse_gyotaku.Module;

import java.sql.SQLException;

import su.rumishistem.rumi_java_lib.ArrayNode;
import su.rumishistem.rumi_java_lib.SQL;

public class GetUserID {
	public static String from_user_name(String name, String host) throws SQLException {
		String instance_id = GetInstanceID.from_host(host);

		ArrayNode sql = SQL.RUN("SELECT `ID` FROM `FG_USER` WHERE `NAME` = ? AND `INSTANCE` = ?;", new Object[] {name, instance_id});
		if (sql.length() == 0) return null;
		return sql.get(0).getData("ID").asString();
	}
}
