import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import winterwell.utils.Printer;
import winterwell.utils.StrUtils;
import winterwell.utils.Utils;
import winterwell.utils.io.CSVReader;
import winterwell.utils.io.FileUtils;
import winterwell.utils.io.LineReader;
import winterwell.utils.io.SqlUtils;
import winterwell.utils.io.SqlUtils.DBOptions;
import winterwell.utils.web.WebUtils;
import winterwell.web.WebPage;

/**
 * 
 */

/**
 * @author daniel
 *
 */
public class SNS {

	public static void main(String[] args) throws Exception {
//		doPostcodeMappings();
		doBigLeafletPage();
	}

	private static void doBigLeafletPage() {
		String html = FileUtils.read(new File("../out/LeafletTemplate.html"));
		
		DBOptions options = new DBOptions();
		options.dbUrl = "jdbc:postgresql:nhs";
		options.dbUser = "hackworker";
		options.dbPassword = "hackpwd";
		SqlUtils.setDBOptions(options);
		Connection conn = null;
		StringBuilder json = new StringBuilder();
		try {
			conn = SqlUtils.getConnection();
			Iterable<Object[]> rs = SqlUtils.executeQuery(
					"select dz_code,dz_name,astext(the_geom) from datazone_2001_bdry", conn, 0);
			int cnt = 0;
			json.append("[");
			Pattern p = Pattern.compile("\\([^\\)\\(]+\\)");
			for (Object[] row : rs) {
				cnt++;
//				Printer.out(row[2]);
//				if (cnt > 1000) break;
				Matcher m = p.matcher((String)row[2]);
				boolean ok = m.find();				
				if ( ! ok) {
					continue;
				}
				String poly = m.group().substring(1, m.group().length()-1);
				String[] bits = poly.split(",");				
				json.append("{'polygon':[");
				for(String b : bits) {
					String[] b2 = b.split(" ");
					json.append("["+b2[1]+","+b2[0]+"],");
				}
				StrUtils.pop(json, 1);
				json.append("],'color':'");
				json.append(Utils.getRandomMember(Arrays.asList("red","green","blue","purple","cyan","yellow")));
				json.append("'},");				
			}
			StrUtils.pop(json, 1);
			json.append("]");
		} finally {
			SqlUtils.close(conn);
		}
		
		html = html.replaceFirst("START DATA.+?END DATA", "*/ var DATA = "+json+"; /*");
		
		FileUtils.write(new File("../out/BigLeaflet.html"), html);
		WebUtils.display(new File("../out/BigLeaflet.html"));
	}

	private static void doPostcodeMappings() throws Exception {
		File f = new File("../data/raw/SNS_postcode_datazone00393445.txt");
		File fout = FileUtils.changeType(f, "sql");
		CSVReader lr = new CSVReader(f, ',','"');
		lr.next(); // throw first row
		BufferedWriter sql = FileUtils.getWriter(fout);
		for (String[] row : lr) {
			String pcode = row[0]+" "+row[1];
			String dz = row[3];
			sql.write("insert into postcode_datazone values ("
						+SqlUtils.sqlEncode(pcode)+","+SqlUtils.sqlEncode(dz)+");\n");
		}
		FileUtils.close(sql);
	}
}
