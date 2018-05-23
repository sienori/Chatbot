import java.io.*;
import java.util.*;

// TwitterAPIを利用した雑談対話プログラム
public class ChatWithTwitter {
	public TwitterAPI twitter;

	//応答の履歴 同じ応答を繰り返さないために記録
	public List<String> responseHistory;
	//ある検索ワードに対して返したツイートのID 同じワードで検索するときはそれより過去のツイートを検索
	public Map<List<String>, String> searchHistory;

	private final int TARGET_MAX_LENGTH=30; //ユーザの入力文に対応するツイートの最大文字数 長いとノイズが増える
	private final int RESPONSE_MAX_LENGTH=30; //応答文の最大文字数
	private final String SEARCH_WORD_WHEN_NOT_FOUND="何言ってる OR 何の話"; //応答が見つからない時にこのワードで検索
	
	private final String SYSTEM_NAME="エガワ";
	private final String USER_NAME="ユーザ";

    public static void main(String[] args) {
		ChatWithTwitter instance = new ChatWithTwitter();

		//引数を渡すと自問自答を繰り返す(テスト用)
		//ChatWithTwitter instance = new ChatWithTwitter("こんにちは");
	}

    public ChatWithTwitter() {
		twitter=new TwitterAPI(); //ツイッターAPIの初期化
		responseHistory=new ArrayList<String>(); //応答の履歴
		searchHistory=new HashMap<List<String>, String>(); //検索ワードに対するツイートIDの履歴

		//最初の会話
		String output = "こんにちは！"; // システムの応答文
		System.out.println(SYSTEM_NAME+"：" + output);
		System.out.print(USER_NAME+"：");

		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
			String input;
			// ユーザによる文の入力を受け取る
			while((input = br.readLine()) != null) {
				// システムの応答を生成し出力する
				output = generateResponse(input);
				System.out.println(SYSTEM_NAME+"：" + output);
				System.out.print(USER_NAME+"：");
			}
			br.close();
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}

	//オーバーロード コンストラクタの引数に文字列を渡すと自問自答を繰り返す テスト用
	public ChatWithTwitter(String firstInput){
		twitter=new TwitterAPI(); //ツイッターAPIの初期化
		responseHistory=new ArrayList<String>(); //応答の履歴
		searchHistory=new HashMap<List<String>, String>(); //検索ワードに対するツイートIDの履歴

		String input=firstInput;
		String output;
		while(true){
			System.out.println(SYSTEM_NAME+"："+input);
			try{Thread.sleep(1000);} catch(Exception e){}

			output=generateResponse(input);
			System.out.println(USER_NAME+"："+output);
			try{Thread.sleep(1000);} catch(Exception e){}

			input=generateResponse(output);
		}
	}

	// 応答文を生成する 優先度の高い方法から試行し，成功したら返す
	private String generateResponse(String input) {
		String response="";
		List<String> prevWords=new ArrayList<String>();

		//ツイッター検索から応答を取得する 徐々に検索ワードの範囲を広げる
		for(int level=0; level<4; level++){
			//入力文から検索ワードを取得 levelが上がるほど数が減る
			List<String> words=getWordList(input, level);

			//前回のwordsと変わらなければ飛ばす
			if(words.containsAll(prevWords) && prevWords.containsAll(words)) continue;
			prevWords=words;

			//Twitterを検索しツイートのリプライから応答を取得 成功すればループを抜ける
			response=generateResponseByTwitter(words);
			if(!response.equals("")) break;
		}

		//会話に詰まった時「何の話？」というニュアンスのツイートを返す
		if(response.equals("")) response=generateResponseWhenNotFound();
		//最後の手段
		if(response.equals("")) response="何の話？";

		//応答履歴に追加して返す
		responseHistory.add(response);
		return response;
	}

	//検索ワードのリストを返す levelが上がるほど少なくなる
	private List<String> getWordList(String input, int level){
		 //入力文から形態素要素のリストを取得
		MorphemeList mophemeList=new MorphemeList();
		List<MorphemeInfo> infoList=mophemeList.get(input);

		//検索ワードのリスト
		List<String> words=new ArrayList<String>();

		String partPattern=""; //品詞のパターン
		switch(level){
			case 0: //入力文をそのまま検索ワードにする
				words.add(input);
				return words;
			case 1: //名詞と動詞と形容詞を抜き出す
				partPattern="名詞|動詞|形容詞";
				break;
			case 2: //名詞と動詞を抜き出す
				partPattern="名詞|動詞";
				break;
			case 3: //名詞を抜き出す
				partPattern="名詞";
				break;
		}

		//品詞が一致するものをリストに加えて返す
		for(MorphemeInfo info: infoList){
			if(info.hinsi1.matches(partPattern)){
				words.add(info.syutugen);
			}
		}
		return words;
	}

	//Twitterから応答文を生成 入力文に似たツイートに対するリプライを応答として返す
	private String generateResponseByTwitter(List<String> words){

		//過去に同じワードで検索されたことがあるなら，それより過去のツイートを検索する
		String maxId=searchHistory.get(words);
		List<Tweet> tweets=twitter.searchTargetTweets(words, "", maxId);

		String response="";
		int index=0;
		//各ツイートに対するリプライを探す
		for(Tweet tweet : tweets){
			index++;
			//リストの最後に到達したら最後のツイートのIDを記録 次回検索時はこれより過去のものを検索する
			if(index==tweets.size()) searchHistory.put(words, tweet.id);

			//文字数が多いツイートは飛ばす
			if(tweet.text.length() > TARGET_MAX_LENGTH) continue;

			//リプライを取得
			Tweet reply=twitter.searchReply(tweet, maxId);

			//リプライからURLなど不要な要素を削除
			reply.text=replaceResponse(reply.text);

			//応答として相応しいか判定
			if(isAvailableResponse(reply.text)){
				 //応答に使用したツイートのIDを記録 次回はこれより過去のツイートを検索
				searchHistory.put(words, tweet.id);
				response=reply.text;
				break;
			}
		}

		return response;
	}

	//会話に詰まった時「何の話？」というニュアンスのツイートを返す
	private String generateResponseWhenNotFound(){
		List<String> word=new ArrayList<String>(){{add(SEARCH_WORD_WHEN_NOT_FOUND);}};

		//過去に同じワードで検索されたことがあるなら，それより過去のツイートを検索する
		String maxId=searchHistory.get(word);
		List<Tweet> tweets=twitter.searchTargetTweets(word, "exclude:retweets lang:ja", maxId);

		String response="";
		int index=0;
		for(Tweet tweet : tweets){
			index++;
			//リストの最後に到達したら最後のツイートのIDを記録 次回検索時はこれより過去のものを検索する
			if(index==tweets.size()) searchHistory.put(word, tweet.id);
			
			//文字数が多いツイートは飛ばす
			if(tweet.text.length() > TARGET_MAX_LENGTH) continue;

			//ツイートからURLなど不要な要素を削除
			tweet.text=replaceResponse(tweet.text);

			//応答として相応しいか判定
			if(isAvailableResponse(tweet.text)){
				//応答に使用したツイートのIDを記録 次回はこれより過去のツイートを検索
				searchHistory.put(word, tweet.id);				
				response=tweet.text;
				break;
			}
		}

		return response;
	}
    
	//応答文から不要な要素を削除
    private static String replaceResponse(String response){
		String replyP="@[^ ]+( |$)"; //リプライのパターン @hogehoge
        String hashP="(^| )#[^ ]+"; //ハッシュタグのパターン #fuga
		String urlP="(http://|https://){1}[\\w\\.\\-/:\\#\\?\\=\\&\\;\\%\\~\\+]+"; //URLのパターン

		//ツイートからマッチするパターンを削除
		response=response.replaceAll(replyP+"|"+hashP+"|"+urlP, "");
		return response;
	}
	
	//応答として利用可能か判定
	private boolean isAvailableResponse(String response){
		if(response.contains("\n")) return false; //改行が含まれていたら不可
		if(isIncludeEmoji(response)) return false; //絵文字を含む場合は不可
		if(response.length()<=1 | RESPONSE_MAX_LENGTH < response.length()) return false; //文字数が少ないOR多い場合は不可
		if(responseHistory.contains(response)) return false; //過去の応答と重複する場合は不可

		return true;
	}

	//絵文字を含むか判定 
	private static boolean isIncludeEmoji(String str){
		String newStr;
		try{
			byte[] bytes= str.getBytes("EUC_JP"); //文字コードをEUCに変換
			newStr= new String(bytes, "EUC_JP"); //元の文字コードに戻す
			return(!str.equals(newStr));  //元の文字列と比較して絵文字の有無を判定
		}catch(UnsupportedEncodingException e){
			e.printStackTrace();
		}
		return true;
	}
}
