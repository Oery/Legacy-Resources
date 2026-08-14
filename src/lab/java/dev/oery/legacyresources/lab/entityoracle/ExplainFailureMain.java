package dev.oery.legacyresources.lab.entityoracle;

import com.google.gson.Gson;
import dev.oery.legacyresources.oracle.ComparisonReport;
import dev.oery.legacyresources.oracle.Scenario;
import dev.oery.legacyresources.oracle.ScenarioCatalog;
import dev.oery.legacyresources.oracle.TraceJson;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ExplainFailureMain {
	private ExplainFailureMain() { }
	public static void main(String[] raw)throws Exception{Map<String,String>a=new LinkedHashMap<>();for(int i=0;i<raw.length;i+=2)a.put(raw[i].substring(2),raw[i+1]);Path project=Path.of(System.getProperty("legacyOracle.project",".")),catalog=project.resolve("src/entityOracle/resources/scenarios/pilot-scenarios.json"),reports=project.resolve("build/reports/legacy-entity-oracle");ComparisonReport worst=null;Scenario input=null;for(Scenario s:ScenarioCatalog.read(catalog,a.get("entity"),a.get("scenario"))){Path p=reports.resolve(s.family()).resolve(s.id()+".json");if(!Files.isRegularFile(p))continue;ComparisonReport r=new Gson().fromJson(Files.readString(p),ComparisonReport.class);if(!r.matches&&(worst==null||r.score>worst.score)){worst=r;input=s;}}if(worst==null)throw new IllegalStateException("No failing report found; run compareLegacyEntityModels first");ComparisonReport.Mismatch m=worst.mismatches.stream().max(Comparator.comparingDouble(x->x.maxPositionError)).orElse(worst.mismatches.get(0));System.out.println(worst.concise());System.out.println("scenario: "+input);System.out.println("worst: "+m.category+" pass="+m.pass+" legacyDraw="+m.oracleDraw+" modernPart="+m.modernPart+"\n  "+m.message);if(m.oraclePosition!=null)System.out.println("  vertex "+m.vertex+" oracle="+java.util.Arrays.toString(m.oraclePosition)+" candidate="+java.util.Arrays.toString(m.candidatePosition));System.out.println("rerun: "+worst.rerunCommand);}
}
