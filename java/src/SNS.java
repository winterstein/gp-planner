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

import winterwell.maths.GridInfo;
import winterwell.maths.chart.HistogramChart;
import winterwell.maths.chart.RenderWithFlot;
import winterwell.maths.stats.distributions.d1.Constant1D;
import winterwell.maths.stats.distributions.d1.Gaussian1D;
import winterwell.maths.stats.distributions.d1.GridDistribution1D;
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


		GridDistribution1D dist = new GridDistribution1D(new GridInfo(0, 5000, 200));		
		
		try {
			conn = SqlUtils.getConnection();
			
			json.append("[");
//			// Select datazone geometry
//			Iterable<Object[]> ds = SqlUtils.executeQuery(
//					"select '','0',b.dz_code,b.dz_name,astext(b.the_geom),c.popest2011,b.stdarea_ha from datazone_2001_bdry b, datazone_2001_cent c where b.dz_code=c.dz_code", conn, 0);
//			process(ds, json);
			
			// select practice markers
			Iterable<Object[]> rs = SqlUtils.executeQuery(
					"select p.name,p.size,b.dz_code,b.dz_name,astext(ST_Centroid(b.the_geom)),c.popest2011,b.stdarea_ha,pl.age_all,(select count(g.gmccode) from gps g where g.practicecode=p.practicecode) as gps from practice_details p,practice_listsize pl,datazone_2001_bdry b, datazone_2001_cent c,datazones pd where p.postcode=pd.postcode and b.dz_code=pd.dz_code and  b.dz_code=c.dz_code and pl.practicecode=p.practicecode", conn, 0);						
			process(rs, json, dist);
			
			StrUtils.pop(json, 1);
			json.append("]");
			System.out.println(cnt);
			
		} finally {
			SqlUtils.close(conn);
		}
		
		HistogramChart chart = new HistogramChart(dist);
		chart.setName("Patients per GP");
		new RenderWithFlot(500, 300).renderToFile(chart, new File("patient_dr_ratio.png"));
		
		html = html.replaceFirst("START DATA.+?END DATA", "*/ var DATA = "+json+"; /*");
		
		FileUtils.write(new File("out/BigLeaflet.html"), html);
		WebUtils.display(new File("out/BigLeaflet.html"));
	}

	private static void process(Iterable<Object[]> rs, StringBuilder json, GridDistribution1D dist) {
		double max = 0;
		SimpleJson sj = new SimpleJson();
		IDistribution1D jitter = 
//				new Constant1D(0); 
				new Gaussian1D(0, 0.0000001);
		
		Pattern p = Pattern.compile("\\([^\\)\\(]+\\)");
		for (Object[] row : rs) {
//			for(int hack=0; hack<3;hack++) {
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
				
				jobj.put("name", row[0]);
				
				// CRUDE colour coding of practice size 
				if ( ! StrUtils.isNumber((String)row[1])) {
					System.out.println("Skip "+Printer.toString(row));
					continue;
				}
				
				double psize = Double.valueOf((String)row[1]);
				int gps = (int) MathUtils.num(row[8]); 
				double patient_dr_ratio = psize/gps;
				dist.count(patient_dr_ratio);
				
	//			json.append(Utils.getRandomMember(Arrays.asList("red","green","blue","purple","cyan","yellow")));
				jobj.put("patients", psize);
				jobj.put("gps", gps);
				
				String js = sj.toJson(jobj);				
				json.append(js);
				json.append(",");
//			}
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
