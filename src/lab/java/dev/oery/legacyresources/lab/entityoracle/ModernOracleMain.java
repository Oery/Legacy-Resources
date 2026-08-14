package dev.oery.legacyresources.lab.entityoracle;

import dev.oery.legacyresources.oracle.ComparisonReport;
import dev.oery.legacyresources.oracle.Scenario;
import dev.oery.legacyresources.oracle.ScenarioCatalog;
import dev.oery.legacyresources.oracle.Trace;
import dev.oery.legacyresources.oracle.TraceComparator;
import dev.oery.legacyresources.oracle.TraceJson;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

public final class ModernOracleMain {
	private ModernOracleMain() { }
	public static void main(String[] raw)throws Exception{SharedConstants.setVersion(DetectedVersion.BUILT_IN);Bootstrap.bootStrap();Map<String,String>a=args(raw);Path project=Path.of(System.getProperty("legacyOracle.project","."));Path catalog=project.resolve("src/entityOracle/resources/scenarios/pilot-scenarios.json"),captures=project.resolve("build/legacy-entity-oracle/1.8.9"),reports=project.resolve("build/reports/legacy-entity-oracle");boolean failed=false;
		for(Scenario s:ScenarioCatalog.read(catalog,a.get("entity"),a.get("scenario"))){Path snapshot=captures.resolve(s.family()).resolve(s.id()+".json");if(!Files.isRegularFile(snapshot))throw new IllegalStateException("Missing cached oracle snapshot "+snapshot+"\nRun ./gradlew captureLegacyEntityOracle -PlegacyScenario="+s.id()+" once; comparison never regenerates it.");Trace oracle=TraceJson.read(snapshot);if(!"3870888a6c3d349d3771a3e9d16c9bf5e076b908".equals(oracle.jarSha1))throw new IllegalStateException("Stale/unverified snapshot "+snapshot+" has jar hash "+oracle.jarSha1);ComparisonReport report=TraceComparator.compare(oracle,ModernModelTracer.capture(s),s.family());Path json=reports.resolve(s.family()).resolve(s.id()+".json");Files.createDirectories(json.getParent());Files.writeString(json,TraceJson.string(report),StandardCharsets.UTF_8);System.out.println(report.concise());if(!report.matches){System.out.println("  "+report.rerunCommand);failed=true;}}
		if(failed)throw new IllegalStateException("Legacy entity geometry mismatches; reports are under "+reports);}
	private static Map<String,String> args(String[]v){Map<String,String>m=new LinkedHashMap<>();for(int i=0;i<v.length;i+=2)m.put(v[i].substring(2),v[i+1]);return m;}
}
