package dev.oery.legacyresources.oracle;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ScenarioCatalog {
	private ScenarioCatalog() { }
	public static List<Scenario> read(Path path, String family, String id) throws IOException {
		JsonElement value=new JsonParser().parse(new String(Files.readAllBytes(path),StandardCharsets.UTF_8));
		if(!value.isJsonArray())throw new IllegalArgumentException(path+": catalog must be an array");
		List<Scenario> out=new ArrayList<Scenario>();Set<String> ids=new HashSet<String>();int index=0;
		for(JsonElement item:(JsonArray)value){if(!item.isJsonObject())throw new IllegalArgumentException(path+"["+index+"]: expected object");Scenario s=Scenario.from(item.getAsJsonObject(),path+"["+index+"]");if(!ids.add(s.id()))throw new IllegalArgumentException(path+": duplicate id "+s.id());if((family==null||family.equals(s.family()))&&(id==null||id.equals(s.id())))out.add(s);index++;}
		if(out.isEmpty())throw new IllegalArgumentException("No scenario matched family="+family+" id="+id);return out;
	}
}
