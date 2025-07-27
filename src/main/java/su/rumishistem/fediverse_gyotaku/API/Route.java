package su.rumishistem.fediverse_gyotaku.API;

import java.util.LinkedHashMap;

import com.fasterxml.jackson.databind.ObjectMapper;

import su.rumishistem.fediverse_gyotaku.Main;
import su.rumishistem.fediverse_gyotaku.API.Search.Search;
import su.rumishistem.fediverse_gyotaku.API.User.GetUserArchive;
import su.rumishistem.fediverse_gyotaku.API.User.GetUserArchiveList;
import su.rumishistem.rumi_java_lib.SmartHTTP.ERRORCODE;
import su.rumishistem.rumi_java_lib.SmartHTTP.HTTP_REQUEST;
import su.rumishistem.rumi_java_lib.SmartHTTP.HTTP_RESULT;
import su.rumishistem.rumi_java_lib.SmartHTTP.SmartHTTP;
import su.rumishistem.rumi_java_lib.SmartHTTP.Type.EndpointFunction;
import su.rumishistem.rumi_java_lib.SmartHTTP.Type.ErrorEndpointFunction;
import su.rumishistem.rumi_java_lib.SmartHTTP.Type.EndpointEntrie.Method;

public class Route {
	public static void init(SmartHTTP SH) {
		SH.SetRoute("/api/Search", Method.GET, new Search());

		SH.SetRoute("/api/Archive", Method.POST, new CreateArchive());

		SH.SetRoute("/api/User", Method.GET, new GetUserArchiveList());
		SH.SetRoute("/api/UserArchive", Method.GET, new GetUserArchive());

		//エラー類
		SH.SetRoute("/api/*", Method.ALL, new EndpointFunction() {
			@Override
			public HTTP_RESULT Run(HTTP_REQUEST r) throws Exception {
				return new HTTP_RESULT(404, "{\"STATUS\": false, \"ERR\": \"EP_NOTFOUND\"}".getBytes(), Main.JsonMimetype);
			}
		});

		SH.SetError("/api", ERRORCODE.INTERNAL_SERVER_ERROR, new ErrorEndpointFunction() {
			
			@Override
			public HTTP_RESULT Run(HTTP_REQUEST r, Exception ex) throws Exception {
				ex.printStackTrace();

				try {
					LinkedHashMap<String, Object> RETURN = new LinkedHashMap<String, Object>();
					RETURN.put("STATUS", false);
					RETURN.put("ERR", "SYSTEM_ERR");
					RETURN.put("EX", r.GetParam("EX"));
					return new HTTP_RESULT(500, new ObjectMapper().writeValueAsString(RETURN).getBytes(), Main.JsonMimetype);
				} catch (Exception EX) {
					EX.printStackTrace();
					return null;
				}
			}
		});
	}
}
