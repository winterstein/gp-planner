import java.awt.Color;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import winterwell.maths.stats.distributions.d1.Constant1D;
import winterwell.maths.stats.distributions.d1.Gaussian1D;
import winterwell.maths.stats.distributions.d1.IDistribution1D;
import winterwell.utils.MathUtils;
import winterwell.utils.Printer;
import winterwell.utils.StrUtils;
import winterwell.utils.Utils;
import winterwell.utils.containers.ArrayMap;
import winterwell.utils.gui.GuiUtils;
import winterwell.utils.io.CSVReader;
import winterwell.utils.io.FileUtils;
import winterwell.utils.io.LineReader;
import winterwell.utils.io.SqlUtils;
import winterwell.utils.io.SqlUtils.DBOptions;
import winterwell.utils.web.SimpleJson;
import winterwell.utils.web.WebUtils;
import winterwell.utils.web.WebUtils2;
import winterwell.web.WebPage;

/**
 * 
 */

/**
 * @author daniel
 *
 */
public class SNS {

	private static int cnt;

	public static void main(String[] args) throws Exception {
//		doPostcodeMappings();
		doBigLeafletPage();
	}

	private static void doBigLeafletPage() {
		File f = new File("out/LeafletTemplate.html");
		System.out.println(f.getAbsolutePath());
		String html = FileUtils.read(f);
		
		DBOptions options = new DBOptions();
		options.dbUrl = "jdbc:postgresql:nhs";
		options.dbUser = "hackworker";
		options.dbPassword = "hackpwd";
		SqlUtils.setDBOptions(options);
		Connection conn = null;
		StringBuilder json = new StringBuilder();

		try {
			conn = SqlUtils.getConnection();
			
			json.append("[");
//			// Select datazone geometry
//			Iterable<Object[]> ds = SqlUtils.executeQuery(
//					"select '','0',b.dz_code,b.dz_name,astext(b.the_geom),c.popest2011,b.stdarea_ha from datazone_2001_bdry b, datazone_2001_cent c where b.dz_code=c.dz_code", conn, 0);
//			process(ds, json);
			
			// select practice markers
			Iterable<Object[]> rs = SqlUtils.executeQuery(
					"select p.name,p.size,b.dz_code,b.dz_name,astext(ST_Centroid(b.the_geom)),c.popest2011,b.stdarea_ha from practice_details p,datazone_2001_bdry b, datazone_2001_cent c,postcode_datazone pd where p.postcode=pd.postcode and b.dz_code=pd.datazone and  b.dz_code=c.dz_code", conn, 0);
			process(rs, json);
			
			StrUtils.pop(json, 1);
			json.append("]");
			System.out.println(cnt);
			
		} finally {
			SqlUtils.close(conn);
		}
		
		html = html.replaceFirst("START DATA.+?END DATA", "*/ var DATA = "+json+"; /*");
		
		FileUtils.write(new File("../out/BigLeaflet.html"), html);
		WebUtils.display(new File("../out/BigLeaflet.html"));
	}

	private static void process(Iterable<Object[]> rs, StringBuilder json) {
		double max = 0;
		SimpleJson sj = new SimpleJson();
		IDistribution1D jitter = 
				new Constant1D(0); 
//				new Gaussian1D(0, 0.00001);
		
		Pattern p = Pattern.compile("\\([^\\)\\(]+\\)");
		for (Object[] row : rs) {
			cnt++;				
			// p.name,p.size,b.dz_code,b.dz_name,astext(c.the_geom),c.popest2011,b.stdarea_ha
			String geom = (String) row[4]; // 2
//			if (cnt > 1000) break;
			Matcher m = p.matcher(geom);
			boolean ok = m.find();				
			if ( ! ok) {
				continue;
			}
			Map jobj = new ArrayMap();
			String type = geom.toLowerCase().contains("point")? "point" : "polygon";
			String poly = m.group().substring(1, m.group().length()-1);
			String[] bits = poly.split(",");
			List geometry = new ArrayList();
			if (type.equals("point")) {
				String[] b2 = bits[0].split(" ");
				double x = Double.valueOf(b2[1])*1.0001 + jitter.sample();
				double y = Double.valueOf(b2[0]) - 0.001 + jitter.sample();
				geometry.add(x);
				geometry.add(y);					
			} else {
				for(String b : bits) {
					String[] b2 = b.split(" ");
					double x = Double.valueOf(b2[1]) * 1.0001;
					double y = Double.valueOf(b2[0])  - 0.001;
					geometry.add(Arrays.asList(x, y));
				}
			}
			jobj.put(type, geometry);
			
			jobj.put("name", row[0]+": "+row[1]);
			
			// CRUDE colour coding of practice size 
			if ( ! StrUtils.isNumber((String)row[1])) {
				continue;
			}
			double psize = Double.valueOf((String)row[1]);
			Color col = null;
			if (psize > 10000) {
				col = Color.red;
			} else if (psize > 7500){
				col = Color.yellow;
			} else if (psize > 2000) {
				col = Color.green;
			} else {
				col = Color.blue;
			}
			
			if (psize==0) {
				int pop = row[5] instanceof Number? ((Number)row[5]).intValue() : Integer.parseInt((String)row[5]);
				double ha = row[6] instanceof Number? ((Number)row[6]).doubleValue() : Double.parseDouble((String)row[6]);
				double r = pop/ha;
				if (r > max) max = r;
				r = r/50;
				r = Math.min(1, r);
				col = GuiUtils.fade(r, Color.white, Color.red);
			}
//			json.append(Utils.getRandomMember(Arrays.asList("red","green","blue","purple","cyan","yellow")));
			jobj.put("color", WebUtils2.color2html(col));
			
			String js = sj.toJson(jobj);				
			json.append(js);
			json.append(",");
		}
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
