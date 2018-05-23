import java.io.*;
import java.net.*;
import java.util.*;
import com.eclipsesource.json.*;

public class TwitterAPI {
	private String token;
	private final String CREDENTIAL="UjhQUXFaVWpqcDM4TkxDdHNydVpIMjNhdzpjSE9aaGtia0lZT2IzazlOOEIyR21QOEJNMVk3WEZUaEZXdW9UdmM2Z21iT0F2S3dKbw";
	private final int TARGET_COUNT=50; //ツイートの検索数
	private final int REPLY_COUNT=100; //リプライの検索数

	TwitterAPI(){
		token=getToken();
	}
	
	//トークンを取得
	private String getToken(){
		String response = "";
		try {
	    	// HTTP接続を確立し，処理要求を送る
			URL url= new URL("https://api.twitter.com/oauth2/token");
	    	HttpURLConnection conn = (HttpURLConnection) url.openConnection();
	    	conn.setDoOutput(true);
	    	conn.setRequestMethod("POST");
	    	conn.setRequestProperty("Authorization", "Basic " + CREDENTIAL);
	    	conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");

	    	OutputStreamWriter out = new OutputStreamWriter(conn.getOutputStream());
			out.write("grant_type=client_credentials");
	    	out.close();

			conn.connect();
			
			final int statusCode=conn.getResponseCode();
			if(statusCode==200){
	    		// Webサーバからの応答を受け取る
				BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(),"UTF-8"));
				response=br.readLine();
		    	br.close();
			}
	    	conn.disconnect();
		} catch (IOException ex) {
		    ex.printStackTrace();
		}

		JsonObject json=Json.parse(response).asObject();
		return json.getString("access_token", "");
    }

	//指定文字列でツイートを検索
	public List<Tweet> searchTargetTweets(List<String> words, String operator, String maxId){
		if(operator.equals("")) operator="exclude:retweets min_replies:1 lang:ja"; //デフォルトの検索オプション

		//検索文字列をスペース区切りで繋げる
		String query="";
		for(String word : words){
			query+=word+" ";
		}
		query+=operator;
		query=encode(query);

		//APIからツイートを取得
		List<Tweet> tweets=getTweet(query, TARGET_COUNT, maxId);
		
		return tweets;
	}

	//指定ツイートに対するリプライを検索
	public Tweet searchReply(Tweet tweet, String maxId){
		final String query=encode("to:"+tweet.userName);
		final List<Tweet> replyList=getTweet(query, REPLY_COUNT, maxId); //元ツイートのユーザ宛のリプライを全て取得

		//リプライ先のIDが元ツイートのIDと一致していればそれを返す
		for(Tweet reply : replyList){
			if(tweet.id.equals(reply.replyId)) return reply;
		}

		return new Tweet();
	}

	//URLエンコードを行う
	private static String encode(String str){
		try {
			str = URLEncoder.encode(str, "UTF-8");
		} catch (IOException ex) {
		    ex.printStackTrace();
		}
		return str;
	}

	//クエリを指定してAPIからツイートを取得
	private List<Tweet> getTweet(String query, int count, String maxId){
		//maxIdより過去のツイートを取得する
		String maxIdParam="";
		if(!(maxId==null)) maxIdParam="&max_id="+maxId;

		JsonObject resultJson=new JsonObject();
		try {
	    	// リクエストを送信
			URL url= new URL("https://api.twitter.com/1.1/search/tweets.json?q="+query+"&count="+count+maxIdParam);
	    	HttpURLConnection conn = (HttpURLConnection) url.openConnection();
	    	conn.setRequestMethod("GET");
	    	conn.setRequestProperty("Authorization", "Bearer " + token);
			conn.setRequestProperty("Content-Type", "application/json");
			conn.connect();
			
			// 成功すればレスポンスをjsonで取り出す
			final int statusCode=conn.getResponseCode();
			if(statusCode==200){
	    		BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(),"UTF-8"));
				String response=br.readLine();
		    	br.close();

				resultJson=Json.parse(response).asObject();
			}
	    	conn.disconnect();
		} catch (IOException ex) {
		    ex.printStackTrace();
		}

		//jsonをリストに変換して返す
		return jsonToTweets(resultJson);
	}

	//APIからのレスポンスをTweetのリストに変換する
	private List<Tweet> jsonToTweets(JsonObject results){
		List<Tweet> tweets=new ArrayList<Tweet>();
		try{
			JsonArray statuses=results.get("statuses").asArray();
			for(JsonValue item : statuses){
				Tweet tweet=new Tweet();

				tweet.text=item.asObject().get("text").asString();
				tweet.id=item.asObject().get("id_str").asString();
				tweet.userName=item.asObject().get("user").asObject().get("screen_name").asString();
				if(!item.asObject().get("in_reply_to_status_id_str").isNull()){
					tweet.replyId=item.asObject().get("in_reply_to_status_id_str").asString();
				} 

				tweets.add(tweet);
			}
		}catch(NullPointerException ex){
		}

		return tweets;
	}
}
