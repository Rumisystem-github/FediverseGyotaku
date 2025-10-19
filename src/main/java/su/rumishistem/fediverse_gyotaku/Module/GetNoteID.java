package su.rumishistem.fediverse_gyotaku.Module;

import java.sql.SQLException;

import su.rumishistem.rumi_java_lib.ArrayNode;
import su.rumishistem.rumi_java_lib.SQL;

public class GetNoteID {
	public static String from_remote_id(String id, String host) throws SQLException {
		String instance_id = GetInstanceID.from_host(host);

		ArrayNode sql = SQL.RUN("SELECT `ID` FROM `FG_POST` WHERE `REMOTE_ID` = ? AND `INSTANCE` = ?;", new Object[] {id, instance_id});
		if (sql.length() == 0) return null;
		return sql.get(0).getData("ID").asString();
	}
}
