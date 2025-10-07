package su.rumishistem.fediverse_gyotaku.Module;

import java.sql.SQLException;

import su.rumishistem.rumi_java_lib.ArrayNode;
import su.rumishistem.rumi_java_lib.SQL;

public class GetInstanceID {
	public static String from_host(String host) throws SQLException {
		ArrayNode sql = SQL.RUN("SELECT `ID` FROM `FG_INSTANCE` WHERE `HOST` = ?;", new Object[] {host});
		if (sql.length() == 0) return null;
		return sql.get(0).getData("ID").asString();
	}
}
