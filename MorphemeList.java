import java.io.*;
import java.util.*;

public class MorphemeList{
    //入力文の形態素要素をリストで返す
	public static List<MorphemeInfo> get(String input){
        List<MorphemeInfo> infoList=new ArrayList<MorphemeInfo>();
        
		try {
			// MeCabを起動し，入出力用のストリームを生成する
			Process process = Runtime.getRuntime().exec("mecab");
			BufferedReader br = new BufferedReader(
			new InputStreamReader(process.getInputStream()));
			PrintWriter pw = new PrintWriter(new BufferedWriter(
			new OutputStreamWriter(process.getOutputStream())));

			// 入力文字列をMeCabに送って形態素解析させる
			pw.println(input); // MeCabへ文を送信
			pw.flush();
			
			String line2;
			while ((line2 = br.readLine()) != null) {  // 解析結果をMeCabから受信
				if (line2.equals("EOS")) break; // EOSは文の終わりを表す
				
				MorphemeInfo info=new MorphemeInfo();
				String[] split = line2.split("[\t,]"); // 区切り文字で分割
				info.syutugen = split[0];
				info.hinsi1 = split[1];
				info.hinsi2 = split[2];
				info.hinsi3 = split[3];
				info.hinsi4 = split[4];
				info.katsuyo1 = split[5];
				info.katsuyo2 = split[6];
				info.kihon = split[7];

				infoList.add(info);
			}

			br.close();
			pw.close();
			process.destroy();
		} catch (IOException ex) {
			ex.printStackTrace();
		}
		return infoList;
    }
}